import com.mgb.keyboard.typing.FixNames;
import com.mgb.keyboard.typing.GlossaryImport;
import com.mgb.keyboard.typing.NovelBook;
import java.util.Arrays;

/** v0.67.0 (RENAME): the pure rules behind Rename in the project list (clean name, renamed set names, the terms still found after a rename). */
public class NovelRenameTest {
    static int ok = 0, bad = 0;
    static void eq(String label, Object actual, Object expected) {
        if (String.valueOf(actual).equals(String.valueOf(expected))) ok++;
        else { bad++; System.out.println("FAIL " + label + ": got " + actual + " expected " + expected); }
    }
    public static void main(String[] args) {
        eq("trim + squeeze", NovelBook.cleanName("  My   Novel  "), "My Novel");
        eq("line break", NovelBook.cleanName("A\nB"), "A B");
        eq("set separator dot", NovelBook.cleanName("A \u00B7 B"), "A - B");
        eq("empty", NovelBook.cleanName("   "), "");
        eq("null", NovelBook.cleanName(null), "");
        eq("cap", NovelBook.cleanName("12345678901234567890123456789012345678901234567890").length(), NovelBook.MAX_NAME);

        String old = NovelBook.mySetName("Old Name", 1);
        String old2 = NovelBook.mySetName("Old Name", 2);
        eq("renamed set 1", NovelBook.renamedSet(old, "New"), NovelBook.mySetName("New", 1));
        eq("renamed set 2", NovelBook.renamedSet(old2, "New"), NovelBook.mySetName("New", 2));
        eq("renamed set still glossary", GlossaryImport.isGlossarySet(NovelBook.renamedSet(old, "New")), true);
        eq("renamed set belongs to new", NovelBook.belongs(NovelBook.renamedSet(old, "New"), "new"), true);
        eq("renamed set no longer belongs to old", NovelBook.belongs(NovelBook.renamedSet(old, "New"), "Old Name"), false);
        eq("own set unchanged", NovelBook.renamedSet("My set", "New"), "My set");
        eq("empty new name unchanged", NovelBook.renamedSet(old, " "), old);
        eq("is still My pairs", NovelBook.isMine(NovelBook.renamedSet(old, "New")), true);

        // the terms of the project are found under the new name
        String rules = "gaam; geme => game\n";
        java.util.List<FixNames.Row> before = FixNames.rows("Old Name", Arrays.asList(old), Arrays.asList(rules));
        java.util.List<FixNames.Row> after = FixNames.rows("New", Arrays.asList(NovelBook.renamedSet(old, "New")), Arrays.asList(rules));
        eq("rows before", before.size(), 1);
        eq("rows after", after.size(), 1);
        eq("same right form", after.get(0).to, before.get(0).to);
        eq("old name finds nothing now", FixNames.rows("Old Name", Arrays.asList(NovelBook.renamedSet(old, "New")), Arrays.asList(rules)).size(), 0);

        // the Keep as is pref follows the name (key from the lower-case name)
        eq("same key for a case-only change", NovelBook.neverKey("Old"), NovelBook.neverKey("OLD"));
        eq("other key for a new name", NovelBook.neverKey("Old").equals(NovelBook.neverKey("New")), false);
        // longest saved set name stays under the 64 characters of ReplaceStore
        StringBuilder sb = new StringBuilder(); for (int i = 0; i < NovelBook.MAX_NAME; i++) sb.append('x');
        eq("longest name fits 64", NovelBook.mySetName(sb.toString(), 10).length() <= 64, true);

        System.out.println("NovelRenameTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
