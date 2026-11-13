import com.mgb.keyboard.typing.ClipListH;

public class ClipListHTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        t("100 = old", ClipListH.heightPx(600, 100, 150) == 600);
        t("unset = old", ClipListH.heightPx(600, -1, 150) == 600);
        t("0 = old", ClipListH.heightPx(600, 0, 150) == 600);
        t("101 = old", ClipListH.heightPx(600, 101, 150) == 600);
        t("49 = old", ClipListH.heightPx(600, 49, 150) == 600);
        t("50 of 600 = 300", ClipListH.heightPx(600, 50, 150) == 300);
        t("80 of 600 = 480", ClipListH.heightPx(600, 80, 150) == 480);
        t("floor one row", ClipListH.heightPx(200, 50, 150) == 150);
        t("old below floor stays", ClipListH.heightPx(100, 50, 150) == 100);
        t("zero / negative height", ClipListH.heightPx(0, 50, 150) == 0 && ClipListH.heightPx(-5, 50, 150) == -5);
        t("negative min", ClipListH.heightPx(600, 50, -10) == 300);
        t("never taller", ClipListH.heightPx(600, 90, 150) <= 600);
        t("monotonic", ClipListH.heightPx(600, 60, 150) <= ClipListH.heightPx(600, 70, 150) && ClipListH.heightPx(600, 70, 150) <= ClipListH.heightPx(600, 100, 150));
        t("sanitize edges", ClipListH.sanitize(50) == 50 && ClipListH.sanitize(100) == 100 && ClipListH.sanitize(49) == 100 && ClipListH.sanitize(101) == 100);
        t("valid edges", ClipListH.valid(50) && ClipListH.valid(100) && !ClipListH.valid(49) && !ClipListH.valid(101));
        t("consts", ClipListH.MIN == 50 && ClipListH.MAX == 100 && ClipListH.STEP == 10 && ClipListH.DEF == 100);
        System.out.println("ClipListHTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
}
