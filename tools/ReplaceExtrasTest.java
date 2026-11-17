import com.mgb.keyboard.formatter.NovelFormatter;
import com.mgb.keyboard.typing.ReplaceExtras;
import com.mgb.keyboard.typing.ReplaceRules;
import com.mgb.keyboard.typing.ReplaceSets;

/** v0.28.0 (R3b) ReplaceExtras tests (computer only): undo stack + Formatter bridge text. */
public class ReplaceExtrasTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] x) {
        ReplaceExtras.UndoStack u = new ReplaceExtras.UndoStack();
        t("empty: cannot undo", !u.canUndo() && u.size() == 0 && u.peek() == null);
        u.drop(); u.clear();
        t("drop / clear on empty is safe", u.size() == 0);
        u.push("b", "a");
        t("one step", u.canUndo() && u.size() == 1 && u.peek().res.equals("b") && u.peek().orig.equals("a"));
        u.push("c", "b"); u.push("d", "c");
        t("three steps, newest on top", u.size() == 3 && u.peek().res.equals("d"));
        u.push("e", "d");
        t("max 3: oldest dropped", u.size() == ReplaceExtras.MAX_UNDO && u.peek().res.equals("e"));
        u.drop();
        t("drop removes the newest", u.size() == 2 && u.peek().res.equals("d"));
        u.drop(); u.drop();
        t("empty again", !u.canUndo());
        u.push("same", "same"); u.push(null, "a"); u.push("a", null);
        t("no-change / null steps ignored", u.size() == 0);
        u.push("y", "x"); u.clear();
        t("clear", u.size() == 0);
        String emo = "ab\uD83D\uDE00cd";   // a b [grin emoji = 2 chars] c d
        t("head keeps a pair whole", ReplaceExtras.head(emo, 3).equals("ab") && ReplaceExtras.head(emo, 4).equals("ab\uD83D\uDE00") && ReplaceExtras.head(emo, 99).equals(emo));
        t("tail keeps a pair whole", ReplaceExtras.tail(emo, 3).equals("cd") && ReplaceExtras.tail(emo, 4).equals("\uD83D\uDE00cd") && ReplaceExtras.tail(emo, 99).equals(emo));
        t("head / tail edge cases", ReplaceExtras.head(null, 3).isEmpty() && ReplaceExtras.head("abc", 0).isEmpty() && ReplaceExtras.tail(null, 3).isEmpty() && ReplaceExtras.tail("abc", -1).isEmpty() && ReplaceExtras.head("abc", 2).equals("ab") && ReplaceExtras.tail("abc", 2).equals("bc"));
        t("head: the cut text is never longer than n", ReplaceExtras.head("\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00", 3).length() <= 3 && ReplaceExtras.head("\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00", 1).isEmpty());
        t("RecentPairs.label does not split a pair", !com.mgb.keyboard.typing.RecentPairs.label(new com.mgb.keyboard.typing.RecentPairs.Pair("ab\uD83D\uDE00cd", "x"), 3).contains("\uD83D\u2026"));
        t("undoLabel", ReplaceExtras.undoLabel(1).equals("Undo") && ReplaceExtras.undoLabel(3).equals("Undo (3)") && ReplaceExtras.undoLabel(0).equals("Undo"));

        t("formatterText empty / null = empty", ReplaceExtras.formatterText("a", "", false, false).isEmpty() && ReplaceExtras.formatterText("a", null, false, false).isEmpty() && ReplaceExtras.formatterText("a", "  \n ", false, false).isEmpty());
        String f = ReplaceExtras.formatterText("My set", "cat => dog\nre: (\\d+)원 => ₩$1", true, false);
        t("header lines + flags", f.startsWith("# From Find & Replace set: My set\n# Set options: match case ON, whole word OFF\n"));
        t("rules kept + ends with newline", f.contains("cat => dog\nre: (\\d+)원 => ₩$1\n") && f.endsWith("\n"));
        t("no name = (no name)", ReplaceExtras.formatterText(null, "a => b", false, true).contains("(no name)") && ReplaceExtras.formatterText(" ", "a => b", false, true).contains("whole word ON"));
        t("newline in name cannot break the comment", !ReplaceExtras.formatterText("a\nb => c", "x => y", false, false).split("\n")[1].startsWith("b"));
        // both parsers accept the bridge text, and read the same rules
        int rules = 0, formatterRules = 0, badLines = 0;
        boolean allOk = true;
        for (ReplaceSets.Def d : ReplaceSets.all()) {
            String ft = ReplaceExtras.formatterText(d.name, d.text, d.matchCase, false);
            ReplaceRules.Parsed p = ReplaceRules.parse(ft);
            ReplaceRules.Parsed p0 = ReplaceRules.parse(d.text);
            if (p.active() != p0.active() || !p.bad.isEmpty()) allOk = false;
            NovelFormatter.Rules fr = NovelFormatter.parseRules(ft);
            NovelFormatter.Rules fr0 = NovelFormatter.parseRules(d.text);
            if (fr.repCount() + fr.delCount() != fr0.repCount() + fr0.delCount()) allOk = false;
        }
        t("every ready-made set: header does not change what either parser reads", allOk);
        // ---- v0.29.0 flags dropped for the Formatter
        {
            String ft = ReplaceExtras.formatterText("S", "keep: word: a => b\nre: x => y\n# keep: note", false, false);
            t("formatter text: flags removed", ft.contains("\na => b\n") && !ft.contains("\nkeep: word:") && ft.contains("# Note: word: / keep:"));
            t("formatter text: plain set has no note", !ReplaceExtras.formatterText("S", "a => b", false, false).contains("# Note:"));
        }
        // ---- v0.31.0 set preview
        {
            com.mgb.keyboard.typing.ReplaceEngine.Opt po = new com.mgb.keyboard.typing.ReplaceEngine.Opt(); po.bangla = false;
            String pv = ReplaceExtras.setPreview("Demo", "a => b\n# note\nzzz => y\ndel: q", po, "a a\nq\nx");
            t("preview: head line", pv.startsWith("Demo: ") && pv.contains("on 7 chars"));
            t("preview: counts per rule", pv.contains("2x  a => b") && pv.contains("0x  zzz => y") && pv.contains("1x  del: q"));
            t("preview: comment not listed", !pv.contains("note"));
            t("preview: no text", ReplaceExtras.setPreview("D", "a => b", po, "").contains("no text"));
            t("preview: no rules", ReplaceExtras.setPreview("D", "# only", po, "abc").contains("no rules"));
            StringBuilder many = new StringBuilder(); for (int i = 0; i < 9; i++) many.append("k" + i + " => v\n");
            t("preview: more rules line", ReplaceExtras.setPreview("D", many.toString(), po, "k1").contains("+3 more rules"));
            t("preview: null safe", ReplaceExtras.setPreview(null, null, null, null) instanceof String);
        }
        System.out.println("ReplaceExtrasTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
