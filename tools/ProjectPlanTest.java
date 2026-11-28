import com.mgb.keyboard.typing.ProjectPlan;

/** v0.66.0 (PROJECT 3): preview window, labels, hint, chip name, list height of the Project screen. */
public class ProjectPlanTest {
    static int ok = 0, bad = 0;
    static void eq(String name, Object a, Object b) {
        if (String.valueOf(a).equals(String.valueOf(b))) ok++; else { bad++; System.out.println("FAIL " + name + ": got [" + a + "] want [" + b + "]"); }
    }
    static String ints(int[] a) { return java.util.Arrays.toString(a); }

    public static void main(String[] x) {
        // short text: all of it, spans unchanged, new lines become spaces
        String t = "The Anomaly appeared.\nA Practitioner came.";
        int i1 = t.indexOf("Anomaly"), i2 = t.indexOf("Practitioner");
        ProjectPlan.Window w = ProjectPlan.window(t, new int[]{i1, i2}, new int[]{i1 + 7, i2 + 12}, 150);
        eq("short: text", w.text, "The Anomaly appeared. A Practitioner came.");
        eq("short: starts", ints(w.starts), "[" + i1 + ", " + i2 + "]");
        eq("short: no cut", w.cutStart + "/" + w.cutEnd, "false/false");

        // long text: window around the first match, ellipsis both sides, highlight lands on the right letters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) sb.append("word").append(i).append(' ');
        int pos = sb.length();
        sb.append("Anomaly ");
        for (int i = 0; i < 40; i++) sb.append("tail").append(i).append(' ');
        String lg = sb.toString();
        w = ProjectPlan.window(lg, new int[]{pos}, new int[]{pos + 7}, 100);
        eq("long: cut both", w.cutStart + "/" + w.cutEnd, "true/true");
        eq("long: starts with ellipsis", w.text.charAt(0), '\u2026');
        eq("long: ends with ellipsis", w.text.charAt(w.text.length() - 1), '\u2026');
        eq("long: highlight is the word", w.text.substring(w.starts[0], w.ends[0]), "Anomaly");
        eq("long: size within max + 2", w.text.length() <= 102, true);

        // no spans in a long text: the start of the text
        w = ProjectPlan.window(lg, new int[0], new int[0], 100);
        eq("nospan: start", w.text.startsWith("word0 word1"), true);
        eq("nospan: cut end only", w.cutStart + "/" + w.cutEnd, "false/true");
        eq("nospan: no spans", w.starts.length, 0);

        // match at the very start / very end
        w = ProjectPlan.window(lg, new int[]{0}, new int[]{5}, 100);
        eq("first at 0: no start cut", w.cutStart, false);
        eq("first at 0: highlight", w.text.substring(w.starts[0], w.ends[0]), "word0");
        int tailPos = lg.lastIndexOf("tail39");
        w = ProjectPlan.window(lg, new int[]{tailPos}, new int[]{tailPos + 6}, 100);
        eq("last: end not cut", w.cutEnd, false);
        eq("last: highlight", w.text.substring(w.starts[0], w.ends[0]), "tail39");

        // second span outside the window is dropped, a span cut by the window is clipped
        w = ProjectPlan.window(lg, new int[]{pos, tailPos}, new int[]{pos + 7, tailPos + 6}, 100);
        eq("outside dropped", w.starts.length, 1);

        // bad input never throws
        eq("null text", ProjectPlan.window(null, null, null, 100).text, "");
        eq("empty text", ProjectPlan.window("", new int[]{0}, new int[]{1}, 100).text, "");
        eq("bad spans ignored", ProjectPlan.window("abc", new int[]{5, -1, 2}, new int[]{9, 3, 2}, 100).starts.length, 0);
        eq("spans longer than text", ProjectPlan.window("abc", new int[]{1}, new int[]{9}, 100).starts.length, 0);
        eq("different array sizes", ProjectPlan.window("abcdef", new int[]{1, 3}, new int[]{2}, 100).starts.length, 1);
        eq("tiny max is raised", ProjectPlan.window(lg, new int[]{pos}, new int[]{pos + 7}, 1).text.length() > 5, true);

        // surrogate pairs are not split
        String em = "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00 Anomaly \uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00";
        int ep = em.indexOf("Anomaly");
        w = ProjectPlan.window(em, new int[]{ep}, new int[]{ep + 7}, 20);
        boolean split = false;
        for (int i = 0; i < w.text.length(); i++) {
            char c = w.text.charAt(i);
            if (Character.isHighSurrogate(c) && (i + 1 >= w.text.length() || !Character.isLowSurrogate(w.text.charAt(i + 1)))) split = true;
            if (Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(w.text.charAt(i - 1)))) split = true;
        }
        eq("surrogates whole", split, false);
        eq("surrogates: highlight", w.text.substring(w.starts[0], w.ends[0]), "Anomaly");

