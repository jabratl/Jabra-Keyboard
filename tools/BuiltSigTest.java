import com.mgb.keyboard.layout.BuiltSig;

public class BuiltSigTest {
    static int checks = 0, fails = 0;
    static void ok(String n, boolean c) { checks++; if (!c) { fails++; System.out.println("FAIL " + n); } }
    public static void main(String[] a) {
        ok("same = same", BuiltSig.of(true, 1, "x").equals(BuiltSig.of(true, 1, "x")));
        ok("numrow toggle differs", !BuiltSig.of(true, 1, "x").equals(BuiltSig.of(false, 1, "x")));
        ok("int differs", !BuiltSig.of(true, 1, "x").equals(BuiltSig.of(true, 2, "x")));
        ok("no merge ambiguity", !BuiltSig.of("a;b", "c").equals(BuiltSig.of("a", "b;c")));
        ok("null safe", BuiltSig.of((Object[]) null).equals("") && BuiltSig.of((Object) null).length() > 0);
        System.out.println("BuiltSigTest: " + checks + " checks, " + fails + " fails");
        if (fails > 0) System.exit(1);
    }
}
