package com.mgb.keyboard.layout;

/**
 * v0.17.73 (S6 FIX): signature of the settings a default (unedited) custom layout is built from (number row, hints, bottom row keys ...).
 * LayoutStore keeps it next to the layout text; when it differs the default layout is rebuilt, so e.g. the "Number row" toggle works again.
 * Pure Java, tools/BuiltSigTest.java.
 */
public final class BuiltSig {
    private BuiltSig() { }

    /** Same values = same text; any changed value = different text. Never null. */
    public static String of(Object... vals) {
        StringBuilder sb = new StringBuilder();
        if (vals != null) for (Object v : vals) {
            String s = String.valueOf(v);
            sb.append(s.length()).append(':').append(s).append(';');   // length prefix: "a;b" + "c" never equals "a" + "b;c"
        }
        return sb.toString();
    }
}