        // labels
        eq("head 0", ProjectPlan.headCount(0), "nothing to fix");
        eq("head 5", ProjectPlan.headCount(5), "5 to fix");
        eq("head -1", ProjectPlan.headCount(-1), "");
        eq("in text 0", ProjectPlan.inText(0), "");
        eq("in text 3", ProjectPlan.inText(3), "3 in text");
        eq("terms 1", ProjectPlan.termsLabel(1), "1 term");
        eq("terms 0", ProjectPlan.termsLabel(0), "0 terms");
        eq("terms 6", ProjectPlan.termsLabel(6), "6 terms");
        eq("fix 7", ProjectPlan.fixLabel(7, true), "Fix 7 in text");
        eq("fix 0", ProjectPlan.fixLabel(0, true), "Nothing to fix");
        eq("fix unknown", ProjectPlan.fixLabel(-1, true), "Fix with project");
        eq("fix unknown no terms", ProjectPlan.fixLabel(-1, false), "Nothing to fix");
        eq("enabled 7", ProjectPlan.fixEnabled(7, true), true);
        eq("enabled 0", ProjectPlan.fixEnabled(0, true), false);
        eq("enabled unknown", ProjectPlan.fixEnabled(-1, true), true);
        eq("enabled no terms", ProjectPlan.fixEnabled(5, false), false);

        // hint
        eq("hint default", ProjectPlan.hint(false, ""), "One line per term. Separate wrong forms with ;");
        eq("hint editing", ProjectPlan.hint(true, null), "Editing a term. Tap Save, or \u2715 to cancel.");
        eq("hint note wins", ProjectPlan.hint(true, " Term added. "), "Term added.");

        // chip name
        eq("chip short", ProjectPlan.chipName("Night market"), "Night market");
        eq("chip long", ProjectPlan.chipName("Grandpa dimensions of the north"), "Grandpa dimensions\u2026".substring(0, 17) + "\u2026");
        eq("chip null", ProjectPlan.chipName(null), "");

        // list height
        eq("list default keys (48)", ProjectPlan.listHeightDp(48), 56);
        eq("list tall keys (64)", ProjectPlan.listHeightDp(64), 152);
        eq("list huge keys", ProjectPlan.listHeightDp(200), 236);
        eq("list zero keys", ProjectPlan.listHeightDp(0), 56);

        // messages
        eq("problem: type", ProjectPlan.isProblem("Type the wrong forms first."), true);
        eq("problem: use", ProjectPlan.isProblem("Use: wrong; wrong => right"), true);
        eq("problem: ok msg", ProjectPlan.isProblem("Term added."), false);
        eq("problem: null", ProjectPlan.isProblem(null), false);
        eq("fixed yes", ProjectPlan.isFixed("Fixed 5. Tap Undo to go back."), true);
        eq("fixed no", ProjectPlan.isFixed("Term added."), false);
        eq("toast cut", ProjectPlan.toastText("Fixed 5. Tap Undo to go back."), "Fixed 5 in the text");
        eq("toast saved", ProjectPlan.toastText("Fixed 2 \u00B7 saved to list"), "Fixed 2 \u00B7 saved to list");
        eq("toast plain", ProjectPlan.toastText("Term added."), "Term added.");
        eq("toast null", ProjectPlan.toastText(null), "");

        System.out.println("ProjectPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
