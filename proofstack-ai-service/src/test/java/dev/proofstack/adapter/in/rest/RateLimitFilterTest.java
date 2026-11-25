package dev.proofstack.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

/** Every answer costs model time: per client, for everyone together, and how many at once. */
class RateLimitFilterTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);
    private final AtomicInteger rejected = new AtomicInteger();

    private int ask(RateLimitFilter filter, String address, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ask");
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response.getStatus();
    }

    private int ask(RateLimitFilter filter, String address) throws Exception {
        return ask(filter, address, (req, res) -> { });
    }

    @Test
    void manyClientsTogetherCannotExceedTheGlobalLimit() throws Exception {
        var filter = new RateLimitFilter(new RateLimitFilter.Limits(10, 5, 4), clock, rejected::incrementAndGet);

        for (int i = 1; i <= 5; i++) {
            assertThat(ask(filter, "203.0.113." + i)).as("client " + i).isEqualTo(200);
        }

        assertThat(ask(filter, "203.0.113.99")).isEqualTo(429);
        assertThat(rejected).hasValue(1);
    }

    /** One IPv6 customer usually gets a whole /64: rotating the low bits is not a new client. */
    @Test
    void addressesOfTheSameIpv6Slash64ShareTheirLimit() throws Exception {
        var filter = new RateLimitFilter(new RateLimitFilter.Limits(2, 100, 4), clock, rejected::incrementAndGet);

        assertThat(ask(filter, "2001:db8:1:2::1")).isEqualTo(200);
        assertThat(ask(filter, "2001:db8:1:2:aaaa::7")).isEqualTo(200);
        assertThat(ask(filter, "2001:db8:1:2:ffff:ffff:ffff:ffff")).isEqualTo(429);
        assertThat(ask(filter, "2001:db8:1:3::1")).as("another /64").isEqualTo(200);
    }

    @Test
    void onlyAFewQuestionsReachTheModelAtTheSameTime() throws Exception {
        var filter = new RateLimitFilter(new RateLimitFilter.Limits(100, 100, 1), clock, rejected::incrementAndGet);
        CountDownLatch inModel = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FilterChain slowModel = (req, res) -> {
            inModel.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> {
            try {
                return ask(filter, "198.51.100.1", slowModel);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(inModel.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(ask(filter, "198.51.100.2")).isEqualTo(429);
        release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(ask(filter, "198.51.100.2")).as("free again").isEqualTo(200);
    }
}
