import com.mgb.keyboard.typing.HelpPlan;
import com.mgb.keyboard.typing.SettingsIndex;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** v0.17.78 (U3) HelpPlan rules + every help text of SettingsIndex (computer only; run from the repo root). */
public class HelpPlanTest {
    static int fail = 0, total = 0;
    static void t(String m, boolean ok) { total++; if (!ok) { fail++; System.out.println("FAIL " + m); } }

    public static void main(String[] a) throws Exception {
        t("sanitize", HelpPlan.sanitize(0) == 0 && HelpPlan.sanitize(1) == 1 && HelpPlan.sanitize(2) == 2 && HelpPlan.sanitize(7) == HelpPlan.SMART && HelpPlan.sanitize(-1) == HelpPlan.SMART);
        t("icon only in smart", HelpPlan.showIcon(0) && !HelpPlan.showIcon(1) && !HelpPlan.showIcon(2));
        t("help exists unless off", HelpPlan.helpExists(0) && HelpPlan.helpExists(1) && !HelpPlan.helpExists(2));
        t("always starts open", HelpPlan.startsOpen(1) && !HelpPlan.startsOpen(0) && !HelpPlan.startsOpen(2));
        t("flagged known", HelpPlan.isFlagged("space_lang") && !HelpPlan.isFlagged("numrow") && !HelpPlan.isFlagged(null));
        t("smart: flagged + unseen opens", HelpPlan.autoOpen(0, "space_lang", "", false));
        t("smart: flagged + seen stays closed", !HelpPlan.autoOpen(0, "space_lang", "a,space_lang", false));
        t("smart: not flagged stays closed", !HelpPlan.autoOpen(0, "numrow", "", false));
        t("smart: search hit opens", HelpPlan.autoOpen(0, "numrow", "numrow", true));
        t("always / off never auto-open", !HelpPlan.autoOpen(1, "space_lang", "", true) && !HelpPlan.autoOpen(2, "space_lang", "", true));
        t("seen add + no duplicate", HelpPlan.addSeen("a", "b").equals("a,b") && HelpPlan.addSeen("a,b", "a").equals("a,b"));
        t("seen bad key ignored", HelpPlan.addSeen("a", null).equals("a") && HelpPlan.addSeen("a", " ").equals("a") && HelpPlan.addSeen("a", "x,y").equals("a"));
        t("seen null raw", HelpPlan.addSeen(null, "k").equals("k") && !HelpPlan.isSeen(null, "k") && HelpPlan.parseSeen(null).isEmpty());
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 305; i++) big.append(i == 0 ? "" : ",").append("k").append(i);
        String capped = HelpPlan.addSeen(big.toString(), "new");
        t("seen cap 300, newest kept, oldest dropped", HelpPlan.parseSeen(capped).size() == HelpPlan.SEEN_MAX && HelpPlan.isSeen(capped, "new") && !HelpPlan.isSeen(capped, "k0"));
        t("shortOf: short text unchanged", HelpPlan.shortOf("x", "Short help.").equals("Short help."));
        t("shortOf: null", HelpPlan.shortOf("x", null).equals(""));
        String longText = "word word word word word word word word word word word word word word word word word word word word word";
        String cut = HelpPlan.shortOf("unknown_key", longText);
        t("shortOf: auto cut <= 90 + dots", cut.length() <= HelpPlan.LIMIT && cut.endsWith("..."));

        // every help text of SettingsIndex: short form <= 90, and a long text has a HANDWRITTEN short form (no silent cuts)
        String src = new String(Files.readAllBytes(new File("app/src/main/java/com/mgb/keyboard/typing/SettingsIndex.java").toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("case \"([a-z_0-9]+)\": return").matcher(src);
        int keys = 0, longN = 0;
        while (m.find()) {
            String k = m.group(1);
            String h = SettingsIndex.help(k);
            if (h == null) continue;
            keys++;
            t("short form of " + k + " <= 90", HelpPlan.shortOf(k, h).length() <= HelpPlan.LIMIT);
            if (h.length() > HelpPlan.LIMIT) { longN++; t("long help of " + k + " has a handwritten short form", HelpPlan.handwritten(k) != null); }
        }
        t("found the help keys", keys > 100);
        for (String f : HelpPlan.FLAGGED) t("flagged key exists in SettingsIndex: " + f, !SettingsIndex.help(f).isEmpty());
        // stale handwritten forms: only for texts that really are longer than 90
        for (String k : new String[]{"lang_en_on","lang_order","bn_design","bn_hints","bn_numpage","tools_keep","pc_chips","pc_chips_list","key_press_anim","lp_punct","space_lang","fb_down","haptic_native","row_gap","bot_style","pb_home_gap","qa_on","clip_new_look"}) {
            String h = HelpPlan.handwritten(k);
            t("handwritten " + k + " <= 90 and shorter than the full text", h != null && h.length() <= HelpPlan.LIMIT && h.length() < SettingsIndex.help(k).length());
        }
        System.out.println("HelpPlanTest: " + (total - fail) + "/" + total + (fail == 0 ? " OK" : " FAILED") + " (" + keys + " help texts, " + longN + " long)");
        if (fail > 0) System.exit(1);
    }
}
