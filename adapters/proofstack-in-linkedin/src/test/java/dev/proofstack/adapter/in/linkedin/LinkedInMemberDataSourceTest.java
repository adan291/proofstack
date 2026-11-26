package dev.proofstack.adapter.in.linkedin;

import com.sun.net.httpserver.HttpServer;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.port.out.ImportException;
import dev.proofstack.core.port.out.ImportException.Kind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import dev.proofstack.core.domain.ProfileDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Member Data Portability API + OAuth against an in-process fake LinkedIn. */
class LinkedInMemberDataSourceTest {

    private HttpServer linkedIn;
    private final Map<String, String> pages = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String tokenForm;

    @BeforeEach
    void start() throws IOException {
        linkedIn = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        linkedIn.createContext("/rest/memberSnapshotData", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            requests.add(exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + exchange.getRequestHeaders().getFirst("Linkedin-Version") + " " + query);
            String body = pages.get(query);
            int code = status != 200 ? status : body == null ? 404 : 200;
            byte[] bytes = (body == null ? "{\"message\":\"No data found for this memberId\"}" : body)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        linkedIn.createContext("/oauth/v2/accessToken", exchange -> {
            tokenForm = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = "{\"access_token\":\"tok-123\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        linkedIn.start();
    }

    @AfterEach
    void stop() {
        linkedIn.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + linkedIn.getAddress().getPort();
    }

    private void snapshot(String domain, String rowsJson) {
        pages.put("q=criteria&domain=" + domain, "{\"paging\":{\"start\":0,\"count\":10,\"links\":[]},"
                + "\"elements\":[{\"snapshotDomain\":\"" + domain + "\",\"snapshotData\":" + rowsJson + "}]}");
    }

    private void fullProfile() {
        snapshot("PROFILE", "[{\"First Name\":\"Adán\",\"Last Name\":\"Sánchez Manzano\",\"Headline\":\"Backend Developer\","
                + "\"Summary\":\"Java y Spring\",\"Geo Location\":\"Madrid\",\"Address\":\"Calle Secreta 1\",\"Websites\":\"\"}]");
        // Positions arrive in two pages; the next link is relative, as in LinkedIn's docs.
        pages.put("q=criteria&domain=POSITIONS", "{\"paging\":{\"links\":[{\"rel\":\"next\","
                + "\"href\":\"/rest/memberSnapshotData?q=criteria&domain=POSITIONS&start=1\"}]},"
                + "\"elements\":[{\"snapshotDomain\":\"POSITIONS\",\"snapshotData\":[{\"Company Name\":\"ACME\","
                + "\"Title\":\"Backend Developer\",\"Description\":\"- Migré Tomcat 9 a 10\",\"Started On\":\"Jan 2022\",\"Finished On\":\"\"}]}]}");
        pages.put("q=criteria&domain=POSITIONS&start=1", "{\"elements\":[{\"snapshotDomain\":\"POSITIONS\",\"snapshotData\":"
                + "[{\"Company Name\":\"Old Co\",\"Title\":\"Junior\",\"Started On\":\"Mar 2019\",\"Finished On\":\"Dec 2021\"}]}]}");
        snapshot("EDUCATION", "[{\"School Name\":\"IES Ejemplo\",\"Degree Name\":\"CFGS DAM\",\"Start Date\":\"2018\",\"End Date\":\"2020\"}]");
        snapshot("SKILLS", "[{\"Name\":\"Java\"},{\"Name\":\"Apache Kafka\"}]");
        snapshot("LANGUAGES", "[{\"Name\":\"Español\",\"Proficiency\":\"Native\"}]");
        snapshot("CERTIFICATIONS", "[{\"Name\":\"Java SE 21\",\"Authority\":\"Oracle\",\"Started On\":\"Mar 2024\"}]");
        // PROJECTS deliberately missing: LinkedIn answers 404 "No data found".
    }

    private LinkedInMemberDataSource withToken() {
        return LinkedInMemberDataSource.fromEnvironment(Map.of(
                LinkedInMemberDataSource.API_ENV, base(), LinkedInMemberDataSource.TOKEN_ENV, "preset-token"),
                uri -> { throw new AssertionError("no login expected"); });
    }

    @Test
    void importsTheSevenDomainsFollowingPagination() {
        fullProfile();

        Profile p = withToken().fetchProfile();

        assertThat(p.person().name()).isEqualTo("Adán Sánchez Manzano");
        assertThat(p.person().email()).isNull();
        assertThat(p.toString()).doesNotContain("Calle Secreta");
        assertThat(p.experience()).extracting("company").containsExactly("ACME", "Old Co");
        assertThat(p.experience().getFirst().current()).isTrue();
        assertThat(p.education().getFirst().period().start()).isEqualTo(ProfileDate.of(2018));
        assertThat(p.skills()).extracting("name").containsExactly("Java", "Apache Kafka");
        assertThat(p.projects()).isEmpty();
        assertThat(p.languages()).extracting("name", "level").containsExactly(org.assertj.core.groups.Tuple.tuple("Español", "Native"));
        assertThat(p.certifications()).extracting("title", "issuer").containsExactly(org.assertj.core.groups.Tuple.tuple("Java SE 21", "Oracle"));
        assertThat(requests).allMatch(r -> r.startsWith("Bearer preset-token 202312 q=criteria&domain="));
        assertThat(requests).anyMatch(r -> r.endsWith("domain=POSITIONS&start=1"));
        assertThat(requests).noneMatch(r -> r.contains("INBOX") || r.contains("CONNECTIONS") || r.contains("EMAIL"));
    }

    @Test
    void nextLinksToForeignOriginsAreNotFollowed() {
        fullProfile();
        pages.put("q=criteria&domain=POSITIONS", "{\"paging\":{\"links\":[{\"rel\":\"next\","
                + "\"href\":\"https://evil.example/steal\"}]},\"elements\":[{\"snapshotData\":[{\"Company Name\":\"ACME\","
                + "\"Title\":\"Dev\",\"Started On\":\"Jan 2022\"}]}]}");

        assertThat(withToken().fetchProfile().experience()).hasSize(1);
    }

    @Test
    void emptySnapshotMeansLinkedInIsStillPreparingData() {
        assertThatThrownBy(() -> withToken().fetchProfile())
                .isInstanceOfSatisfying(ImportException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND);
                    assertThat(e.getMessage()).contains("try again later");
                });
    }

    @Test
    void missingProductOrScopeIsExplained() {
        fullProfile();
        status = 403;

        assertThatThrownBy(() -> withToken().fetchProfile())
                .hasMessageContaining("Member Data Portability API (Member)")
                .hasMessageContaining(LinkedInOAuth.SCOPE);
    }

    @Test
    void oauthLoginCompletesWithStateCheckAndTokenNeverLeaks() throws IOException {
        fullProfile();
        int port = freePort();
        Consumer<URI> browser = authorizationUri -> {
            // Behave like the user's browser: LinkedIn redirects back with the same state and a code.
            Map<String, String> q = LinkedInOAuth.query(authorizationUri.getRawQuery());
            assertThat(q).containsEntry("scope", LinkedInOAuth.SCOPE).containsEntry("client_id", "my-client");
            hit(q.get("redirect_uri") + "?code=auth-code&state=" + q.get("state"));
        };

        Profile p = LinkedInMemberDataSource.fromEnvironment(Map.of(
                LinkedInMemberDataSource.API_ENV, base(), LinkedInMemberDataSource.AUTH_ENV, base(),
                LinkedInMemberDataSource.CLIENT_ID_ENV, "my-client",
                LinkedInMemberDataSource.CLIENT_SECRET_ENV, "my-secret",
                LinkedInMemberDataSource.REDIRECT_ENV, "http://127.0.0.1:" + port + "/callback"), browser).fetchProfile();

        assertThat(p.experience()).hasSize(2);
        assertThat(tokenForm).contains("code=auth-code").contains("client_secret=my-secret");
        assertThat(requests).allMatch(r -> r.startsWith("Bearer tok-123 "));
    }

    private LinkedInOAuth oauth(int port, java.time.Duration timeout) {
        return new LinkedInOAuth(HttpClient.newHttpClient(), URI.create(base()), "c", "s",
                URI.create("http://127.0.0.1:" + port + "/callback"), timeout);
    }

    @Test
    void aForgedCallbackIsIgnoredAndNeverExchangesACode() throws IOException {
        int port = freePort();
        Consumer<URI> attacker = uri -> hit("http://127.0.0.1:" + port + "/callback?code=evil&state=forged");

        assertThatThrownBy(() -> oauth(port, java.time.Duration.ofSeconds(2)).login(attacker))
                .isInstanceOfSatisfying(ImportException.class, e -> assertThat(e.getMessage()).contains("no LinkedIn login"));
        assertThat(tokenForm).isNull();
    }

    @Test
    void aForgedCallbackCannotAbortTheLoginNorWriteToTheTerminal() throws IOException {
        int port = freePort();
        Consumer<URI> browser = uri -> {
            // Another local page gets there first, with a wrong state and an "error" full of terminal escapes...
            hit("http://127.0.0.1:" + port + "/callback?error=%1B%5B2J%1B%5B31mpwned&state=forged");
            // ...then LinkedIn redirects the real browser back.
            hit("http://127.0.0.1:" + port + "/callback?code=auth-code&state="
                    + LinkedInOAuth.query(uri.getRawQuery()).get("state"));
        };

        assertThat(oauth(port, java.time.Duration.ofSeconds(10)).login(browser)).isEqualTo("tok-123");
        assertThat(tokenForm).contains("code=auth-code");
    }

    @Test
    void aRefusalFromLinkedInIsReportedWithoutControlCharacters() throws IOException {
        int port = freePort();
        Consumer<URI> browser = uri -> hit("http://127.0.0.1:" + port + "/callback?error=access_denied%1B%5B31m&state="
                + LinkedInOAuth.query(uri.getRawQuery()).get("state"));

        assertThatThrownBy(() -> oauth(port, java.time.Duration.ofSeconds(10)).login(browser))
                .isInstanceOfSatisfying(ImportException.class, e -> assertThat(e.getMessage())
                        .contains("(access_denied31m)").doesNotContain("\u001b"));
        assertThat(tokenForm).isNull();
    }

    @Test
    void redirectMustBeLoopbackAndCredentialsRequired() {
        for (String bad : new String[]{"https://example.com/cb", "http://192.168.1.10:8080/cb", "http://127.0.0.1/cb"}) {
            assertThatThrownBy(() -> LinkedInOAuth.loopback(URI.create(bad))).as(bad).isInstanceOf(ImportException.class);
        }
        assertThatThrownBy(() -> LinkedInMemberDataSource.fromEnvironment(Map.of(), u -> { }))
                .hasMessageContaining(LinkedInMemberDataSource.CLIENT_ID_ENV);
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void hit(String url) {
        try {
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
