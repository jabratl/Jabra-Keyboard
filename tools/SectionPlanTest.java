import com.mgb.keyboard.typing.ChangedPlan;
import com.mgb.keyboard.typing.PagePlan;
import com.mgb.keyboard.typing.SectionPlan;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;

/** v0.17.80 (U5a). Run from the repo root (tools/run_tests.sh does). */
public class SectionPlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (Objects.equals(a, b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }

    public static void main(String[] x) throws Exception {
        eq("unknown = basic", SectionPlan.level("zzz_unknown"), SectionPlan.BASIC);
        eq("null = basic", SectionPlan.level(null), SectionPlan.BASIC);
        eq("bn_map advanced", SectionPlan.isAdvanced("bn_map"), true);
        eq("theme basic", SectionPlan.isAdvanced("theme"), false);
        eq("listed theme", SectionPlan.listed("theme"), true);
        eq("not listed", SectionPlan.listed("zzz"), false);
        eq("typing has adv", SectionPlan.hasAdvanced(PagePlan.TYPING), true);
        eq("look has adv", SectionPlan.hasAdvanced(PagePlan.LOOK), true);
        eq("layout none yet", SectionPlan.hasAdvanced(PagePlan.LAYOUT), false);
        eq("home none", SectionPlan.hasAdvanced(PagePlan.HOME), false);
        eq("null page", SectionPlan.hasAdvanced(null), false);
        // visibility rule
        eq("basic always", SectionPlan.visible(false, false, false, false), true);
        eq("adv hidden", SectionPlan.visible(false, true, false, false), false);
        eq("adv on", SectionPlan.visible(true, true, false, false), true);
        eq("adv changed shows", SectionPlan.visible(false, true, true, false), true);
        eq("adv forced shows", SectionPlan.visible(false, true, false, true), true);
        eq("button off 0", SectionPlan.buttonText(false, 0), "Show advanced settings");
        eq("button off n", SectionPlan.buttonText(false, 5), "Show advanced settings (5 more)");
        eq("button on", SectionPlan.buttonText(true, 0), "Hide advanced settings");
        // tables: no duplicates, no key in both
        Set<String> seen = new HashSet<>();
        for (String k : SectionPlan.basicKeys()) eq("unique basic " + k, seen.add(k), true);
        for (String k : SectionPlan.advancedKeys()) eq("unique adv " + k, seen.add(k), true);
        // sections come from ChangedPlan (single source)
        eq("section forward", SectionPlan.section("Look", "theme"), ChangedPlan.sectionOf("Look", "theme"));
        eq("section forward 2", SectionPlan.section("Typing", "bs_word"), "Editing");

        // EVERY row key of the 3 pages' groups (read from SettingsGroups.kt) has an entry. Source missing = skipped.
        File f = new File("app/src/main/java/com/mgb/keyboard/SettingsGroups.kt");
        if (f.exists()) {
            String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Pattern rx = Pattern.compile("\\b(?:toggle|cycle|stepper|textRow|level)\\(\\s*\\w+,\\s*\"(?:[^\"\\\\]|\\\\.)*\",\\s*\"([a-z0-9_]+)\"");
            String[] parts = s.split("\n        group\\(\"");
            int found = 0;
            Set<String> keysFound = new HashSet<>();
            for (int i = 1; i < parts.length; i++) {
                String name = parts[i].substring(0, parts[i].indexOf('"'));
                if (!(name.equals("Languages & layouts") || name.equals("Typing") || name.equals("Look"))) continue;
                Matcher m = rx.matcher(parts[i]);
                while (m.find()) { found++; keysFound.add(m.group(1)); eq("entry for " + m.group(1), SectionPlan.listed(m.group(1)), true); }
            }
            eq("found rows (sanity)", found >= 50, true);
            for (String k : SectionPlan.advancedKeys()) eq("table key exists as a row: " + k, keysFound.contains(k), true);
            for (String k : SectionPlan.basicKeys()) eq("table key exists as a row: " + k, keysFound.contains(k), true);
        }
        System.out.println("SectionPlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
