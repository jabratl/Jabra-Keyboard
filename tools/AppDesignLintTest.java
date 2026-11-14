import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v0.17.76 (U1) DESIGN DRIFT GUARD for the main app screens (app/src/main/java/com/mgb/keyboard/App*.kt).
 * Counts hard-coded `textSize = <number>` and `0xFFrrggbb` literals per file. A file may NOT have more than its BASELINE below
 * (a file that is not listed has baseline 0 = must use AppTokens.T_* and AppUi / AppPalette colours). Steps U2..U11 LOWER the numbers;
 * U11 must reach an empty table. Never raise a number: use a token instead (AppTokens.T_CAPTION/T_LABEL/T_BODY/T_ROW/T_TITLE/T_HEAD/T_DISPLAY, AppUi.*).
 * Run from the repo root (tools/run_tests.sh does). Source folder missing = skipped (pass).
 */
public class AppDesignLintTest {
    static int fail = 0, total = 0;
    // file, max hard-coded text sizes, max hard-coded 0xFF colours
    static final String[][] BASELINE = {
        {"AppPages.kt", "4", "0"},
        {"AppSearch.kt", "7", "0"},
        {"AppThemeGrid.kt", "3", "0"},
        {"AppThemeStudio.kt", "13", "1"},
        {"AppToolbarEditor.kt", "4", "0"},
        {"AppWizard.kt", "1", "0"}
    };

    static int[] base(String name) {
        for (String[] b : BASELINE) if (b[0].equals(name)) return new int[]{Integer.parseInt(b[1]), Integer.parseInt(b[2])};
        return new int[]{0, 0};
    }

    public static void main(String[] a) throws Exception {
        File dir = new File("app/src/main/java/com/mgb/keyboard");
        File[] fs = dir.listFiles();
        if (fs == null) { System.out.println("AppDesignLintTest: source folder not found, skipped"); return; }
        Pattern ts = Pattern.compile("textSize\\s*=\\s*\\d");
        Pattern col = Pattern.compile("0x[Ff]{2}[0-9A-Fa-f]{6}");
        for (File f : fs) {
            String n = f.getName();
            if (!n.startsWith("App") || !n.endsWith(".kt")) continue;
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            int c1 = 0, c2 = 0;
            Matcher m = ts.matcher(s); while (m.find()) c1++;
            m = col.matcher(s); while (m.find()) c2++;
            int[] b = base(n);
            total++; if (c1 > b[0]) { fail++; System.out.println("FAIL " + n + ": " + c1 + " hard-coded text sizes (max " + b[0] + ") - use AppTokens.T_*"); }
            total++; if (c2 > b[1]) { fail++; System.out.println("FAIL " + n + ": " + c2 + " hard-coded colours (max " + b[1] + ") - use AppUi / AppPalette"); }
        }
        System.out.println("AppDesignLintTest: " + (total - fail) + "/" + total + (fail == 0 ? " OK" : " FAILED"));
        if (fail > 0) System.exit(1);
    }
}
