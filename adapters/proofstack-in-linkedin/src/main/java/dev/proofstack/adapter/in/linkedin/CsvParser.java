package dev.proofstack.adapter.in.linkedin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RFC 4180 CSV parser: quoted fields with commas, escaped quotes ({@code ""}) and line breaks.
 * Rows are returned as header-name → value maps so column order does not matter.
 */
final class CsvParser {

    private CsvParser() {
    }

    static List<Row> parse(String content) {
        List<List<String>> records = records(stripBom(content));
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.getFirst().stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).toList();
        List<Row> rows = new ArrayList<>();
        for (List<String> record : records.subList(1, records.size())) {
            if (record.stream().allMatch(String::isBlank)) {
                continue;
            }
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < header.size() && i < record.size(); i++) {
                values.put(header.get(i), record.get(i));
            }
            rows.add(new Row(values));
        }
        return rows;
    }

    private static List<List<String>> records(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean any = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            any = true;
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                current.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                current.add(field.toString());
                field.setLength(0);
                records.add(current);
                current = new ArrayList<>();
                any = false;
            } else {
                field.append(c);
            }
        }
        if (any || !field.isEmpty() || !current.isEmpty()) {
            current.add(field.toString());
            records.add(current);
        }
        return records;
    }

    private static String stripBom(String content) {
        return content != null && content.startsWith("\uFEFF") ? content.substring(1) : content == null ? "" : content;
    }

    /** A CSV row addressed by (case-insensitive) column name. Missing columns read as empty. */
    record Row(Map<String, String> values) {
        String get(String column) {
            String v = values.get(column.toLowerCase(Locale.ROOT));
            return v == null ? "" : v.strip();
        }
    }
}
