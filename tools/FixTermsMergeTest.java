import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import com.mgb.keyboard.typing.*;

/** v0.60.0 (FT1): guard for the merge of Fix terms with the Replace panel.
 *  1) WIRING (reads the REAL Kotlin sources, run from the repo root): every FixNamesUi.Api function is implemented by ReplacePanel and every api.xxx() the view calls exists;
 *     the old Novels screen and the old Never change screen are gone, their entry points open Fix terms; typing 13 = new novel name, 14 = name to keep; braces balance.
 *  2) LOGIC of "Terms > Paste" (real Java classes): lines "wrong;forms => right" become one saved pair with several wrong forms, shown as one term row. */
public class FixTermsMergeTest {
    static int total = 0, fail = 0;
    static void ok(boolean c, String m) { total++; if (!c) { fail++; System.out.println("FAIL: " + m); } }
    static String read(String p) throws IOException {
        File f = new File(p);
        ok(f.isFile(), p + " found (run from the repo root)");
        if (!f.isFile()) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String l; while ((l = r.readLine()) != null) sb.append(l).append('\n');
        }
        return sb.toString();
    }
    static boolean has(String s, String t) { return s.indexOf(t) >= 0; }

    /** Rough balance check: strings and comments removed first. */
    static boolean balanced(String s) {
        s = s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        s = s.replaceAll("\"(?:\\\\.|[^\"\\\\\\n])*\"", "\"\"").replaceAll("'(?:\\\\.|[^'\\\\\\n])'", "''");
        int b = 0, p = 0, k = 0;
        for (char c : s.toCharArray()) { if (c == '{') b++; else if (c == '}') b--; else if (c == '(') p++; else if (c == ')') p--; else if (c == '[') k++; else if (c == ']') k--; }
        return b == 0 && p == 0 && k == 0;
    }

    static Set<String> all(Pattern p, String s, int g) { Set<String> o = new TreeSet<String>(); Matcher m = p.matcher(s); while (m.find()) o.add(m.group(g)); return o; }

    public static void main(String[] a) throws Exception {
        String rp = read("app/src/main/java/com/mgb/keyboard/ReplacePanel.kt");
        String fv = read("app/src/main/java/com/mgb/keyboard/FixNamesView.kt");

        // ---- wiring
        int i0 = fv.indexOf("interface Api {"), i1 = fv.indexOf("// state that lives");
        ok(i0 > 0 && i1 > i0, "Api interface found");
        String iface = i0 > 0 && i1 > i0 ? fv.substring(i0, i1) : "";
        Set<String> decl = all(Pattern.compile("fun (\\w+)\\("), iface, 1);
        Set<String> impl = all(Pattern.compile("override fun (\\w+)\\("), rp, 1);
        Set<String> used = all(Pattern.compile("\\bapi\\.(\\w+)\\("), fv, 1);
        ok(decl.size() >= 30, "Api has its functions (" + decl.size() + ")");
        Set<String> missing = new TreeSet<String>(decl); missing.removeAll(impl);
        ok(missing.isEmpty(), "every Api function is implemented in ReplacePanel: missing " + missing);
        Set<String> unknown = new TreeSet<String>(used); unknown.removeAll(decl);
        ok(unknown.isEmpty(), "the view calls only declared api functions: " + unknown);
        for (String n : new String[]{"keepSelection", "keepClipboard", "keepClear", "keepClearAsk", "pasteTerms", "addKeep", "keepInput", "newNovelInput", "fixOne"})
            ok(decl.contains(n) && impl.contains(n), "merged feature present: " + n);

        // ---- old screens gone, entry points open Fix terms
        ok(!has(rp, "fun novelsView()") && !has(rp, "fun protectView()"), "old Novels and Never change screens are removed");
        ok(!has(rp, "typing == 7) protectView()") && !has(rp, "typing == 9) novelsView()"), "dispatcher no longer knows typing 7 / 9");
        ok(!has(rp, "typing = 7;") && !has(rp, "typing = 9;"), "nothing opens the removed screens");
        ok(has(rp, "private fun openProtect() {") && has(rp, "fnUi.tab = 3; typing = 10"), "Never / Never change buttons open Fix terms > Keep as is");
        ok(has(rp, "fnUi.showNovels(); typing = 10"), "Novels menu opens Fix terms > Novels");
        ok(!has(rp, "fnKeepBack") && !has(rp, "openKeep"), "the old 'open Never change from Fix terms' hop is gone");

        // ---- typing codes
        ok(has(fv, "fieldBox(\"New novel name\", api.newNovelInput(), 13)") && has(fv, "api.type(13)"), "view: new novel name = typing 13");
        ok(has(fv, "fieldBox(\"Name to keep as is\", api.keepInput(), 14)") && has(fv, "api.type(14)"), "view: name to keep = typing 14");
        ok(has(rp, "if (typing == 14) head.addView(btn(\"Add\", 1.4f, 34) { commitKeepName() })"), "typeView: Add button for the Keep box");
        ok(has(rp, "else if (typing == 14) fnKeepIn = v else if (typing == 13) novelNameBuf = v else repl = v"), "setCur knows typing 14");
        ok(has(rp, "else if (typing == 14) 14 else if (typing == 13) 13 else 1;"), "paste picker returns to the Keep box");
        ok(has(rp, "typing = if (fnBack) 10 else 0;"), "Done goes back to Fix terms");

        // ---- best-of-both features are in the view
        ok(has(fv, "\"+ Selection\"") && has(fv, "\"+ Clipboard\"") && has(fv, "Sure? Clear"), "Keep as is: + Selection / + Clipboard / Clear (from Never change)");
        ok(has(fv, "api.pasteTerms()"), "Terms: Paste list (from Pairs > Paste)");
        ok(has(fv, "api.pickNovel(\"\")"), "Novels: No novel (from the old Novels list)");

        // ---- syntax guard (no Kotlin compiler in the test box)
        ok(balanced(rp), "ReplacePanel.kt: braces, brackets and parentheses balance");
        ok(balanced(fv), "FixNamesView.kt: braces, brackets and parentheses balance");

        // ---- logic of Terms > Paste
        ReplacePaste.Result res = ReplacePaste.parse("Gaam;geme;gome => game\nLin Fen => Lin Feng\n# comment\nbad line", new ArrayList<ReplaceRules.Rule>(), 200);
        ok(res.changed() == 2, "paste reads 2 pairs, got " + res.changed());
        List<String> setLines = new ArrayList<String>();
        int saved = 0;
        String setName = NovelBook.mySetName("Demo", 1);
        String text = "# " + setName + " (pairs saved by me)\n";
        for (ReplaceRules.Rule r : res.rules) {
            if (r.kind != ReplaceRules.K_TEXT || r.with.isEmpty()) continue;
            List<String> vs = ReplaceRules.variants(r.find);
            List<String> forms = vs.isEmpty() ? Collections.singletonList(r.find) : vs;
            StringBuilder sb = new StringBuilder();
            for (String f : forms) { if (sb.length() > 0) sb.append("; "); sb.append(f); }
            ReplacePairList.Made m = ReplacePairList.make(sb.toString(), r.with, ReplaceEngine.MODE_TEXT, false, false);
            ok(m.rule != null, "pair is valid: " + r.find);
            if (m.rule == null) continue;
            String line = ReplacePairList.text(Collections.singletonList(m.rule)).trim();
            NovelBook.Merged mg = FixNames.mergeWithTopic(text, line, NovelBook.MY_TOPIC, NovelBook.RULES_PER_SET);
            text = mg.text; saved++;
        }
        ok(saved == 2, "both pasted pairs saved, got " + saved);
        List<FixNames.Row> rows = FixNames.rows("Demo", Collections.singletonList(setName), Collections.singletonList(text));
        ok(rows.size() == 2, "novel shows 2 rows, got " + rows.size());
        FixNames.Row multi = null;
        for (FixNames.Row r : rows) if ("game".equals(r.to)) multi = r;
        ok(multi != null && multi.forms.size() == 3 && multi.forms.contains("geme") && multi.forms.contains("gome"), "Gaam;geme;gome => game is ONE term with 3 wrong forms");
        FixNames.Scan sc = FixNames.scan("The gome was a geme. Lin Fen came.", rows, new HashSet<String>(), new ArrayList<String>(), new ArrayList<String>(), "");
        ok(sc.hits == 3, "the pasted terms fix 3 places in the text, got " + sc.hits);

        System.out.println("FixTermsMergeTest " + (total - fail) + "/" + total + (fail == 0 ? " OK" : " FAILED"));
        if (fail > 0) System.exit(1);
    }
}
