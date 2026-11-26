package dev.proofstack.adapter.in.studio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Three-way merge of profile documents (spec proofstack-studio-merge): base is what Studio served, mine is what the
 * user edited, theirs is what is on disk now. A change on one side only is taken; different changes on both sides are
 * a conflict, resolved by the user. Lists whose items have an {@code id} are matched by id, not by position.
 *
 * <p>Pure function: it never reads or writes the file. The result is only a proposal; saving it goes through the
 * normal, validated {@code PUT}.
 */
final class ThreeWayMerge {

    enum Side { MINE, THEIRS }

    /** A field changed differently on both sides. {@code null} means "deleted on that side". */
    record Conflict(String path, JsonNode mine, JsonNode theirs) {
    }

    /** {@code merged} holds my value wherever there is an unresolved conflict. */
    record Result(JsonNode merged, List<Conflict> conflicts) {
    }

    private final Set<String> idLists;
    private final Map<String, Side> choices;
    private final List<Conflict> conflicts = new ArrayList<>();

    private ThreeWayMerge(Set<String> idLists, Map<String, Side> choices) {
        this.idLists = idLists;
        this.choices = choices;
    }

    /**
     * @param idLists top-level properties that are lists of objects with an {@code id} (from the schema)
     * @param choices conflicts already resolved by the user, by path
     */
    static Result merge(JsonNode base, JsonNode mine, JsonNode theirs, Set<String> idLists, Map<String, Side> choices) {
        ThreeWayMerge merge = new ThreeWayMerge(idLists, choices);
        JsonNode merged = merge.node("$", absent(base), absent(mine), absent(theirs));
        return new Result(merged == null ? JsonNodeFactory.instance.objectNode() : merged, List.copyOf(merge.conflicts));
    }

    private JsonNode node(String path, JsonNode base, JsonNode mine, JsonNode theirs) {
        if (Objects.equals(mine, theirs)) {
            return mine;
        }
        if (Objects.equals(base, mine)) {
            return theirs;
        }
        if (Objects.equals(base, theirs)) {
            return mine;
        }
        if (isObject(mine) && isObject(theirs) && (base == null || base.isObject())) {
            return object(path, base, (ObjectNode) mine, (ObjectNode) theirs);
        }
        if (isArray(mine) && isArray(theirs) && (base == null || base.isArray()) && idLists.contains(topLevel(path))) {
            return byId(path, base, (ArrayNode) mine, (ArrayNode) theirs);
        }
        return conflict(path, mine, theirs);
    }

    private ObjectNode object(String path, JsonNode base, ObjectNode mine, ObjectNode theirs) {
        Set<String> keys = new LinkedHashSet<>(mine.propertyNames());
        keys.addAll(theirs.propertyNames());
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        for (String key : keys) {
            JsonNode value = node(path + "." + key, base == null ? null : base.get(key), mine.get(key), theirs.get(key));
            if (value != null) {
                out.set(key, value);
            }
        }
        return out;
    }

    /** Items matched by id: my order first, then the items only theirs added, in their order. */
    private ArrayNode byId(String path, JsonNode base, ArrayNode mine, ArrayNode theirs) {
        Map<String, JsonNode> baseItems = items(base);
        Map<String, JsonNode> mineItems = items(mine);
        Map<String, JsonNode> theirItems = items(theirs);
        if (mineItems == null || theirItems == null || baseItems == null) {
            return (ArrayNode) conflict(path, mine, theirs); // an item without id: no safe way to match them
        }
        Set<String> ids = new LinkedHashSet<>(mineItems.keySet());
        ids.addAll(theirItems.keySet());
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        for (String id : ids) {
            JsonNode item = node(path + "[id=" + id + "]", baseItems.get(id), mineItems.get(id), theirItems.get(id));
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    private JsonNode conflict(String path, JsonNode mine, JsonNode theirs) {
        Side chosen = choices.get(path);
        if (chosen != null) {
            return chosen == Side.MINE ? mine : theirs;
        }
        conflicts.add(new Conflict(path, mine, theirs));
        return mine;
    }

    /**
     * id → item, or {@code null} when the ids cannot match items safely: an item without a textual id, with an empty
     * one, or with an id another item already uses. Keying those would keep one item and drop the rest silently.
     */
    private static Map<String, JsonNode> items(JsonNode list) {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        if (list == null) {
            return byId;
        }
        for (JsonNode item : list) {
            JsonNode id = item.get("id");
            if (id == null || !id.isString() || id.asString().isEmpty()
                    || byId.putIfAbsent(id.asString(), item) != null) {
                return null;
            }
        }
        return byId;
    }

    private static String topLevel(String path) {
        if (path.length() < 3) {
            return ""; // the document itself
        }
        String rest = path.substring(2); // after "$."
        int end = rest.indexOf('.');
        int bracket = rest.indexOf('[');
        if (bracket >= 0 && (end < 0 || bracket < end)) {
            end = bracket;
        }
        return end < 0 ? rest : rest.substring(0, end);
    }

    private static boolean isObject(JsonNode n) {
        return n != null && n.isObject();
    }

    private static boolean isArray(JsonNode n) {
        return n != null && n.isArray();
    }

    private static JsonNode absent(JsonNode n) {
        return n == null || n.isMissingNode() ? null : n;
    }
}
