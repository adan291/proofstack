package dev.proofstack.adapter.out.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Where the assistant's provider is configured (ADR-017): {@code ~/.proofstack/ai.json}, outside the repository, owner
 * only. Environment variables {@code PROOFSTACK_AI_*} win over the file and the file wins over the preset defaults.
 * The file stores the <em>name</em> of the variable that holds the key ({@code apiKeyEnv}); storing the key itself
 * is allowed but reported as less safe. The key is never part of {@link #view()}, which is what reaches the browser.
 */
public final class AiSettingsStore {

    public static final String ENV_PROTOCOL = "PROOFSTACK_AI_PROTOCOL";
    public static final String ENV_BASE_URL = "PROOFSTACK_AI_BASE_URL";
    public static final String ENV_MODEL = "PROOFSTACK_AI_MODEL";
    public static final String ENV_API_KEY = "PROOFSTACK_AI_API_KEY";

    private static final Set<String> FILE_FIELDS = Set.of("preset", "protocol", "baseUrl", "model", "apiKeyEnv",
            "apiKey", "authHeader", "headers", "timeoutSeconds", "temperature", "allowInsecureHttp", "acceptedHosts");
    private static final java.util.regex.Pattern ENV_NAME = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final java.util.regex.Pattern HEADER_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final Path file;
    private final Map<String, String> env;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, JsonNode> presets;
    private final ArrayNode presetList;

    public AiSettingsStore(Path file, Map<String, String> env) {
        this.file = file;
        this.env = Map.copyOf(env);
        this.presetList = loadPresets();
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        presetList.forEach(p -> byId.put(p.path("id").asString(), p));
        this.presets = byId;
    }

    /** Same home as the LinkedIn settings: {@code PROOFSTACK_HOME} when set, else {@code ~/.proofstack}. */
    public static final String HOME_ENV = "PROOFSTACK_HOME";

    /** {@code ~/.proofstack/ai.json}: in the user's home, never in the repository. */
    public static Path file(Map<String, String> env) {
        String home = env.get(HOME_ENV);
        Path base = home == null || home.isBlank() ? Path.of(System.getProperty("user.home"), ".proofstack") : Path.of(home);
        return base.resolve("ai.json");
    }

    // ------------------------------------------------------------------ resolve

    /**
     * @return the provider to use, or empty when none is configured (the assistant is then off)
     * @throws AiConfigException when something is configured but unusable
     */
    public Optional<AiSettings> resolve() {
        ObjectNode stored = read();
        JsonNode preset = presets.getOrDefault(text(stored, "preset"), json.createObjectNode());
        String protocolText = first(env.get(ENV_PROTOCOL), text(stored, "protocol"), text(preset, "protocol"));
        if (protocolText.isEmpty()) {
            return Optional.empty();
        }
        Protocol protocol;
        try {
            protocol = Protocol.parse(protocolText);
        } catch (IllegalArgumentException e) {
            throw new AiConfigException(e.getMessage());
        }
        String baseText = first(env.get(ENV_BASE_URL), text(stored, "baseUrl"), text(preset, "baseUrl"),
                protocol == Protocol.OLLAMA ? env.get("OLLAMA_HOST") : null,
                protocol == Protocol.GEMINI ? GeminiClient.DEFAULT_BASE_URL.toString() : null);
        String model = first(env.get(ENV_MODEL), text(stored, "model"), text(preset, "model"),
                protocol == Protocol.GEMINI ? GeminiClient.DEFAULT_MODEL : null,
                protocol == Protocol.OLLAMA ? OllamaClient.DEFAULT_MODEL : null);
        if (baseText.isEmpty() || model.isEmpty()) {
            return Optional.empty();
        }
        URI baseUrl = parseBaseUrl(baseText);

        boolean allowInsecure = stored.path("allowInsecureHttp").asBoolean(false);
        AiSettings probe = new AiSettings(protocol, baseUrl, model, null, "none", null, Map.of(), 1, 0, allowInsecure);
        if ("http".equals(baseUrl.getScheme()) && !probe.local() && !allowInsecure) {
            throw new AiConfigException("http to " + probe.providerHost()
                    + " sends your profile and key unencrypted. Use https, or confirm it in the AI settings");
        }

        String key = null;
        String source = "none";
        String envKey = env.get(ENV_API_KEY);
        String namedEnv = first(text(stored, "apiKeyEnv"), text(preset, "apiKeyEnv"),
                protocol == Protocol.GEMINI ? "GEMINI_API_KEY" : null);
        if (envKey != null && !envKey.isBlank()) {
            key = envKey;
            source = "env";
        } else if (!namedEnv.isEmpty() && env.get(namedEnv) != null && !env.get(namedEnv).isBlank()) {
            key = env.get(namedEnv);
            source = "env";
        } else if (!text(stored, "apiKey").isEmpty()) {
            key = text(stored, "apiKey");
            source = "file";
        }

        Map<String, String> headers = new LinkedHashMap<>();
        readHeaders(preset.path("headers"), headers);
        readHeaders(stored.path("headers"), headers);
        int timeout = clamp(stored.path("timeoutSeconds").asInt(AiSettings.DEFAULT_TIMEOUT_SECONDS), 1, 600);
        double temperature = Math.max(0, Math.min(2, stored.path("temperature").asDouble(AiSettings.DEFAULT_TEMPERATURE)));
        return Optional.of(new AiSettings(protocol, baseUrl, model, key, source,
                first(text(stored, "authHeader"), text(preset, "authHeader")), headers, timeout, temperature, allowInsecure));
    }

    // ------------------------------------------------------------------ what the browser may see

    /** The stored configuration, the effective one and the presets. Never contains a key. */
    public ObjectNode view() {
        ObjectNode stored = read();
        ObjectNode out = json.createObjectNode();
        ObjectNode fileView = out.putObject("file");
        for (String field : List.of("preset", "protocol", "baseUrl", "model", "apiKeyEnv", "authHeader")) {
            fileView.put(field, text(stored, field));
        }
        fileView.set("headers", stored.path("headers").isObject() ? stored.path("headers") : json.createObjectNode());
        fileView.put("timeoutSeconds", stored.path("timeoutSeconds").asInt(AiSettings.DEFAULT_TIMEOUT_SECONDS));
        fileView.put("temperature", stored.path("temperature").asDouble(AiSettings.DEFAULT_TEMPERATURE));
        fileView.put("allowInsecureHttp", stored.path("allowInsecureHttp").asBoolean(false));
        fileView.put("hasKey", !text(stored, "apiKey").isEmpty());
        ArrayNode overrides = out.putArray("envOverrides");
        for (String[] pair : new String[][] {{ENV_PROTOCOL, "protocol"}, {ENV_BASE_URL, "baseUrl"},
                {ENV_MODEL, "model"}, {ENV_API_KEY, "apiKey"}}) {
            if (env.get(pair[0]) != null && !env.get(pair[0]).isBlank()) {
                overrides.add(pair[1]);
            }
        }
        try {
            Optional<AiSettings> settings = resolve();
            out.set("effective", effective(settings));
        } catch (AiConfigException e) {
            out.set("effective", effective(Optional.empty()));
            out.put("problem", e.getMessage());
        }
        out.set("presets", presetList);
        return out;
    }

    /** The five facts of {@code GET /api/ai/status}. */
    public ObjectNode status() {
        try {
            return effective(resolve());
        } catch (AiConfigException e) {
            return effective(Optional.empty());
        }
    }

    private ObjectNode effective(Optional<AiSettings> settings) {
        ObjectNode out = json.createObjectNode();
        out.put("configured", settings.isPresent());
        out.put("protocol", settings.map(s -> s.protocol().id()).orElse(""));
        out.put("model", settings.map(AiSettings::model).orElse(""));
        out.put("local", settings.map(AiSettings::local).orElse(false));
        out.put("keySource", settings.map(AiSettings::keySource).orElse("none"));
        return out;
    }

    // ------------------------------------------------------------------ write

    /**
     * Writes the fields of {@code update} that this file knows. {@code apiKey} absent keeps the stored key,
     * {@code clearKey: true} removes it. Rejects what {@link #resolve()} would reject, before writing.
     *
     * @return a warning to show the author, or {@code null}
     * @throws AiConfigException on an invalid value
     */
    public String save(JsonNode update) {
        if (update == null || !update.isObject()) {
            throw new AiConfigException("send the configuration as a JSON object");
        }
        ObjectNode stored = read();
        for (var entry : update.properties()) {
            String name = entry.getKey();
            if (!FILE_FIELDS.contains(name) || name.equals("acceptedHosts")) {
                continue;
            }
            JsonNode value = entry.getValue();
            if (value.isNull() || (value.isString() && value.asString().isBlank())) {
                stored.remove(name);
            } else {
                stored.set(name, value);
            }
        }
        if (update.path("clearKey").asBoolean(false)) {
            stored.remove("apiKey");
        }
        validate(stored);
        write(stored);
        return text(stored, "apiKey").isEmpty() ? null
                : "The key is stored in plain text in ai.json. It is safer to keep it in an environment variable"
                + " and store only its name.";
    }

    /** Remembers that the author accepted sending the profile to {@code host}. */
    public void acceptHost(String host) {
        ObjectNode stored = read();
        Set<String> hosts = new LinkedHashSet<>(acceptedHosts(stored));
        hosts.add(host.toLowerCase(Locale.ROOT));
        ArrayNode array = stored.putArray("acceptedHosts");
        hosts.forEach(array::add);
        write(stored);
    }

    public boolean accepted(String host) {
        return acceptedHosts(read()).contains(host.toLowerCase(Locale.ROOT));
    }

    private static Set<String> acceptedHosts(ObjectNode stored) {
        Set<String> hosts = new LinkedHashSet<>();
        stored.path("acceptedHosts").forEach(h -> hosts.add(h.asString("")));
        return hosts;
    }

    private void validate(ObjectNode stored) {
        String protocol = text(stored, "protocol");
        if (!protocol.isEmpty()) {
            try {
                Protocol.parse(protocol);
            } catch (IllegalArgumentException e) {
                throw new AiConfigException(e.getMessage());
            }
        }
        String base = text(stored, "baseUrl");
        if (!base.isEmpty()) {
            parseBaseUrl(base);
        }
        String keyEnv = text(stored, "apiKeyEnv");
        if (!keyEnv.isEmpty() && !ENV_NAME.matcher(keyEnv).matches()) {
            throw new AiConfigException("apiKeyEnv must be the NAME of an environment variable, not the key itself");
        }
        String header = text(stored, "authHeader");
        if (!header.isEmpty() && !HEADER_NAME.matcher(header).matches()) {
            throw new AiConfigException("the authentication header has characters a header name cannot have");
        }
        if (stored.has("headers")) {
            if (!stored.path("headers").isObject()) {
                throw new AiConfigException("headers must be an object of name and value");
            }
            readHeaders(stored.path("headers"), new LinkedHashMap<>());
        }
        if (stored.has("timeoutSeconds") && !(stored.path("timeoutSeconds").isInt()
                && stored.path("timeoutSeconds").asInt() >= 1 && stored.path("timeoutSeconds").asInt() <= 600)) {
            throw new AiConfigException("timeoutSeconds must be a whole number between 1 and 600");
        }
        if (stored.has("temperature") && !(stored.path("temperature").isNumber()
                && stored.path("temperature").asDouble() >= 0 && stored.path("temperature").asDouble() <= 2)) {
            throw new AiConfigException("temperature must be a number between 0 and 2");
        }
    }

    // ------------------------------------------------------------------ file

    private ObjectNode read() {
        if (!Files.isRegularFile(file)) {
            return json.createObjectNode();
        }
        try {
            JsonNode node = json.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (!node.isObject()) {
                throw new AiConfigException("ai.json must contain a JSON object");
            }
            return (ObjectNode) node;
        } catch (JacksonException e) {
            throw new AiConfigException("ai.json is not valid JSON", e);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read ai.json", e);
        }
    }

    private void write(ObjectNode stored) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            restrict(dir, true);
            Path temp = Files.createTempFile(dir, "ai", ".tmp");
            try {
                restrict(temp, false);
                Files.writeString(temp, json.writerWithDefaultPrettyPrinter().writeValueAsString(stored) + "\n",
                        StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write ai.json", e);
        }
    }

    /** Owner-only where the file system can say so (POSIX mode, or the Windows ACL); best effort elsewhere. */
    private static void restrict(Path path, boolean directory) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
                return;
            }
            AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl != null) {
                AclEntry owner = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                        .setPermissions(java.util.EnumSet.allOf(AclEntryPermission.class)).build();
                acl.setAcl(List.of(owner));
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // Not fatal: the file is still outside the repository and in the user's own directory.
        }
    }

    // ------------------------------------------------------------------ helpers

    private static URI parseBaseUrl(String value) {
        String v = value.strip();
        URI uri;
        try {
            uri = URI.create(v.contains("://") ? v : "http://" + v);
        } catch (IllegalArgumentException e) {
            throw new AiConfigException("the base URL is not a valid URL");
        }
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
            throw new AiConfigException("the base URL must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new AiConfigException("the base URL needs a host and must not contain a user or password");
        }
        return uri;
    }

    private static void readHeaders(JsonNode node, Map<String, String> into) {
        if (!node.isObject()) {
            return;
        }
        for (var entry : node.properties()) {
            String name = entry.getKey();
            String value = entry.getValue().asString("");
            if (!HEADER_NAME.matcher(name).matches() || value.contains("\r") || value.contains("\n")) {
                throw new AiConfigException("the header '" + name + "' is not valid");
            }
            into.put(name, value);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("").strip();
    }

    private static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return "";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private ArrayNode loadPresets() {
        try (InputStream in = AiSettingsStore.class.getResourceAsStream("/ai/presets.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing /ai/presets.json");
            }
            return (ArrayNode) json.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Names of the presets, for tests and messages. */
    List<String> presetIds() {
        return new ArrayList<>(presets.keySet());
    }
}
