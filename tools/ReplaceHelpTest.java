import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplaceHelp;
import com.mgb.keyboard.typing.ReplaceSets;

/** v0.28.0 (R3b) ReplaceHelp tests (computer only): every example is RUN with the engine. */
public class ReplaceHelpTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] x) {
        t("5 examples", ReplaceHelp.exampleCount() == 5 && ReplaceHelp.examples().size() == 5);
        String[] langs = { "Korean", "Japanese", "Chinese", "Russian", "Arabic" };
        for (int i = 0; i < 5; i++) {
            ReplaceHelp.Example e = ReplaceHelp.example(i);
            t("lang " + langs[i], e != null && e.lang.equals(langs[i]));
            ReplaceEngine.Opt o = new ReplaceEngine.Opt();
            o.mode = e.mode; o.matchCase = e.matchCase; o.bangla = false; o.escapes = false;
            ReplaceEngine.Result r = ReplaceEngine.replaceAll(e.input, e.find, e.with, o);
            t("example " + langs[i] + " no error", r.err == null);
            t("example " + langs[i] + " result = expected", e.expected.equals(r.out));
            t("example " + langs[i] + " changed something", r.changed > 0 && !e.input.equals(r.out));
            t("example text has all parts " + langs[i], ReplaceHelp.exampleText(e).contains(e.find) && ReplaceHelp.exampleText(e).contains(e.expected));
        }
        t("modes: Korean example is regex, Russian is word", ReplaceHelp.example(0).mode == ReplaceEngine.MODE_REGEX && ReplaceHelp.example(3).mode == ReplaceEngine.MODE_WORD);
        t("example out of range = null", ReplaceHelp.example(-1) == null && ReplaceHelp.example(5) == null);
        t("exampleText(null) = empty", ReplaceHelp.exampleText(null).isEmpty());
        t("3 pages", ReplaceHelp.PAGES.length == 3);
        t("sanitizePage", ReplaceHelp.sanitizePage(-1) == 0 && ReplaceHelp.sanitizePage(3) == 0 && ReplaceHelp.sanitizePage(2) == 2);
        String m = ReplaceHelp.modesText();
        t("modes text names Text / Word / Regex", m.contains("TEXT") && m.contains("WORD") && m.contains("REGEX"));
        t("modes text names the 5 scripts", m.contains("Korean") && m.contains("Japanese") && m.contains("Chinese") && m.contains("Russian") && m.contains("Arabic"));
        t("modes text says undo 3", m.contains("3"));
        String s = ReplaceHelp.setsText();
        boolean all = true;
        for (ReplaceSets.Def d : ReplaceSets.all()) if (!s.contains(d.name)) all = false;
        t("sets text lists every ready-made set", all);
        t("sets text mentions Recent pairs + where to edit", s.contains("RECENT PAIRS") && s.contains("Find & Replace sets"));
        t("pageText per page non-empty", !ReplaceHelp.pageText(0).isEmpty() && !ReplaceHelp.pageText(1).isEmpty() && !ReplaceHelp.pageText(2).isEmpty());
        t("pageText bad page = modes", ReplaceHelp.pageText(99).equals(ReplaceHelp.modesText()));
        t("no Bangla in help (owner decision 2026-11-17)", !ReplaceHelp.modesText().matches("(?s).*[\\u0980-\\u09FF].*") && !s.matches("(?s).*[\\u0980-\\u09FF].*"));
        System.out.println("ReplaceHelpTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
