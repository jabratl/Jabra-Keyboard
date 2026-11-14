import com.mgb.keyboard.gif.GifPlan;
import java.util.Arrays;
import java.util.List;

/** v0.18.5: checks for GifPlan (key / query cleaning, request url, https rule, file name, cache overflow, error text). */
public class GifPlanTest {
    static int fails = 0, checks = 0;
    static void eq(String what, Object got, Object want) {
        checks++;
        boolean ok = (got == null) ? want == null : got.equals(want);
        if (!ok) { fails++; System.out.println("FAIL " + what + ": got <" + got + "> want <" + want + ">"); }
    }
    static void yes(String what, boolean b) { eq(what, b, true); }

    public static void main(String[] a) {
        String k = "abcDEF1234567890_-xyz";
        eq("key ok", GifPlan.cleanKey(k), k);
        eq("key trimmed", GifPlan.cleanKey("  " + k + "\n"), k);
        eq("key quoted", GifPlan.cleanKey("\"" + k + "\""), k);
        eq("key null", GifPlan.cleanKey(null), "");
        eq("key short", GifPlan.cleanKey("abc123"), "");
        eq("key has space", GifPlan.cleanKey("abcdefgh ijklmnopqrst"), "");
        eq("key has slash", GifPlan.cleanKey("abcdefghijklmnop/qrst"), "");
        eq("key sentence", GifPlan.cleanKey("Here is my key: abcdefghijklmnopqrstuvwxyz"), "");
        StringBuilder big = new StringBuilder(); for (int i = 0; i < 129; i++) big.append('a');
        eq("key too long", GifPlan.cleanKey(big.toString()), "");

        eq("query trim", GifPlan.cleanQuery("  funny   cat  "), "funny cat");
        eq("query null", GifPlan.cleanQuery(null), "");
        StringBuilder lq = new StringBuilder(); for (int i = 0; i < 100; i++) lq.append('a');
        eq("query cut", GifPlan.cleanQuery(lq.toString()).length(), 60);

        eq("url no key", GifPlan.searchUrl("x", "cat", "id", 1), null);
        String s = GifPlan.searchUrl(k, "funny cat", "id-1", 1);
        eq("url search", s, "https://api.klipy.com/api/v1/" + k + "/gifs/search?customer_id=id-1&page=1&per_page=24&q=funny+cat");
        String t = GifPlan.searchUrl(k, "   ", "id-1", 0);
        eq("url trending + page min 1", t, "https://api.klipy.com/api/v1/" + k + "/gifs/trending?customer_id=id-1&page=1&per_page=24");
        yes("url anon id", GifPlan.searchUrl(k, "", null, 2).contains("customer_id=anon&page=2"));
        yes("url encodes bangla", GifPlan.searchUrl(k, "\u09AC\u09BE", "i", 1).contains("&q=%E0%A6%AC%E0%A6%BE"));
        yes("url encodes &", !GifPlan.searchUrl(k, "a&b=c", "i", 1).contains("a&b=c"));

        yes("https ok", GifPlan.isHttps("https://static.klipy.com/a.gif?x=1"));
        yes("http no", !GifPlan.isHttps("http://static.klipy.com/a.gif"));
        yes("null no", !GifPlan.isHttps(null));
        yes("space no", !GifPlan.isHttps("https://static.klipy.com/a b.gif"));
        yes("file no", !GifPlan.isHttps("file:///etc/passwd"));
        yes("js no", !GifPlan.isHttps("javascript:alert(1)"));

        eq("name plain", GifPlan.fileName("Funny-Cat_01"), "funny-cat_01.gif");
        eq("name unsafe", GifPlan.fileName("../../etc/passwd"), "etcpasswd.gif");
        eq("name empty", GifPlan.fileName(""), "gif.gif");
        eq("name null", GifPlan.fileName(null), "gif.gif");
        eq("name cut", GifPlan.fileName("a123456789a123456789a123456789a123456789zzzz").length(), 44);

        List<String> l = Arrays.asList("1", "2", "3", "4");
        eq("overflow 2", GifPlan.overflow(l, 2), Arrays.asList("3", "4"));
        eq("overflow big", GifPlan.overflow(l, 9).size(), 0);
        eq("overflow 0", GifPlan.overflow(l, 0).size(), 4);
        eq("overflow null", GifPlan.overflow(null, 3).size(), 0);

        yes("err none", GifPlan.errorText(0).startsWith("No internet"));
        yes("err 401", GifPlan.errorText(401).startsWith("Key not accepted"));
        yes("err 429", GifPlan.errorText(429).startsWith("Too many"));
        yes("err 404", GifPlan.errorText(404).contains("404"));
        yes("err 500", GifPlan.errorText(500).contains("500"));

        eq("order preview first", GifPlan.PREVIEW_ORDER[0], "xs");
        eq("order send first", GifPlan.SEND_ORDER[0], "md");

        System.out.println(fails == 0 ? "GifPlanTest OK (" + checks + " checks)" : "GifPlanTest FAILED " + fails + " of " + checks);
        if (fails > 0) System.exit(1);
    }
}
