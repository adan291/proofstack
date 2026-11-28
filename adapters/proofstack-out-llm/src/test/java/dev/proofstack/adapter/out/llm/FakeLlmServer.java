package dev.proofstack.adapter.out.llm;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-process HTTP server that records the last request and replies with a canned response. */
final class FakeLlmServer implements AutoCloseable {

    private final HttpServer server;
    volatile int status = 200;
    volatile String responseBody = "{}";
    volatile long delayMillis;

    volatile String lastPath;
    volatile String lastQuery;
    volatile String lastBody;
    final Map<String, String> lastHeaders = new ConcurrentHashMap<>();
    /** Every request body received, in order. */
    final java.util.List<String> bodies = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.Queue<int[]> scriptedStatus = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.Queue<String> scriptedBody = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** The next unanswered request gets this reply; once the queue is empty the default status and body apply. */
    void enqueue(int replyStatus, String body) {
        scriptedStatus.add(new int[] {replyStatus});
        scriptedBody.add(body);
    }

    FakeLlmServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            lastQuery = exchange.getRequestURI().getRawQuery();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(lastBody);
            lastHeaders.clear();
            exchange.getRequestHeaders().forEach((k, v) -> lastHeaders.put(k.toLowerCase(), String.join(",", v)));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            int[] next = scriptedStatus.poll();
            String reply = next == null ? responseBody : scriptedBody.poll();
            int replyCode = next == null ? status : next[0];
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(replyCode, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
