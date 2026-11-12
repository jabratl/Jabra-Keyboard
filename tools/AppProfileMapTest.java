import com.mgb.keyboard.typing.AppProfileMap;
import java.util.*;

/** v0.10.18 (W6.1) tests for the app -> profile map. */
public class AppProfileMapTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        AppProfileMap e = AppProfileMap.empty();
        t("empty size 0", e.size() == 0 && e.isEmpty() && e.other().isEmpty() && e.packages().isEmpty());
        t("empty lookup ''", e.lookup("com.x").isEmpty() && e.lookup(null).isEmpty());
        t("empty json", e.toJson().equals("{\"ver\":1,\"map\":{},\"other\":\"\"}"));

        // validPackage
        t("pkg ok", AppProfileMap.validPackage("com.whatsapp") && AppProfileMap.validPackage("com.google.android.keep") && AppProfileMap.validPackage("a") && AppProfileMap.validPackage("My_App.v2"));
        t("pkg null / empty", !AppProfileMap.validPackage(null) && !AppProfileMap.validPackage(""));
        t("pkg leading / trailing dot", !AppProfileMap.validPackage(".a") && !AppProfileMap.validPackage("a."));
        t("pkg double dot", !AppProfileMap.validPackage("a..b"));
        t("pkg bad chars", !AppProfileMap.validPackage("a b") && !AppProfileMap.validPackage("a/b") && !AppProfileMap.validPackage("a\"b") && !AppProfileMap.validPackage("a-b") && !AppProfileMap.validPackage("\u09AC\u09BE"));
        StringBuilder lg = new StringBuilder(); for (int i = 0; i < 150; i++) lg.append('a');
        t("pkg 150 ok, 151 not", AppProfileMap.validPackage(lg.toString()) && !AppProfileMap.validPackage(lg.toString() + "a"));

        // put / lookup
        AppProfileMap m = e.put("com.whatsapp", "Compact");
        t("put ok", m != null && m.size() == 1 && m.lookup("com.whatsapp").equals("Compact"));
        t("original untouched (immutable)", e.size() == 0);
        t("profileOf", "Compact".equals(m.profileOf("com.whatsapp")) && m.profileOf("com.x") == null && m.profileOf(null) == null);
        t("put replaces", m.put("com.whatsapp", "Large").lookup("com.whatsapp").equals("Large") && m.put("com.whatsapp", "Large").size() == 1);
        t("put bad package", e.put("bad pkg", "X") == null && e.put(null, "X") == null && e.put("", "X") == null);
        t("put empty name", e.put("com.a", "") == null && e.put("com.a", "   ") == null && e.put("com.a", null) == null);
        t("put name cleaned", e.put("com.a", "  My   Writing  ").lookup("com.a").equals("My Writing"));
        t("put name cut to 24", e.put("com.a", "123456789012345678901234567890").lookup("com.a").length() == 24);
        t("unknown app lookup ''", m.lookup("com.other").isEmpty());
        AppProfileMap m2 = m.put("com.google.android.keep", "Novel Writing");
        t("two apps in order", m2.packages().equals(Arrays.asList("com.whatsapp", "com.google.android.keep")));

        // other
        AppProfileMap o = m2.withOther("Normal");
        t("other set", o.other().equals("Normal") && o.size() == 2);
        t("other used for unknown app", o.lookup("com.unknown").equals("Normal"));
        t("own mapping beats other", o.lookup("com.whatsapp").equals("Compact"));
        t("lookup null with other", o.lookup(null).equals("Normal"));
        t("other cleared", o.withOther("").other().isEmpty() && o.withOther(null).other().isEmpty());
        t("isEmpty false with only other", !AppProfileMap.empty().withOther("X").isEmpty());

        // remove
        AppProfileMap r = m2.remove("com.whatsapp");
        t("remove one", r.size() == 1 && r.profileOf("com.whatsapp") == null && r.profileOf("com.google.android.keep") != null);
        t("remove unknown = same", m2.remove("com.nope").size() == 2 && m2.remove(null).size() == 2);
        t("remove keeps other", o.remove("com.whatsapp").other().equals("Normal"));

        // limit 50
        AppProfileMap big = AppProfileMap.empty();
        for (int i = 0; i < 50; i++) big = big.put("com.app" + i, "P");
        t("50 apps ok", big != null && big.size() == 50);
        t("51st refused", big.put("com.app50", "P") == null);
        t("replace at 50 ok", big.put("com.app7", "Q") != null && big.put("com.app7", "Q").size() == 50);
        t("remove then add at 50", big.remove("com.app0").put("com.app50", "P") != null);

        // json round trip
        AppProfileMap rt = AppProfileMap.parse(o.toJson());
        t("round trip packages", rt.packages().equals(o.packages()));
        t("round trip values", rt.lookup("com.whatsapp").equals("Compact") && rt.lookup("com.google.android.keep").equals("Novel Writing") && rt.other().equals("Normal"));
        t("round trip json stable", rt.toJson().equals(o.toJson()));
        AppProfileMap q = AppProfileMap.empty().put("com.a", "He said \"hi\" \\ x").withOther("Tab\there");
        AppProfileMap q2 = AppProfileMap.parse(q.toJson());
        t("round trip quotes / backslash", q2.lookup("com.a").equals(q.lookup("com.a")));
        AppProfileMap bn = AppProfileMap.empty().put("com.a", "\u09B2\u09C7\u0996\u09BE");
        t("round trip Bangla name", AppProfileMap.parse(bn.toJson()).lookup("com.a").equals("\u09B2\u09C7\u0996\u09BE"));
        t("round trip 50", AppProfileMap.parse(big.toJson()).size() == 50);

        // junk
        String[] junk = { null, "", " ", "{", "}", "[]", "null", "123", "\"x\"", "{\"ver\":1", "{\"ver\":2,\"map\":{\"com.a\":\"P\"}}", "{\"map\":{\"com.a\":\"P\"}}",
            "{\"ver\":\"1\",\"map\":{}}", "{\"ver\":1,\"map\":[]}", "{\"ver\":1,\"map\":5}", "{\"ver\":1,\"map\":{\"com.a\":}}", "{\"ver\":1,\"map\":{\"com.a\":\"P\"}} trailing",
            "not json at all", "{{{{{{{{{{", "{\"ver\":1,\"map\":{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":1}}}}}}" };
        boolean allEmpty = true, noThrow = true;
        for (String j : junk) { try { AppProfileMap x = AppProfileMap.parse(j); if (x == null || x.size() != 0) allEmpty = false; } catch (Throwable ex) { noThrow = false; } }
        t("junk never throws", noThrow);
        t("junk -> empty map", allEmpty);
        AppProfileMap part = AppProfileMap.parse("{\"ver\":1,\"map\":{\"bad pkg\":\"P\",\"com.ok\":\"Good\",\"com.num\":5,\"com.empty\":\"\",\"com.null\":null},\"other\":7}");
        t("bad entries skipped, good kept", part.size() == 1 && part.lookup("com.ok").equals("Good"));
        t("other of wrong type ignored", part.other().isEmpty());
        t("missing other = ''", AppProfileMap.parse("{\"ver\":1,\"map\":{\"com.a\":\"P\"}}").other().isEmpty());
        t("missing map = empty but valid", AppProfileMap.parse("{\"ver\":1,\"other\":\"X\"}").other().equals("X"));
        StringBuilder many = new StringBuilder("{\"ver\":1,\"map\":{");
        for (int i = 0; i < 80; i++) { if (i > 0) many.append(','); many.append("\"com.a").append(i).append("\":\"P\""); }
        many.append("}}");
        t("80 apps in file -> cut to 50", AppProfileMap.parse(many.toString()).size() == 50);
        StringBuilder huge = new StringBuilder("{\"ver\":1,\"map\":{}, \"x\":\""); for (int i = 0; i < 25000; i++) huge.append('a'); huge.append("\"}");
        t("text over 20000 chars -> empty", AppProfileMap.parse(huge.toString()).size() == 0);
        t("whitespace tolerated", AppProfileMap.parse("  {\n \"ver\" : 1 ,\n \"map\" : { \"com.a\" : \"P\" } }  ").lookup("com.a").equals("P"));
        t("unicode escape", AppProfileMap.parse("{\"ver\":1,\"map\":{\"com.a\":\"\\u0041B\"}}").lookup("com.a").equals("AB"));
        t("bad unicode escape -> empty", AppProfileMap.parse("{\"ver\":1,\"map\":{\"com.a\":\"\\u00\"}}").size() == 0);
        t("dup key: last wins, one entry", AppProfileMap.parse("{\"ver\":1,\"map\":{\"com.a\":\"P\",\"com.a\":\"Q\"}}").lookup("com.a").equals("Q"));

        // dropMissing
        AppProfileMap dm = o.dropMissing(Arrays.asList("compact", "Normal"));
        t("dropMissing keeps existing (case-insens)", dm.size() == 1 && dm.lookup("com.whatsapp").equals("Compact"));
        t("dropMissing drops missing other", o.dropMissing(Arrays.asList("Compact")).other().isEmpty());
        t("dropMissing keeps other when exists", dm.other().equals("Normal"));
        t("dropMissing null list = unchanged", o.dropMissing(null).size() == 2);
        t("dropMissing empty list = empty", o.dropMissing(new ArrayList<String>()).isEmpty());
        t("dropMissing immutable", o.size() == 2);

        System.out.println("AppProfileMapTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
