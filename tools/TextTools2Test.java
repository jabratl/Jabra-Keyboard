import com.mgb.keyboard.typing.TextTools2;
import com.mgb.keyboard.typing.TextTools2.R;

/** v0.10.16 (W5) TextTools2 tests (computer only). */
public class TextTools2Test {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }
    static String a(int tool, String s) { return TextTools2.apply(tool, s, 0).out; }
    static boolean err(int tool, String s) { return TextTools2.apply(tool, s, 0).err != null; }
    static void eq(String name, String got, String want) { if (want.equals(got)) ok++; else { bad++; System.out.println("FAIL: " + name + " got [" + got.replace("\n", "\\n").replace("\r", "\\r") + "]"); } }

    public static void main(String[] x) {
        final int AZ = TextTools2.SORT_AZ, ZA = TextTools2.SORT_ZA, DD = TextTools2.DEDUPE, RV = TextTools2.REVERSE, NU = TextTools2.NUMBER, TR = TextTools2.TRIM, JO = TextTools2.JOIN;
        // ---- tables ----
        t("15 tools", TextTools2.TOOL_COUNT == 15 && TextTools2.TOOL_NAMES.length == 15);
        int total = 0; boolean seen[] = new boolean[15];
        for (int[] g : TextTools2.GROUP_TOOLS) for (int v : g) { total++; seen[v] = true; }
        boolean all = true; for (boolean b : seen) all &= b;
        t("groups cover every tool once", total == 15 && all && TextTools2.GROUP_NAMES.length == TextTools2.GROUP_TOOLS.length);
        t("groupOf / firstOf", TextTools2.groupOf(TextTools2.JSON_MIN) == 3 && TextTools2.groupOf(TextTools2.TO_BN) == 1 && TextTools2.groupOf(99) == 0
                && TextTools2.firstOf(2) == TextTools2.URL_ENC && TextTools2.firstOf(-1) == AZ && TextTools2.firstOf(9) == AZ);
        t("join tables same size", TextTools2.JOIN_SEPS.length == TextTools2.JOIN_NAMES.length);
        // ---- empty / null / unknown ----
        for (int tool = 0; tool < 15; tool++) {
            R r = TextTools2.apply(tool, "", 0);
            t("empty ok tool " + tool, r.ok() && r.out.isEmpty());
            R n = TextTools2.apply(tool, null, 0);
            t("null ok tool " + tool, n.ok() && n.out.isEmpty());
        }
        t("unknown tool = error", err(99, "abc") && err(-1, "abc"));
        // ---- sort ----
        eq("sort az", a(AZ, "pear\napple\nFig"), "apple\nFig\npear");
        eq("sort za", a(ZA, "pear\napple\nFig"), "pear\nFig\napple");
        eq("sort one line", a(AZ, "solo"), "solo");
        eq("sort crlf keeps crlf", a(AZ, "b\r\na\r\n"), "a\r\nb\r\n");
        eq("sort keeps trailing newline", a(AZ, "b\na\n"), "a\nb\n");
        eq("sort no trailing newline", a(AZ, "b\na"), "a\nb");
        eq("sort case tie stable (a before A)", a(AZ, "b\nA\na"), "A\na\nb");
        eq("sort bangla", a(AZ, "\u0996\n\u0995"), "\u0995\n\u0996");
        eq("sort emoji line", a(AZ, "\uD83D\uDE00\na"), "a\n\uD83D\uDE00");
        // ---- dedupe ----
        eq("dedupe", a(DD, "a\nb\na\nc\nb"), "a\nb\nc");
        eq("dedupe keeps blank lines (only the 2nd 'a' goes)", a(DD, "a\n\nb\n\na"), "a\n\nb\n");
        eq("dedupe case sensitive", a(DD, "a\nA\na"), "a\nA");
        eq("dedupe crlf", a(DD, "x\r\nx\r\ny"), "x\r\ny");
        // ---- reverse ----
        eq("reverse", a(RV, "1\n2\n3"), "3\n2\n1");
        eq("reverse one line", a(RV, "only"), "only");
        eq("reverse trailing newline", a(RV, "1\n2\n"), "2\n1\n");
        // ---- number ----
        eq("number", a(NU, "a\nb\nc"), "1. a\n2. b\n3. c");
        eq("number skips blank lines", a(NU, "a\n\nb"), "1. a\n\n2. b");
        eq("number bangla", a(NU, "\u0995\n\u0996"), "1. \u0995\n2. \u0996");
        eq("number 12 lines last", a(NU, "a\nb\nc\nd\ne\nf\ng\nh\ni\nj\nk\nl").split("\n")[11], "12. l");
        // ---- trim ----
        eq("trim lines", a(TR, "  a  \n\tb\t\n c"), "a\nb\nc");
        eq("trim nbsp + ideographic space", a(TR, "\u00A0a\u3000\n b"), "a\nb");
        eq("trim keeps inner spaces", a(TR, "  a  b  "), "a  b");
        eq("trim blank-only line becomes empty", a(TR, "a\n   \nb"), "a\n\nb");
        // ---- join ----
        eq("join space", TextTools2.apply(JO, "a\nb\nc", 0).out, "a b c");
        eq("join comma", TextTools2.apply(JO, "a\nb\nc", 1).out, "a, b, c");
        eq("join semicolon", TextTools2.apply(JO, "a\nb", 2).out, "a; b");
        eq("join none", TextTools2.apply(JO, "a\nb", 3).out, "ab");
        eq("join skips blank lines", TextTools2.apply(JO, "a\n\nb\n", 0).out, "a b");
        eq("join bad sep index = space", TextTools2.apply(JO, "a\nb", 77).out, "a b");
        eq("join crlf", TextTools2.apply(JO, "a\r\nb", 1).out, "a, b");
        eq("join one line", TextTools2.apply(JO, "solo", 1).out, "solo");
        // ---- huge ----
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < TextTools2.MAX_LINES_IN + 5; i++) big.append('a');
        t("lines input cap", err(AZ, big.toString()) && err(JO, big.toString()));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 5000; i++) many.append(i % 50).append('\n');
        t("5000 lines dedupe -> 50", a(DD, many.toString()).split("\n").length == 50);
        // ---- digits ----
        eq("to english", a(TextTools2.TO_EN, "\u09E7\u09E8\u09E9 abc \u09E6\u09EF"), "123 abc 09");
        eq("to bangla", a(TextTools2.TO_BN, "2026-11-13 abc"), "\u09E8\u09E6\u09E8\u09EC-\u09E7\u09E7-\u09E7\u09E9 abc");
        eq("digits roundtrip", a(TextTools2.TO_EN, a(TextTools2.TO_BN, "0123456789")), "0123456789");
        eq("digits leave letters / emoji", a(TextTools2.TO_BN, "a\uD83D\uDE001"), "a\uD83D\uDE00\u09E7");
        eq("to english leaves latin digits", a(TextTools2.TO_EN, "42"), "42");
        // ---- url ----
        eq("url enc", a(TextTools2.URL_ENC, "a b&c=d/e"), "a%20b%26c%3Dd%2Fe");
        eq("url enc keeps unreserved", a(TextTools2.URL_ENC, "AZaz09-_.~"), "AZaz09-_.~");
        eq("url enc bangla", a(TextTools2.URL_ENC, "\u0995"), "%E0%A6%95");
        eq("url enc emoji", a(TextTools2.URL_ENC, "\uD83D\uDE00"), "%F0%9F%98%80");
        eq("url dec", a(TextTools2.URL_DEC, "a%20b%26c"), "a b&c");
        eq("url dec plus = space", a(TextTools2.URL_DEC, "a+b"), "a b");
        eq("url dec lowercase hex", a(TextTools2.URL_DEC, "%e0%a6%95"), "\u0995");
        eq("url dec emoji", a(TextTools2.URL_DEC, "%F0%9F%98%80"), "\uD83D\uDE00");
        eq("url dec leaves plain unicode", a(TextTools2.URL_DEC, "\u0995%20x"), "\u0995 x");
        eq("url roundtrip", a(TextTools2.URL_DEC, a(TextTools2.URL_ENC, "a b+c \u0995 \uD83D\uDE00 100%")), "a b+c \u0995 \uD83D\uDE00 100%");
        t("url dec bad: lone %", err(TextTools2.URL_DEC, "abc%"));
        t("url dec bad: %4", err(TextTools2.URL_DEC, "abc%4"));
        t("url dec bad: %ZZ", err(TextTools2.URL_DEC, "%ZZ"));
        t("url dec bad: not utf-8 (%FF)", err(TextTools2.URL_DEC, "%FF"));
        t("url dec bad: cut utf-8 (%E0%A6)", err(TextTools2.URL_DEC, "%E0%A6"));
        t("url error leaves out empty", TextTools2.apply(TextTools2.URL_DEC, "%", 0).out.isEmpty());
        // ---- base64 ----
        eq("b64 enc", a(TextTools2.B64_ENC, "hello"), "aGVsbG8=");
        eq("b64 enc 1 byte", a(TextTools2.B64_ENC, "a"), "YQ==");
        eq("b64 enc 2 bytes", a(TextTools2.B64_ENC, "ab"), "YWI=");
        eq("b64 enc 3 bytes", a(TextTools2.B64_ENC, "abc"), "YWJj");
        eq("b64 enc bangla", a(TextTools2.B64_ENC, "\u0995"), "4KaV");
        eq("b64 dec", a(TextTools2.B64_DEC, "aGVsbG8="), "hello");
        eq("b64 dec no padding", a(TextTools2.B64_DEC, "aGVsbG8"), "hello");
        eq("b64 dec with whitespace / newlines", a(TextTools2.B64_DEC, "aGVs\nbG8= "), "hello");
        eq("b64 dec url-safe chars", a(TextTools2.B64_DEC, "4KaV"), "\u0995");
        eq("b64 roundtrip emoji + bangla", a(TextTools2.B64_DEC, a(TextTools2.B64_ENC, "\u0995\u0996 \uD83D\uDE00 x")), "\u0995\u0996 \uD83D\uDE00 x");
        t("b64 dec bad char", err(TextTools2.B64_DEC, "a$b="));
        t("b64 dec bad length (5)", err(TextTools2.B64_DEC, "aGVsb"));
        t("b64 dec only padding", err(TextTools2.B64_DEC, "===="));
        t("b64 dec too much padding", err(TextTools2.B64_DEC, "a==="));
        t("b64 dec padding in the middle", err(TextTools2.B64_DEC, "aG=VsbG8="));
        t("b64 dec not utf-8 (/w==)", err(TextTools2.B64_DEC, "/w=="));
        StringBuilder enc = new StringBuilder();
        for (int i = 0; i < TextTools2.MAX_ENC_IN + 1; i++) enc.append('a');
        t("encode input cap", err(TextTools2.URL_ENC, enc.toString()) && err(TextTools2.B64_ENC, enc.toString()) && err(TextTools2.B64_DEC, enc.toString()));
        // ---- json ----
        eq("json minify", a(TextTools2.JSON_MIN, "{ \"a\" : 1 ,\n \"b\" : [ 1 , 2 , true , null ] }"), "{\"a\":1,\"b\":[1,2,true,null]}");
        eq("json pretty", a(TextTools2.JSON_PRETTY, "{\"a\":1,\"b\":[1,2]}"), "{\n  \"a\": 1,\n  \"b\": [\n    1,\n    2\n  ]\n}");
        eq("json pretty empty obj / arr", a(TextTools2.JSON_PRETTY, "{\"a\":{},\"b\":[]}"), "{\n  \"a\": {},\n  \"b\": []\n}");
        eq("json scalar", a(TextTools2.JSON_PRETTY, " 42 "), "42");
        eq("json string with spaces inside kept", a(TextTools2.JSON_MIN, "{ \"k\" : \"a  b\" }"), "{\"k\":\"a  b\"}");
        eq("json escapes kept", a(TextTools2.JSON_MIN, "\"a\\n\\u0995\\\"\""), "\"a\\n\\u0995\\\"\"");
        eq("json bangla kept", a(TextTools2.JSON_MIN, "{\"n\": \"\u0995\u0996\"}"), "{\"n\":\"\u0995\u0996\"}");
        eq("json numbers", a(TextTools2.JSON_MIN, "[-1, 0, 1.5, 2e10, -3.2E-4, 10]"), "[-1,0,1.5,2e10,-3.2E-4,10]");
        eq("json pretty then minify roundtrip", a(TextTools2.JSON_MIN, a(TextTools2.JSON_PRETTY, "{\"a\":[1,{\"b\":null}],\"c\":\"x\"}")), "{\"a\":[1,{\"b\":null}],\"c\":\"x\"}");
        eq("json nested pretty", a(TextTools2.JSON_PRETTY, "[[1]]"), "[\n  [\n    1\n  ]\n]");
        t("json bad: trailing comma obj", err(TextTools2.JSON_PRETTY, "{\"a\":1,}"));
        t("json bad: trailing comma arr", err(TextTools2.JSON_MIN, "[1,]"));
        t("json bad: single quotes", err(TextTools2.JSON_MIN, "{'a':1}"));
        t("json bad: missing colon", err(TextTools2.JSON_MIN, "{\"a\" 1}"));
        t("json bad: unquoted key", err(TextTools2.JSON_MIN, "{a:1}"));
        t("json bad: cut off", err(TextTools2.JSON_MIN, "{\"a\":[1,2"));
        t("json bad: leading zero", err(TextTools2.JSON_MIN, "[01]"));
        t("json bad: lone minus", err(TextTools2.JSON_MIN, "[-]"));
        t("json bad: 1. no digits", err(TextTools2.JSON_MIN, "[1.]"));
        t("json bad: exponent no digits", err(TextTools2.JSON_MIN, "[1e]"));
        t("json bad: bad escape", err(TextTools2.JSON_MIN, "\"\\x\""));
        t("json bad: short \\u", err(TextTools2.JSON_MIN, "\"\\u12\""));
        t("json bad: raw newline in string", err(TextTools2.JSON_MIN, "\"a\nb\""));
        t("json bad: extra text after value", err(TextTools2.JSON_MIN, "{} x"));
        t("json bad: plain text", err(TextTools2.JSON_PRETTY, "hello world"));
        t("json bad: truee", err(TextTools2.JSON_MIN, "truee"));
        t("json error has no output", TextTools2.apply(TextTools2.JSON_MIN, "{", 0).out.isEmpty());
        t("json error mentions a character number", TextTools2.apply(TextTools2.JSON_MIN, "[1,]", 0).err.contains("character"));
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 150; i++) deep.append('[');
        for (int i = 0; i < 150; i++) deep.append(']');
        t("json depth cap", err(TextTools2.JSON_MIN, deep.toString()));
        StringBuilder ok100 = new StringBuilder();
        for (int i = 0; i < 90; i++) ok100.append('[');
        for (int i = 0; i < 90; i++) ok100.append(']');
        t("json depth 90 ok", !err(TextTools2.JSON_MIN, ok100.toString()));
        StringBuilder hj = new StringBuilder("[");
        for (int i = 0; i < 11000; i++) hj.append("1,");
        hj.append("1]");
        t("json input cap (20000)", err(TextTools2.JSON_MIN, hj.toString()));
        t("json at the cap is allowed", !err(TextTools2.JSON_MIN, "[" + "1,".repeat(9990) + "1]"));

        System.out.println("TextTools2Test: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
