import com.mgb.keyboard.typing.ClipCardSize;

public class ClipCardSizeTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        t("100 = old height", ClipCardSize.minHeightDp(72, 100) == 72);
        t("unset = old", ClipCardSize.minHeightDp(72, -1) == 72);
        t("0 = old", ClipCardSize.minHeightDp(72, 0) == 72);
        t("999 = old", ClipCardSize.minHeightDp(72, 999) == 72);
        t("140 of 72 -> 101", ClipCardSize.minHeightDp(72, 140) == 101);
        t("70 of 72 -> 56 (floor)", ClipCardSize.minHeightDp(72, 70) == 56);
        t("80 of 72 -> 58", ClipCardSize.minHeightDp(72, 80) == 58);
        t("old 40 stays 40 at 70", ClipCardSize.minHeightDp(40, 70) == 40);
        t("bad old dp 0", ClipCardSize.minHeightDp(0, 80) == 0);
        t("sanitize 69", ClipCardSize.sanitize(69) == 100);
        t("sanitize 70", ClipCardSize.sanitize(70) == 70);
        t("sanitize 140", ClipCardSize.sanitize(140) == 140);
        t("sanitize 141", ClipCardSize.sanitize(141) == 100);
        t("valid edges", ClipCardSize.valid(70) && ClipCardSize.valid(140) && !ClipCardSize.valid(69) && !ClipCardSize.valid(141));
        t("text 100", ClipCardSize.textSize(12.5f, 100) == 12.5f);
        t("text 80", Math.abs(ClipCardSize.textSize(12.5f, 80) - 10f) < 0.001f);
        t("text 140", Math.abs(ClipCardSize.textSize(12.5f, 140) - 17.5f) < 0.001f);
        t("text floor 9", ClipCardSize.textSize(12.5f, 70) == 9f);
        t("text bad = old", ClipCardSize.textSize(12.5f, -5) == 12.5f);
        t("small old text stays", ClipCardSize.textSize(8f, 70) == 8f);
        t("monotonic height", ClipCardSize.minHeightDp(72, 90) <= ClipCardSize.minHeightDp(72, 100) && ClipCardSize.minHeightDp(72, 100) <= ClipCardSize.minHeightDp(72, 120));
        t("monotonic text", ClipCardSize.textSize(12.5f, 90) <= ClipCardSize.textSize(12.5f, 100) && ClipCardSize.textSize(12.5f, 100) <= ClipCardSize.textSize(12.5f, 120));
        System.out.println("ClipCardSizeTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
}
