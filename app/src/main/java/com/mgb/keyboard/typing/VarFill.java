package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v0.4.4 variable fields for Vault entries (pure Java, tested by tools/VarFillTest.java).
 *
 * A field is {name}: 1-30 characters, letters / digits (Bangla too) / _ - and single spaces inside, no quotes or colons,
 * so JSON text like {"a":1} or code braces are NOT treated as fields.
 * fields(text) = distinct names in order of first use (max MAX_FIELDS). fill() replaces in ONE pass:
 * a value that itself contains {x} is never expanded again. A name with no value stays as it is.
 */
public final class VarFill {

    private VarFill() { }

    public static final int MAX_FIELDS = 12;
    public static final int MAX_NAME = 30;

    /** Bangla vowel signs, hasanta (্) and ZWJ / ZWNJ are combining marks, not "letters": they must be allowed inside a name. */
    private static boolean isMark(char c) {
        if (c == '‌' || c == '‍') return true;
        int ty = Character.getType(c);
        return ty == Character.NON_SPACING_MARK || ty == Character.COMBINING_SPACING_MARK || ty == Character.ENCLOSING_MARK;
    }

    /** Length of the {name} starting at index i (including braces), or 0 if there is no valid field there. */
    private static int fieldLen(String s, int i) {
        if (s.charAt(i) != '{') return 0;
        int j = i + 1;
        int n = 0;
        while (j < s.length() && n <= MAX_NAME) {
            char c = s.charAt(j);
            if (c == '}') break;
            boolean okc = Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ' || isMark(c);
            if (!okc) return 0;
            j++; n++;
        }
        if (j >= s.length() || s.charAt(j) != '}') return 0;
        if (n == 0 || n > MAX_NAME) return 0;
        String name = s.substring(i + 1, j);
        if (!name.equals(name.trim()) || name.indexOf("  ") >= 0) return 0;
        return j - i + 1;
    }

    /** Distinct field names, in order. Empty list when there are none. */
    public static List<String> fields(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        int i = 0;
        while (i < text.length()) {
            int len = fieldLen(text, i);
            if (len > 0) {
                String name = text.substring(i + 1, i + len - 1);
                if (!out.contains(name) && out.size() < MAX_FIELDS) out.add(name);
                i += len;
            } else i++;
        }
        return out;
    }

    public static boolean hasFields(String text) { return !fields(text).isEmpty(); }

    /** Replace every {name} that has a value in [values]. Other text, and fields without a value, stay unchanged. */
    public static String fill(String text, Map<String, String> values) {
        if (text == null) return "";
        if (values == null || values.isEmpty()) return text;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int len = fieldLen(text, i);
            if (len > 0) {
                String name = text.substring(i + 1, i + len - 1);
                String v = values.get(name);
                if (v != null) sb.append(v); else sb.append(text, i, i + len);
                i += len;
            } else { sb.append(text.charAt(i)); i++; }
        }
        return sb.toString();
    }
}
