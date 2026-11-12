import com.mgb.keyboard.typing.QuickActionPlan;
import com.mgb.keyboard.typing.TextTools;
import java.util.List;

public class QuickActionPlanTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static String ids(String s) { return QuickActionPlan.ids(s).toString(); }
    public static void main(String[] a) {
        // active
        eq("active on, selection", QuickActionPlan.active(true, false, 3, 9), true);
        eq("active off pref", QuickActionPlan.active(false, false, 3, 9), false);
        eq("active blocked (password)", QuickActionPlan.active(true, true, 3, 9), false);
        eq("active no selection", QuickActionPlan.active(true, false, 5, 5), false);
        eq("active reversed", QuickActionPlan.active(true, false, 9, 3), false);
        eq("active negative", QuickActionPlan.active(true, false, -1, 4), false);
        eq("active too long", QuickActionPlan.active(true, false, 0, QuickActionPlan.MAX_SEL + 1), false);
        eq("active max ok", QuickActionPlan.active(true, false, 0, QuickActionPlan.MAX_SEL), true);
        // ids
        eq("ids null", ids(null), "[]");
        eq("ids empty", ids(""), "[]");
        eq("ids blank", ids("   \n "), "[]");
        eq("ids plain word", ids("hello"), "[copy, case, count]");
        eq("ids digits only", ids("12345"), "[copy, count]");
        eq("ids bangla (no case)", ids("আমি"), "[copy, count]");
        eq("ids messy spaces", ids("a  b"), "[copy, case, clean]");
        eq("ids trailing space", ids("hello "), "[copy, case, clean]");
        eq("ids digits + double space", ids("1  2"), "[copy, clean, count]");
        eq("ids max 3", QuickActionPlan.ids("A  b\n\n\n\nc").size(), 3);
        eq("ids too long", ids(new String(new char[QuickActionPlan.MAX_SEL + 1]).replace('\0', 'a')), "[]");
        eq("ids first is copy", QuickActionPlan.ids("x y").get(0), "copy");
        // labels
        eq("label copy", QuickActionPlan.label("copy", "x"), "Copy");
        eq("label case", QuickActionPlan.label("case", "x"), "Case");
        eq("label clean", QuickActionPlan.label("clean", "x"), "Clean");
        eq("label count 1", QuickActionPlan.label("count", "hello"), "1 word");
        eq("label count 3", QuickActionPlan.label("count", "a b c"), "3 words");
        eq("label count bangla", QuickActionPlan.label("count", "আমি তুমি"), "2 words");
        eq("label unknown", QuickActionPlan.label("zzz", "x"), "");
        eq("label count null", QuickActionPlan.label("count", null), "0 words");
        // nextCase cycle
        eq("case mixed > UPPER", QuickActionPlan.nextCase("Hello world"), TextTools.UPPER);
        eq("case UPPER > lower", QuickActionPlan.nextCase("HELLO"), TextTools.LOWER);
        eq("case lower > Title", QuickActionPlan.nextCase("hello world"), TextTools.TITLE);
        eq("case Title > UPPER", QuickActionPlan.nextCase("Hello World"), TextTools.UPPER);
        eq("case null", QuickActionPlan.nextCase(null), TextTools.UPPER);
        // apply
        eq("apply case 1", QuickActionPlan.apply("case", "Hello world"), "HELLO WORLD");
        eq("apply case 2", QuickActionPlan.apply("case", "HELLO WORLD"), "hello world");
        eq("apply case 3", QuickActionPlan.apply("case", "hello world"), "Hello World");
        String cyc = "mIxEd text";
        for (int i = 0; i < 3; i++) cyc = QuickActionPlan.apply("case", cyc);
        eq("case cycle 3 taps ends as Title", cyc, "Mixed Text");
        eq("apply clean spaces", QuickActionPlan.apply("clean", "a   b  "), "a b");
        eq("apply clean blank lines", QuickActionPlan.apply("clean", "a\n\n\n\nb"), "a\n\nb");
        eq("apply copy = no change", QuickActionPlan.apply("copy", "abc"), null);
        eq("apply count = no change", QuickActionPlan.apply("count", "abc"), null);
        eq("apply unknown", QuickActionPlan.apply("zzz", "abc"), null);
        eq("apply null", QuickActionPlan.apply("case", null), null);
        eq("apply empty", QuickActionPlan.apply("case", ""), null);
        eq("apply bangla case = unchanged text", QuickActionPlan.apply("case", "আমি"), "আমি");
        eq("apply emoji safe", QuickActionPlan.apply("case", "hi 😀"), "Hi 😀");
        System.out.println("QuickActionPlanTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
