import com.mgb.keyboard.typing.AutoCorrect;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AutoCorrectTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static AutoCorrect.Known k(String... w) { final Set<String> s = new HashSet<String>(Arrays.asList(w)); return new AutoCorrect.Known() { public boolean has(String l) { return s.contains(l); } }; }
    public static void main(String[] a) {
        AutoCorrect.Known W = k("the", "hello", "world", "because", "friend", "went", "want", "wait", "from", "form", "tell", "will", "there", "their", "which", "and");
        eq("swap len3", AutoCorrect.fix("teh", W, null), "the");
        eq("swap capital", AutoCorrect.fix("Teh", W, null), "The");
        eq("swap upper", AutoCorrect.fix("TEH", W, null), "");   // inner capitals = acronym
        eq("delete extra letter", AutoCorrect.fix("helllo", W, null), "hello");
        eq("missing letter", AutoCorrect.fix("wrld", W, null), "world");
        eq("insert letter", AutoCorrect.fix("wold", W, null), "world");
        eq("replace letter", AutoCorrect.fix("becausd", W, null), "because");
        eq("swap neighbours", AutoCorrect.fix("freind", W, null), "friend");
        eq("known word untouched", AutoCorrect.fix("hello", W, null), "");
        eq("known word capital untouched", AutoCorrect.fix("Hello", W, null), "");
        eq("learned untouched", AutoCorrect.fix("helllo", W, k("helllo")), "");
        eq("ambiguous none", AutoCorrect.fix("wnt", W, null), "");   // len 3 = swap only
        eq("ambiguous 4", AutoCorrect.fix("want".replace("a", "e").replace("e", "i"), W, null), "");   // wint -> want / went / wait? several
        eq("first letter change not allowed", AutoCorrect.fix("xello", W, null), "");   // hello is 1 replace of first letter
        eq("too short", AutoCorrect.fix("te", W, null), "");
        eq("too long", AutoCorrect.fix("abcdefghijklmnopqrstu", W, null), "");
        eq("digit", AutoCorrect.fix("hell0", W, null), "");
        eq("apostrophe", AutoCorrect.fix("don't", W, null), "");
        eq("bangla", AutoCorrect.fix("আমি", W, null), "");
        eq("inner capital", AutoCorrect.fix("heLlo", W, null), "");
        eq("null", AutoCorrect.fix(null, W, null), "");
        eq("empty", AutoCorrect.fix("", W, null), "");
        eq("null words", AutoCorrect.fix("teh", null, null), "");
        eq("len3 insert not allowed", AutoCorrect.fix("thx", k("thx2", "the", "thy"), null), "");
        eq("len3 replace not allowed", AutoCorrect.fix("thy", k("the"), null), "");
        eq("eligible ok", AutoCorrect.eligible("Word"), true);
        eq("eligible swap", AutoCorrect.eligible("wOrd"), false);
        eq("eligible space", AutoCorrect.eligible("a b"), false);
        eq("canUndo yes", AutoCorrect.canUndo("say the ", "the"), true);
        eq("canUndo no space", AutoCorrect.canUndo("say the", "the"), false);
        eq("canUndo other", AutoCorrect.canUndo("say them ", "the"), false);
        eq("canUndo blank", AutoCorrect.canUndo("say ", ""), false);
        eq("canUndo null", AutoCorrect.canUndo(null, "the"), false);
        eq("constants", AutoCorrect.MIN_LEN + "/" + AutoCorrect.MAX_LEN, "3/20");
        System.out.println("AutoCorrectTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
