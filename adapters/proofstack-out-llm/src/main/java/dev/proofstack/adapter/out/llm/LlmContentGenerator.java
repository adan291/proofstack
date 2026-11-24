package dev.proofstack.adapter.out.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.Error;

import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.content.ContentBrief;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;
import dev.proofstack.core.domain.content.ContentSubject;
import dev.proofstack.core.domain.content.GeneratedContent;
import dev.proofstack.core.port.out.ContentGenerator;

/**
 * Driven adapter: turns a {@link ContentBrief} into a draft through any {@link LlmClient}.
 * The model's answer is treated as untrusted input and validated against a JSON Schema.
 */
public final class LlmContentGenerator implements ContentGenerator {

    private final LlmClient client;
    private final PromptLibrary prompts;
    private final ObjectMapper json;
    private final JsonNode schemaNode;
    private final Schema schema;
    private final JsonNode providerSchema;

    LlmContentGenerator(LlmClient client, PromptLibrary prompts, ObjectMapper json) {
        this.client = client;
        this.prompts = prompts;
        this.json = json;
        try (InputStream in = LlmContentGenerator.class.getResourceAsStream("/prompts/content.schema.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing /prompts/content.schema.json");
            }
            this.schemaNode = json.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SchemaRegistryConfig config = SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build();
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaRegistryConfig(config)).getSchema(schemaNode);
        // Providers accept a JSON Schema subset; meta keywords like "$schema" are only for local validation.
        ObjectNode copy = (ObjectNode) schemaNode.deepCopy();
        copy.remove("$schema");
        this.providerSchema = copy;
    }

    @Override
    public GeneratedContent generate(ContentBrief brief) {
        String system = prompts.system(brief.language());
        String user = prompts.user(brief.format(), brief.language(), toJson(data(brief)));

        String raw = client.completeJson(system, user, providerSchema);
        JsonNode answer = parse(raw);

        List<Error> errors = schema.validate(answer);
        if (!errors.isEmpty()) {
            throw new ContentGenerationException(client.describe() + " answer does not match the content schema: "
                    + errors.stream().map(Error::getMessage).sorted().collect(Collectors.joining("; ")),
                    Kind.INVALID_OUTPUT);
        }

        List<String> hashtags = new ArrayList<>();
        answer.path("hashtags").forEach(h -> hashtags.add(h.asString()));
        return new GeneratedContent(brief.format(), answer.path("title").asString(), answer.path("body").asString(),
                hashtags, List.of());
    }

    private JsonNode parse(String raw) {
        String text = stripCodeFence(raw);
        try {
            return json.readTree(text);
        } catch (JacksonException e) {
            throw new ContentGenerationException(client.describe() + " did not return valid JSON", Kind.INVALID_OUTPUT, e);
        }
    }

    /** Some local models wrap JSON in ```json fences despite the format constraint. */
    static String stripCodeFence(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return text.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return text;
    }

    /** Only the facts the model may use. Email and location are deliberately not sent. */
    private static Map<String, Object> data(ContentBrief brief) {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> author = new LinkedHashMap<>();
        author.put("name", brief.author().name());
        author.put("headline", brief.author().headline());
        putIfPresent(author, "summary", brief.author().summary());
        data.put("author", author);

        switch (brief.subject()) {
            case ContentSubject.ProjectSubject p -> data.put("project", project(p.project()));
            case ContentSubject.EvidenceSubject e -> data.put("evidence", evidence(e.evidence()));
        }
        if (!brief.relatedEvidence().isEmpty()) {
            data.put("relatedEvidence", brief.relatedEvidence().stream().map(LlmContentGenerator::evidence).toList());
        }
        if (!brief.relatedExperience().isEmpty()) {
            data.put("relatedExperience", brief.relatedExperience().stream().map(x -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", x.role());
                m.put("company", x.company());
                m.put("period", x.period().start() + " – " + x.period().endDate().map(Object::toString).orElse("present"));
                m.put("highlights", x.highlights());
                if (!x.technologies().isEmpty()) {
                    m.put("technologies", x.technologies());
                }
                return m;
            }).toList());
        }
        return data;
    }

    private static Map<String, Object> project(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.name());
        m.put("summary", p.summary());
        putIfPresent(m, "problem", p.problem());
        putIfPresent(m, "solution", p.solution());
        if (!p.techStack().isEmpty()) {
            m.put("techStack", p.techStack());
        }
        if (!p.learnings().isEmpty()) {
            m.put("learnings", p.learnings());
        }
        if (!p.links().isEmpty()) {
            m.put("links", p.links().stream().map(l -> Map.of("label", l.label(), "url", l.url())).toList());
        }
        return m;
    }

    private static Map<String, Object> evidence(Evidence e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", e.title());
        m.put("category", e.category().name().toLowerCase(Locale.ROOT));
        m.put("context", e.context());
        m.put("action", e.action());
        m.put("result", e.result());
        return m;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }
}
