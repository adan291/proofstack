package dev.proofstack.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/**
 * A local provider that speaks the OpenAI protocol ({@code POST /chat/completions}), so the browser tests run the
 * whole chain (Studio, adapter, HTTP) without a network or a key. It records what it was asked.
 */
final class FakeAiProvider implements AutoCloseable {

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpServer server;
    final List<String> requests = new CopyOnWriteArrayList<>();
    volatile List<String> authorization = new CopyOnWriteArrayList<>();
    /** Builds the model's JSON answer from the user prompt of the request. */
    volatile Function<String, String> answer = prompt ->
            "{\"text\":\"Texto propuesto por la IA.\",\"changes\":[\"Acorté la frase.\"],\"questions\":[\"¿Cuántos usuarios tiene?\"]}";
    volatile long delayMillis;

    FakeAiProvider(String host) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(host), 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(body);
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null) {
                authorization.add(auth);
            }
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            String user = json.readTree(body).at("/messages/1/content").asString("");
            String content = exchange.getRequestURI().getPath().endsWith("/chat/completions")
                    ? (body.contains("\"ping\"") ? "{\"ok\":true}" : answer.apply(user)) : "{}";
            byte[] bytes = json.writeValueAsString(java.util.Map.of("choices", List.of(java.util.Map.of("message",
                    java.util.Map.of("role", "assistant", "content", content))))).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    String baseUrl(String host) {
        return "http://" + host + ":" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
