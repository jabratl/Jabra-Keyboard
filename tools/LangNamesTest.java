import com.mgb.keyboard.typing.LangNames;
import com.mgb.keyboard.typing.LangOrder;
import com.mgb.keyboard.typing.LangSwipe;
import com.mgb.keyboard.typing.PagePlan;

/** v0.16.2 (plan X, X2): the shown names of the two Bangla keyboards come from ONE class. */
public class LangNamesTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + n); } }
    static boolean clean(String s) { return s != null && !s.isEmpty() && !s.contains("Avro") && !s.contains("Probhat"); }

    public static void main(String[] a) {
        String B = "Bang\u09B2\u09BE", P = "\u09AC\u09BE\u0982\u09B2\u09BE";
        eq("AVRO const", LangNames.AVRO, B);
        eq("PROBHAT const", LangNames.PROBHAT, P);
        eq("name false", LangNames.name(false), B);
        eq("name true", LangNames.name(true), P);
        eq("keyLabel false", LangNames.keyLabel(false), B);
        eq("keyLabel true", LangNames.keyLabel(true), P);
        eq("bubbleName false", LangNames.bubbleName(false), B);
        eq("bubbleName true", LangNames.bubbleName(true), P);
        eq("summary false", LangNames.summary(false), B);
        eq("summary true", LangNames.summary(true), P);
        eq("key label is 6 chars", LangNames.AVRO.length(), 6);
        t("names differ", !LangNames.AVRO.equals(LangNames.PROBHAT));
        t("name never empty / old text", clean(LangNames.name(false)) && clean(LangNames.name(true)));
        t("keyLabel clean", clean(LangNames.keyLabel(false)) && clean(LangNames.keyLabel(true)));
        t("bubble clean", clean(LangNames.bubbleName(false)) && clean(LangNames.bubbleName(true)));
        t("summary clean", clean(LangNames.summary(false)) && clean(LangNames.summary(true)));
        String[] lc = LangNames.layoutChoices();
        eq("layout choices count", lc.length, 2);
        t("layout choice 0 = Bangla-lA + phonetic", lc[0].startsWith(B) && lc[0].contains("phonetic"));
        t("layout choice 1 = Bangla + fixed", lc[1].startsWith(P) && lc[1].contains("fixed"));
        t("layout choices clean", clean(lc[0]) && clean(lc[1]));
        String[] tn = LangNames.tapNames();
        eq("tap names count", tn.length, 3);
        eq("tap 0 unchanged", tn[0], "EN <-> Bangla");
        eq("tap 1", tn[1], "Cycle EN > " + B + " > " + P);
        eq("tap 2", tn[2], "Cycle EN > " + P + " > " + B);
        for (int i = 0; i < 3; i++) t("tap name " + i + " clean", clean(tn[i]));
        String[] fn = LangNames.favNames();
        eq("fav count", fn.length, 3);
        eq("fav 0 unchanged", fn[0], "Last used");
        eq("fav 1", fn[1], B);
        eq("fav 2", fn[2], P);
        // the other classes use the same text
        eq("LangOrder.TAP_NAMES = LangNames", java.util.Arrays.toString(LangOrder.TAP_NAMES), java.util.Arrays.toString(tn));
        eq("LangOrder.FAV_NAMES = LangNames", java.util.Arrays.toString(LangOrder.FAV_NAMES), java.util.Arrays.toString(fn));
        eq("LangSwipe.name false", LangSwipe.name(true, false), B);
        eq("LangSwipe.name true", LangSwipe.name(true, true), P);
        eq("LangSwipe.name EN", LangSwipe.name(false, false), "English");
        eq("LangSwipe.label false", LangSwipe.label(true, false), B);
        eq("LangSwipe.label true", LangSwipe.label(true, true), P);
        eq("LangSwipe.label EN", LangSwipe.label(false, true), "English");
        eq("spaceLabel avro", LangSwipe.spaceLabel(true, false), "\u25C2 " + B + " \u25B8");
        eq("spaceLabel probhat", LangSwipe.spaceLabel(true, true), "\u25C2 " + P + " \u25B8");
        eq("spaceLabel EN", LangSwipe.spaceLabel(false, false), "\u25C2 English \u25B8");
        eq("PagePlan summary avro", PagePlan.typingSummary(false, true), B + ", space swipe on");
        eq("PagePlan summary probhat", PagePlan.typingSummary(true, false), P + ", space swipe off");
        // behaviour (pref values / states) did not change
        t("tap EN -> avro still 1,0", LangOrder.tap(false, false, true, true, 0, 0)[0] == 1 && LangOrder.tap(false, false, true, true, 0, 0)[1] == 0);
        t("long-press avro -> probhat still 1,1", LangOrder.longPress(true, false, true, true)[1] == 1);
        System.out.println("LangNamesTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
