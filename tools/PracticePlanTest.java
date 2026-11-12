import com.mgb.keyboard.phonetic.RidmikMap;
import com.mgb.keyboard.typing.KeyMapData;
import com.mgb.keyboard.typing.PracticePlan;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** v0.16.5 (X5): PracticePlan = deck (every word typeable with the real engine), next(), check(), streak. */
public class PracticePlanTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }

    public static void main(String[] a) {
        List<PracticePlan.Item> deck = PracticePlan.deck();
        t("deck >= 60 words", deck.size() >= 60);
        t("size() = deck size", PracticePlan.size() == deck.size());
        Set<String> seen = new HashSet<String>();
        for (PracticePlan.Item it : deck) {
            t("no duplicate " + it.bangla, seen.add(it.bangla));
            String got = RidmikMap.convert(it.hint);
            t("hint " + it.hint + " -> " + it.bangla + " (engine gave " + got + ")", KeyMapData.norm(got).equals(KeyMapData.norm(it.bangla)));
            t("check RIGHT with hint " + it.hint, PracticePlan.check(it.bangla, it.hint).state == PracticePlan.RIGHT);
            t("word not empty " + it.hint, it.bangla.length() > 0 && it.hint.length() > 0);
        }
        // examples of the key map page are all in the deck
        for (KeyMapData.Example x : KeyMapData.examples()) t("example in deck " + x.bangla, seen.contains(x.bangla));

        // check()
        PracticePlan.Result r = PracticePlan.check("বাংলা", "bangla");
        t("right", r.state == PracticePlan.RIGHT && r.made.equals("বাংলা"));
        r = PracticePlan.check("বাংলা", "banglaa");
        t("wrong shows engine text", r.state == PracticePlan.WRONG && r.made.length() > 0 && !r.made.equals("বাংলা"));
        t("empty", PracticePlan.check("বাংলা", "").state == PracticePlan.EMPTY);
        t("blank = empty", PracticePlan.check("বাংলা", "   ").state == PracticePlan.EMPTY);
        t("null typed = empty", PracticePlan.check("বাংলা", null).state == PracticePlan.EMPTY);
        t("null target = wrong, no crash", PracticePlan.check(null, "ami").state == PracticePlan.WRONG);
        t("spaces around ok", PracticePlan.check("বাংলা", "  bangla  ").state == PracticePlan.RIGHT);
        t("case matters (T vs t)", PracticePlan.check("টিটু", "titu").state == PracticePlan.WRONG && PracticePlan.check("টিটু", "TiTu").state == PracticePlan.RIGHT);
        t("ZWNJ ignored", PracticePlan.check("আল্লাহ্‌", "allahhs").state == PracticePlan.RIGHT);
        t("ZWNJ ignored in target without it", PracticePlan.check("আল্লাহ্", "allahhs").state == PracticePlan.RIGHT);
        t("ড় one char target", PracticePlan.check("গাড়িতে", "gaRite").state == PracticePlan.RIGHT && PracticePlan.check("গাড়িতে", "gaRite").state == PracticePlan.RIGHT);
        t("odd input no crash", PracticePlan.check("x", "!!!@@@ ???").state != PracticePlan.RIGHT);
        t("long input no crash", PracticePlan.check("ক", new String(new char[500]).replace('\0', 'k')).state == PracticePlan.WRONG);

        // next()
        for (int i = 0; i < 300; i++) {
            int n = PracticePlan.next(i % PracticePlan.size(), i * 7919L);
            t("next in range " + i, n >= 0 && n < PracticePlan.size());
            t("next != prev " + i, n != i % PracticePlan.size());
        }
        t("same seed same word", PracticePlan.next(-1, 42L) == PracticePlan.next(-1, 42L));
        t("first word with prev -1 in range", PracticePlan.next(-1, 0L) >= 0 && PracticePlan.next(-1, -5L) >= 0 && PracticePlan.next(-1, Long.MIN_VALUE) >= 0);
        Set<Integer> spread = new HashSet<Integer>();
        for (int i = 0; i < 400; i++) spread.add(PracticePlan.next(-1, i));
        t("seeds reach many words (>= 30)", spread.size() >= 30);
        t("at wraps", PracticePlan.at(0).bangla.equals(PracticePlan.at(PracticePlan.size()).bangla) && PracticePlan.at(-1).bangla.equals(PracticePlan.at(PracticePlan.size() - 1).bangla));

        // streak
        int[] s = PracticePlan.streak(0, 0, true);
        t("first right", s[0] == 1 && s[1] == 1);
        s = PracticePlan.streak(s[0], s[1], true);
        t("second right", s[0] == 2 && s[1] == 2);
        s = PracticePlan.streak(s[0], s[1], false);
        t("wrong resets, best kept", s[0] == 0 && s[1] == 2);
        s = PracticePlan.streak(0, 5, true);
        t("best stays higher", s[0] == 1 && s[1] == 5);
        s = PracticePlan.streak(-3, -9, true);
        t("negative input safe", s[0] == 1 && s[1] == 1);
        t("streak text", PracticePlan.streakText(3, 7).equals("Streak 3   Best 7") && PracticePlan.streakText(-1, -1).equals("Streak 0   Best 0"));

        System.out.println("PracticePlanTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
