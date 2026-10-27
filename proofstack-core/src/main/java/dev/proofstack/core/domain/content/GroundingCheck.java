package dev.proofstack.core.domain.content;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cheap anti-hallucination guard: LLMs love inventing metrics ("reduced latency by 40%").
 * Any number in the output that does not appear in the source data is reported for review.
 */
public final class GroundingCheck {

    // A standalone number, optionally decimal, optionally followed by % (with or without space).
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d.,])(\\d+(?:[.,]\\d+)?)(\\s?%)?(?![\\p{L}\\d])");

    private static final Pattern ANY_NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    private GroundingCheck() {
    }

    public static List<String> findUnsupportedNumbers(String generated, String source) {
        Set<String> known = numbersIn(source);
        Set<String> unsupported = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(generated == null ? "" : generated);
        while (m.find()) {
            String value = normalize(m.group(1));
            boolean percent = m.group(2) != null;
            // Single digits without % are mostly list markers or counts like "3 pasos": too noisy.
            if (!percent && value.length() == 1) {
                continue;
            }
            if (!known.contains(value)) {
                unsupported.add(value + (percent ? "%" : ""));
            }
        }
        return List.copyOf(unsupported);
    }

    /** Every number in the source, including embedded ones ("Java25", "v2.1"): being lenient avoids false alarms. */
    private static Set<String> numbersIn(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = ANY_NUMBER.matcher(text == null ? "" : text);
        while (m.find()) {
            found.add(normalize(m.group()));
        }
        return found;
    }

    private static String normalize(String number) {
        return number.replace(',', '.');
    }
}
