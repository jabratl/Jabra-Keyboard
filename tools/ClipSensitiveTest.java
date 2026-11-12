import com.mgb.keyboard.typing.ClipSensitive;

/** v0.10.10 (W4) ClipSensitive tests (computer only). */
public class ClipSensitiveTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }
    static boolean c(String s) { return ClipSensitive.looksLikeCode(s); }

    public static void main(String[] a) {
        // digits only
        t("4 digits", c("1234"));
        t("6 digits", c("482913"));
        t("8 digits", c("12345678"));
        t("3 digits no", !c("123"));
        t("9 digits no", !c("123456789"));
        t("trim spaces", c("  482913  "));
        t("trim newline", c("482913\n"));
        t("trim crlf + tab", c("\t482913\r\n"));
        t("space inside no", !c("482 913"));
        t("newline inside no", !c("482\n913"));
        t("hyphen inside no", !c("482-913"));
        t("plus phone no", !c("+8801712345678"));
        t("phone 11 digits no", !c("01712345678"));
        // Bangla digits
        t("bangla 6 digits", c("\u09E7\u09E8\u09E9\u09EA\u09EB\u09EC"));
        t("bangla 4 digits", c("\u09E7\u09E8\u09E9\u09EA"));
        t("bangla 3 digits no", !c("\u09E7\u09E8\u09E9"));
        t("bangla 9 digits no", !c("\u09E7\u09E8\u09E9\u09EA\u09EB\u09EC\u09ED\u09EE\u09EF"));
        t("bangla + english digits mix", c("\u09E7\u09E8 3456"  .replace(" ", "")));
        t("bangla letters no", !c("\u0986\u09AE\u09BF\u0995\u09BF"));
        t("bangla word + digit no", !c("\u0986\u09AE\u09BF12"));
        // mixed ASCII code
        t("A1B2C3", c("A1B2C3"));
        t("X7K9Q2", c("X7K9Q2"));
        t("lower a3f9k2", c("a3f9k2"));
        t("10 chars mixed", c("AB12CD34EF"));
        t("11 chars mixed no", !c("AB12CD34EFG"));
        t("one digit only no (html5)", !c("html5"));
        t("one digit word no", !c("abcd1"));
        t("letters only no", !c("hello"));
        t("letters only 10 no", !c("abcdefghij"));
        t("4 mixed ok", c("A1B2"));
        t("3 mixed no", !c("A12"));
        t("underscore no", !c("A1_B2C"));
        t("hyphen mixed no", !c("G-123456"));
        t("dot no", !c("A1.B2C"));
        // normal text
        t("sentence no", !c("Your code is 123456"));
        t("sentence with digits no", !c("call me at 5"));
        t("long text no", !c("x".repeat(500)));
        t("long digits no", !c("1".repeat(200)));
        t("url no", !c("https://a.com/1"));
        t("email no", !c("a1b2@c.com"));
        t("empty no", !c(""));
        t("spaces only no", !c("    "));
        t("null no", !c(null));
        t("emoji no", !c("\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00"));
        t("emoji + digits no", !c("12\uD83D\uDE0034"));
        t("fullwidth digits (Unicode Nd) ok", c("\uFF11\uFF12\uFF13\uFF14"));
        t("superscript digits no", !c("\u00B9\u00B2\u00B3\u2074"));
        // isDue
        long T = ClipSensitive.CODE_TTL_MS;
        t("ttl is 2 minutes", T == 120000L);
        t("due: code, old", ClipSensitive.isDue(false, "123456", 1000L, 1000L + T + 1));
        t("not due: exactly 2 min", !ClipSensitive.isDue(false, "123456", 1000L, 1000L + T));
        t("not due: fresh", !ClipSensitive.isDue(false, "123456", 1000L, 1000L + 5000L));
        t("not due: pinned", !ClipSensitive.isDue(true, "123456", 1000L, 1000L + T * 10));
        t("not due: sentence", !ClipSensitive.isDue(false, "Meet me at 5 pm", 1000L, 1000L + T * 10));
        t("not due: no record", !ClipSensitive.isDue(false, "123456", 0L, 1000L + T * 10));
        t("not due: negative record", !ClipSensitive.isDue(false, "123456", -5L, T * 10));
        t("due: mixed code old", ClipSensitive.isDue(false, "A1B2C3", 5L, 5L + T + 1000L));
        t("due: bangla code old", ClipSensitive.isDue(false, "\u09E7\u09E8\u09E9\u09EA\u09EB\u09EC", 5L, 5L + T + 1000L));
        System.out.println("ClipSensitiveTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
