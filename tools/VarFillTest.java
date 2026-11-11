import com.mgb.keyboard.typing.VarFill;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v0.4.4 VarFill tests (computer only).
 * javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/VarFillTest.java && java -cp out VarFillTest
 */
public class VarFillTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static Map<String, String> m(String... kv) { Map<String, String> x = new HashMap<String, String>(); for (int i = 0; i + 1 < kv.length; i += 2) x.put(kv[i], kv[i + 1]); return x; }

    public static void main(String[] a) {
        List<String> f = VarFill.fields("Translate {from} to {to}. Again {from}.");
        t("fields distinct in order", f.size() == 2 && f.get(0).equals("from") && f.get(1).equals("to"));
        t("no fields", VarFill.fields("plain text").isEmpty() && VarFill.fields("").isEmpty() && VarFill.fields(null).isEmpty());
        t("hasFields", VarFill.hasFields("a {b} c") && !VarFill.hasFields("a b c"));
        t("empty braces are not a field", VarFill.fields("{}").isEmpty());
        t("json is not a field", VarFill.fields("{\"a\":1} and {\"k\": \"v\"}").isEmpty());
        t("code braces are not a field", VarFill.fields("if (x) { return 1; }").isEmpty());
        t("space inside ok", VarFill.fields("{first name}").size() == 1 && VarFill.fields("{first name}").get(0).equals("first name"));
        t("edge spaces not a field", VarFill.fields("{ x }").isEmpty() && VarFill.fields("{a  b}").isEmpty());
        t("name too long", VarFill.fields("{0123456789012345678901234567890}").isEmpty() && VarFill.fields("{012345678901234567890123456789}").size() == 1);
        t("unclosed", VarFill.fields("{abc").isEmpty() && VarFill.fields("abc}").isEmpty());
        t("nested braces", VarFill.fields("{{x}}").size() == 1 && VarFill.fields("{{x}}").get(0).equals("x"));
        t("bangla names", VarFill.fields("{চরিত্র} কে {নাম} বলো").size() == 2);
        t("bangla name with space + conjunct", VarFill.fields("{মূল নাম} {শ্রী}").size() == 2 && VarFill.fill("{শ্রী}!", m("শ্রী", "X")).equals("X!"));
        StringBuilder s = new StringBuilder(); for (int i = 0; i < 20; i++) s.append("{v").append(i).append("} ");
        t("max 12 fields", VarFill.fields(s.toString()).size() == VarFill.MAX_FIELDS);

        t("fill all", VarFill.fill("Translate {from} to {to}. Again {from}.", m("from", "EN", "to", "BN")).equals("Translate EN to BN. Again EN."));
        t("fill bangla value", VarFill.fill("নাম: {name}", m("name", "রহিম")).equals("নাম: রহিম"));
        t("missing value stays", VarFill.fill("a {x} b {y}", m("x", "1")).equals("a 1 b {y}"));
        t("empty value replaces", VarFill.fill("a {x} b", m("x", "")).equals("a  b"));
        t("value with {y} is not expanded again", VarFill.fill("{x} {y}", m("x", "{y}", "y", "Y")).equals("{y} Y"));
        t("no values = same text", VarFill.fill("a {x}", m()).equals("a {x}") && VarFill.fill("a {x}", null).equals("a {x}"));
        t("fill keeps json text", VarFill.fill("{\"a\":1} {x}", m("x", "ok")).equals("{\"a\":1} ok"));
        t("fill null text", VarFill.fill(null, m("x", "1")).equals(""));
        t("fill keeps newlines", VarFill.fill("L1 {x}\nL2 {x}", m("x", "Z")).equals("L1 Z\nL2 Z"));

        System.out.println("VarFillTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
