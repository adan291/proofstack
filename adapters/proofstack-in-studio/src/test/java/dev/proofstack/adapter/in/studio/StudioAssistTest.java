package dev.proofstack.adapter.in.studio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.domain.assist.AssistMode;
import dev.proofstack.core.domain.assist.FieldPath;
import dev.proofstack.core.domain.assist.Suggestion;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.port.in.EditProfileUseCase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** The {@code /api/ai/*} routes over real HTTP, with an assistant that records what it is asked. */
class StudioAssistTest {

    private static final String SECRET = "sk-must-never-reach-the-browser";

    private static class FakeAssistant implements StudioAssistant {
        final JsonMapper json = JsonMapper.builder().build();
        boolean configured = true;
        String pending;
        final List<String> accepted = new ArrayList<>();
        final List<Object[]> calls = new ArrayList<>();
        final AtomicInteger suggestCalls = new AtomicInteger();
        volatile CountDownLatch hold;
        volatile CountDownLatch entered = new CountDownLatch(1);
        volatile RuntimeException failure;
        Suggestion answer = new Suggestion("Better text", List.of("shortened"), List.of("How many users?"), List.of("40%"));
        String invalidMessage;

        @Override
        public ObjectNode status() {
            return json.createObjectNode().put("configured", configured).put("protocol", "openai")
                    .put("model", "m").put("local", false).put("keySource", "env");
        }

        @Override
        public ObjectNode config() {
            return json.createObjectNode().put("note", "config");
        }

        @Override
        public String saveConfig(JsonNode update) {
            if (invalidMessage != null) {
                throw new IllegalArgumentException(invalidMessage);
            }
            calls.add(new Object[] {"save", update});
            return update.has("apiKey") ? "stored in plain text" : null;
        }

        @Override
        public ObjectNode test() {
            return json.createObjectNode().put("ok", true).put("model", "m").put("millis", 5).put("error", "");
        }

        @Override
        public String pendingConsent() {
            return pending;
        }

        @Override
        public void consent(String host) {
            accepted.add(host);
            pending = null;
        }

        @Override
        public Suggestion suggest(Profile saved, FieldPath path, String currentText, String request, AssistMode mode,
                                  Locale targetLanguage) {
            suggestCalls.incrementAndGet();
            calls.add(new Object[] {path.value(), currentText, request, mode, targetLanguage, saved});
            entered.countDown();
            if (hold != null) {
                try {
                    hold.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) {
                throw failure;
            }
            return answer;
        }
    }

    private static class FakeOther implements EditProfileUseCase {
        String json = "{\"person\":{\"name\":\"Ada\",\"headline\":\"Dev\",\"summary\":\"old\"},\"projects\":[{\"id\":\"a\",\"summary\":\"A\"}]}";
        int version = 1;
        boolean exists = true;
        final List<String> saved = new ArrayList<>();
        SaveResult forced;

        @Override
        public Snapshot load() {
            return new Snapshot(json, "o" + version, List.of(), List.of());
        }

        @Override
        public SaveResult save(String body, String expectedVersion) {
            if (forced != null) {
                return forced;
            }
            if (!expectedVersion.equals("o" + version)) {
                return new Conflict("o" + version);
            }
            json = body;
            saved.add(body);
            return new Saved("o" + (++version), List.of());
        }
    }

    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();
    private final FakeAssistant assistant = new FakeAssistant();
    private final FakeOther other = new FakeOther();
    private final Profile profile = new Profile(new PersonalInfo("Ada", "Dev", "Saved summary", null, "ada@example.com",
            List.of()), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    private StudioServer server;
    private String base;

    @BeforeEach
    void start() {
        server = server(assistant, new StudioTranslation("profile.en.yaml", () -> other.exists, other));
    }

    private StudioServer server(StudioAssistant assistant, StudioTranslation translation) {
        StudioServer s = new StudioServer(new StudioServerTestEditor(), () -> profile, p -> List.of(), p -> List.of(),
                "{}", 0, null, assistant, translation);
        s.start();
        base = "http://127.0.0.1:" + s.port();
        return s;
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private record Reply(int status, JsonNode body, String raw) {
    }

    private Reply call(String method, String path, String body, String token, String origin) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).method(method,
                body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (token != null) {
            b.header("X-Studio-Token", token);
        }
        if (origin != null) {
            b.header("Origin", origin);
        }
        if (body != null) {
            b.header("Content-Type", "application/json");
        }
        try {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(r.statusCode(), json.readTree(r.body()), r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private Reply get(String path) throws IOException {
        return call("GET", path, null, server.token(), null);
    }

    private Reply post(String path, String body) throws IOException {
        return call("POST", path, body, server.token(), base);
    }

    private static final String SUGGEST = "{\"path\":\"person.summary\",\"request\":\"shorter\",\"currentText\":\"My draft\",\"mode\":\"improve\"}";

    // ------------------------------------------------------------------ guard

    @Test
    void everyAiRouteIsGuarded() throws IOException {
        for (String[] route : new String[][] {{"GET", "/api/ai/status"}, {"GET", "/api/ai/config"}, {"PUT", "/api/ai/config"},
                {"POST", "/api/ai/test"}, {"POST", "/api/ai/suggest"}, {"POST", "/api/ai/consent"},
                {"POST", "/api/ai/apply-translation"}}) {
            String body = route[0].equals("GET") ? null : "{}";
            assertThat(call(route[0], route[1], body, null, base).status).as("no token " + route[1]).isEqualTo(401);
            assertThat(call(route[0], route[1], body, "wrong", base).status).as("wrong token " + route[1]).isEqualTo(401);
            if (!route[0].equals("GET")) {
                assertThat(call(route[0], route[1], body, server.token(), "http://evil.example").status)
                        .as("foreign origin " + route[1]).isEqualTo(403);
                assertThat(call(route[0], route[1], body, server.token(), null).status)
                        .as("no origin " + route[1]).isEqualTo(403);
            }
        }
        assertThat(assistant.suggestCalls.get()).isZero();
        assertThat(assistant.calls).isEmpty();
    }

    @Test
    void aWrongContentTypeIsRefused() throws IOException {
        HttpRequest r = HttpRequest.newBuilder(URI.create(base + "/api/ai/suggest"))
                .header("X-Studio-Token", server.token()).header("Origin", base).header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(SUGGEST)).build();
        try {
            assertThat(http.send(r, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(415);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ status and config

    @Test
    void statusIsExactlyTheFiveFacts() throws IOException {
        Reply r = get("/api/ai/status");
        assertThat(r.status).isEqualTo(200);
        assertThat(r.body.propertyNames()).containsExactlyInAnyOrder("configured", "protocol", "model", "local", "keySource");
    }

    @Test
    void withoutAnAssistantStatusSaysNotConfiguredAndTheRestIs404() throws IOException {
        server.close();
        server = server(null, null);
        Reply status = get("/api/ai/status");
        assertThat(status.status).isEqualTo(200);
        assertThat(status.body.path("configured").asBoolean()).isFalse();
        assertThat(get("/api/ai/config").status).isEqualTo(404);
        assertThat(post("/api/ai/suggest", SUGGEST).status).isEqualTo(404);
    }

    @Test
    void configShowsTheOtherFileAndPutReturnsTheWarning() throws IOException {
        Reply c = get("/api/ai/config");
        assertThat(c.body.at("/translation/file").asString()).isEqualTo("profile.en.yaml");
        assertThat(c.body.at("/translation/exists").asBoolean()).isTrue();
        other.exists = false;
        assertThat(get("/api/ai/config").body.at("/translation/exists").asBoolean()).isFalse();

        Reply put = call("PUT", "/api/ai/config", "{\"preset\":\"openai\",\"apiKey\":\"" + SECRET + "\"}", server.token(), base);
        assertThat(put.status).isEqualTo(200);
        assertThat(put.body.path("warning").asString()).contains("plain text");
        assertThat(put.raw).doesNotContain(SECRET);

        assistant.invalidMessage = "the base URL must start with http:// or https://";
        Reply bad = call("PUT", "/api/ai/config", "{\"baseUrl\":\"ftp://x\"}", server.token(), base);
        assertThat(bad.status).isEqualTo(400);
        assertThat(bad.body.path("error").asString()).contains("http");
    }

    @Test
    void testRouteReportsTheOutcome() throws IOException {
        Reply r = post("/api/ai/test", "{}");
        assertThat(r.status).isEqualTo(200);
        assertThat(r.body.path("ok").asBoolean()).isTrue();
        assistant.configured = false;
        assertThat(post("/api/ai/test", "{}").status).isEqualTo(409);
    }

    // ------------------------------------------------------------------ suggest

    @Test
    void suggestUsesTheSavedProfileAndTheDraftText() throws IOException {
        Reply r = post("/api/ai/suggest", SUGGEST);
        assertThat(r.status).as(r.raw).isEqualTo(200);
        assertThat(r.body.path("text").asString()).isEqualTo("Better text");
        assertThat(r.body.path("changes").get(0).asString()).isEqualTo("shortened");
        assertThat(r.body.path("questions").get(0).asString()).isEqualTo("How many users?");
        assertThat(r.body.path("unsupportedNumbers").get(0).asString()).isEqualTo("40%");
        Object[] call = assistant.calls.getFirst();
        assertThat(call[0]).isEqualTo("person.summary");
        assertThat(call[1]).isEqualTo("My draft");
        assertThat(call[2]).isEqualTo("shorter");
        assertThat(call[3]).isEqualTo(AssistMode.IMPROVE);
        assertThat(call[5]).isSameAs(profile);
    }

    @Test
    void theContextIsNeverTakenFromTheRequest() throws IOException {
        post("/api/ai/suggest", "{\"path\":\"person.summary\",\"currentText\":\"x\",\"mode\":\"improve\","
                + "\"profile\":{\"person\":{\"name\":\"Mallory\"}},\"document\":{}}");
        assertThat(assistant.calls.getFirst()[5]).isSameAs(profile);
    }

    @Test
    void translateNeedsATargetLanguageAndJobOfferModeIsAccepted() throws IOException {
        assertThat(post("/api/ai/suggest", "{\"path\":\"person.summary\",\"currentText\":\"Hola\",\"mode\":\"translate\"}").status)
                .isEqualTo(400);
        assertThat(post("/api/ai/suggest", "{\"path\":\"person.summary\",\"currentText\":\"Hola\",\"mode\":\"translate\","
                + "\"targetLanguage\":\"en\"}").status).isEqualTo(200);
        assertThat(assistant.calls.getFirst()[4]).isEqualTo(Locale.ENGLISH);
        assertThat(post("/api/ai/suggest", "{\"path\":\"person.summary\",\"currentText\":\"x\",\"request\":\"We need Kafka\","
                + "\"mode\":\"job-offer\"}").status).isEqualTo(200);
        assertThat(assistant.calls.get(1)[3]).isEqualTo(AssistMode.JOB_OFFER);
    }

    @Test
    void inputsAreValidatedAtTheBoundary() throws IOException {
        assertThat(post("/api/ai/suggest", "{\"path\":\"person.email\",\"mode\":\"improve\"}").status).isEqualTo(400);
        assertThat(post("/api/ai/suggest", "{\"path\":\"../x\",\"mode\":\"improve\"}").status).isEqualTo(400);
        assertThat(post("/api/ai/suggest", "{\"path\":\"person.summary\",\"mode\":\"hack\"}").status).isEqualTo(400);
        assertThat(post("/api/ai/suggest", "{\"path\":\"person.summary\",\"currentText\":" + json.writeValueAsString("x".repeat(4001))
                + ",\"mode\":\"improve\"}").status).isEqualTo(400);
        assertThat(post("/api/ai/suggest", "{\"path\":\"person.summary\",\"request\":" + json.writeValueAsString("x".repeat(2001))
                + ",\"mode\":\"improve\"}").status).isEqualTo(400);
        assertThat(post("/api/ai/suggest", "{\"path\":\"person.summary\",\"currentText\":" + json.writeValueAsString("x".repeat(4000))
                + ",\"request\":" + json.writeValueAsString("y".repeat(2000)) + ",\"mode\":\"improve\"}").status).isEqualTo(200);
        assertThat(post("/api/ai/suggest", "{\"path\":\"person.summary\",\"currentText\":42,\"mode\":\"improve\"}").status)
                .isEqualTo(400);
        assertThat(post("/api/ai/suggest", "[1]").status).isEqualTo(400);
    }

    @Test
    void withoutAProviderThereIsNothingToCall() throws IOException {
        assistant.configured = false;
        Reply r = post("/api/ai/suggest", SUGGEST);
        assertThat(r.status).isEqualTo(409);
        assertThat(r.body.path("code").asString()).isEqualTo("not-configured");
        assertThat(assistant.suggestCalls.get()).isZero();
    }

    @Test
    void aRemoteProviderNeedsConsentBeforeAnythingIsSent() throws IOException {
        assistant.pending = "api.openai.com";
        Reply r = post("/api/ai/suggest", SUGGEST);
        assertThat(r.status).isEqualTo(409);
        assertThat(r.body.path("code").asString()).isEqualTo("consent-required");
        assertThat(r.body.path("provider").asString()).isEqualTo("api.openai.com");
        assertThat(assistant.suggestCalls.get()).isZero();

        assertThat(post("/api/ai/consent", "{\"host\":\"evil.example\"}").status).isEqualTo(400);
        assertThat(assistant.accepted).isEmpty();
        assertThat(post("/api/ai/consent", "{\"host\":\"api.openai.com\"}").status).isEqualTo(200);
        assertThat(assistant.accepted).containsExactly("api.openai.com");
        assertThat(post("/api/ai/suggest", SUGGEST).status).isEqualTo(200);
    }

    @Test
    void providerFailuresAreReadableAndNeverAnInternalError() throws IOException {
        assistant.failure = new ContentGenerationException("openai answered HTTP 429 (rate limit)",
                ContentGenerationException.Kind.PROVIDER_UNAVAILABLE);
        Reply r = post("/api/ai/suggest", SUGGEST);
        assertThat(r.status).isEqualTo(502);
        assertThat(r.body.path("code").asString()).isEqualTo("provider");
        assertThat(r.body.path("error").asString()).contains("429");

        assistant.failure = new ContentGenerationException("answered twice with something unusable",
                ContentGenerationException.Kind.INVALID_OUTPUT);
        assertThat(post("/api/ai/suggest", SUGGEST).body.path("code").asString()).isEqualTo("invalid-output");
        assistant.failure = null;
        assertThat(post("/api/ai/suggest", SUGGEST).status).isEqualTo(200);
    }

    @Test
    void onlyOneRequestToTheAiAtATime() throws Exception {
        assistant.hold = new CountDownLatch(1);
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return post("/api/ai/suggest", SUGGEST);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(assistant.entered.await(5, TimeUnit.SECONDS)).isTrue();
        Reply second = post("/api/ai/suggest", SUGGEST);
        assertThat(second.status).isEqualTo(429);
        assertThat(second.body.path("code").asString()).isEqualTo("busy");
        assertThat(post("/api/ai/test", "{}").status).isEqualTo(429);
        assistant.hold.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).status).isEqualTo(200);
        assertThat(post("/api/ai/suggest", SUGGEST).status).isEqualTo(200);
    }

    // ------------------------------------------------------------------ translation

    @Test
    void anAcceptedTranslationIsWrittenIntoTheSameFieldOfTheOtherFile() throws IOException {
        Reply r = post("/api/ai/apply-translation", "{\"path\":\"projects[a].summary\",\"text\":\"Translated\"}");
        assertThat(r.status).as(r.raw).isEqualTo(200);
        assertThat(r.body.path("file").asString()).isEqualTo("profile.en.yaml");
        assertThat(other.saved).hasSize(1);
        JsonNode saved = json.readTree(other.saved.getFirst());
        assertThat(saved.at("/projects/0/summary").asString()).isEqualTo("Translated");
        assertThat(saved.at("/person/summary").asString()).isEqualTo("old");
    }

    @Test
    void translationFailuresAreExplainedAndNothingIsWritten() throws IOException {
        assertThat(post("/api/ai/apply-translation", "{\"path\":\"projects[zzz].summary\",\"text\":\"T\"}").status).isEqualTo(422);
        assertThat(post("/api/ai/apply-translation", "{\"path\":\"person.email\",\"text\":\"T\"}").status).isEqualTo(400);
        assertThat(post("/api/ai/apply-translation", "{\"path\":\"person.headline\",\"text\":\"" + "x".repeat(301) + "\"}").status)
                .isEqualTo(400);
        assertThat(post("/api/ai/apply-translation", "{\"path\":\"person.headline\",\"text\":\" \"}").status).isEqualTo(400);
        other.forced = new EditProfileUseCase.Invalid(List.of(new Violation("$.person.headline", "too long")));
        Reply invalid = post("/api/ai/apply-translation", "{\"path\":\"person.headline\",\"text\":\"T\"}");
        assertThat(invalid.status).isEqualTo(422);
        assertThat(invalid.body.at("/violations/0/path").asString()).isEqualTo("$.person.headline");
        other.forced = new EditProfileUseCase.Conflict("o9");
        assertThat(post("/api/ai/apply-translation", "{\"path\":\"person.headline\",\"text\":\"T\"}").status).isEqualTo(409);
        other.exists = false;
        assertThat(post("/api/ai/apply-translation", "{\"path\":\"person.headline\",\"text\":\"T\"}").status).isEqualTo(404);
        assertThat(other.saved).isEmpty();
    }

    /** Minimal editor for the file being edited: the AI routes never touch it. */
    private static final class StudioServerTestEditor implements EditProfileUseCase {
        @Override
        public Snapshot load() {
            return new Snapshot("{}", "v1", List.of(), List.of());
        }

        @Override
        public SaveResult save(String json, String expectedVersion) {
            throw new AssertionError("the AI routes must never save the profile being edited");
        }
    }
}
