import com.mgb.keyboard.typing.SuggestPlan;
import java.util.Arrays;

public class SuggestPlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    public static void main(String[] a) {
        eq("word simple", SuggestPlan.currentWord("hello wor"), "wor");
        eq("word only", SuggestPlan.currentWord("hel"), "hel");
        eq("word after space = none", SuggestPlan.currentWord("hello "), "");
        eq("word after comma = none", SuggestPlan.currentWord("hello,"), "");
        eq("word after newline", SuggestPlan.currentWord("hi\nthe"), "the");
        eq("empty", SuggestPlan.currentWord(""), "");
        eq("null", SuggestPlan.currentWord(null), "");
        eq("apostrophe inside", SuggestPlan.currentWord("say don'"), "don'");
        eq("apostrophe inside 2", SuggestPlan.currentWord("say don't"), "don't");
        eq("curly apostrophe", SuggestPlan.currentWord("say don’t"), "don’t");
        eq("leading quote cut", SuggestPlan.currentWord("say 'hel"), "hel");
        eq("only quote", SuggestPlan.currentWord("say '"), "");
        eq("digits glued = none", SuggestPlan.currentWord("room a1"), "");
        eq("digit end = none", SuggestPlan.currentWord("call 99"), "");
        eq("word then digit = none", SuggestPlan.currentWord("abc9"), "");
        eq("capital", SuggestPlan.currentWord("Hel"), "Hel");
        eq("hyphen splits", SuggestPlan.currentWord("well-kno"), "kno");
        eq("dot splits", SuggestPlan.currentWord("end.The"), "The");
        eq("paren splits", SuggestPlan.currentWord("(hel"), "hel");
        // Bangla (ami with signs, hasanta)
        eq("bangla word", SuggestPlan.currentWord("আম আমি"), "আমি");
        eq("bangla sign kept", SuggestPlan.currentWord("কা"), "কা");
        eq("bangla hasanta kept", SuggestPlan.currentWord("ক্"), "ক্");
        eq("bangla after danda", SuggestPlan.currentWord("আমি। ক"), "ক");
        eq("bangla danda only = none", SuggestPlan.currentWord("আমি।"), "");
        eq("bangla digit = none", SuggestPlan.currentWord("১২"), "");
        // show / active / lang
        eq("show on", SuggestPlan.show(true, false), true);
        eq("show off pref", SuggestPlan.show(false, false), false);
        eq("hidden when folded", SuggestPlan.show(true, true), false);
        eq("both off", SuggestPlan.show(false, true), false);
        eq("active normal", SuggestPlan.active(false), true);
        eq("inactive latin-only / password", SuggestPlan.active(true), false);
        eq("lang en", SuggestPlan.lang(false, false), "en");
        eq("lang bn", SuggestPlan.lang(true, false), "bn");
        eq("lang bn but latin-only field", SuggestPlan.lang(true, true), "en");
        eq("lang en latin-only", SuggestPlan.lang(false, true), "en");
        // slots
        eq("slots 3 full", SuggestPlan.slots(Arrays.asList("a", "b", "c", "d")).toString(), "[a, b, c]");
        eq("slots padded", SuggestPlan.slots(Arrays.asList("a")).toString(), "[a, , ]");
        eq("slots null", SuggestPlan.slots(null).size(), 3);
        eq("slots empty", SuggestPlan.slots(Arrays.<String>asList()).toString(), "[, , ]");
        eq("slots null item", SuggestPlan.slots(Arrays.asList("a", null, "c")).toString(), "[a, , c]");
        // lookup rule
        eq("wants 1 char", SuggestPlan.wantsLookup("h"), true);
        eq("wants empty", SuggestPlan.wantsLookup(""), false);
        eq("wants null", SuggestPlan.wantsLookup(null), false);
        // toType
        eq("type lower", SuggestPlan.toType("he", "hello"), "hello ");
        eq("type Title", SuggestPlan.toType("He", "hello"), "Hello ");
        eq("type UPPER", SuggestPlan.toType("HE", "hello"), "HELLO ");
        eq("type blank", SuggestPlan.toType("he", ""), "");
        eq("type null", SuggestPlan.toType("he", null), "");
        eq("type bangla", SuggestPlan.toType("আম", "আমি"), "আমি ");
        // layout / grid
        eq("layout normal", SuggestPlan.layout("skin", Arrays.asList("skim", "skins", "skink")).toString(), "[skim, skin, skins]");
        eq("layout one sugg", SuggestPlan.layout("skin", Arrays.asList("skim")).toString(), "[skim, skin, ]");
        eq("layout no sugg keeps typed", SuggestPlan.layout("xq", Arrays.<String>asList()).toString(), "[, xq, ]");
        eq("layout null sugg", SuggestPlan.layout("xq", null).toString(), "[, xq, ]");
        eq("layout nothing typed", SuggestPlan.layout("", Arrays.asList("a", "b")).toString(), "[, , ]");
        eq("layout null typed", SuggestPlan.layout(null, Arrays.asList("a")).toString(), "[, , ]");
        java.util.List<String> many = new java.util.ArrayList<String>(); for (int i = 0; i < 30; i++) many.add("w" + i);
        eq("grid max 18", SuggestPlan.grid(many).size(), 18);
        eq("grid null", SuggestPlan.grid(null).size(), 0);
        eq("grid skips blanks", SuggestPlan.grid(Arrays.asList("a", "", null, "b")).toString(), "[a, b]");
        eq("rows 0", SuggestPlan.gridRows(0), 0);
        eq("rows 1", SuggestPlan.gridRows(1), 1);
        eq("rows 3", SuggestPlan.gridRows(3), 1);
        eq("rows 4", SuggestPlan.gridRows(4), 2);
        eq("rows 18", SuggestPlan.gridRows(18), 6);
        eq("cell first", SuggestPlan.gridIndex(5, 5, 0, 0, 100, 40, 18), 0);
        eq("cell col 2", SuggestPlan.gridIndex(250, 10, 0, 0, 100, 40, 18), 2);
        eq("cell row 1 col 1", SuggestPlan.gridIndex(150, 50, 0, 0, 100, 40, 18), 4);
        eq("cell last", SuggestPlan.gridIndex(299, 239, 0, 0, 100, 40, 18), 17);
        eq("cell empty (n=4, row1 col2)", SuggestPlan.gridIndex(250, 50, 0, 0, 100, 40, 4), -1);
        eq("cell left of grid", SuggestPlan.gridIndex(-1, 10, 0, 0, 100, 40, 18), -1);
        eq("cell above grid", SuggestPlan.gridIndex(10, -1, 0, 0, 100, 40, 18), -1);
        eq("cell right of grid", SuggestPlan.gridIndex(300, 10, 0, 0, 100, 40, 18), -1);
        eq("cell below grid", SuggestPlan.gridIndex(10, 240, 0, 0, 100, 40, 18), -1);
        eq("cell offset origin", SuggestPlan.gridIndex(120, 130, 100, 100, 50, 20, 9), 3);
        eq("cell n0", SuggestPlan.gridIndex(5, 5, 0, 0, 100, 40, 0), -1);
        eq("cell zero size", SuggestPlan.gridIndex(5, 5, 0, 0, 0, 40, 9), -1);
        eq("long ms", SuggestPlan.LONG_MS, 350L);
        eq("constants", SuggestPlan.SLOTS + "/" + SuggestPlan.LOOKBACK, "3/40");
        System.out.println("SuggestPlanTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
