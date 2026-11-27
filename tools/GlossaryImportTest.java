import com.mgb.keyboard.typing.GlossaryImport;
import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplaceProtect;
import com.mgb.keyboard.typing.ReplaceRules;

import java.util.ArrayList;
import java.util.List;

/** v0.54.0 (GL1) the text of the plugin's "Copy for keyboard" becomes sets + never-change terms, and the sets really fix a text. */
public class GlossaryImportTest {
    static int ok = 0, bad = 0;
    static void eq(String name, Object a, Object b) {
        if (String.valueOf(a).equals(String.valueOf(b))) ok++; else { bad++; System.out.println("FAIL " + name + ": got [" + a + "] want [" + b + "]"); }
    }

    public static void main(String[] a) {
        String t = "## jabra-glossary v1 | Novel A | newest names\n## wrong forms\nword: Lin Fong | Lin Fen => Lin Feng\nBlue Dragon => Azure Dragon\nnot a rule\n"
                 + "## raw to English\n林风 => Lin Feng\n## never change\nLin Fen Pavilion\nMayor Gu\n";
        eq("looksLike", GlossaryImport.looksLike(t), true);
        eq("plain text is not a glossary", GlossaryImport.looksLike("a => b"), false);
        eq("null", GlossaryImport.looksLike(null), false);
        GlossaryImport.Plan p = GlossaryImport.parse(t, 40);
        eq("prefix", p.prefix, "G Novel A");
        eq("two sets", p.setNames.size(), 2);
        eq("name 1", p.setNames.get(0), "G Novel A \u00B7 wrong");
        eq("name 2", p.setNames.get(1), "G Novel A \u00B7 raw");
        eq("wrong count", p.wrong, 2);
        eq("raw count", p.raw, 1);
        eq("bad line", p.bad, 1);
        eq("never", p.never.size(), 2);
        eq("glossary set name", GlossaryImport.isGlossarySet(p.setNames.get(0)), true);
        eq("user set name", GlossaryImport.isGlossarySet("Pairs 1"), false);

        // the generated set fixes a text and the never-change term stays
        ReplaceRules.Parsed ps = ReplaceRules.parse(p.setTexts.get(0));
        eq("no bad lines in the set", ps.bad.size(), 0);
        eq("2 rules", ps.active(), 2);
        ReplaceEngine.Opt o = new ReplaceEngine.Opt();
        o.mode = ReplaceEngine.MODE_TEXT; o.matchCase = p.setCase.get(0);
        List<String> prot = new ArrayList<String>(p.never);
        o.protect = prot;
        ReplaceRules.RunResult rr = ReplaceRules.run("Lin Fen met Lin Fong. The Blue Dragon roared over Lin Fen Pavilion. Fence.", ps.rules, o);
        eq("fixed text", rr.out, "Lin Feng met Lin Feng. The Azure Dragon roared over Lin Fen Pavilion. Fence.");

        // long section = several sets of 50, same names every time, a cap on the number of sets
        StringBuilder sb = new StringBuilder("## jabra-glossary v1 | X\n## raw to English\n");
        for (int i = 0; i < 120; i++) sb.append("a").append(i).append(" => b").append(i).append('\n');
        GlossaryImport.Plan q = GlossaryImport.parse(sb.toString(), 40);
        eq("3 sets", q.setNames.size(), 3);
        eq("set names", q.setNames.get(2), "G X \u00B7 raw 3");
        eq("each set parses", ReplaceRules.parse(q.setTexts.get(0)).active(), 50);
        eq("last set", ReplaceRules.parse(q.setTexts.get(2)).active(), 20);
        eq("same names again", GlossaryImport.parse(sb.toString(), 40).setNames, q.setNames);
        GlossaryImport.Plan c = GlossaryImport.parse(sb.toString(), 2);
        eq("cap: 2 sets", c.setNames.size(), 2);
        eq("cap: dropped sets", c.droppedSets, 1);
        eq("cap: dropped rules", c.droppedRules, 20);
        eq("no room", GlossaryImport.parse(sb.toString(), 0).setNames.size(), 0);
        eq("long names stay under 40", GlossaryImport.parse("## jabra-glossary v1 | A very very long novel name that goes on\n## wrong forms\na => b\n", 5).setNames.get(0).length() <= 40, true);
        eq("never list is the engine's", ReplaceProtect.parse("x\ny").size(), 2);
        eq("garbage", GlossaryImport.parse(null, 5).setNames.size(), 0);

        System.out.println("GlossaryImportTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
