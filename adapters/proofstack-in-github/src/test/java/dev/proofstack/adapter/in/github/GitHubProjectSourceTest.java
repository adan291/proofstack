package dev.proofstack.adapter.in.github;

import com.sun.net.httpserver.HttpServer;
import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.port.out.ProjectSource.ImportException;
import dev.proofstack.core.port.out.ProjectSource.ImportException.Kind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubProjectSourceTest {

    private HttpServer server;
    private final Map<String, String> pages = new ConcurrentHashMap<>();
    private final Map<String, String> linkHeaders = new ConcurrentHashMap<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile boolean rateLimitHeader;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestURI().getPath() + "?" + exchange.getRequestURI().getRawQuery();
            authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            byte[] body = pages.getOrDefault(key, "{\"message\":\"Not Found\"}").getBytes(StandardCharsets.UTF_8);
            if (linkHeaders.containsKey(key)) {
                exchange.getResponseHeaders().add("Link", linkHeaders.get(key));
            }
            if (rateLimitHeader) {
                exchange.getResponseHeaders().add("X-RateLimit-Remaining", "0");
            }
            int code = pages.containsKey(key) ? status : 404;
            exchange.sendResponseHeaders(code, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private GitHubProjectSource source(String token) {
        return new GitHubProjectSource(URI.create(base()), token);
    }

    private static final String PAGE_1 = """
            [
              {"name":"proofstack","description":"Portfolio as code","language":"Java","topics":["hexagonal-architecture","spring-ai"],
               "html_url":"https://github.com/ada/proofstack","homepage":"https://ada.dev","fork":false,"archived":false},
              {"name":"forked-lib","description":"fork","language":"Java","html_url":"https://github.com/ada/forked-lib","fork":true},
              {"name":"old","description":"archived","html_url":"https://github.com/ada/old","archived":true}
            ]""";
    private static final String PAGE_2 = """
            [ {"name":"ESP32_Coffee","description":null,"language":"C++","topics":["mqtt"],
               "html_url":"https://github.com/ada/ESP32_Coffee"} ]""";

    private void twoPages() {
        String first = "/users/ada/repos?type=owner&sort=pushed&per_page=100";
        String second = "/users/ada/repos?type=owner&sort=pushed&per_page=100&page=2";
        pages.put(first, PAGE_1);
        pages.put(second, PAGE_2);
        linkHeaders.put(first, "<" + base() + second + ">; rel=\"next\", <" + base() + second + ">; rel=\"last\"");
    }

    @Test
    void mapsReposFollowsPaginationAndSkipsForksAndArchived() {
        twoPages();

        List<Project> projects = source(null).fetchProjects("ada", false);

        assertThat(projects).extracting(Project::id).containsExactly("proofstack", "esp32-coffee");
        Project p = projects.getFirst();
        assertThat(p.summary()).isEqualTo("Portfolio as code");
        assertThat(p.techStack()).containsExactly("Java", "hexagonal-architecture", "spring-ai");
        assertThat(p.areas()).containsExactly(Area.ARCHITECTURE, Area.AI);
        assertThat(p.links()).extracting("url").containsExactly("https://github.com/ada/proofstack", "https://ada.dev");
        assertThat(p.problem()).startsWith("TODO");

        Project esp = projects.get(1);
        assertThat(esp.summary()).startsWith("TODO");
        assertThat(esp.areas()).containsExactly(Area.HARDWARE, Area.MESSAGING);
        assertThat(authHeaders).containsOnly("null");
    }

    @Test
    void includeForksAddsThem() {
        twoPages();
        assertThat(source(null).fetchProjects("ada", true)).extracting(Project::id).contains("forked-lib");
    }

    @Test
    void tokenIsSentAsBearerButNeverToForeignOrigins() {
        String first = "/users/ada/repos?type=owner&sort=pushed&per_page=100";
        pages.put(first, PAGE_1);
        linkHeaders.put(first, "<https://evil.example/steal?page=2>; rel=\"next\"");

        assertThat(source("ghp_secret").fetchProjects("ada", false)).hasSize(1);
        assertThat(authHeaders).containsExactly("Bearer ghp_secret");
    }

    @Test
    void unknownUserIsNotFound() {
        assertThatThrownBy(() -> source(null).fetchProjects("ghost", false))
                .isInstanceOfSatisfying(ImportException.class, e -> assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND));
    }

    @Test
    void rateLimitSuggestsToken() {
        twoPages();
        status = 403;
        rateLimitHeader = true;

        assertThatThrownBy(() -> source(null).fetchProjects("ada", false))
                .isInstanceOfSatisfying(ImportException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.RATE_LIMITED);
                    assertThat(e.getMessage()).contains("GITHUB_TOKEN");
                });
    }

    @Test
    void invalidUsernamesAreRejectedBeforeAnyRequest() {
        for (String bad : new String[]{"../admin", "a/b", "", "x".repeat(40), "-lead"}) {
            assertThatThrownBy(() -> source(null).fetchProjects(bad, false)).as(bad)
                    .isInstanceOfSatisfying(ImportException.class, e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_INPUT));
        }
        assertThat(authHeaders).isEmpty();
    }

    @Test
    void unreachableApiIsUnavailable() {
        server.stop(0);
        assertThatThrownBy(() -> source(null).fetchProjects("ada", false))
                .isInstanceOfSatisfying(ImportException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
    }
}
