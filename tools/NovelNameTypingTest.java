import java.io.*;
import java.nio.charset.StandardCharsets;

/** v0.58.7: guard. "Add novel" must NOT use an AlertDialog (a keyboard has no window token: the dialog never opened and the novel was not created).
 *  The name is typed with the panel's own small keys (typing == 13). Reads the REAL Kotlin sources (run from the repo root). */
public class NovelNameTypingTest {
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

    public static void main(String[] a) throws Exception {
        String rp = read("app/src/main/java/com/mgb/keyboard/ReplacePanel.kt");
        String fv = read("app/src/main/java/com/mgb/keyboard/FixNamesView.kt");

        ok(!has(rp, "setTitle(\"Add novel\")"), "ReplacePanel: no AlertDialog titled Add novel");
        ok(!has(fv, "setTitle(\"Add novel\")"), "FixNamesView: no AlertDialog titled Add novel");
        ok(has(fv, "api.type(13)"), "FixNamesView: + Add novel opens the typing box (api.type(13))");

        ok(has(rp, "private var novelNameBuf"), "ReplacePanel: novelNameBuf field");
        ok(has(rp, "else if (typing == 13) novelNameBuf else repl"), "cur() knows typing 13");
        ok(has(rp, "else if (typing == 13) novelNameBuf = v else repl = v"), "setCur() knows typing 13");
        ok(has(rp, "else if (typing == 13) \"Novel: \""), "showValue() label for typing 13");
        ok(has(rp, "if (typing == 13) head.addView(btn(\"Add novel\""), "typeView has the Add novel button");
        ok(has(rp, "fun commitNovelName()"), "commitNovelName exists");
        ok(has(rp, "val r = fnAddNovel(novelNameBuf)"), "commit uses the tested fnAddNovel (NovelBook.add + verified save)");
        ok(has(rp, "typing = if (fnBack) 10 else 0;"), "Done leaves the name / keep box back to Fix terms (v0.60.0: no old Novels list any more)");
        ok(has(rp, "else if (typing == 13) 13 else 1"), "paste picker returns to the name box");
        ok(has(rp, "if (which == 13) novelNameBuf = \"\""), "api.type(13) starts with an empty name");
        ok(!has(rp, "fun showNovelNameDialog()") && !has(rp, "fun novelsView()"), "v0.60.0: the old Novels screen and its dialog starter are gone");
        ok(has(rp, "else if (typing == 14) fnKeepIn else if (typing == 13) novelNameBuf else repl"), "cur() also knows typing 14 (Keep as is box)");

        System.out.println("NovelNameTypingTest " + (total - fail) + "/" + total + (fail == 0 ? " OK" : " FAILED"));
        if (fail > 0) System.exit(1);
    }
}
