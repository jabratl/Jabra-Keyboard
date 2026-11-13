import com.mgb.keyboard.layout.*;
import java.util.*;

/** v0.17.18 (L2) texts of the "Current layout" bar of the layout editor. */
public class LayoutStatusTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        t("saved", LayoutStatus.headline("Coder", false).equals("Coder  ✓ saved"));
        t("unsaved", LayoutStatus.headline("Coder", true).equals("Coder  ● unsaved changes"));
        t("no name", LayoutStatus.headline("", true).equals("Not saved under a name yet") && LayoutStatus.headline(null, false).equals("Not saved under a name yet") && LayoutStatus.headline("  ", false).equals("Not saved under a name yet"));
        t("quick save needs a name", LayoutStatus.canQuickSave("A") && !LayoutStatus.canQuickSave("") && !LayoutStatus.canQuickSave(null) && !LayoutStatus.canQuickSave("  "));
        t("use line on", LayoutStatus.useLine(true).startsWith("In use"));
        t("use line off", LayoutStatus.useLine(false).startsWith("Not in use") && LayoutStatus.useLine(false).contains("Save & use"));
        t("no rules no warning", LayoutStatus.ruleWarning("A", new ArrayList<String>()).isEmpty() && LayoutStatus.ruleWarning("A", null).isEmpty());
        t("rule = active is fine", LayoutStatus.ruleWarning("A", Arrays.asList("a", "A")).isEmpty());
        String w = LayoutStatus.ruleWarning("A", Arrays.asList("BN", "bn", "A", "Coder"));
        t("warning lists other names once", w.contains("\"BN\"") && w.contains("\"Coder\"") && !w.contains("\"bn\"") && !w.contains("\"A\""));
        t("warning ignores null / empty", LayoutStatus.ruleWarning("A", Arrays.asList(null, "", " ")).isEmpty());
        t("warning with no active name", LayoutStatus.ruleWarning("", Arrays.asList("BN")).contains("\"BN\""));
        // suggestName
        LayoutBook b = LayoutBook.empty();
        t("suggest on empty book", LayoutStatus.suggestName(b).equals("My layout") && LayoutStatus.suggestName(null).equals("My layout"));
        LayoutDef m = LayoutDefaults.build();
        String txt = LayoutCodec.encode(m);
        LayoutBook b2 = b.saveAs("Mine", txt);
        t("suggest = active name", LayoutStatus.suggestName(b2).equals("Mine"));
        LayoutBook b3 = b2.add("Other", txt);
        t("suggest keeps active", LayoutStatus.suggestName(b3).equals("Mine"));
        // quick save (same name replaces, name stays active): the book rule the store relies on
        LayoutBook b4 = b3.saveAs(b3.active(), txt + "\n");
        t("save under active name replaces, size same", b4.size() == b3.size() && b4.active().equals("Mine"));
        System.out.println("LayoutStatusTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
