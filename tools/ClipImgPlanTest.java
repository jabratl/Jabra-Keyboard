import com.mgb.keyboard.typing.ClipImgPlan;
import java.util.Arrays;
import java.util.List;

/** v0.18.1 tests for clipboard images (computer only). */
public class ClipImgPlanTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        t("png", "png".equals(ClipImgPlan.ext("image/png")) && "png".equals(ClipImgPlan.ext("IMAGE/PNG")));
        t("jpeg", "jpg".equals(ClipImgPlan.ext("image/jpeg")) && "jpg".equals(ClipImgPlan.ext("image/jpg")));
        t("webp gif", "webp".equals(ClipImgPlan.ext("image/webp")) && "gif".equals(ClipImgPlan.ext("image/gif")));
        t("unsupported", ClipImgPlan.ext("image/svg+xml") == null && ClipImgPlan.ext("text/plain") == null && ClipImgPlan.ext(null) == null && !ClipImgPlan.supported("image/*"));
        t("mime of name", "image/png".equals(ClipImgPlan.mimeOfName("1_a.png")) && "image/jpeg".equals(ClipImgPlan.mimeOfName("1_a.JPG")) && ClipImgPlan.mimeOfName("x.txt") == null && ClipImgPlan.mimeOfName(null) == null);
        t("too big", ClipImgPlan.tooBig(0) && ClipImgPlan.tooBig(ClipImgPlan.MAX_BYTES + 1) && !ClipImgPlan.tooBig(1000) && !ClipImgPlan.tooBig(ClipImgPlan.MAX_BYTES));
        String n = ClipImgPlan.fileName(123L, 0xBEEF, "image/png");
        t("file name", "123_beef.png".equals(n) && "beef".equals(ClipImgPlan.hashOf(n)) && ClipImgPlan.fileName(1, 1, "image/bmp") == null);
        t("hash of foreign name", ClipImgPlan.hashOf("photo.png").isEmpty() && ClipImgPlan.hashOf(null).isEmpty());
        List<String> l = Arrays.asList("9_a.png", "8_b.png", "7_c.png", "6_d.png");
        t("overflow keeps newest", ClipImgPlan.overflow(l, 2).equals(Arrays.asList("7_c.png", "6_d.png")) && ClipImgPlan.overflow(l, 4).isEmpty() && ClipImgPlan.overflow(l, 0).size() == 4);
        t("overflow null / negative", ClipImgPlan.overflow(null, 3).isEmpty() && ClipImgPlan.overflow(l, -1).size() == 4);
        t("uri text", ClipImgPlan.isImageUriText("content://media/external/1") && ClipImgPlan.isImageUriText(" file:///x/y.png ") && !ClipImgPlan.isImageUriText("hello content://x") && !ClipImgPlan.isImageUriText(null) && !ClipImgPlan.isImageUriText("https://a.b"));
        t("expired", ClipImgPlan.expired(1000, 5000, 3000) && !ClipImgPlan.expired(1000, 3500, 3000) && !ClipImgPlan.expired(1000, 999999, 0) && !ClipImgPlan.expired(0, 999999, 3000));
        t("limits", ClipImgPlan.MAX_IMAGES == 8);
        System.out.println("ClipImgPlanTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
