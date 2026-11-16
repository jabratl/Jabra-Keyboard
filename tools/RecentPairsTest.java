import com.mgb.keyboard.typing.RecentPairs;
import com.mgb.keyboard.typing.RecentPairs.Pair;
import java.util.*;

/** v0.26.0 (R3, step 3f) RecentPairs tests (computer only). Run: bash tools/run_tests.sh */
public class RecentPairsTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    static List<Pair> round(List<Pair> l) { return RecentPairs.parse(RecentPairs.serialize(l)); }

    public static void main(String[] args) {
        // ---- when to record
        t("rec: all good", RecentPairs.shouldRecord(true, false, "cat", false));
        t("rec: switch OFF", !RecentPairs.shouldRecord(false, false, "cat", false));
        t("rec: password field", !RecentPairs.shouldRecord(true, true, "cat", false));
        t("rec: empty Find", !RecentPairs.shouldRecord(true, false, "", false) && !RecentPairs.shouldRecord(true, false, null, false));
        t("rec: set active", !RecentPairs.shouldRecord(true, false, "cat", true));

        // ---- add
        List<Pair> l = new ArrayList<Pair>();
        l = RecentPairs.add(l, "cat", "dog");
        l = RecentPairs.add(l, "red", "blue");
        t("add: newest first", l.size() == 2 && l.get(0).find.equals("red") && l.get(1).find.equals("cat"));
        l = RecentPairs.add(l, "cat", "dog");
        t("add: same pair moves to the top, no duplicate", l.size() == 2 && l.get(0).find.equals("cat") && l.get(1).find.equals("red"));
        l = RecentPairs.add(l, "cat", "bird");
        t("add: same Find, other With = new pair", l.size() == 3 && l.get(0).with.equals("bird") && l.get(1).with.equals("dog"));
        List<Pair> before = new ArrayList<Pair>(l);
        List<Pair> same = RecentPairs.add(l, "", "x");
        t("add: empty Find = unchanged", same.equals(before) && same != l);
        t("add: null Find = unchanged", RecentPairs.add(l, null, "x").equals(before));
        t("add: input list not changed", l.equals(before));
        t("add: null list ok", RecentPairs.add(null, "a", "b").size() == 1 && RecentPairs.add(null, "", "b").isEmpty());
        t("add: null With = empty With", RecentPairs.add(null, "a", null).get(0).with.isEmpty());
        List<Pair> big = new ArrayList<Pair>();
        for (int i = 0; i < 25; i++) big = RecentPairs.add(big, "f" + i, "w" + i);
        t("limit 10", big.size() == RecentPairs.MAX && big.get(0).find.equals("f24") && big.get(9).find.equals("f15"));
        String longT = new String(new char[700]).replace('\0', 'x');
        t("add: cut to FIELD_MAX", RecentPairs.add(null, longT, longT).get(0).find.length() == 500);
        String sur = new String(new char[499]).replace('\0', 'y') + "\uD83D\uDE00";   // emoji would be cut in half at 500
        String cutS = RecentPairs.add(null, sur, "").get(0).find;
        t("add: no half emoji", cutS.length() == 499);

        // ---- text round trip (Korean / Japanese / Chinese / Russian / Arabic + special characters)
        List<Pair> w = new ArrayList<Pair>();
        w.add(new Pair("\uD55C\uAD6D\uC5B4", "\uD55C\uAE00"));
        w.add(new Pair("\u3053\u3093\u306B\u3061\u306F", "\u30B3\u30F3\u30CB\u30C1\u30CF"));
        w.add(new Pair("\u4E2D\u6587", "\u201C\u4E2D\u6587\u201D"));
        w.add(new Pair("\u0440\u0443\u0441\u0441\u043A\u0438\u0439", "\u00AB\u0440\u0443\u0441\u00BB"));
        w.add(new Pair("\u0639\u0631\u0628\u064A", "\u0663\u0662\u0661"));
        w.add(new Pair("a;b|c%d", "x%0041;|y"));
        w.add(new Pair("line1\nline2\ttab\r", "\\n \\1 $1"));
        w.add(new Pair("emoji \uD83D\uDE00", "\u2028sep"));
        t("round: all pairs kept", round(w).equals(w));
        String txt = RecentPairs.serialize(w);
        t("round: text has no raw new line / tab / control char", txt.indexOf('\n') < 0 && txt.indexOf('\t') < 0 && txt.indexOf('\r') < 0 && txt.indexOf('\u0001') < 0 && txt.indexOf('\u2028') < 0);
        t("round: CJK stays readable", txt.contains("\u3053\u3093\u306B\u3061\u306F") && txt.contains("\uD55C\uAD6D\uC5B4"));
        t("round: only 7 separators for 8 pairs", count(txt, '|') == 7 && count(txt, ';') == 8);
        t("round: empty With kept", round(Arrays.asList(new Pair("del", ""))).get(0).with.isEmpty());
        t("round: empty list", RecentPairs.serialize(new ArrayList<Pair>()).isEmpty() && RecentPairs.serialize(null).isEmpty());
        t("round: serialize drops empty Find", RecentPairs.serialize(Arrays.asList(new Pair("", "x"))).isEmpty());

        // ---- broken text never throws
        t("parse: null / empty", RecentPairs.parse(null).isEmpty() && RecentPairs.parse("").isEmpty());
        t("parse: garbage", RecentPairs.parse("hello world").isEmpty() && RecentPairs.parse("|||").isEmpty() && RecentPairs.parse(";").isEmpty());
        t("parse: too many separators in one item skipped", RecentPairs.parse("a;b;c").isEmpty());
        List<Pair> mix = RecentPairs.parse("a;b|broken|c;d|;x|e;");
        t("parse: good items kept, bad skipped", mix.size() == 3 && mix.get(0).find.equals("a") && mix.get(1).find.equals("c") && mix.get(2).find.equals("e") && mix.get(2).with.isEmpty());
        t("parse: bad %XXXX kept as text", RecentPairs.parse("a%zz;b").get(0).find.equals("a%zz") && RecentPairs.parse("50%;b").get(0).find.equals("50%"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) sb.append(i > 0 ? "|" : "").append("f").append(i).append(";w");
        t("parse: max 10", RecentPairs.parse(sb.toString()).size() == 10);

        // ---- texts
        Pair p = new Pair("a\nb", "");
        t("label: new line sign + empty sign", RecentPairs.label(p, 20).contains("\u23CE") && RecentPairs.label(p, 20).contains("\u2205"));
        t("label: cut", RecentPairs.label(new Pair(longT, "x"), 10).contains("\u2026") && RecentPairs.label(null, 10).isEmpty());
        t("text: switch help says stored + deleted + backup + password", RecentPairs.switchHelp().contains("stored") && RecentPairs.switchHelp().contains("deleted") && RecentPairs.switchHelp().contains("Backup") && RecentPairs.switchHelp().contains("Password"));
        t("text: status line", RecentPairs.statusLine(false, 3).contains("OFF") && RecentPairs.statusLine(true, 0).contains("No pair") && RecentPairs.statusLine(true, 1).contains("1 pair ") && RecentPairs.statusLine(true, 4).contains("4 pairs"));
        t("text: empty list text", RecentPairs.emptyText(true).contains("No pair") && RecentPairs.emptyText(false).contains("OFF"));
        t("prefs names", RecentPairs.PREF_ON.equals("rpr_on") && RecentPairs.PREF_LIST.equals("rpr_list") && com.mgb.keyboard.typing.BackupPlan.isSkippedPref(RecentPairs.PREF_ON) && com.mgb.keyboard.typing.BackupPlan.isSkippedPref(RecentPairs.PREF_LIST));

        System.out.println("RecentPairsTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }

    static int count(String s, char c) { int n = 0; for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++; return n; }
}
