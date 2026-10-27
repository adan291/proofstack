package dev.proofstack.adapter.in.github;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.AreaClassifier;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.TodoCounter;
import dev.proofstack.core.port.out.ProjectSource;
import dev.proofstack.core.port.out.ProjectSource.ImportException.Kind;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Driven adapter: public repositories of a GitHub user → draft {@link Project}s.
 *
 * <p>The optional token is read from {@code GITHUB_TOKEN} by the composition root and only travels in the
 * {@code Authorization} header. Pagination follows {@code Link: rel="next"} links, but only towards the
 * configured API origin, so a malicious response cannot redirect the token elsewhere.
 */
public final class GitHubProjectSource implements ProjectSource {

    public static final String TOKEN_ENV = "GITHUB_TOKEN";
    public static final String API_ENV = "PROOFSTACK_GITHUB_API";
    static final URI DEFAULT_API = URI.create("https://api.github.com");
    static final int MAX_PAGES = 5;

    private static final Pattern USER = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$");
    private static final Pattern NEXT = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final URI api;
    private final String token;

    public GitHubProjectSource(URI api, String token) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(), api, token);
    }

    GitHubProjectSource(HttpClient http, URI api, String token) {
        this.http = http;
        this.api = api == null ? DEFAULT_API : api;
        this.token = token == null || token.isBlank() ? null : token.strip();
    }

    /** Factory for the composition root: configuration from environment variables. */
    public static GitHubProjectSource fromEnvironment(Map<String, String> env) {
        String base = env.get(API_ENV);
        return new GitHubProjectSource(base == null || base.isBlank() ? DEFAULT_API : URI.create(base.strip()),
                env.get(TOKEN_ENV));
    }

    @Override
    public List<Project> fetchProjects(String account, boolean includeForks) {
        if (account == null || !USER.matcher(account).matches()) {
            throw new ImportException("invalid GitHub username '" + account + "'", Kind.INVALID_INPUT);
        }
        List<JsonNode> repos = new ArrayList<>();
        URI next = api.resolve("/users/" + account + "/repos?type=owner&sort=pushed&per_page=100");
        for (int page = 0; next != null && page < MAX_PAGES; page++) {
            HttpResponse<String> response = get(next);
            check(response, account);
            JsonNode body = parse(response.body());
            if (!body.isArray()) {
                throw new ImportException("unexpected GitHub response", Kind.UNAVAILABLE);
            }
            body.forEach(repos::add);
            next = nextPage(response).orElse(null);
        }

        Set<String> ids = new HashSet<>();
        List<Project> projects = new ArrayList<>();
        for (JsonNode repo : repos) {
            if (repo.path("archived").asBoolean(false) || repo.path("disabled").asBoolean(false)) {
                continue;
            }
            if (repo.path("fork").asBoolean(false) && !includeForks) {
                continue;
            }
            projects.add(toProject(repo, ids));
        }
        return projects;
    }

    static Project toProject(JsonNode repo, Set<String> usedIds) {
        String name = repo.path("name").asString();
        String description = repo.path("description").asString("").strip();

        Set<String> stack = new LinkedHashSet<>();
        String language = repo.path("language").asString("");
        if (!language.isBlank()) {
            stack.add(language);
        }
        repo.path("topics").forEach(t -> stack.add(t.asString()));

        Set<Area> areas = new LinkedHashSet<>();
        stack.forEach(term -> AreaClassifier.match(term).ifPresent(areas::add));
        AreaClassifier.match(name + " " + description).ifPresent(areas::add);

        List<Link> links = new ArrayList<>();
        String url = repo.path("html_url").asString("");
        if (url.startsWith("https://")) {
            links.add(new Link("GitHub", url));
        }
        String homepage = repo.path("homepage").asString("");
        if (homepage.startsWith("https://")) {
            links.add(new Link("Demo", homepage));
        }

        return new Project(uniqueId(name, usedIds), name,
                description.isEmpty() ? TodoCounter.MARKER + ": describe " + name + " in one sentence" : description,
                TodoCounter.MARKER + ": what problem does it solve?", TodoCounter.MARKER + ": how did you solve it?",
                List.copyOf(stack), List.of(), links, List.copyOf(areas), List.of());
    }

    static String uniqueId(String name, Set<String> used) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "repo";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60).replaceAll("-+$", "");
        }
        String id = base;
        for (int n = 2; !used.add(id); n++) {
            id = base + "-" + n;
        }
        return id;
    }

    private HttpResponse<String> get(URI uri) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "proofstack")
                .GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new ImportException("GitHub did not answer in time", Kind.UNAVAILABLE, e);
        } catch (IOException e) {
            throw new ImportException("cannot reach GitHub at " + api.getHost(), Kind.UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImportException("interrupted", Kind.UNAVAILABLE, e);
        }
    }

    private void check(HttpResponse<String> response, String account) {
        int status = response.statusCode();
        if (status == 200) {
            return;
        }
        if (status == 404) {
            throw new ImportException("GitHub user '" + account + "' not found", Kind.NOT_FOUND);
        }
        boolean rateLimited = status == 429 || (status == 403
                && response.headers().firstValue("x-ratelimit-remaining").map("0"::equals).orElse(false));
        if (rateLimited) {
            throw new ImportException("GitHub rate limit reached"
                    + (token == null ? "; set GITHUB_TOKEN to raise the limit" : ""), Kind.RATE_LIMITED);
        }
        if (status == 401) {
            throw new ImportException("GitHub rejected the token in GITHUB_TOKEN", Kind.UNAVAILABLE);
        }
        throw new ImportException("GitHub answered HTTP " + status, Kind.UNAVAILABLE);
    }

    private Optional<URI> nextPage(HttpResponse<String> response) {
        return response.headers().firstValue("link").flatMap(link -> {
            Matcher m = NEXT.matcher(link);
            if (!m.find()) {
                return Optional.empty();
            }
            URI uri = URI.create(m.group(1));
            boolean sameOrigin = api.getScheme().equalsIgnoreCase(uri.getScheme())
                    && api.getAuthority().equalsIgnoreCase(uri.getAuthority());
            return sameOrigin ? Optional.of(uri) : Optional.empty();
        });
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            throw new ImportException("GitHub returned invalid JSON", Kind.UNAVAILABLE, e);
        }
    }
}
