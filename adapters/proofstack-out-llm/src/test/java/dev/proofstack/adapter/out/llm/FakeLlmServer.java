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
    volatile String lastBody;
    final Map<String, String> lastHeaders = new ConcurrentHashMap<>();

    FakeLlmServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastHeaders.clear();
            exchange.getRequestHeaders().forEach((k, v) -> lastHeaders.put(k.toLowerCase(), String.join(",", v)));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
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
