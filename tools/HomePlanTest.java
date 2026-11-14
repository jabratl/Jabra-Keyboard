import com.mgb.keyboard.typing.HomePlan;
import com.mgb.keyboard.typing.PagePlan;
import com.mgb.keyboard.typing.SettingsIndex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/{EdgePlan,SizePct,PagePlan,SettingsIndex,HomePlan}.java tools/HomePlanTest.java && java -cp out HomePlanTest */
public class HomePlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (java.util.Objects.equals(a, b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    static SettingsIndex.Entry e(String key, String title, String help, String group) { return new SettingsIndex.Entry(key, title, help, group); }
    public static void main(String[] x) {
        eq("default ui is the new screen (v0.14.1)", HomePlan.DEFAULT_APP_UI, 1);
        // panes
        eq("360 one pane", HomePlan.twoPane(360), false);
        eq("599 one pane", HomePlan.twoPane(599), false);
        eq("600 two panes", HomePlan.twoPane(600), true);
        eq("1280 two panes", HomePlan.twoPane(1280), true);
        eq("left 600", HomePlan.leftPaneDp(600), 300);
        eq("left 800", HomePlan.leftPaneDp(800), 304);
        eq("left 1280 capped", HomePlan.leftPaneDp(1280), 380);
        eq("left 640 keeps right 300", HomePlan.leftPaneDp(640), 300);
        eq("left tiny never negative", HomePlan.leftPaneDp(100), 0);
        for (int w = 600; w <= 2000; w += 7) { int l = HomePlan.leftPaneDp(w); eq("right >= 300 at " + w, w - l >= 300, true); eq("left in range at " + w, l >= 300 && l <= 380, true); }
        eq("pv one pane tall", HomePlan.previewMode(false, 800), HomePlan.PV_PIN);
        eq("pv one pane short", HomePlan.previewMode(false, 300), HomePlan.PV_PIN);
        eq("pv two panes tall", HomePlan.previewMode(true, 800), HomePlan.PV_PIN);
        eq("pv two panes landscape phone", HomePlan.previewMode(true, 360), HomePlan.PV_INLINE);
        eq("pv 480 pinned", HomePlan.previewMode(true, 480), HomePlan.PV_PIN);
        eq("first page empty", HomePlan.firstPage(""), "layout");
        eq("first page junk", HomePlan.firstPage("zzz"), "layout");
        eq("first page null", HomePlan.firstPage(null), "layout");
        eq("first page keeps", HomePlan.firstPage("keys"), "keys");

        // search
        List<SettingsIndex.Entry> all = new ArrayList<>();
        all.add(e("haptic", "Vibrate", "Short vibration on every key press.", "Keys & gestures"));
        all.add(e("fb_down", "Feedback at touch-down", "Vibration the moment your finger touches", "Keys & gestures"));
        all.add(e("kh_pct_p", "Key height (portrait)", "Key height in portrait.", "Layout & size"));
        all.add(e("key_gap", "Key gap", "Space between keys in dp.", "Layout & size"));
        all.add(e("theme", "Theme", "Colour theme of the keyboard", "Look"));
        all.add(e("bn_layout", "Bangla layout", "Which Bangla keyboard", "Languages & layouts"));
        all.add(e("autocap", "Auto caps", "Starts a sentence with a capital letter.", "Typing"));
        all.add(e("clip_on", "Clip history", "Saves copied text", "Clipboard & tools"));
        all.add(e("weird", "Weird", "x", "Some New Group"));
        eq("empty query", HomePlan.searchByPage(all, "").size(), 0);
        eq("blank query", HomePlan.searchByPage(all, "   ").size(), 0);
        eq("null query", HomePlan.searchByPage(all, null).size(), 0);
        eq("null list", HomePlan.searchByPage(null, "key").size(), 0);
        List<HomePlan.Group> g = HomePlan.searchByPage(all, "vibrat");
        eq("vibrat pages", g.size(), 1);
        eq("vibrat page id", g.get(0).page, "keys");
        eq("vibrat title first", g.get(0).items.get(0).key, "haptic");
        eq("vibrat count", HomePlan.count(g), 2);
        g = HomePlan.searchByPage(all, "key");
        // pages in tile order: typing(Bangla layout has 'keyboard'), look(theme help 'keyboard'), layout, keys
        StringBuilder order = new StringBuilder();
        for (HomePlan.Group gg : g) order.append(gg.page).append(',');
        eq("page order follows tiles", order.toString(), "typing,look,layout,keys,");
        List<String> lay = new ArrayList<>();
        for (HomePlan.Group gg : g) if (gg.page.equals("layout")) for (SettingsIndex.Entry en : gg.items) lay.add(en.key);
        eq("title start before title contains", lay, Arrays.asList("kh_pct_p", "key_gap"));
        g = HomePlan.searchByPage(all, "gap");
        eq("gap one hit", HomePlan.count(g), 1);
        g = HomePlan.searchByPage(all, "history clip");
        eq("two words any order", HomePlan.count(g), 1);
        eq("two words page", g.get(0).page, "clip");
        g = HomePlan.searchByPage(all, "zzzzz");
        eq("no hit", HomePlan.count(g), 0);
        g = HomePlan.searchByPage(all, "weird");
        eq("unknown group goes to data page", g.get(0).page, "data");
        g = HomePlan.searchByPage(all, "LAYOUT");
        eq("case insensitive", HomePlan.count(g) >= 2, true);
        // cap
        List<SettingsIndex.Entry> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) many.add(e("k" + i, "Row " + i, "", "Look"));
        eq("cap 40", HomePlan.count(HomePlan.searchByPage(many, "row")), HomePlan.MAX_RESULTS);
        eq("pageOf null", HomePlan.pageOf(null), "data");
        eq("pageOf layout", HomePlan.pageOf(all.get(2)), "layout");
        eq("line none", HomePlan.resultLine(0, " abc "), "No setting found for \u201Cabc\u201D. Try another word.");
        eq("line one", HomePlan.resultLine(1, "a"), "1 setting");
        eq("line many", HomePlan.resultLine(5, "a"), "5 settings");
        eq("with value", HomePlan.withValue("Vibrate", "ON"), "Vibrate, ON");
        eq("with empty value", HomePlan.withValue("Vibrate", ""), "Vibrate");
        eq("with null title", HomePlan.withValue(null, null), "");

        // lists
        List<String> exist = Arrays.asList("haptic", "theme", "key_gap");
        eq("recent filtered", HomePlan.recentKeys("key_gap,gone,theme", exist), Arrays.asList("key_gap", "theme"));
        eq("recent empty", HomePlan.recentKeys("", exist).size(), 0);
        eq("recent null csv", HomePlan.recentKeys(null, exist).size(), 0);
        eq("recent null existing", HomePlan.recentKeys("a", null).size(), 0);
        eq("changed keys", HomePlan.changedKeys(Arrays.asList("a", "b", "c"), new boolean[] { true, false, true }), Arrays.asList("a", "c"));
        eq("changed short flags", HomePlan.changedKeys(Arrays.asList("a", "b", "c"), new boolean[] { true }), Arrays.asList("a"));
        eq("changed short keys", HomePlan.changedKeys(Arrays.asList("a"), new boolean[] { true, true }), Arrays.asList("a"));
        eq("changed null", HomePlan.changedKeys(null, new boolean[0]).size(), 0);
        eq("changed title closed", HomePlan.changedTitle(3, false), "\u25B6  Changed from default (3)");
        eq("changed title open", HomePlan.changedTitle(0, true), "\u25BC  Changed from default (0)");
        eq("changed title negative", HomePlan.changedTitle(-2, true), "\u25BC  Changed from default (0)");
        eq("revealY", HomePlan.revealY(500, 72), 428);
        eq("revealY top", HomePlan.revealY(10, 72), 0);
        eq("revealY negative pad", HomePlan.revealY(100, -5), 100);
        // v0.17.79 (U4): status card priority
        eq("st none", HomePlan.status(false, "", ""), HomePlan.ST_NONE);
        eq("st none null", HomePlan.status(false, null, null), HomePlan.ST_NONE);
        eq("st setup wins", HomePlan.status(true, "warn", "bk"), HomePlan.ST_SETUP);
        eq("st guard over backup", HomePlan.status(false, "warn", "bk"), HomePlan.ST_GUARD);
        eq("st backup", HomePlan.status(false, "", "bk"), HomePlan.ST_BACKUP);
        eq("st backup null guard", HomePlan.status(false, null, "bk"), HomePlan.ST_BACKUP);
        eq("title none", HomePlan.statusTitle(HomePlan.ST_NONE), "");
        eq("title guard", HomePlan.statusTitle(HomePlan.ST_GUARD), "A keyboard panel is switched off");
        eq("action guard", HomePlan.statusAction(HomePlan.ST_GUARD), "Switch it on again");
        eq("action backup", HomePlan.statusAction(HomePlan.ST_BACKUP), "Open Data & Backup");
        eq("action setup none", HomePlan.statusAction(HomePlan.ST_SETUP), "");
        eq("action none", HomePlan.statusAction(HomePlan.ST_NONE), "");
        eq("text guard", HomePlan.statusText(HomePlan.ST_GUARD, "g", "b"), "g");
        eq("text backup", HomePlan.statusText(HomePlan.ST_BACKUP, "g", "b"), "b");
        eq("text none", HomePlan.statusText(HomePlan.ST_NONE, "g", "b"), "");
        eq("text null", HomePlan.statusText(HomePlan.ST_GUARD, null, null), "");
        // changed badge per page
        int[] c = PagePlan.changedByPage(Arrays.asList("Look", "Look", "Typing", "Weird group", "Keys & gestures"), new boolean[] { true, true, false, true, false });
        eq("by page look", c[PagePlan.index(PagePlan.LOOK)], 2);
        eq("by page typing", c[PagePlan.index(PagePlan.TYPING)], 0);
        eq("by page unknown -> data", c[PagePlan.index(PagePlan.DATA)], 1);
        eq("by page keys", c[PagePlan.index(PagePlan.KEYS)], 0);
        eq("by page len", c.length, PagePlan.ORDER.length);
        eq("by page null", PagePlan.changedByPage(null, new boolean[1]).length, PagePlan.ORDER.length);
        eq("by page short flags", PagePlan.changedByPage(Arrays.asList("Look", "Look"), new boolean[] { true })[PagePlan.index(PagePlan.LOOK)], 1);
        eq("badge zero", PagePlan.badge(0), "");
        eq("badge neg", PagePlan.badge(-3), "");
        eq("badge n", PagePlan.badge(3), "3 changed");
        eq("desc none", PagePlan.tileDescription(PagePlan.KEYS, "Vibrate on", 0), "Keys & Gestures. Vibrate on");
        eq("desc one", PagePlan.tileDescription(PagePlan.KEYS, "Vibrate on", 1), "Keys & Gestures. Vibrate on, 1 setting changed");
        eq("desc many", PagePlan.tileDescription(PagePlan.KEYS, null, 4), "Keys & Gestures. , 4 settings changed");
        // ---- v0.17.83 (U7) ----
        eq("U7 active empty", HomePlan.searchActive("", false), false);
        eq("U7 active blank", HomePlan.searchActive("  ", false), false);
        eq("U7 active null", HomePlan.searchActive(null, false), false);
        eq("U7 active text", HomePlan.searchActive("a", false), true);
        eq("U7 active chip only", HomePlan.searchActive("", true), true);
        eq("U7 back page -> home", HomePlan.backStep(true, true), HomePlan.BACK_HOME);
        eq("U7 back page no search -> home", HomePlan.backStep(false, true), HomePlan.BACK_HOME);
        eq("U7 back home + search -> clear", HomePlan.backStep(true, false), HomePlan.BACK_CLEAR_SEARCH);
        eq("U7 back home -> system", HomePlan.backStep(false, false), HomePlan.BACK_SYSTEM);
        List<SettingsIndex.Entry> u7 = new ArrayList<>();
        u7.add(e("haptic", "Vibrate", "Short vibration.", "Keys & gestures"));
        u7.add(e("kh_pct_p", "Key height (portrait)", "Key height.", "Layout & size"));
        u7.add(e("theme", "Theme", "Colours.", "Look"));
        u7.add(e("sound", "Sound", "Click sound.", "Keys & gestures"));
        java.util.Set<String> ch = new java.util.HashSet<>(Arrays.asList("haptic", "theme", "gone_key"));
        eq("U7 filter off = plain search", HomePlan.count(HomePlan.searchFiltered(u7, "sound", ch, false)), 1);
        eq("U7 filter on, empty query = all changed rows", HomePlan.count(HomePlan.searchFiltered(u7, "", ch, true)), 2);
        eq("U7 filter on, null changed set", HomePlan.count(HomePlan.searchFiltered(u7, "", null, true)), 0);
        eq("U7 filter on + text keeps match", HomePlan.count(HomePlan.searchFiltered(u7, "vibrat", ch, true)), 1);
        eq("U7 filter on + text drops unchanged", HomePlan.count(HomePlan.searchFiltered(u7, "sound", ch, true)), 0);
        eq("U7 filter on null list", HomePlan.searchFiltered(null, "", ch, true).size(), 0);
        List<HomePlan.Group> u7g = HomePlan.searchFiltered(u7, "", ch, true);
        eq("U7 groups follow page order", Arrays.asList(PagePlan.ORDER).indexOf(u7g.get(0).page) <= Arrays.asList(PagePlan.ORDER).indexOf(u7g.get(u7g.size() - 1).page), true);
        eq("U7 chip label", HomePlan.chipLabel(3), "Changed (3)");
        eq("U7 chip label negative", HomePlan.chipLabel(-2), "Changed (0)");
        eq("U7 line n", HomePlan.resultLine(2, "x", false), "2 settings");
        eq("U7 line changed 1", HomePlan.resultLine(1, "", true), "1 changed setting");
        eq("U7 line changed n", HomePlan.resultLine(5, "", true), "5 changed settings");
        eq("U7 empty plain", HomePlan.resultLine(0, "zzz", false), "No setting found for \u201Czzz\u201D.");
        eq("U7 empty changed no text", HomePlan.emptyTitle(null, true), "Nothing changed from default yet.");
        eq("U7 empty changed text", HomePlan.emptyTitle("zzz", true), "No changed setting matches \u201Czzz\u201D.");
        eq("U7 help plain", HomePlan.emptyHelp("zzz", false), "Try one of these words:");
        eq("U7 help changed no text", HomePlan.emptyHelp("", true), "Settings you change will be listed here.");
        eq("U7 help changed text", HomePlan.emptyHelp("zzz", true).startsWith("Turn off"), true);
        eq("U7 suggestions count", HomePlan.SUGGEST.length >= 4, true);
        for (String w : HomePlan.SUGGEST) eq("U7 suggestion lower-case single word " + w, w.equals(w.toLowerCase(java.util.Locale.ROOT)) && !w.trim().isEmpty(), true);
        System.out.println("HomePlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
