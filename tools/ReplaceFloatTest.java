import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplaceFloat;
import java.util.ArrayList;
import java.util.List;

/** v0.39.0 (FLOAT, F1) ReplaceFloat tests (computer only): rows, tabs, wrap, windows, surrogates, Bangla, huge text, state, position math, never throws. */
public class ReplaceFloatTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    static boolean broken(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) { if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) return true; i++; }
            else if (Character.isLowSurrogate(c)) return true;
        }
        return false;
    }
    static boolean rowsOk(ReplaceFloat.Snap p) {
        for (ReplaceFloat.Row r : p.rows) {
            if (broken(r.left) || broken(r.right) || broken(r.found) || broken(r.repl)) return false;
            for (int tab = 0; tab < 3; tab++) { int[] m = r.mark(tab); if (m[0] < 0 || m[0] > m[1] || m[1] > r.text(tab).length()) return false; }
        }
        return true;
    }

    public static void main(String[] x) {
        ReplaceEngine.Opt o = new ReplaceEngine.Opt();
        // ---- state / tab
        t("state default", ReplaceFloat.sanitizeState(0) == 0 && ReplaceFloat.sanitizeState(7) == 0 && ReplaceFloat.sanitizeState(-1) == 0);
        t("state mini / closed", ReplaceFloat.sanitizeState(1) == 1 && ReplaceFloat.sanitizeState(2) == 2);
        t("tab sanitize", ReplaceFloat.sanitizeTab(0) == 0 && ReplaceFloat.sanitizeTab(2) == 2 && ReplaceFloat.sanitizeTab(3) == 0 && ReplaceFloat.sanitizeTab(-5) == 0);
        // ---- count texts
        t("count 4", ReplaceFloat.countText(false, false, 4, false).equals("4 matches"));
        t("count 1", ReplaceFloat.countText(false, false, 1, false).equals("1 match"));
        t("count 0", ReplaceFloat.countText(false, false, 0, false).equals("No match"));
        t("count err", ReplaceFloat.countText(true, false, 5, false).equals("Error"));
        t("count blank", ReplaceFloat.countText(false, true, 0, false).equals("Type Find"));
        t("count set", ReplaceFloat.countText(false, false, 3, true).equals("3 changes") && ReplaceFloat.countText(false, false, 1, true).equals("1 change") && ReplaceFloat.countText(false, false, 0, true).equals("No match"));
        t("bubble", ReplaceFloat.bubbleText(false, 4).equals("4") && ReplaceFloat.bubbleText(false, 250).equals("99+") && ReplaceFloat.bubbleText(true, 3).equals("!") && ReplaceFloat.bubbleText(false, 0).equals("0") && ReplaceFloat.bubbleText(false, 99).equals("99"));
        // ---- step wrap
        t("step wrap", ReplaceFloat.step(0, 3, -1) == 2 && ReplaceFloat.step(2, 3, 1) == 0 && ReplaceFloat.step(1, 3, 1) == 2 && ReplaceFloat.step(0, 0, 1) == 0 && ReplaceFloat.step(9, 3, 1) == 0);

        // ---- many matches
        String txt = "I like cats and cats like me, cats are great, my cats purr";
        ReplaceEngine.Result r = ReplaceEngine.replaceAll(txt, "cats", "dogs", o);
        ReplaceFloat.Snap p = ReplaceFloat.snap(txt, r, 0, false, null);
        t("many: count", p.count.equals("4 matches") && p.total == 4 && p.cur == 1);
        t("many: 3 rows", p.rows.size() == 3);
        t("many: current first", p.rows.get(0).current && !p.rows.get(1).current && p.rows.get(0).n == 1 && p.rows.get(1).n == 2 && p.rows.get(2).n == 3);
        t("many: before text", p.rows.get(0).text(0).contains("cats") && p.rows.get(0).found.equals("cats"));
        ReplaceFloat.Row r0 = p.rows.get(0);
        t("many: before mark = found", r0.text(0).substring(r0.mark(0)[0], r0.mark(0)[1]).equals("cats"));
        t("many: after mark = repl", r0.text(1).substring(r0.mark(1)[0], r0.mark(1)[1]).equals("dogs"));
        t("many: changes text", r0.text(2).equals("cats \u2192 dogs  #1"));
        t("many: rows ok", rowsOk(p));
        // wrap-around from the current match
        p = ReplaceFloat.snap(txt, r, 3, false, null);
        t("wrap: cur 4", p.cur == 4 && p.rows.get(0).n == 4 && p.rows.get(1).n == 1 && p.rows.get(2).n == 2);
        p = ReplaceFloat.snap(txt, r, 99, false, null);
        t("wrap: cur clamp high", p.cur == 4 && p.rows.get(0).n == 4);
        p = ReplaceFloat.snap(txt, r, -5, false, null);
        t("wrap: cur clamp low", p.cur == 1 && p.rows.get(0).n == 1);

        // ---- 1 match / 2 matches
        String one = "only one cat here";
        r = ReplaceEngine.replaceAll(one, "cat", "dog", o);
        p = ReplaceFloat.snap(one, r, 0, false, null);
        t("one: 1 row", p.rows.size() == 1 && p.count.equals("1 match") && p.cur == 1);
        r = ReplaceEngine.replaceAll("ab ab", "ab", "c", o);
        p = ReplaceFloat.snap("ab ab", r, 1, false, null);
        t("two: rows 2, wrap", p.rows.size() == 2 && p.rows.get(0).n == 2 && p.rows.get(1).n == 1);

        // ---- no match / blank / error
        r = ReplaceEngine.replaceAll(one, "zzz", "q", o);
        p = ReplaceFloat.snap(one, r, 0, false, null);
        t("none: empty text", p.rows.isEmpty() && p.empty.equals("No match") && p.count.equals("No match") && p.cur == 0);
        r = ReplaceEngine.replaceAll(one, "", "q", o);
        p = ReplaceFloat.snap(one, r, 0, false, null);
        t("blank: message", p.rows.isEmpty() && p.empty.equals("Type what to find") && p.count.equals("Type Find"));
        ReplaceEngine.Opt rx = new ReplaceEngine.Opt(); rx.mode = ReplaceEngine.MODE_REGEX;
        r = ReplaceEngine.replaceAll(one, "(", "q", rx);
        p = ReplaceFloat.snap(one, r, 0, false, null);
        t("error: message", p.err && p.rows.isEmpty() && p.count.equals("Error") && p.empty.startsWith("Pattern error"));

        // ---- start / end of text
        String se = "cat in the middle of a long sentence with more words before the final cat";
        r = ReplaceEngine.replaceAll(se, "cat", "dog", o);
        p = ReplaceFloat.snap(se, r, 0, false, null);
        t("start: no left ellipsis", !p.rows.get(0).left.startsWith("\u2026") && p.rows.get(0).left.isEmpty() && p.rows.get(0).right.endsWith("\u2026"));
        p = ReplaceFloat.snap(se, r, 1, false, null);
        t("end: left ellipsis, no right", p.rows.get(0).left.startsWith("\u2026") && p.rows.get(0).right.isEmpty());
        t("start/end rows ok", rowsOk(p));

        // ---- Bangla
        String bn = "\u0986\u09AE\u09BF \u09AD\u09BE\u09A4 \u0996\u09BE\u0987 \u0986\u09AE\u09BF \u09AD\u09BE\u09A4 \u09B0\u09BE\u0981\u09A7\u09BF";
        r = ReplaceEngine.replaceAll(bn, "\u09AD\u09BE\u09A4", "\u09B0\u09C1\u099F\u09BF", o);
        p = ReplaceFloat.snap(bn, r, 0, false, null);
        t("bangla: 2 rows", p.rows.size() == 2 && p.rows.get(0).found.equals("\u09AD\u09BE\u09A4") && p.rows.get(0).repl.equals("\u09B0\u09C1\u099F\u09BF"));
        t("bangla: rows ok", rowsOk(p));

        // ---- emoji pair at a cut
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append('a');
        sb.append("\uD83D\uDE00");              // pair right at the left cut (context 12 -> cut inside the pair)
        sb.append("XX");
        sb.append("\uD83D\uDE00");
        for (int i = 0; i < 12; i++) sb.append('b');
        String em = sb.toString();
        r = ReplaceEngine.replaceAll(em, "XX", "yy", o);
        p = ReplaceFloat.snap(em, r, 0, false, null);
        t("emoji: no broken pair", p.rows.size() == 1 && rowsOk(p));
        sb = new StringBuilder("\uD83D\uDE00");
        for (int i = 0; i < 30; i++) sb.append('c');
        String em2 = sb.toString() + "TARGET" + "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00";
        r = ReplaceEngine.replaceAll(em2, "TARGET", "T", o);
        p = ReplaceFloat.snap(em2, r, 0, false, null);
        t("emoji right cut: no broken pair", rowsOk(p));
        // long found with a pair at the cut of mid()
        StringBuilder lf = new StringBuilder();
        for (int i = 0; i < 23; i++) lf.append('z');
        lf.append("\uD83D\uDE00\uD83D\uDE00");
        String lt = "pre " + lf + " post";
        r = ReplaceEngine.replaceAll(lt, lf.toString(), "ok", o);
        p = ReplaceFloat.snap(lt, r, 0, false, null);
        t("emoji in long found: cut ok", rowsOk(p) && p.rows.get(0).found.endsWith("\u2026"));

        // ---- empty replacement / empty found sign
        r = ReplaceEngine.replaceAll("a-b-c", "-", "", o);
        p = ReplaceFloat.snap("a-b-c", r, 0, false, null);
        t("empty repl: sign", p.rows.get(0).repl.equals(ReplaceFloat.EMPTY) && p.rows.get(0).text(2).startsWith("- \u2192 " + ReplaceFloat.EMPTY));
        r = ReplaceEngine.replaceAll("a\nb", "\n", " ", o);
        p = ReplaceFloat.snap("a\nb", r, 0, true == false, null);
        t("new line found: visible mark", p.rows.size() == 1 && p.rows.get(0).found.equals("\u23CE") && !p.rows.get(0).text(0).contains("\n"));

        // ---- long found cut
        StringBuilder lg = new StringBuilder(); for (int i = 0; i < 80; i++) lg.append('q');
        String lgt = "x " + lg + " y";
        r = ReplaceEngine.replaceAll(lgt, lg.toString(), "r", o);
        p = ReplaceFloat.snap(lgt, r, 0, false, null);
        t("long found: cut to 24 + dots", p.rows.get(0).found.length() == ReplaceFloat.MAX_MID + 1 && p.rows.get(0).found.endsWith("\u2026"));

        // ---- huge text stays fast
        StringBuilder hg = new StringBuilder();
        while (hg.length() < 99000) hg.append("lorem ipsum dolor cat sit amet ");
        String huge = hg.toString();
        long t0 = System.nanoTime();
        r = ReplaceEngine.replaceAll(huge, "cat", "dog", o);
        long t1 = System.nanoTime();
        p = ReplaceFloat.snap(huge, r, 10, false, null);
        long ms = (System.nanoTime() - t1) / 1000000L;
        t("huge: snap fast (<50 ms)", ms < 50);
        t("huge: rows + count", p.rows.size() == 3 && p.total == r.count && p.cur == 11 && rowsOk(p));
        ReplaceEngine.Result tooLong = ReplaceEngine.replaceAll(huge + huge, "cat", "dog", o);
        p = ReplaceFloat.snap(huge + huge, tooLong, 0, false, null);
        t("too long: error shown", p.err && p.rows.isEmpty() && p.count.equals("Error"));

        // ---- sets
        List<String> sl = new ArrayList<String>();
        sl.add("3\u00D7  cat  \u2192  dog"); sl.add("1\u00D7  x  (line removed)");
        ReplaceEngine.Result sr = new ReplaceEngine.Result(); sr.changed = 4; sr.count = 4;
        p = ReplaceFloat.snap("t", sr, 0, true, sl);
        t("set: lines + count", p.setMode && p.setLines.size() == 2 && p.count.equals("4 changes") && p.rows.isEmpty() && p.empty.isEmpty());
        sr.changed = 0; sr.count = 0;
        p = ReplaceFloat.snap("t", sr, 0, true, new ArrayList<String>());
        t("set: nothing", p.count.equals("No match") && p.empty.equals("No match"));
        List<String> many = new ArrayList<String>(); for (int i = 0; i < 20; i++) many.add("l" + i);
        sr.changed = 20;
        p = ReplaceFloat.snap("t", sr, 0, true, many);
        t("set: max 6 lines", p.setLines.size() == 6);
        List<String> withNull = new ArrayList<String>(); withNull.add(null); withNull.add("a");
        p = ReplaceFloat.snap("t", sr, 0, true, withNull);
        t("set: null line skipped", p.setLines.size() == 1);

        // ---- never throws
        p = ReplaceFloat.snap(null, null, 0, false, null);
        t("null res", p.empty.equals("No result") && p.rows.isEmpty());
        r = ReplaceEngine.replaceAll(one, "cat", "dog", o);
        p = ReplaceFloat.snap(null, r, 0, false, null);
        t("null src: no throw", p != null);
        ReplaceEngine.Result odd = ReplaceEngine.replaceAll("abcdef", "cd", "x", o);
        p = ReplaceFloat.snap("ab", odd, 0, false, null);     // text shorter than the hits (text changed meanwhile)
        t("short src: clamped, no throw", p.rows.size() == 1 && rowsOk(p));

        // ---- clamp
        int[] c = ReplaceFloat.clamp(-50, -50, 300, 190, 0, 0, 1080, 600, 10);
        t("clamp top-left", c[0] == 10 && c[1] == 10);
        c = ReplaceFloat.clamp(5000, 5000, 300, 190, 0, 0, 1080, 600, 10);
        t("clamp bottom-right", c[0] == 1080 - 10 - 300 && c[1] == 600 - 10 - 190);
        c = ReplaceFloat.clamp(100, 100, 300, 190, 0, 0, 1080, 600, 10);
        t("clamp inside unchanged", c[0] == 100 && c[1] == 100);
        c = ReplaceFloat.clamp(100, 100, 2000, 900, 0, 0, 320, 480, 8);
        t("clamp bigger than room: sticks left / top", c[0] == 8 && c[1] == 8);
        c = ReplaceFloat.clamp(0, 0, 100, 100, 50, 40, 400, 300, 0);
        t("clamp with offset bounds", c[0] == 50 && c[1] == 40);
        c = ReplaceFloat.clamp(0, 0, 10, 10, 0, 0, 100, 100, -5);
        t("clamp negative margin = 0", c[0] == 0 && c[1] == 0);
        // tiny screen
        c = ReplaceFloat.clamp(500, 500, 46, 46, 0, 0, 40, 40, 4);
        t("clamp tiny screen no throw", c[0] == 4 && c[1] == 4);
        // ---- place / fallback
        int[] pl = ReplaceFloat.place(-1, -1, 300, 190, 0, 0, 1080, 600, 10);
        t("place: nothing saved = centred top", pl[0] == (1080 - 300) / 2 && pl[1] == 10);
        pl = ReplaceFloat.place(200, 120, 300, 190, 0, 0, 1080, 600, 10);
        t("place: saved kept", pl[0] == 200 && pl[1] == 120);
        pl = ReplaceFloat.place(2400, 100, 300, 190, 0, 0, 1080, 600, 10);   // saved on a bigger screen / other rotation
        t("place: far outside = fallback", pl[0] == (1080 - 300) / 2 && pl[1] == 10);
        pl = ReplaceFloat.place(800, 450, 300, 190, 0, 0, 1080, 600, 10);   // centre still inside = only clamped
        t("place: near edge = clamped", pl[0] == 1080 - 10 - 300 && pl[1] == 600 - 10 - 190);
        pl = ReplaceFloat.place(100, -1, 300, 190, 0, 0, 1080, 600, 10);
        t("place: half saved = fallback", pl[0] == (1080 - 300) / 2);

        System.out.println("ReplaceFloatTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
