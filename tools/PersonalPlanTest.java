import com.mgb.keyboard.typing.PagePlan;
import com.mgb.keyboard.typing.PersonalPlan;
import java.util.Arrays;
import java.util.List;

/** Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/{PagePlan,PersonalPlan}.java tools/PersonalPlanTest.java && java -cp out PersonalPlanTest */
public class PersonalPlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (java.util.Objects.equals(a, b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    static List<String> L(String... s) { return Arrays.asList(s); }
    public static void main(String[] x) {
        List<String> valid = L("haptic", "sound", "kh_pct_p", "key_radius", "row_gap", "numrow", "clip_on", "edit_bar", "bk_auto", "look", "ac_on", "learn_on");
        // parse / join
        eq("parse null", PersonalPlan.parseList(null), L());
        eq("parse blank", PersonalPlan.parseList("  ,, "), L());
        eq("parse dedupe", PersonalPlan.parseList("a, b,a ,c"), L("a", "b", "c"));
        eq("join", PersonalPlan.join(L("a", "b")), "a,b");
        eq("join empty", PersonalPlan.join(L()), "");
        // favs
        eq("favs unknown dropped", PersonalPlan.favs("haptic,zzz,sound", valid), L("haptic", "sound"));
        eq("favs order kept", PersonalPlan.favs("sound,haptic", valid), L("sound", "haptic"));
        eq("favs null valid", PersonalPlan.favs("haptic", null), L());
        eq("favs max", PersonalPlan.favs("haptic,sound,kh_pct_p,key_radius,row_gap,numrow,clip_on,edit_bar,bk_auto,look", valid).size(), PersonalPlan.MAX_FAVS);
        eq("isFav yes", PersonalPlan.isFav("haptic,sound", "sound"), true);
        eq("isFav no", PersonalPlan.isFav("haptic", "sound"), false);
        eq("isFav null", PersonalPlan.isFav("haptic", null), false);
        // toggle
        eq("pin first", PersonalPlan.toggleFav("", "haptic", valid), "haptic");
        eq("pin second appended", PersonalPlan.toggleFav("haptic", "sound", valid), "haptic,sound");
        eq("unpin", PersonalPlan.toggleFav("haptic,sound", "haptic", valid), "sound");
        eq("unknown key unchanged", PersonalPlan.toggleFav("haptic", "zzz", valid), "haptic");
        eq("null key unchanged", PersonalPlan.toggleFav("haptic", null, valid), "haptic");
        eq("null raw", PersonalPlan.toggleFav(null, "sound", valid), "sound");
        eq("toggle cleans junk", PersonalPlan.toggleFav("zzz,haptic", "sound", valid), "haptic,sound");
        String full = "haptic,sound,kh_pct_p,key_radius,row_gap,numrow,clip_on,edit_bar";
        eq("full refuses new", PersonalPlan.toggleFav(full, "look", valid), full);
        eq("full canPin new", PersonalPlan.canPin(full, "look", valid), false);
        eq("full canPin existing", PersonalPlan.canPin(full, "haptic", valid), true);
        eq("full can unpin", PersonalPlan.toggleFav(full, "haptic", valid), "sound,kh_pct_p,key_radius,row_gap,numrow,clip_on,edit_bar");
        eq("toggle twice = same", PersonalPlan.toggleFav(PersonalPlan.toggleFav("haptic", "sound", valid), "sound", valid), "haptic");
        // tiles order
        eq("default order", PersonalPlan.order(""), L(PagePlan.ORDER));
        eq("null order", PersonalPlan.order(null), L(PagePlan.ORDER));
        eq("custom order + rest", PersonalPlan.order("clip,look"), L("clip", "look", "typing", "layout", "keys", "data"));
        eq("unknown id dropped", PersonalPlan.order("zzz,keys"), L("keys", "typing", "look", "layout", "clip", "data"));
        eq("duplicate dropped", PersonalPlan.order("keys,keys,look"), L("keys", "look", "typing", "layout", "clip", "data"));
        eq("home id not a tile", PersonalPlan.order(",look"), L("look", "typing", "layout", "keys", "clip", "data"));
        eq("order always 6", PersonalPlan.order("a,b,c,keys,data,clip,look,layout,typing").size(), 6);
        // hide
        eq("canHide data", PersonalPlan.canHide("data"), false);
        eq("canHide look", PersonalPlan.canHide("look"), true);
        eq("canHide unknown", PersonalPlan.canHide("zzz"), false);
        eq("canHide home", PersonalPlan.canHide(""), false);
        eq("hidden drops data", PersonalPlan.hidden("look,data,zzz"), L("look"));
        eq("visible default", PersonalPlan.visible("", ""), L(PagePlan.ORDER));
        eq("visible hides", PersonalPlan.visible("", "look,clip"), L("typing", "layout", "keys", "data"));
        eq("visible order + hide", PersonalPlan.visible("clip,look", "look"), L("clip", "typing", "layout", "keys", "data"));
        eq("hide everything keeps data", PersonalPlan.visible("", "typing,look,layout,keys,clip,data"), L("data"));
        eq("visible never empty", PersonalPlan.visible("zzz", "typing,look,layout,keys,clip,data").isEmpty(), false);
        // toggleHide
        eq("hide look", PersonalPlan.toggleHide("", "look"), "look");
        eq("hide two", PersonalPlan.toggleHide("look", "clip"), "look,clip");
        eq("show again", PersonalPlan.toggleHide("look,clip", "look"), "clip");
        eq("hide data refused", PersonalPlan.toggleHide("look", "data"), "look");
        eq("hide unknown refused", PersonalPlan.toggleHide("", "zzz"), "");
        // move
        eq("move down", PersonalPlan.move("", "typing", 1), "look,typing,layout,keys,clip,data");
        eq("move up", PersonalPlan.move("", "layout", -1), "typing,layout,look,keys,clip,data");
        eq("move up at top", PersonalPlan.move("", "typing", -1), "typing,look,layout,keys,clip,data");
        eq("move down at bottom", PersonalPlan.move("", "data", 1), "typing,look,layout,keys,clip,data");
        eq("move unknown", PersonalPlan.move("", "zzz", 1), "typing,look,layout,keys,clip,data");
        eq("move dir 0", PersonalPlan.move("", "look", 0), "typing,look,layout,keys,clip,data");
        eq("move twice", PersonalPlan.move(PersonalPlan.move("", "typing", 1), "typing", 1), "look,layout,typing,keys,clip,data");
        // every move keeps all 6 pages exactly once
        for (String id : PagePlan.ORDER) for (int d = -1; d <= 1; d += 2) {
            List<String> o = PersonalPlan.order(PersonalPlan.move("clip,look", id, d));
            eq("move keeps 6 " + id + d, o.size(), 6);
            eq("move keeps all " + id + d, new java.util.HashSet<>(o).size(), 6);
        }
        // customised
        eq("customised no", PersonalPlan.customised("", ""), false);
        eq("customised junk only", PersonalPlan.customised("zzz", "data"), false);
        eq("customised order", PersonalPlan.customised("look", ""), true);
        eq("customised hide", PersonalPlan.customised("", "look"), true);
        eq("customised same order saved", PersonalPlan.customised("typing,look,layout,keys,clip,data", ""), false);
        // texts
        eq("title", PersonalPlan.favTitle(3), "Favourites (3)");
        eq("pin label", PersonalPlan.pinLabel("Vibrate", false), "Pin Vibrate to Home");
        eq("unpin label", PersonalPlan.pinLabel("Vibrate", true), "Unpin Vibrate from Home");
        eq("full note", PersonalPlan.fullNote().contains("8"), true);
        eq("hint not empty", PersonalPlan.favHint().isEmpty(), false);
        System.out.println("PersonalPlanTest: " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
