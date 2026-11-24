package dev.proofstack.adapter.in.linkedin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.proofstack.adapter.in.linkedin.CsvParser.Row;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.port.out.MemberProfileSource;
import dev.proofstack.core.port.out.ProjectSource.ImportException;
import dev.proofstack.core.port.out.ProjectSource.ImportException.Kind;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Driven adapter: the member's own LinkedIn profile through the Member Data Portability API
 * ({@code GET /rest/memberSnapshotData}), available self-serve to members in the EU (DMA).
 *
 * <p>Only the five domains a portfolio needs are requested (data minimisation): PROFILE, POSITIONS,
 * EDUCATION, SKILLS, PROJECTS. Email and phone are not even in those domains, and the mapping
 * ignores them anyway (shared with the ZIP importer).
 */
public final class LinkedInMemberDataSource implements MemberProfileSource {

    public static final String CLIENT_ID_ENV = "LINKEDIN_CLIENT_ID";
    public static final String CLIENT_SECRET_ENV = "LINKEDIN_CLIENT_SECRET";
    public static final String REDIRECT_ENV = "LINKEDIN_REDIRECT_URI";
    public static final String TOKEN_ENV = "LINKEDIN_ACCESS_TOKEN";
    static final String API_ENV = "PROOFSTACK_LINKEDIN_API";
    static final String AUTH_ENV = "PROOFSTACK_LINKEDIN_AUTH";

    static final URI DEFAULT_API = URI.create("https://api.linkedin.com");
    /** The snapshot endpoint only accepts this version (426 NONEXISTENT_VERSION otherwise). */
    static final String API_VERSION = "202312";
    static final List<String> DOMAINS = List.of("PROFILE", "POSITIONS", "EDUCATION", "SKILLS", "PROJECTS");
    private static final int MAX_PAGES_PER_DOMAIN = 50;

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final URI api;
    private final Supplier<String> token;

    LinkedInMemberDataSource(HttpClient http, URI api, Supplier<String> token) {
        this.http = http;
        this.api = api;
        this.token = token;
    }

    /**
     * Configuration from the environment. With {@code LINKEDIN_ACCESS_TOKEN} no login is needed; otherwise
     * the OAuth login runs on {@link #fetchProfile()} and {@code openBrowser} receives the consent URL.
     */
    public static LinkedInMemberDataSource fromEnvironment(Map<String, String> env, Consumer<URI> openBrowser) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        URI api = uri(env.get(API_ENV), DEFAULT_API);
        String preset = env.get(TOKEN_ENV);
        Supplier<String> token;
        if (preset != null && !preset.isBlank()) {
            token = preset::strip;
        } else {
            String redirect = env.get(REDIRECT_ENV);
            LinkedInOAuth oauth = new LinkedInOAuth(http, uri(env.get(AUTH_ENV), LinkedInOAuth.DEFAULT_AUTH),
                    env.get(CLIENT_ID_ENV), env.get(CLIENT_SECRET_ENV),
                    redirect == null || redirect.isBlank() ? null : URI.create(redirect.strip()),
                    Duration.ofMinutes(5));
            token = () -> oauth.login(openBrowser);
        }
        return new LinkedInMemberDataSource(http, api, token);
    }

    @Override
    public Profile fetchProfile() {
        String accessToken = token.get();
        Map<String, List<Row>> tables = new LinkedHashMap<>();
        for (String domain : DOMAINS) {
            tables.put(domain, fetchDomain(domain, accessToken));
        }
        if (tables.get("PROFILE").isEmpty() && tables.get("POSITIONS").isEmpty()) {
            throw new ImportException("LinkedIn returned no profile data yet. After the first authorisation it can"
                    + " take a few minutes to prepare it; try again later", Kind.NOT_FOUND);
        }
        return LinkedInExportImporter.fromTables(tables);
    }

    List<Row> fetchDomain(String domain, String accessToken) {
        List<Row> rows = new ArrayList<>();
        URI next = api.resolve("/rest/memberSnapshotData?q=criteria&domain=" + domain);
        for (int page = 0; next != null && page < MAX_PAGES_PER_DOMAIN; page++) {
            HttpResponse<String> response = get(next, accessToken);
            if (response.statusCode() == 404) {
                break; // LinkedIn signals "No data found for this memberId" (end of pages, or empty domain)
            }
            check(response);
            JsonNode body = parse(response.body());
            JsonNode elements = body.path("elements");
            for (JsonNode element : elements.isArray() ? elements : List.of(elements)) {
                element.path("snapshotData").forEach(item -> rows.add(row(item)));
            }
            next = nextPage(body);
        }
        return rows;
    }

    private static Row row(JsonNode item) {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, JsonNode> field : item.properties()) {
            JsonNode v = field.getValue();
            values.put(field.getKey().strip().toLowerCase(Locale.ROOT), v.isValueNode() ? v.asString("") : v.toString());
        }
        return new Row(values);
    }

    /** Follows {@code paging.links[rel=next]} only when it stays on the API origin (the token must not leak). */
    private URI nextPage(JsonNode body) {
        for (JsonNode link : body.path("paging").path("links")) {
            if ("next".equals(link.path("rel").asString(""))) {
                URI target = api.resolve(link.path("href").asString(""));
                boolean sameOrigin = api.getScheme().equalsIgnoreCase(target.getScheme())
                        && api.getAuthority().equalsIgnoreCase(target.getAuthority());
                return sameOrigin ? target : null;
            }
        }
        return null;
    }

    private HttpResponse<String> get(URI uri, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Linkedin-Version", API_VERSION)
                .header("X-Restli-Protocol-Version", "2.0.0")
                .GET().build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ImportException("cannot reach the LinkedIn API", Kind.UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImportException("interrupted", Kind.UNAVAILABLE, e);
        }
    }

    private void check(HttpResponse<String> response) {
        String detail = errorDetail(response.body());
        switch (response.statusCode()) {
            case 200 -> { }
            case 401 -> throw new ImportException("LinkedIn rejected the token (HTTP 401" + detail + ")",
                    Kind.INVALID_INPUT);
            case 403 -> throw new ImportException("access denied (HTTP 403" + detail + "): your LinkedIn app needs"
                    + " the product 'Member Data Portability API (Member)' and the scope " + LinkedInOAuth.SCOPE,
                    Kind.INVALID_INPUT);
            case 429 -> throw new ImportException("LinkedIn rate limit reached, try again later", Kind.RATE_LIMITED);
            default -> throw new ImportException("LinkedIn API answered HTTP " + response.statusCode() + detail,
                    Kind.UNAVAILABLE);
        }
    }

    /** LinkedIn's own error code and message (never contains the token); empty if the body is not JSON. */
    private String errorDetail(String body) {
        try {
            JsonNode node = json.readTree(body == null ? "" : body);
            StringBuilder sb = new StringBuilder();
            for (String field : List.of("code", "serviceErrorCode", "message")) {
                String value = node.path(field).asString("");
                if (!value.isBlank()) {
                    sb.append(sb.isEmpty() ? ": " : ", ").append(value.strip());
                }
            }
            return sb.length() > 300 ? sb.substring(0, 300) + "..." : sb.toString();
        } catch (JacksonException | IllegalArgumentException e) {
            return "";
        }
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            throw new ImportException("LinkedIn API returned invalid JSON", Kind.UNAVAILABLE, e);
        }
    }

    private static URI uri(String value, URI fallback) {
        return value == null || value.isBlank() ? fallback : URI.create(value.strip());
    }
}
