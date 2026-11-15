import com.mgb.keyboard.hist.HistLog;
import java.util.List;

/** v0.20.0 (H1) HistLog tests (computer only). */
public class HistLogTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }
    static String last(HistLog l) { List<HistLog.Entry> e = l.entries(); return e.isEmpty() ? null : e.get(e.size() - 1).text; }

    public static void main(String[] a) {
        long T = 1000000L;
        // typing builds one entry
        HistLog l = new HistLog();
        t("empty at start", l.size() == 0 && !l.isDirty());
        t("type returns true", l.type("h", "app.a", T));
        l.type("i", "app.a", T + 10);
        t("one entry", l.size() == 1 && "hi".equals(last(l)));
        t("time = start of the run", l.entries().get(0).time == T);
        t("app saved", "app.a".equals(l.entries().get(0).app));
        t("dirty after typing", l.isDirty());
        t("null / empty ignored", !l.type(null, "x", T) && !l.type("", "x", T));

        // backspace follows the run
        l = new HistLog();
        l.type("hello", "a", T); l.back(2, "a", T + 5);
        t("backspace 2", "hel".equals(last(l)));
        l.back(10, "a", T + 6);
        t("backspace more than typed = run gone", l.size() == 0);
        t("backspace with no run = false", !l.back(1, "a", T + 7));
        l.type("a", "a", T + 8);
        t("new run after it was emptied", l.size() == 1 && "a".equals(last(l)));
        // surrogate pair (emoji) is one backspace
        l = new HistLog();
        l.type("ok", "a", T); l.type("\uD83D\uDE00", "a", T + 1); l.back(1, "a", T + 2);
        t("emoji removed as one", "ok".equals(last(l)));
        // Bangla text
        l = new HistLog();
        l.type("\u0986\u09AE\u09BF", "a", T); l.back(1, "a", T + 1);
        t("bangla backspace one code point", "\u0986\u09AE".equals(last(l)));
        // backspace in another app does not touch the run
        l = new HistLog();
        l.type("abc", "a", T);
        t("other app backspace false", !l.back(1, "b", T + 1));
        t("other app backspace closes the run", l.size() == 1 && "abc".equals(last(l)));
        l.back(1, "a", T + 2);
        t("closed entry is not edited", "abc".equals(last(l)));

        // word backspace
        l = new HistLog();
        l.type("hello world", "a", T); l.backWord("a", T + 1);
        t("word delete", "hello".equals(last(l).trim()));
        l.backWord("a", T + 2);
        t("word delete all = run gone", l.size() == 0);

        // run splitting
        l = new HistLog();
        l.type("one", "a", T); l.type("two", "b", T + 1);
        t("app change = new entry", l.size() == 2 && "one".equals(l.entries().get(0).text) && "two".equals(l.entries().get(1).text));
        l = new HistLog();
        l.type("one", "a", T); l.type("two", "a", T + HistLog.IDLE_MS + 1);
        t("idle gap = new entry", l.size() == 2);
        l = new HistLog();
        l.type("one", "a", T); l.type("two", "a", T + HistLog.IDLE_MS);
        t("exactly idle limit = same entry", l.size() == 1 && "onetwo".equals(last(l)));
        l = new HistLog();
        l.type("one", "a", T); l.closeRun(); l.type("two", "a", T + 1);
        t("closeRun (Enter) = new entry", l.size() == 2);
        // long run split at MAX_ENTRY_CHARS
        l = new HistLog();
        String c50 = "01234567890123456789012345678901234567890123456789";
        for (int i = 0; i < 9; i++) l.type(c50, "a", T + i);          // 450 chars, one run
        t("450 chars is still one entry", l.size() == 1);
        l.type(c50, "a", T + 20);                                     // would be 500 = limit, still one
        t("exactly the limit is one entry", l.size() == 1 && last(l).length() == 500);
        l.type("x", "a", T + 21);
        t("over the limit splits", l.size() == 2 && "x".equals(last(l)));
        // blank run dropped
        l = new HistLog();
        l.type("   ", "a", T); l.closeRun();
        t("blank run dropped", l.size() == 0);
        l = new HistLog();
        l.type("  hi  ", "a", T); l.closeRun();
        t("closed entry is trimmed", "hi".equals(last(l)));

        // paste guard
        l = new HistLog();
        l.type("abc", "a", T);
        StringBuilder big = new StringBuilder(); for (int i = 0; i < HistLog.MAX_CHUNK + 1; i++) big.append('z');
        t("paste-like chunk not saved", !l.type(big.toString(), "a", T + 1));
        t("paste-like chunk ends the run", l.size() == 1 && "abc".equals(last(l)));
        l.back(1, "a", T + 2);
        t("backspace after a paste does not touch the old run", "abc".equals(last(l)));
        StringBuilder ok64 = new StringBuilder(); for (int i = 0; i < HistLog.MAX_CHUNK; i++) ok64.append('z');
        l = new HistLog();
        t("exactly MAX_CHUNK is typing", l.type(ok64.toString(), "a", T) && l.size() == 1);

        // entry limit
        l = new HistLog();
        for (int i = 0; i < HistLog.MAX_ENTRIES + 50; i++) { l.type("e" + i, "a", T + i); l.closeRun(); }
        t("max entries", l.size() == HistLog.MAX_ENTRIES);
        t("oldest dropped first", "e50".equals(l.entries().get(0).text) && ("e" + (HistLog.MAX_ENTRIES + 49)).equals(last(l)));
        // char limit
        l = new HistLog();
        String q50 = "qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq";
        for (int i = 0; i < 1000; i++) { for (int k = 0; k < 9; k++) l.type(q50, "a", T + i); l.closeRun(); }
        int tot = 0; for (HistLog.Entry e : l.entries()) tot += e.text.length();
        t("total chars limit", tot <= HistLog.MAX_TOTAL_CHARS && l.size() > 100);

        // prune
        l = new HistLog();
        long day = 86400000L;
        l.type("old", "a", T); l.closeRun();
        l.type("mid", "a", T + 5 * day); l.closeRun();
        l.type("new", "a", T + 9 * day); l.closeRun();
        int r = l.prune(T + 10 * day, 7 * day);
        t("prune removes older than ttl", r == 1 && l.size() == 2 && "mid".equals(l.entries().get(0).text));
        t("prune ttl 0 = nothing", l.prune(T + 100 * day, 0) == 0 && l.size() == 2);
        r = l.prune(T + 100 * day, 1 * day);
        t("prune all", r == 2 && l.size() == 0);
        l = new HistLog();
        l.type("typing now", "a", T + 20 * day);
        t("open recent run survives prune", l.prune(T + 20 * day + 5, day) == 0 && l.size() == 1);
        t("open run that is too old is pruned", l.prune(T + 22 * day, day) == 1 && l.size() == 0);

        // remove / clear
        l = new HistLog();
        l.type("a1", "a", T); l.closeRun(); l.type("a2", "a", T + 1); l.closeRun(); l.type("a3", "a", T + 2);
        long id2 = l.entries().get(1).id, id3 = l.entries().get(2).id;
        t("remove closed", l.remove(id2) && l.size() == 2);
        t("remove open run", l.remove(id3) && l.size() == 1);
        t("remove unknown", !l.remove(-5));
        l.type("b", "a", T + 3);
        t("clear returns count", l.clear() == 2 && l.size() == 0);
        t("clear is dirty", l.isDirty());

        // serialize / parse
        l = new HistLog();
        l.type("line1\nline2\ttab \\ back", "com.x.y", 123456789L); l.closeRun();
        l.type("\u0986\u09AE\u09BF", "com.z", 123456999L);
        String s = l.serialize();
        t("header first", s.startsWith("JHIST1\n"));
        t("one line per entry", s.split("\n").length == 3);
        HistLog p = HistLog.parseOrNull(s);
        t("parse ok", p != null && p.size() == 2);
        t("roundtrip text with newline tab backslash", "line1\nline2\ttab \\ back".equals(p.entries().get(0).text));
        t("roundtrip bangla", "\u0986\u09AE\u09BF".equals(p.entries().get(1).text));
        t("roundtrip time and app", p.entries().get(0).time == 123456789L && "com.x.y".equals(p.entries().get(0).app));
        t("parsed log is clean", !p.isDirty());
        t("serialize includes the open run", s.contains("\u0986\u09AE\u09BF"));
        t("no header = null", HistLog.parseOrNull("hello\n1\ta\tb") == null && HistLog.parseOrNull("") == null && HistLog.parseOrNull(null) == null);
        HistLog q = HistLog.parseOrNull("JHIST1\nbad line\nxx\tapp\ttext\n5\tapp\n7\tok.app\tfine\n\n9\tapp\t   \n");
        t("bad lines skipped", q != null && q.size() == 1 && "fine".equals(last(q)));
        HistLog w = HistLog.parseOrNull("JHIST1\r\n3\tapp\tcrlf\r\n");
        t("CRLF header accepted", w != null);
        HistLog tabApp = new HistLog(); tabApp.type("x", "a\tb", 1L);
        t("app with tab is cleaned", HistLog.parseOrNull(tabApp.serialize()).entries().get(0).app.equals("a_b"));

        // mergeOlder
        HistLog older = new HistLog();
        older.type("o1", "a", 100L); older.closeRun(); older.type("o2", "a", 200L); older.closeRun();
        HistLog cur = new HistLog();
        cur.type("n1", "a", 300L); cur.closeRun(); cur.type("n2", "a", 400L);
        cur.mergeOlder(HistLog.parseOrNull(older.serialize()));
        List<HistLog.Entry> m = cur.entries();
        t("merge keeps time order", m.size() == 4 && "o1".equals(m.get(0).text) && "o2".equals(m.get(1).text) && "n1".equals(m.get(2).text) && "n2".equals(m.get(3).text));
        boolean uniq = true; for (int i = 0; i < m.size(); i++) for (int j = i + 1; j < m.size(); j++) if (m.get(i).id == m.get(j).id) uniq = false;
        t("merge ids unique", uniq);
        t("merge with null", true);
        cur.mergeOlder(null);
        t("merge null is harmless", cur.size() == 4);

        System.out.println("HistLogTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
