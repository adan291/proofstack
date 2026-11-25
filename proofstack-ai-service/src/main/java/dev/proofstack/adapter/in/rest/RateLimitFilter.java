package dev.proofstack.adapter.in.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fixed-window rate limit per client address on {@code POST /api/ask}: every answer costs model time.
 *
 * <p>Uses {@code request.getRemoteAddr()}. Behind a reverse proxy, enable Spring's
 * {@code server.forward-headers-strategy} so the real client address is used; never trust a raw
 * {@code X-Forwarded-For} header from the internet.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private record Window(long start, AtomicInteger count) {
    }

    private final int limitPerMinute;
    private final Clock clock;
    private final Runnable onRejected;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(int limitPerMinute, Clock clock) {
        this(limitPerMinute, clock, () -> { });
    }

    /** @param onRejected hook called on every rejected request (e.g. a metrics counter), kept framework-free. */
    public RateLimitFilter(int limitPerMinute, Clock clock, Runnable onRejected) {
        if (limitPerMinute < 1) {
            throw new IllegalArgumentException("limitPerMinute must be >= 1");
        }
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
        this.onRejected = onRejected;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // The same path Spring MVC uses to pick the controller: without ";params", decoded, with "//" merged.
        // Comparing the raw request URI let "/api/ask;x=1" reach the controller without being counted.
        String path = PATHS.getPathWithinApplication(request);
        return !("POST".equals(request.getMethod()) && "/api/ask".equals(path));
    }

    private static final org.springframework.web.util.UrlPathHelper PATHS = new org.springframework.web.util.UrlPathHelper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
        }
        Window window = windows.compute(request.getRemoteAddr(), (ip, w) ->
                w == null || now - w.start() >= WINDOW_MILLIS ? new Window(now, new AtomicInteger()) : w);

        if (window.count().incrementAndGet() > limitPerMinute) {
            onRejected.run();
            long retryAfter = Math.max(1, (window.start() + WINDOW_MILLIS - now + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType("application/json");
            response.getOutputStream().write("{\"error\":\"too many questions, try again later\"}"
                    .getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
