import com.mgb.keyboard.typing.SelCase;

public class SelCaseTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + n); } }
    public static void main(String[] a) {
        t("small -> First", SelCase.next("hello").equals("Hello"));
        t("First -> ALL", SelCase.next("Hello").equals("HELLO"));
        t("ALL -> small", SelCase.next("HELLO").equals("hello"));
        t("words", SelCase.next("hello big world").equals("Hello Big World"));
        t("words 2", SelCase.next("Hello Big World").equals("HELLO BIG WORLD"));
        t("words 3", SelCase.next("HELLO BIG WORLD").equals("hello big world"));
        t("mixed -> First", SelCase.next("hELLo wORLD").equals("Hello World"));
        t("one letter cycle", SelCase.next("a").equals("A") && SelCase.next("A").equals("a"));
        t("no case = same", SelCase.next("\u0986\u09AE\u09BF 123").equals("\u0986\u09AE\u09BF 123"));
        t("empty / null", SelCase.next("").equals("") && SelCase.next(null) == null);
        t("newline word start", SelCase.next("ab\ncd").equals("Ab\nCd"));
        t("digits stay", SelCase.next("abc 12x").equals("Abc 12x"));
        System.out.println("SelCaseTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
}
