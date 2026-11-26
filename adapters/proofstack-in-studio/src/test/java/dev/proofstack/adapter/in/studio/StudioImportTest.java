package dev.proofstack.adapter.in.studio;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.proofstack.core.application.DraftImportService;
import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.port.in.EditProfileUseCase;
import dev.proofstack.core.port.out.ImportException;
import dev.proofstack.core.port.out.MemberProfileSource;
import dev.proofstack.core.port.out.ProfileJsonCodec;
import dev.proofstack.core.port.out.ProjectSource;

class StudioImportTest {

    private static final Profile DRAFT = new Profile(new PersonalInfo("Ada", "Dev", null, null, null, List.of()),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    /** Any document with "broken" in it is invalid; the result is reported by its project ids. */
    private static final ProfileJsonCodec CODEC = new ProfileJsonCodec() {
        @Override
        public Profile parse(String json) {
            if (json.contains("broken")) {
                throw new ProfileValidationException(new Violation("$.person.name", "must not be blank"));
            }
            return DRAFT;
        }

        @Override
        public String toJson(Profile profile) {
            return "{\"projects\":" + profile.projects().stream().map(p -> "\"" + p.id() + "\"").toList() + "}";
        }
    };

    private final List<String> accounts = new ArrayList<>();
    private StudioServer server;

    private StudioServer start(MemberProfileSource linkedIn, ProjectSource gitHub) {
        EditProfileUseCase editor = new EditProfileUseCase() {
            @Override
            public Snapshot load() {
                return new Snapshot("{}", "v1", List.of(), List.of());
            }

            @Override
            public SaveResult save(String json, String expectedVersion) {
                throw new AssertionError("an import must never save");
            }
        };
        server = new StudioServer(editor, () -> DRAFT, p -> List.of(), p -> List.of(), "{}", 0,
                new StudioImports(new DraftImportService(CODEC), () -> {
                    if (linkedIn == null) {
                        throw new IllegalStateException("LinkedIn is not configured: run proofstack setup");
                    }
                    return linkedIn;
                }, gitHub));
        server.start();
        return server;
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        String origin = "http://127.0.0.1:" + server.port();
        HttpRequest request = HttpRequest.newBuilder(URI.create(origin + path))
                .header("X-Studio-Token", server.token()).header("Origin", origin)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private ProjectSource repos(Project... projects) {
        return (account, forks) -> {
            accounts.add(account);
            return List.of(projects);
        };
    }

    private static Project repo(String id) {
        return new Project(id, id, "A repo", null, null, List.of(), List.of(),
                List.of(new Link("GitHub", "https://github.com/ada/" + id)), List.of(Area.BACKEND), List.of());
    }

    @Test
    void gitHubImportReturnsTheMergedDraftWithoutSaving() throws Exception {
        start(null, repos(repo("fresh")));
        var response = post("/api/import/github", "{\"document\":{\"person\":{}},\"account\":\" ada \"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("\"document\":{\"projects\":[\"fresh\"]}").contains("\"added\":[\"fresh\"]");
        assertThat(accounts).containsExactly("ada");
    }

    @Test
    void linkedInNotConfiguredExplainsWhatToDo() throws Exception {
        start(null, repos());
        var response = post("/api/import/linkedin", "{\"document\":{}}");
        assertThat(response.statusCode()).isEqualTo(412);
        assertThat(response.body()).contains("proofstack setup");
    }

    @Test
    void linkedInImportReportsWhatIsNew() throws Exception {
        start(() -> DRAFT, repos());
        var response = post("/api/import/linkedin", "{\"document\":{}}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("\"added\":[]").contains("\"leftOut\":[]");
    }

    @Test
    void aDraftWithErrorsIsRefusedWithItsViolations() throws Exception {
        start(() -> DRAFT, repos());
        var response = post("/api/import/linkedin", "{\"document\":{\"broken\":true}}");
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("$.person.name");
    }

    @Test
    void sourceFailuresKeepTheirMeaning() throws Exception {
        start(null, (account, forks) -> {
            throw new ImportException("GitHub user not found: " + account, ImportException.Kind.NOT_FOUND);
        });
        var response = post("/api/import/github", "{\"document\":{},\"account\":\"nobody\"}");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("GitHub user not found: nobody");
    }

    @Test
    void theAssistantRoutesSaveTheLinkedInAppAndIgnoredReposWithoutEchoingTheSecret() throws Exception {
        List<String> saved = new ArrayList<>();
        List<String> ignored = new ArrayList<>();
        EditProfileUseCase editor = new EditProfileUseCase() {
            @Override
            public Snapshot load() {
                return new Snapshot("{}", "v1", List.of(), List.of());
            }

            @Override
            public SaveResult save(String json, String expectedVersion) {
                throw new AssertionError();
            }
        };
        server = new StudioServer(editor, () -> DRAFT, p -> List.of(), p -> List.of(), "{}", 0,
                new StudioImports(new DraftImportService(CODEC), () -> null, repos(), () -> !saved.isEmpty(),
                        (id, secret) -> saved.add(id + ":" + secret), ignored::addAll,
                        () -> new StudioImports.Workspace("jane", "site", "profiles/jane/profile.yaml")));
        server.start();

        HttpRequest get = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/api/setup"))
                .header("X-Studio-Token", server.token()).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(client.send(get, HttpResponse.BodyHandlers.ofString()).body())
                    .contains("\"linkedInConfigured\":false", "\"owner\":\"jane\"", "\"profilePath\":\"profiles/jane/profile.yaml\"");
        }
        var app = post("/api/linkedin/app", "{\"clientId\":\"id\",\"clientSecret\":\"s3cret\"}");
        assertThat(app.statusCode()).isEqualTo(200);
        assertThat(app.body()).doesNotContain("s3cret");
        assertThat(saved).containsExactly("id:s3cret");
        assertThat(post("/api/linkedin/app", "{\"clientId\":\"id\"}").statusCode()).isEqualTo(400);

        assertThat(post("/api/github/ignore", "{\"names\":[\"dotfiles\",\"old.homework\"]}").statusCode()).isEqualTo(200);
        assertThat(ignored).containsExactly("dotfiles", "old.homework");
        assertThat(post("/api/github/ignore", "{\"names\":[\"../etc\\npasswd\"]}").statusCode()).isEqualTo(400);
        assertThat(ignored).hasSize(2);
    }

    @Test
    void malformedRequestsAndWrongMethodsAreRefused() throws Exception {
        start(null, repos());
        assertThat(post("/api/import/github", "{\"document\":{}}").statusCode()).isEqualTo(400);
        assertThat(post("/api/import/linkedin", "[]").statusCode()).isEqualTo(400);
        HttpRequest get = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/api/import/github"))
                .header("X-Studio-Token", server.token()).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(client.send(get, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(405);
        }
    }
}
