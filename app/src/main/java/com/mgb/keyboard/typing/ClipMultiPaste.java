package com.mgb.keyboard.typing;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v0.43.0: paste SEVERAL selected clips WITH or WITHOUT format. Pure Java, no Android (tools/ClipMultiPasteTest.java).
 * Plain paste = the old joined text (blank line between clips). Format paste = one html piece: every clip in its own block,
 * a clip that has a stored format keeps it, a clip without a format becomes escaped plain html (so a mix still looks right).
 * Any problem = null = the caller pastes plain (nothing is ever lost).
 */
public final class ClipMultiPaste {
    private ClipMultiPaste() { }

    /** Sign on the "paste with format" button. */
    public static final String FORMAT_SIGN = "Aa";
    /** Sign on the "paste plain text" button. */
    public static final String PLAIN_SIGN = "T";
    /** Above this many characters of html the clipboard hand-over could fail: plain paste instead. */
    public static final int MAX_HTML = 400000;

    private static final Pattern HEAD = Pattern.compile("(?is)<head\\b.*?</head>");
    private static final Pattern COMMENT = Pattern.compile("(?is)<!--.*?-->");
    private static final Pattern DOCTYPE = Pattern.compile("(?is)<!doctype[^>]*>");
    private static final Pattern META = Pattern.compile("(?is)<meta\\b[^>]*>");
    private static final Pattern BODY = Pattern.compile("(?is)<body\\b[^>]*>(.*?)(</body>|$)");
    private static final Pattern WRAP_TAGS = Pattern.compile("(?is)</?(html|body)\\b[^>]*>");

    /** true = at least one clip of the selection has a stored format. */
    public static boolean anyFormat(List<String> htmls) {
        if (htmls == null) return false;
        for (String h : htmls) if (h != null && !h.trim().isEmpty()) return true;
        return false;
    }

    /** The two paste buttons (Aa + T) show only when the switch is ON and the selection has a format; else the one old Paste button. */
    public static boolean twoButtons(boolean prefOn, boolean anyFormat) { return prefOn && anyFormat; }

    /** Plain text as html: &amp; &lt; &gt; escaped, line breaks = br, extra spaces kept. Null = empty. */
    public static String plainToHtml(String t) {
        if (t == null || t.isEmpty()) return "";
        String s = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        s = s.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>");
        s = s.replace("\t", "&emsp;");
        while (s.contains("  ")) s = s.replace("  ", " &nbsp;");
        return s;
    }

    /** The visible part of a stored html (what is inside body); head, comments, meta, doctype and html / body tags are dropped. */
    public static String bodyOf(String html) {
        if (html == null) return "";
        String s = HEAD.matcher(html).replaceAll("");
        s = COMMENT.matcher(s).replaceAll("");
        s = DOCTYPE.matcher(s).replaceAll("");
        s = META.matcher(s).replaceAll("");
        Matcher m = BODY.matcher(s);
        if (m.find()) s = m.group(1);
        s = WRAP_TAGS.matcher(s).replaceAll("");
        return s.trim();
    }

    /**
     * One html for all clips, in the given order. texts and htmls must have the same size (htmls entry null = that clip has no format).
     * null = nothing has a format, bad input, or too big: the caller pastes plain.
     * ONE clip = its stored html untouched (exactly the old single-clip format paste).
     */
    public static String combine(List<String> texts, List<String> htmls) {
        try {
            if (texts == null || htmls == null || texts.isEmpty() || texts.size() != htmls.size()) return null;
            if (!anyFormat(htmls)) return null;
            if (texts.size() == 1) {
                String only = htmls.get(0);
                return only == null || only.trim().isEmpty() ? null : only;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < texts.size(); i++) {
                String h = htmls.get(i);
                String body = h == null || h.trim().isEmpty() ? "" : bodyOf(h);
                if (body.isEmpty()) body = plainToHtml(texts.get(i));
                if (i > 0) sb.append("<br>");   // blank line between the clips, like the plain "\n\n"
                sb.append("<div>").append(body).append("</div>");
                if (sb.length() > MAX_HTML) return null;
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
