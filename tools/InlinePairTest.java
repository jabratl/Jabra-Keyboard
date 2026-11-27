import com.mgb.keyboard.typing.*;
import java.util.*;

/** v0.58.8: "wrong;forms => right" in ONE line (Find box + Paste pairs). Run: bash tools/run_tests.sh */
public class InlinePairTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    static String run(String text, ReplaceRules.Rule r) {
        List<ReplaceRules.Rule> l = new ArrayList<ReplaceRules.Rule>(); l.add(r);
        return ReplaceRules.run(text, l, new ReplaceEngine.Opt()).out;
    }

    public static void main(String[] a) {
        // Find box: all in Find, With empty
        ReplacePairList.Made m = ReplacePairList.make("Gaam;geme;gome;gggo=>game", "", ReplaceEngine.MODE_TEXT, false, false);
        t("inline: made", m.rule != null && m.error == null);
        t("inline: serialized", m.rule != null && ReplacePairList.text(Arrays.asList(m.rule)).equals("Gaam | geme | gome | gggo => game"));
        t("inline: replaces all forms", m.rule != null && run("Gaam geme gome gggo game", m.rule).equals("game game game game game"));
        // spaces around => and ;
        m = ReplacePairList.make("  Lin Fen ; Lin Fong  =>  Lin Feng ", "", ReplaceEngine.MODE_TEXT, false, false);
        t("inline: spaces", m.rule != null && m.rule.with.equals("Lin Feng") && ReplaceRules.variants(m.rule.find).size() == 2);
        // old way (Find + With boxes) unchanged
        m = ReplacePairList.make("Gaam;geme", "game", ReplaceEngine.MODE_TEXT, false, false);
        t("old: two boxes", m.rule != null && m.rule.with.equals("game") && ReplaceRules.variants(m.rule.find).size() == 2);
        // With filled + => in Find stays an error (old behaviour)
        m = ReplacePairList.make("a=>b", "c", ReplaceEngine.MODE_TEXT, false, false);
        t("old: => in Find with With filled = error", m.rule == null && m.error != null);
        // nothing before => stays an error
        m = ReplacePairList.make("=>b", "", ReplaceEngine.MODE_TEXT, false, false);
        t("empty find before => = error", m.rule == null);
        // regex mode untouched
        m = ReplacePairList.make("a=>b", "", ReplaceEngine.MODE_REGEX, false, false);
        t("regex: untouched (still an error, like before)", m.rule == null && m.error != null);
        // delete pair: "oops=>"
        m = ReplacePairList.make("oops;ooops=>", "", ReplaceEngine.MODE_TEXT, false, false);
        t("inline: right form empty = delete", m.rule != null && run("an oops and ooops", m.rule).equals("an  and "));
        // Paste pairs
        ReplacePaste.Result r = ReplacePaste.parse("Gaam;geme;gome;gggo => game\ncat;kat=>dog\n&amp; => and\nplain => ok", new ArrayList<ReplaceRules.Rule>(), 50);
        t("paste: 4 pairs", r.added == 4 && r.bad == 0);
        t("paste: split", r.rules.size() == 4 && run("Gaam gome gggo", r.rules.get(0)).equals("game game game"));
        t("paste: no spaces around =>", run("kat cat", r.rules.get(1)).equals("dog dog"));
        t("paste: one piece with ; stays text", r.rules.get(2).find.equals("&amp;"));
        t("paste: plain unchanged", r.rules.get(3).find.equals("plain"));
        r = ReplacePaste.parse("word: a;b => c", new ArrayList<ReplaceRules.Rule>(), 50);
        t("paste: word keeps", r.rules.size() == 1 && r.rules.get(0).word && ReplaceRules.variants(r.rules.get(0).find).size() == 2);
        // Never throws
        t("null safe", ReplacePairList.make(null, null, 0, false, false).rule == null);
        System.out.println("InlinePairTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
