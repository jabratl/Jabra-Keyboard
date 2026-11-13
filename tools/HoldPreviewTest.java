import com.mgb.keyboard.typing.HoldPreview;

/** v0.17.31 (UI7): bubble text for a held key. */
public class HoldPreviewTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }

    public static void main(String[] a) {
        t("null = no bubble", HoldPreview.label(null) == null);
        t("empty = no bubble", HoldPreview.label("") == null);
        t("question mark as is", "?".equals(HoldPreview.label("?")));
        t("digit as is", "5".equals(HoldPreview.label("5")));
        t("Bangla digit as is", "\u09EB".equals(HoldPreview.label("\u09EB")));
        t("action text = no bubble", HoldPreview.label("@paste") == null && HoldPreview.label("@") == null);
        t("single space = space symbol", HoldPreview.SPACE.equals(HoldPreview.label(" ")));
        t("Enter = enter symbol", HoldPreview.ENTER.equals(HoldPreview.label("\n")));
        t("CRLF = one enter symbol", HoldPreview.ENTER.equals(HoldPreview.label("\r\n")));
        t("Tab = tab symbol", HoldPreview.TAB.equals(HoldPreview.label("\t")));
        t("two spaces = no bubble", HoldPreview.label("  ") == null);
        t("4 chars shown in full", "abcd".equals(HoldPreview.label("abcd")));
        t("5 chars cut to 3 + dots", ("abc" + HoldPreview.DOTS).equals(HoldPreview.label("abcde")));
        t("long url cut", ("htt" + HoldPreview.DOTS).equals(HoldPreview.label("https://example.com")));
        String emoji = new String(Character.toChars(0x1F600));
        t("emoji counts as one char", (emoji + emoji + emoji + emoji).equals(HoldPreview.label(emoji + emoji + emoji + emoji)));
        String cut = HoldPreview.label(emoji + emoji + emoji + emoji + emoji);
        t("emoji cut keeps whole emoji", cut != null && cut.equals(emoji + emoji + emoji + HoldPreview.DOTS));
        t("text with a newline inside", ("a" + HoldPreview.ENTER + "b").equals(HoldPreview.label("a\nb")));
        t("label never longer than MAX + 1 chars", HoldPreview.label("0123456789").codePointCount(0, HoldPreview.label("0123456789").length()) <= HoldPreview.MAX);
        System.out.println("HoldPreviewTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
