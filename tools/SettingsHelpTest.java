import com.mgb.keyboard.typing.SettingsIndex;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * v0.15.8 (N8): guard. Reads the REAL SettingsGroups.kt, lists every settings row of the app pages
 * (toggle / level / cycle / stepper / textRow) and FAILS when a row has no help line (SettingsIndex.help) or a help line is not one sane line. (Some old lines are longer than 90 chars; that is fine, only 200 is the guard.)
 * Run from the repo root: java -cp out SettingsHelpTest
 */
public class SettingsHelpTest {
    static int ok = 0, bad = 0;
    static void check(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    public static void main(String[] a) throws Exception {
        File f = new File("app/src/main/java/com/mgb/keyboard/SettingsGroups.kt");
        check("SettingsGroups.kt found (run from the repo root)", f.isFile());
        if (!f.isFile()) { System.out.println("SettingsHelpTest " + ok + "/" + (ok + bad) + " FAILED"); System.exit(1); }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        Matcher m = Pattern.compile("\\b(toggle|level|cycle|stepper|textRow)\\(\\s*[a-z]+,\\s*\"([^\"]+)\",\\s*\"([a-z0-9_]+)\"").matcher(sb.toString());
        LinkedHashMap<String, String> rows = new LinkedHashMap<>();   // key -> title
        while (m.find()) if (!rows.containsKey(m.group(3))) rows.put(m.group(3), m.group(2));
        check("found at least 80 rows (parser sanity, now 87)", rows.size() >= 80);
        for (Map.Entry<String, String> e : rows.entrySet()) {
            String h = SettingsIndex.help(e.getKey());
            check("help line for row \"" + e.getValue() + "\" (key " + e.getKey() + ")", !h.isEmpty());
            check("help line is one sane line (<= 200 chars) for " + e.getKey(), h.length() <= 200 && h.indexOf('\n') < 0);
        }
        System.out.println("SettingsHelpTest " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " FAILED") + "  (" + rows.size() + " rows)");
        if (bad > 0) System.exit(1);
    }
}
