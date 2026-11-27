import com.mgb.keyboard.typing.FixNames;
import com.mgb.keyboard.typing.NovelBook;
import com.mgb.keyboard.typing.ReplacePairList;
import com.mgb.keyboard.typing.ReplaceEngine;
import java.util.ArrayList;
import java.util.Arrays;

/** v0.58.1: regression checks for manually-created novel pair lists. */
public class NovelManualPairsTest {
    static int ok = 0, bad = 0;
    static void eq(String label, Object actual, Object expected) {
        if (String.valueOf(actual).equals(String.valueOf(expected))) ok++;
        else { bad++; System.out.println("FAIL " + label + ": got " + actual + " expected " + expected); }
    }
    public static void main(String[] args) {
        ArrayList<String> novels = new ArrayList<>();
        eq("empty novel rejected", NovelBook.add(novels, "  "), false);
        eq("create novel", NovelBook.add(novels, "Novel One"), true);
        eq("case-insensitive duplicate", NovelBook.add(novels, "novel one"), true);
        eq("one novel only", novels.size(), 1);
        eq("registry survives serialization", NovelBook.parse(NovelBook.text(novels)).get(0), "Novel One");

        String setName = NovelBook.mySetName("Novel One", 1);
        ReplacePairList.Made made = ReplacePairList.make("Wrong A; Wrong B", "Right Name", ReplaceEngine.MODE_TEXT, false, false);
        String rules = ReplacePairList.text(Arrays.asList(made.rule));
        NovelBook.Merged merged = NovelBook.merge("", Arrays.asList(rules.trim()), NovelBook.RULES_PER_SET);
        eq("one pair stored", merged.added, 1);
        eq("variants retained", merged.text.contains("Wrong A") && merged.text.contains("Wrong B"), true);
        java.util.List<FixNames.Row> rows = FixNames.rows("Novel One", Arrays.asList(setName), Arrays.asList(merged.text));
        eq("one saved row", rows.size(), 1);
        eq("correct form", rows.get(0).to, "Right Name");
        eq("wrong variants", rows.get(0).formsText(), "Wrong A; Wrong B");
        System.out.println("NovelManualPairsTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
