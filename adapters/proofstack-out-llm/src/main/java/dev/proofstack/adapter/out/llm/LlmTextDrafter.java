package dev.proofstack.adapter.out.llm;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import dev.proofstack.core.domain.assist.AssistantBrief;
import dev.proofstack.core.domain.assist.Draft;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;
import dev.proofstack.core.port.out.TextDrafter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Driven adapter: asks any {@link LlmClient} to rewrite one text. The answer is untrusted: it must be JSON that
 * matches the schema, and the model gets one more try when it does not. What it says is never used as anything but
 * text; the core checks length and numbers afterwards.
 */
public final class LlmTextDrafter implements TextDrafter {

    private static final int ATTEMPTS = 2;

    private final LlmClient client;
    private final AssistantPrompts prompts;
    private final ObjectMapper json;
    private final Schema schema;
    private final JsonNode providerSchema;

    LlmTextDrafter(LlmClient client, AssistantPrompts prompts, ObjectMapper json) {
        this.client = client;
        this.prompts = prompts;
        this.json = json;
        SchemaRegistryConfig config = SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build();
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaRegistryConfig(config)).getSchema(prompts.localSchema());
        this.providerSchema = prompts.providerSchema();
    }

    @Override
    public Draft draft(AssistantBrief brief) {
        String system = prompts.system(brief);
        String user = prompts.user(brief);
        String problem = "";
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            String raw = client.completeJson(system, user, providerSchema);
            JsonNode answer;
            try {
                answer = json.readTree(LlmContentGenerator.stripCodeFence(raw));
            } catch (JacksonException e) {
                problem = "it was not valid JSON";
                continue;
            }
            List<Error> errors = schema.validate(answer);
            if (!errors.isEmpty()) {
                problem = "it does not match the expected format: "
                        + errors.stream().map(Error::getMessage).sorted().collect(Collectors.joining("; "));
                continue;
            }
            return new Draft(answer.path("text").asString(), strings(answer.path("changes")),
                    strings(answer.path("questions")));
        }
        throw new ContentGenerationException(client.describe() + " answered twice with something unusable (" + problem
                + "). Try again or choose another model", Kind.INVALID_OUTPUT);
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(item -> out.add(item.asString()));
        return out;
    }
}
