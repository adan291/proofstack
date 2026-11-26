package dev.proofstack.adapter.yaml;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.port.out.ProfileJsonCodec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Driven adapter: the editor's JSON document, read with the same schema and domain checks as {@code profile.yaml}
 * and written in the same field order as {@link YamlProfileWriter}, so a draft keeps its shape after an import.
 */
public final class YamlProfileJsonCodec implements ProfileJsonCodec {

    private final YamlProfileSource source = new YamlProfileSource();
    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public Profile parse(String document) {
        return source.fromJson(document);
    }

    @Override
    public String toJson(Profile profile) {
        JsonNode tree = YamlCodec.YAML.valueToTree(YamlProfileWriter.document(profile));
        source.fromTree(tree); // the domain is looser than the schema: never hand the editor a document it would refuse
        return json.writeValueAsString(tree);
    }
}
