package dev.proofstack.adapter.yaml;

import java.util.regex.Pattern;

import tools.jackson.dataformat.yaml.util.StringQuotingChecker;

/**
 * Quotes every text that a YAML reader would take for something else. Jackson's default already quotes booleans,
 * nulls and plain integers or decimals, but writes {@code 1e3}, {@code .inf} or {@code .NaN} bare, and those come back
 * as numbers (or do not parse at all).
 */
final class TextQuoting extends StringQuotingChecker.Default {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private static final Pattern NUMBER_LIKE = Pattern.compile(String.join("|",
            "[-+]?(\\.[0-9_]+|[0-9][0-9_]*(\\.[0-9_]*)?)([eE][-+]?[0-9]+)?",   // decimal, with optional exponent
            "[-+]?[0-9][0-9_]*(:[0-5]?[0-9])+(\\.[0-9_]*)?",                   // YAML 1.1 sexagesimal
            "0[xX][0-9a-fA-F_]+|0[oO][0-7_]+|0[bB][01_]+",                      // hex, octal, binary
            "[-+]?\\.(inf|Inf|INF)|\\.(nan|NaN|NAN)"));                         // infinity, not-a-number

    static final TextQuoting INSTANCE = new TextQuoting();

    @Override
    public boolean needToQuoteValue(String value) {
        return super.needToQuoteValue(value) || NUMBER_LIKE.matcher(value).matches();
    }
}
