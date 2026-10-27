package dev.proofstack.service.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MCP session over stdio: starts the server in a child JVM, as Kiro would, and speaks JSON-RPC.
 * Also proves that stdout carries nothing but protocol messages.
 */
class McpStdioIntegrationTest {

    private static final Path REPO = Path.of(System.getProperty("proofstack.repoRoot", "..")).toAbsolutePath();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void initializeListAndCallToolsOverStdio() throws Exception {
        Process process = start(REPO.resolve("profiles/example/profile.yaml").toString());
        try (Writer in = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
             BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            send(in, """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                     "capabilities":{},"clientInfo":{"name":"proofstack-test","version":"1"}}}""");
            JsonNode init = read(out, 1);
            assertThat(init.at("/result/serverInfo/name").asString()).isEqualTo("proofstack");
            assertThat(init.at("/result/instructions").asString()).contains("Never invent");

            send(in, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

            send(in, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
            JsonNode tools = read(out, 2).at("/result/tools");
            List<String> names = new ArrayList<>();
            tools.forEach(t -> {
                names.add(t.path("name").asString());
                assertThat(t.at("/annotations/readOnlyHint").asBoolean()).as(t.path("name").asString()).isTrue();
                assertThat(t.at("/annotations/destructiveHint").asBoolean(true)).isFalse();
            });
            assertThat(names).containsExactlyInAnyOrder("get_profile_summary", "list_projects", "get_project",
                    "search_portfolio", "get_content_facts");

            send(in, """
                    {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"search_portfolio",
                     "arguments":{"query":"kafka outbox"}}}""");
            JsonNode call = read(out, 3).path("result");
            assertThat(call.path("isError").asBoolean(false)).isFalse();
            assertThat(call.toString()).contains("project:billing-events");

            send(in, """
                    {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_project",
                     "arguments":{"id":"ghost"}}}""");
            JsonNode error = read(out, 4);
            assertThat(error.toString()).contains("billing-events");
        } finally {
            process.destroy();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void invalidProfileFailsFastOnStderrWithEmptyStdout() throws Exception {
        Process process = start(REPO.resolve("profiles/does-not-exist.yaml").toString());
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();

        assertThat(process.exitValue()).isEqualTo(1);
        assertThat(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEmpty();
        assertThat(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)).contains("profile not found");
    }

    private Process start(String profile) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                ProofstackMcpServerApplication.class.getName());
        builder.environment().put(ProofstackMcpServerApplication.PROFILE_ENV, profile);
        return builder.start();
    }

    private static void send(Writer in, String message) throws Exception {
        in.write(message.replace("\n", "").replace("\r", "") + "\n");
        in.flush();
    }

    /** Every stdout line must be JSON-RPC; returns the response with the given id. */
    private JsonNode read(BufferedReader out, int id) throws Exception {
        String line;
        while ((line = out.readLine()) != null) {
            JsonNode node = json.readTree(line);
            assertThat(node.path("jsonrpc").asString()).as("stdout line: " + line).isEqualTo("2.0");
            if (node.path("id").asInt(-1) == id) {
                return node;
            }
        }
        throw new AssertionError("server closed stdout before answering id " + id);
    }
}
