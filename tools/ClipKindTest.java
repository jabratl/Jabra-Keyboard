import com.mgb.keyboard.clip.ClipAge;
import com.mgb.keyboard.clip.ClipKind;

public class ClipKindTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static String k(String s) { return ClipKind.name(ClipKind.kind(s)); }
    public static void main(String[] a) {
        // links
        eq("https", k("https://example.com/page?x=1"), "Link");
        eq("http", k("http://a.bc"), "Link");
        eq("HTTPS upper", k("HTTPS://EXAMPLE.COM"), "Link");
        eq("www", k("www.example.com"), "Link");
        eq("link with spaces around", k("  https://example.com  "), "Link");
        eq("sentence with link = none", k("see https://example.com now"), "");
        eq("bare scheme", k("https://"), "");
        eq("www alone", k("www."), "");
        // email
        eq("email", k("me@example.com"), "Email");
        eq("email dots", k("a.b-c@mail.example.org"), "Email");
        eq("no domain dot", k("me@example"), "");
        eq("two @", k("a@b@c.com"), "");
        eq("at start", k("@example.com"), "");
        eq("email with space", k("me @example.com"), "");
        eq("trailing dot domain", k("me@example."), "");
        // code
        eq("otp 6 digits", k("482913"), "Code");
        eq("otp 4 digits", k("1234"), "Code");
        eq("otp 8 digits", k("12345678"), "Code");
        // phone
        eq("phone intl", k("+8801712345678"), "Phone");
        eq("phone spaces", k("+880 1712-345678"), "Phone");
        eq("phone local 11", k("01712345678"), "Phone");
        eq("phone brackets", k("(555) 123-4567"), "Phone");
        eq("too short digits", k("12345"), "Code");
        eq("digits too long", k("1234567890123456"), "");
        eq("plus in middle", k("12+345678901"), "");
        // bangla
        eq("bangla word", k("আমি ভাত খাই"), "Bangla");
        eq("bangla with english word", k("আমি ভাত খাই ok"), "Bangla");
        eq("mostly english with bangla word", k("this is a long english sentence আমি"), "");
        eq("bangla too short (2 chars)", k("আম"), "");
        eq("bangla digits only = code (same as the code guard)", k("\u09E7\u09E8\u09E9\u09EA\u09EB"), "Code");
        // none
        eq("plain text", k("hello world"), "");
        eq("single word", k("hello"), "");
        eq("sentence", k("Remember to buy milk."), "");
        eq("null", k(null), "");
        eq("empty", k(""), "");
        eq("blank", k("   \n "), "");
        eq("emoji", k("😀"), "");
        eq("code snippet text", k("int x = 5;"), "");
        // limits
        StringBuilder big = new StringBuilder("https://");
        for (int i = 0; i < ClipKind.MAX_LOOK; i++) big.append('a');
        eq("too long = none", k(big.toString()), "");
        eq("name out of range", ClipKind.name(99), "");
        eq("name negative", ClipKind.name(-1), "");
        eq("kinds count", ClipKind.NAMES.length, 6);
        // age
        long now = 1_000_000_000_000L;
        eq("age unknown", ClipAge.shortAge(0L, now), "");
        eq("age negative made", ClipAge.shortAge(-5L, now), "");
        eq("age now unknown", ClipAge.shortAge(now, 0L), "");
        eq("age 10 sec", ClipAge.shortAge(now - 10_000L, now), "now");
        eq("age future", ClipAge.shortAge(now + 5000L, now), "now");
        eq("age 1 min", ClipAge.shortAge(now - 60_000L, now), "1m");
        eq("age 59 min", ClipAge.shortAge(now - 59 * 60_000L, now), "59m");
        eq("age 60 min", ClipAge.shortAge(now - 60 * 60_000L, now), "1h");
        eq("age 23 h", ClipAge.shortAge(now - 23 * 3600_000L, now), "23h");
        eq("age 24 h", ClipAge.shortAge(now - 24 * 3600_000L, now), "1d");
        eq("age 6 d", ClipAge.shortAge(now - 6 * 86400_000L, now), "6d");
        eq("age 7 d", ClipAge.shortAge(now - 7 * 86400_000L, now), "1w");
        eq("age 8 w", ClipAge.shortAge(now - 56 * 86400_000L, now), "8w");
        eq("age 9 w = blank", ClipAge.shortAge(now - 63 * 86400_000L, now), "");
        System.out.println("ClipKindTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
