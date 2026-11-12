import com.mgb.keyboard.typing.PanelSearchData;
import com.mgb.keyboard.typing.PanelTiles;
import java.util.*;

/** v0.16.11 (X11 part 2b): static list behind the in-keyboard settings search. */
public class PanelSearchDataTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }

    static boolean has(List<PanelSearchData.Item> l, String route) { for (PanelSearchData.Item i : l) if (i.route.equals(route)) return true; return false; }
    static boolean hasTitle(List<PanelSearchData.Item> l, String s) { for (PanelSearchData.Item i : l) if (i.title.contains(s)) return true; return false; }

    public static void main(String[] a) {
        List<PanelSearchData.Item> all = PanelSearchData.all();
        t("at least 40 entries", all.size() >= 40 && all.size() == PanelSearchData.count());
        boolean empty = false, badRoute = false, badGroup = false;
        Set<String> routes = new HashSet<>();
        for (PanelSearchData.Item i : all) {
            if (i.title.trim().isEmpty() || i.group.trim().isEmpty() || i.route.trim().isEmpty()) empty = true;
            if (!PanelSearchData.isKnownRoute(i.route)) badRoute = true;
            routes.add(i.route);
        }
        t("no empty title / group / route", !empty);
        t("every route is a known panel", !badRoute);
        for (PanelTiles.Tile x : PanelTiles.tiles()) t("tile route has a search entry: " + x.route, routes.contains(x.route));
        t("more has entries", routes.contains("more"));
        t("settings has an entry", routes.contains("settings"));
        t("all() is a fresh list", PanelSearchData.all() != PanelSearchData.all());
        t("isKnownRoute size", PanelSearchData.isKnownRoute("size"));
        t("isKnownRoute more", PanelSearchData.isKnownRoute("more"));
        t("isKnownRoute unknown", !PanelSearchData.isKnownRoute("nope"));
        t("isKnownRoute null / empty", !PanelSearchData.isKnownRoute(null) && !PanelSearchData.isKnownRoute(""));

        List<PanelSearchData.Item> h = PanelSearchData.search("height");
        t("'height' finds Size", has(h, "size"));
        t("'height' first hit is Size", !h.isEmpty() && h.get(0).route.equals("size") && h.get(0).group.equals("Size"));
        t("'height' no more than SEARCH_MAX", h.size() <= PanelTiles.SEARCH_MAX);
        t("'toolbar' finds Toolbar", has(PanelSearchData.search("toolbar"), "toolbar"));
        t("'toolbar' finds quick toolbar", has(PanelSearchData.search("toolbar"), "tbquick"));
        t("mixed case 'HeIgHt'", has(PanelSearchData.search("HeIgHt"), "size"));
        t("Bangla word finds Size", has(PanelSearchData.search("উচ্চতা"), "size"));
        t("Bangla 'বাংলা' finds key map", has(PanelSearchData.search("বাংলা"), "keymap"));
        t("blank = none", PanelSearchData.search("   ").isEmpty());
        t("null = none", PanelSearchData.search(null).isEmpty());
        t("empty = none", PanelSearchData.search("").isEmpty());
        t("no match = none", PanelSearchData.search("zzzzqq").isEmpty());
        t("max 8 on a wide query", PanelSearchData.search("e").size() == PanelTiles.SEARCH_MAX);
        t("two words AND 'key height'", hasTitle(PanelSearchData.search("key height"), "Key height"));
        t("two words that never meet = none", PanelSearchData.search("vault height").isEmpty());
        t("'vibrate' -> typing", has(PanelSearchData.search("vibrate"), "typing"));
        t("'backspace' -> more", has(PanelSearchData.search("backspace"), "more"));
        t("'pc' -> more", has(PanelSearchData.search("pc"), "more"));
        t("'theme' finds themes + studio", has(PanelSearchData.search("theme"), "themes") && has(PanelSearchData.search("theme"), "tstudio"));
        t("'backup' -> profiles", has(PanelSearchData.search("backup"), "profiles"));
        t("'snippets' -> vault", has(PanelSearchData.search("snippets"), "vault"));
        t("'emoji' -> emoji", has(PanelSearchData.search("emoji"), "emoji"));
        t("'cursor' -> pad", has(PanelSearchData.search("cursor"), "pad"));
        t("every hit route is known", allKnown(PanelSearchData.search("key")));
        t("search twice = same result", PanelSearchData.search("height").size() == PanelSearchData.search("height").size());
        System.out.println("PanelSearchDataTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }

    static boolean allKnown(List<PanelSearchData.Item> l) { for (PanelSearchData.Item i : l) if (!PanelSearchData.isKnownRoute(i.route)) return false; return true; }
}
