import com.mgb.keyboard.typing.ClipBtn;

public class ClipBtnTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        t("100 = old height", ClipBtn.heightDp(40, 100) == 40);
        t("unset = old", ClipBtn.heightDp(40, -1) == 40);
        t("0 = old", ClipBtn.heightDp(40, 0) == 40);
        t("999 = old", ClipBtn.heightDp(40, 999) == 40);
        t("140 -> 56", ClipBtn.heightDp(40, 140) == 56);
        t("70 of 40 -> 28 (floor)", ClipBtn.heightDp(40, 70) == 28);
        t("80 of 40 -> 32", ClipBtn.heightDp(40, 80) == 32);
        t("70 of 34 -> 28 floor", ClipBtn.heightDp(34, 70) == 28);
        t("old 20 stays 20 at 70", ClipBtn.heightDp(20, 70) == 20);
        t("bad old dp 0", ClipBtn.heightDp(0, 80) == 0);
        t("sanitize 69", ClipBtn.sanitize(69) == 100);
        t("sanitize 70", ClipBtn.sanitize(70) == 70);
        t("sanitize 140", ClipBtn.sanitize(140) == 140);
        t("sanitize 141", ClipBtn.sanitize(141) == 100);
        t("text 100", ClipBtn.textSize(14f, 100) == 14f);
        t("text 70", Math.abs(ClipBtn.textSize(10f, 70) - 7f) < 0.001f);
        t("text bad = old", ClipBtn.textSize(12f, -5) == 12f);
        t("monotonic", ClipBtn.heightDp(48, 90) <= ClipBtn.heightDp(48, 100) && ClipBtn.heightDp(48, 100) <= ClipBtn.heightDp(48, 120));
        System.out.println("ClipBtnTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
}
