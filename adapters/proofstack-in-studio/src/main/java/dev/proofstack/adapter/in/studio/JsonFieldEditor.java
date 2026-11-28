package dev.proofstack.adapter.in.studio;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.proofstack.core.domain.assist.FieldPath;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes one text into a profile document by its {@link FieldPath}: entities are found by id, so the same path
 * reaches the same project in {@code profile.yaml} and in {@code profile.en.yaml} even if they are ordered differently.
 * Only the lists of plain strings ({@code learnings}, {@code highlights}) are addressed by position.
 */
final class JsonFieldEditor {

    private static final Pattern STEP = Pattern.compile("([A-Za-z]+)(?:\\[([^\\]]+)])?");

    private JsonFieldEditor() {
    }

    /**
     * @throws IllegalArgumentException if the entity or the list item is not in the document
     */
    static void set(ObjectNode document, FieldPath path, String text) {
        String[] steps = path.value().split("\\.");
        ObjectNode current = document;
        for (int i = 0; i < steps.length; i++) {
            Matcher m = step(steps[i]);
            String name = m.group(1);
            String selector = m.group(2);
            boolean last = i == steps.length - 1;
            if (selector == null) {
                if (last) {
                    current.put(name, text);
                    return;
                }
                JsonNode child = current.get(name);
                if (child == null && name.equals("areaNotes")) {
                    child = current.putObject(name);
                }
                if (!(child instanceof ObjectNode object)) {
                    throw missing(path);
                }
                current = object;
            } else if (isIndexed(name)) {
                ArrayNode list = current.get(name) instanceof ArrayNode array ? array : null;
                int index = parseIndex(selector);
                if (list == null || index < 0 || index >= list.size() || !last) {
                    throw missing(path);
                }
                list.set(index, tools.jackson.databind.node.StringNode.valueOf(text));
                return;
            } else {
                current = entity(current, name, selector, path);
            }
        }
        throw missing(path);
    }

    /** The text at {@code path}, or empty when it is not there. */
    static Optional<String> read(JsonNode document, FieldPath path) {
        JsonNode current = document;
        for (String raw : path.value().split("\\.")) {
            Matcher m = step(raw);
            current = current.path(m.group(1));
            String selector = m.group(2);
            if (selector != null) {
                JsonNode found = tools.jackson.databind.node.MissingNode.getInstance();
                if (isIndexed(m.group(1))) {
                    found = current.path(parseIndex(selector));
                } else {
                    for (JsonNode item : current) {
                        if (selector.equals(item.path("id").asString(""))) {
                            found = item;
                        }
                    }
                }
                current = found;
            }
        }
        return current.isString() ? Optional.of(current.asString()) : Optional.empty();
    }

    private static ObjectNode entity(ObjectNode parent, String list, String id, FieldPath path) {
        if (parent.get(list) instanceof ArrayNode array) {
            for (JsonNode item : array) {
                if (item instanceof ObjectNode object && id.equals(object.path("id").asString(""))) {
                    return object;
                }
            }
        }
        throw new IllegalArgumentException(list + "[" + id + "] is not in the other file (" + path.value() + ")");
    }

    private static Matcher step(String raw) {
        Matcher m = STEP.matcher(raw);
        if (!m.matches()) {
            throw new IllegalArgumentException("bad path");
        }
        return m;
    }

    private static boolean isIndexed(String name) {
        return name.equals("learnings") || name.equals("highlights");
    }

    private static int parseIndex(String selector) {
        try {
            return Integer.parseInt(selector);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static IllegalArgumentException missing(FieldPath path) {
        return new IllegalArgumentException("the field " + path.value() + " does not exist in the other file");
    }
}
