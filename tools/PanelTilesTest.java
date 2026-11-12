import com.mgb.keyboard.typing.PanelTiles;
import com.mgb.keyboard.typing.SettingsIndex;
import java.util.*;

/** v0.16.11 (X11): tile list + in-keyboard search. */
public class PanelTilesTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }

    static List<SettingsIndex.Entry> idx() {
        List<SettingsIndex.Entry> l = new ArrayList<>();
        l.add(new SettingsIndex.Entry("kb_height", "Keyboard height", "Make the keys taller or shorter", "Size"));
        l.add(new SettingsIndex.Entry("haptic", "Vibrate", "Short buzz on each key", "Typing"));
        l.add(new SettingsIndex.Entry("sound", "Sound", "Click sound on each key", "Typing"));
        l.add(new SettingsIndex.Entry("theme", "Theme", "Colours of the keyboard", "Look"));
        l.add(new SettingsIndex.Entry("clip_on", "Clip history", "Remember copied text", "Clipboard"));
        for (int i = 0; i < 12; i++) l.add(new SettingsIndex.Entry("key_x" + i, "Key option " + i, "key tweak", "Typing"));
        return l;
    }

    public static void main(String[] a) {
        List<PanelTiles.Tile> all = PanelTiles.tiles();
        t("has tiles", all.size() >= 12);
        t("8 group names", PanelTiles.GROUPS.length == 8);
        Set<String> ids = new HashSet<>(), routes = new HashSet<>();
        boolean dup = false, empty = false;
        for (PanelTiles.Tile x : all) { if (!ids.add(x.id)) dup = true; routes.add(x.route); if (x.id.isEmpty() || x.label.isEmpty() || x.route.isEmpty()) empty = true; }
        t("no duplicate ids", !dup);
        t("no empty fields", !empty);
        t("tiles() same count as ALL groups sum", sumOfGroups() == all.size());
        t("every tile group is a known group", allKnown(all));
        t("order follows GROUPS", orderOk(all));
        String[] must = { "more", "keymap", "typing", "size", "toolbar", "tbquick", "themes", "vault", "tt", "profiles", "clipboard", "format", "emoji", "pad", "tstudio" };
        // every old panel reachable except 'more' itself (tiles replace it)
        for (String r : must) if (!r.equals("more")) t("route reachable: " + r, routes.contains(r));
        t("byRoute keymap", PanelTiles.byRoute("keymap") != null && PanelTiles.byRoute("keymap").group.equals("Languages"));
        t("byRoute unknown = null", PanelTiles.byRoute("nope") == null);
        t("byRoute null = null", PanelTiles.byRoute(null) == null);
        t("tilesOf unknown = empty", PanelTiles.tilesOf("Zzz").isEmpty());
        t("tilesOf null = empty", PanelTiles.tilesOf(null).isEmpty());
        t("groups() non empty + subset", !PanelTiles.groups().isEmpty() && Arrays.asList(PanelTiles.GROUPS).containsAll(PanelTiles.groups()));
        t("Languages is first group", PanelTiles.groups().get(0).equals("Languages"));
        t("Data is last group", PanelTiles.groups().get(PanelTiles.groups().size() - 1).equals("Data"));
        t("tiles() returns a fresh list", PanelTiles.tiles() != PanelTiles.tiles());
        t("Tile null args safe", new PanelTiles.Tile(null, null, null, null).route.equals(""));

        List<SettingsIndex.Entry> x = idx();
        t("search 'height' finds Keyboard height", PanelTiles.search(x, "height").size() == 1 && PanelTiles.search(x, "height").get(0).key.equals("kb_height"));
        t("search 'vibrate'", PanelTiles.search(x, "vibrate").size() == 1);
        t("search 'sound'", PanelTiles.search(x, "sound").get(0).key.equals("sound"));
        t("search 'theme'", PanelTiles.search(x, "theme").size() >= 1);
        t("search 'clip'", PanelTiles.search(x, "clip").get(0).key.equals("clip_on"));
        t("search by help text 'taller'", PanelTiles.search(x, "taller").size() == 1);
        t("search is case-insensitive", PanelTiles.search(x, "HEIGHT").size() == 1);
        t("search blank = empty", PanelTiles.search(x, "   ").isEmpty());
        t("search null query = empty", PanelTiles.search(x, null).isEmpty());
        t("search null list = empty", PanelTiles.search(null, "height").isEmpty());
        t("search no match = empty", PanelTiles.search(x, "zzzzqq").isEmpty());
        t("search capped at SEARCH_MAX", PanelTiles.search(x, "key").size() == PanelTiles.SEARCH_MAX);
        t("search two words (AND)", PanelTiles.search(x, "keyboard height").size() == 1);
        t("isSearching", PanelTiles.isSearching("a") && !PanelTiles.isSearching("  ") && !PanelTiles.isSearching(null) && !PanelTiles.isSearching(""));
        System.out.println("PanelTilesTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }

    static int sumOfGroups() { int n = 0; for (String g : PanelTiles.GROUPS) n += PanelTiles.tilesOf(g).size(); return n; }
    static boolean allKnown(List<PanelTiles.Tile> l) { List<String> g = Arrays.asList(PanelTiles.GROUPS); for (PanelTiles.Tile t : l) if (!g.contains(t.group)) return false; return true; }
    static boolean orderOk(List<PanelTiles.Tile> l) { List<String> g = Arrays.asList(PanelTiles.GROUPS); int last = -1; for (PanelTiles.Tile t : l) { int i = g.indexOf(t.group); if (i < last) return false; last = i; } return true; }
}
