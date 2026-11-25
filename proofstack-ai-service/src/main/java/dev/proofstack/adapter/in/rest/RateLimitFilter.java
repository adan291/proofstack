package dev.proofstack.adapter.in.rest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limits on {@code POST /api/ask}, because every answer costs model time:
 *
 * <ul>
 *   <li>per client and minute (fixed window). An IPv6 client is its /64, which is what one customer usually gets;</li>
 *   <li>for all clients together and minute, so many addresses cannot add up to an unbounded bill;</li>
 *   <li>how many questions are being answered at the same time, so a burst cannot queue up on the model.</li>
 * </ul>
 *
 * <p>Uses {@code request.getRemoteAddr()}. Behind a reverse proxy, enable Spring's
 * {@code server.forward-headers-strategy} so the real client address is used; never trust a raw
 * {@code X-Forwarded-For} header from the internet.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    /**
     * @param perClient   questions per client and minute
     * @param global      questions per minute for all clients together
     * @param concurrent  questions answered at the same time
     */
    public record Limits(int perClient, int global, int concurrent) {
        public Limits {
            if (perClient < 1 || global < 1 || concurrent < 1) {
                throw new IllegalArgumentException("rate limits must be >= 1");
            }
        }
    }

    private record Window(long start, AtomicInteger count) {
    }

    private final Limits limits;
    private final Clock clock;
    private final Runnable onRejected;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicReference<Window> all;
    private final Semaphore answering;

    public RateLimitFilter(int limitPerMinute, Clock clock) {
        this(limitPerMinute, clock, () -> { });
    }

    /** Per-client limit only (the global and concurrency limits are effectively off). */
    public RateLimitFilter(int limitPerMinute, Clock clock, Runnable onRejected) {
        this(new Limits(limitPerMinute, Integer.MAX_VALUE, Integer.MAX_VALUE), clock, onRejected);
    }

    /** @param onRejected hook called on every rejected request (e.g. a metrics counter), kept framework-free. */
    public RateLimitFilter(Limits limits, Clock clock, Runnable onRejected) {
        this.limits = limits;
        this.clock = clock;
        this.onRejected = onRejected;
        this.all = new AtomicReference<>(new Window(clock.millis(), new AtomicInteger()));
        this.answering = new Semaphore(limits.concurrent());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // The same path Spring MVC uses to pick the controller: without ";params", decoded, with "//" merged.
        // Comparing the raw request URI let "/api/ask;x=1" reach the controller without being counted.
        String path = PATHS.getPathWithinApplication(request);
        return !("POST".equals(request.getMethod()) && "/api/ask".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
        }
        Window client = windows.compute(client(request.getRemoteAddr()), (key, w) -> fresh(w, now));
        if (client.count().incrementAndGet() > limits.perClient()) {
            reject(response, client.start() + WINDOW_MILLIS - now);
            return;
        }
        Window everyone = all.updateAndGet(w -> fresh(w, now));
        if (everyone.count().incrementAndGet() > limits.global()) {
            reject(response, everyone.start() + WINDOW_MILLIS - now);
            return;
        }
        if (!answering.tryAcquire()) {
            reject(response, 1000);
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            answering.release();
        }
    }

    private static Window fresh(Window w, long now) {
        return w == null || now - w.start() >= WINDOW_MILLIS ? new Window(now, new AtomicInteger()) : w;
    }

    /** The key a client is counted under: its IPv4 address, or the /64 of its IPv6 address. */
    static String client(String address) {
        if (address == null || !address.contains(":")) {
            return address == null ? "" : address;
        }
        try {
            byte[] bytes = InetAddress.getByName(address).getAddress(); // a literal: no DNS lookup
            if (bytes.length != 16) {
                return address;
            }
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < 8; i += 2) {
                prefix.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
            }
            return prefix + ":/64";
        } catch (UnknownHostException e) {
            return address;
        }
    }

    private void reject(HttpServletResponse response, long retryAfterMillis) throws IOException {
        onRejected.run();
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(Math.max(1, (retryAfterMillis + 999) / 1000)));
        response.setContentType("application/json");
        response.getOutputStream().write("{\"error\":\"too many questions, try again later\"}"
                .getBytes(StandardCharsets.UTF_8));
    }
}
