import com.mgb.keyboard.typing.AvroNumPage;

/** v0.17.55 (M3): Ridmik number page table + gate. */
public class AvroNumPageTest {
    static int fails = 0, checks = 0;
    static void eq(String what, Object a, Object b) {
        checks++;
        if (a == null ? b != null : !a.equals(b)) { fails++; System.out.println("FAIL " + what + ": got " + a + " want " + b); }
    }
    public static void main(String[] x) {
        eq("rows", AvroNumPage.ROWS, 3);
        String[][] r0 = AvroNumPage.row(0), r1 = AvroNumPage.row(1), r2 = AvroNumPage.row(2);
        eq("r0 len", r0.length, 10); eq("r1 len", r1.length, 10); eq("r2 len", r2.length, 7);
        String bn = "\u09E7\u09E8\u09E9\u09EA\u09EB\u09EC\u09ED\u09EE\u09EF\u09E6", en = "1234567890";
        for (int i = 0; i < 10; i++) {
            eq("digit typed " + i, r0[i][1], String.valueOf(bn.charAt(i)));
            eq("digit label " + i, r0[i][0], String.valueOf(bn.charAt(i)));
            eq("digit hold " + i, r0[i][2], String.valueOf(en.charAt(i)));
        }
        String r1t = "@#\u09F3%&*-+()";
        for (int i = 0; i < 10; i++) eq("r1 typed " + i, r1[i][1], String.valueOf(r1t.charAt(i)));
        eq("taka hold", r1[2][2], "$"); eq("star hold", r1[5][2], "\u2605"); eq("minus hold", r1[6][2], "_");
        eq("( hold", r1[8][2], "<"); eq(") hold", r1[9][2], ">"); eq("@ hold", r1[0][2], ""); eq("+ hold", r1[7][2], "");
        String r2t = "!\"':;/?";
        for (int i = 0; i < 7; i++) eq("r2 typed " + i, r2[i][1], String.valueOf(r2t.charAt(i)));
        eq("colon hold", r2[3][2], "\u00B0");
        for (int r = 0; r < 3; r++) for (String[] k : AvroNumPage.row(r)) {
            eq("one char typed " + k[1], k[1].length(), 1);
            eq("hold <= 1 char " + k[1], k[2].length() <= 1, true);
            eq("label not empty", k[0].isEmpty(), false);
        }
        eq("bad row -1", AvroNumPage.row(-1).length, 0); eq("bad row 3", AvroNumPage.row(3).length, 0);
        AvroNumPage.row(0)[0][1] = "X"; eq("fresh copy", AvroNumPage.row(0)[0][1], "\u09E7");
        eq("switch label", AvroNumPage.SWITCH_LABEL, "=\\<");
        eq("gate on", AvroNumPage.active(true, true, 0), true);
        eq("gate bad page = 0", AvroNumPage.active(true, true, 9), true);
        eq("gate page 1", AvroNumPage.active(true, true, 1), false);
        eq("gate page 2", AvroNumPage.active(true, true, 2), false);
        eq("gate off", AvroNumPage.active(false, true, 0), false);
        eq("gate english", AvroNumPage.active(true, false, 0), false);
        System.out.println((fails == 0 ? "OK " : "FAILED ") + checks + " checks");
        if (fails > 0) System.exit(1);
    }
}
