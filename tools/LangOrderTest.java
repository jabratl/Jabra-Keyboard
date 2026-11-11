import com.mgb.keyboard.typing.LangOrder;
import java.util.List;

/** v0.5.0 LangOrder tests. javac -encoding UTF-8 -d out $(find app/src/main/java -name '*.java') tools/LangOrderTest.java && java -cp out LangOrderTest */
public class LangOrderTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static boolean is(int[] r, int b, int p) { return r[0] == b && r[1] == p; }

    public static void main(String[] x) {
        // ---- DEFAULT settings = old behaviour
        t("old: EN -> Avro (last = avro)", is(LangOrder.tap(false, false, true, true, 0, 0), 1, 0));
        t("old: EN -> Probhat (last = probhat)", is(LangOrder.tap(false, true, true, true, 0, 0), 1, 1));
        t("old: Avro -> EN keeps layout", is(LangOrder.tap(true, false, true, true, 0, 0), 0, 0));
        t("old: Probhat -> EN keeps layout", is(LangOrder.tap(true, true, true, true, 0, 0), 0, 1));
        t("old long: Avro -> Probhat", is(LangOrder.longPress(true, false, true, true), 1, 1));
        t("old long: Probhat -> Avro", is(LangOrder.longPress(true, true, true, true), 1, 0));
        t("old long from EN turns Bangla on (other layout)", is(LangOrder.longPress(false, false, true, true), 1, 1));

        // ---- favourite first
        t("fav Avro", is(LangOrder.tap(false, true, true, true, 0, 1), 1, 0));
        t("fav Probhat", is(LangOrder.tap(false, false, true, true, 0, 2), 1, 1));
        t("fav last used = default", is(LangOrder.tap(false, true, true, true, 0, 0), 1, 1));
        t("fav Avro but Avro off -> last used", is(LangOrder.tap(false, true, false, true, 0, 1), 1, 1));

        // ---- hidden layouts
        t("Avro off: EN -> Probhat even if last = avro", is(LangOrder.tap(false, false, false, true, 0, 0), 1, 1));
        t("Probhat off: EN -> Avro even if last = probhat", is(LangOrder.tap(false, true, true, false, 0, 0), 1, 0));
        t("both off: stays EN", is(LangOrder.tap(false, false, false, false, 0, 0), 0, 0));
        t("both off: Bangla state -> EN", is(LangOrder.tap(true, false, false, false, 0, 0), 0, 0));
        t("long: other off, current on -> stays", is(LangOrder.longPress(true, false, true, false), 1, 0));
        t("long: other off, current off -> EN", is(LangOrder.longPress(true, false, false, true), 1, 1));
        t("long: both off -> EN", is(LangOrder.longPress(true, false, false, false), 0, 0));

        // ---- cycle modes
        t("cycle1: EN -> Avro", is(LangOrder.tap(false, true, true, true, 1, 0), 1, 0));
        t("cycle1: Avro -> Probhat", is(LangOrder.tap(true, false, true, true, 1, 0), 1, 1));
        t("cycle1: Probhat -> EN (keeps probhat)", is(LangOrder.tap(true, true, true, true, 1, 0), 0, 1));
        t("cycle2: EN -> Probhat", is(LangOrder.tap(false, false, true, true, 2, 0), 1, 1));
        t("cycle2: Probhat -> Avro", is(LangOrder.tap(true, true, true, true, 2, 0), 1, 0));
        t("cycle2: Avro -> EN", is(LangOrder.tap(true, false, true, true, 2, 0), 0, 0));
        t("cycle1 Avro off: EN -> Probhat -> EN", is(LangOrder.tap(false, false, false, true, 1, 0), 1, 1) && is(LangOrder.tap(true, true, false, true, 1, 0), 0, 1));
        t("cycle1 Probhat off: EN -> Avro -> EN", is(LangOrder.tap(false, false, true, false, 1, 0), 1, 0) && is(LangOrder.tap(true, false, true, false, 1, 0), 0, 0));
        t("cycle: active layout hidden -> EN", is(LangOrder.tap(true, false, false, true, 1, 0), 0, 0));
        t("cycle both off: stays EN", is(LangOrder.tap(false, false, false, false, 1, 0), 0, 0));
        t("unknown tap mode = default", is(LangOrder.tap(false, true, true, true, 9, 0), 1, 1));
        t("name tables", LangOrder.TAP_NAMES.length == 3 && LangOrder.FAV_NAMES.length == 3);

        // ---- custom character row
        List<String> r = LangOrder.customRow("@#_ -+=/ ");
        t("custom row skips spaces", r.size() == 7 && r.get(0).equals("@") && r.get(6).equals("/"));
        t("custom row distinct", LangOrder.customRow("aabbcc").size() == 3);
        t("custom row max 10", LangOrder.customRow("abcdefghijklmnop").size() == 10);
        t("custom row bangla", LangOrder.customRow("ৎঁঃ").size() == 3);
        t("custom row emoji = 1 char", LangOrder.customRow("😀x").size() == 2);
        t("custom row empty / null", LangOrder.customRow("").isEmpty() && LangOrder.customRow(null).isEmpty() && LangOrder.customRow(" \n\t").isEmpty());
        t("custom row text", LangOrder.customRowText("a b  c").equals("abc") && LangOrder.customRowText(null).equals(""));
        t("custom row text 10 max", LangOrder.customRowText("0123456789ABC").equals("0123456789"));

        System.out.println("LangOrderTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
