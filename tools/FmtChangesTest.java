import com.mgb.keyboard.formatter.FmtChanges;
import com.mgb.keyboard.formatter.NovelFormatter;
import java.util.*;

/** v0.52.0: java -cp out FmtChangesTest */
public class FmtChangesTest {
    static int pass = 0, fail = 0;
    static void check(String n, Object got, Object want) {
        if (String.valueOf(got).equals(String.valueOf(want))) pass++;
        else { fail++; System.out.println("FAIL " + n + "\n  got : " + got + "\n  want: " + want); }
    }
    public static void main(String[] a) {
        Map<String, String> o = new LinkedHashMap<String, String>(NovelFormatter.defaults());
        String t = "Chapter 1\n\n\n\n\"Hi,\" she said... He -- *no* **yes**\u200b.\n\n***\n\nrecieve recieve";
        o.put("quotes", "curly"); o.put("dots", "true"); o.put("dash", "true"); o.put("autoH", "2");
        o.put("rules", "recieve => receive\n# note\nre:okay => OK");
        Map<String, Integer> c = FmtChanges.changes(t, o);
        check("zw", c.get("z"), 1);
        check("md", c.get("m"), 2);
        check("dots", c.get("d"), 1);
        check("dash", c.get("a"), 1);
        check("quotes", c.get("q"), 2);
        check("blank", c.get("b"), 1);
        check("scene", c.get("s"), 1);
        check("title", c.get("t"), 1);
        check("rules", c.get("r"), 2);
        o.put("zw", "false"); o.put("quotes", "none");
        c = FmtChanges.changes(t, o);
        check("zw off", c.get("z"), null);
        check("q none", c.get("q"), null);
        // rules on/off
        String r = "recieve => receive\nokay => OK";
        r = FmtChanges.toggleRule(r, 1);
        check("off text", r, "recieve => receive\n#off okay => OK");
        check("rulesSummary", FmtChanges.rulesSummary(new HashMap<String, String>() {{ put("rules", "a => b\n#off c => d\n# x"); }}), "1 of 2 on");
        r = FmtChanges.toggleRule(r, 1);
        check("on again", r, "recieve => receive\nokay => OK");
        // off rule must not reach the engine as active rule
        Map<String, String> o2 = new LinkedHashMap<String, String>(NovelFormatter.defaults());
        o2.put("rules", "#off recieve => receive");
        List<NovelFormatter.Block> bl = NovelFormatter.parsePlain("recieve");
        check("engine ignores off", NovelFormatter.plainText(o2, bl), "recieve");
        o2.put("rules", FmtChanges.toggleRule(o2.get("rules"), 0));
        check("engine uses on", NovelFormatter.plainText(o2, bl), "receive");
        List<FmtChanges.RuleLine> rl = FmtChanges.ruleLines("a => b\n#off del: x\n# c");
        check("lines", rl.size(), 2);
        check("matches", FmtChanges.matches("aaa a", rl.get(0)), 4);
        check("find", rl.get(0).find(), "a");
        check("to", rl.get(0).to(), "b");
        check("empty", FmtChanges.changes("", o).size(), 0);
        check("para", FmtChanges.paraSummary(NovelFormatter.defaults()), "2em · gap 1 · auto");
        check("keep", FmtChanges.keepSummary(NovelFormatter.defaults()), "B I U");
        check("suggest yes", FmtChanges.titleSuggest("\n Chapter 5: X\nbody"), true);
        check("suggest no", FmtChanges.titleSuggest("He said hi.\nChapter 2"), false);
        System.out.println(pass + "/" + (pass + fail) + " passed");
        if (fail > 0) System.exit(1);
    }
}
