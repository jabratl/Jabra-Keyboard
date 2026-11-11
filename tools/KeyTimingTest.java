import com.mgb.keyboard.typing.KeyTiming;

public class KeyTimingTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + n); } }
    public static void main(String[] a) {
        KeyTiming k = new KeyTiming();
        t("empty count", k.count(0) == 0);
        t("empty avg", k.avgNanos(0) == 0);
        t("empty max", k.maxNanos(0) == 0);
        t("empty p95", k.p95Nanos(0) == 0);
        t("empty line", k.line(0).endsWith("no samples yet"));
        k.add(0, 1000000L);
        t("one count", k.count(0) == 1);
        t("one avg", k.avgNanos(0) == 1000000L);
        t("one max", k.maxNanos(0) == 1000000L);
        t("one p95", k.p95Nanos(0) == 1000000L);
        k.add(0, 3000000L);
        t("two avg", k.avgNanos(0) == 2000000L);
        t("two max", k.maxNanos(0) == 3000000L);
        t("kinds separate", k.count(1) == 0 && k.count(4) == 0);
        k.reset();
        t("reset count", k.count(0) == 0);
        t("reset avg", k.avgNanos(0) == 0);
        for (int i = 1; i <= 100; i++) k.add(1, i * 1000000L);
        t("100 count", k.count(1) == 100);
        t("100 avg", k.avgNanos(1) == 50500000L);
        t("100 p95", k.p95Nanos(1) == 95000000L);
        t("100 max", k.maxNanos(1) == 100000000L);
        for (int i = 0; i < 100; i++) k.add(1, 2000000L);   // ring overwrites everything
        t("ring count capped", k.count(1) == 100);
        t("ring avg", k.avgNanos(1) == 2000000L);
        t("ring max", k.maxNanos(1) == 2000000L);
        k.add(2, -5);
        t("negative ignored", k.count(2) == 0);
        k.add(-1, 5); k.add(99, 5);
        t("bad kind ignored", k.count(-1) == 0 && k.count(99) == 0);
        t("bad kind avg", k.avgNanos(99) == 0 && k.maxNanos(-1) == 0 && k.p95Nanos(77) == 0);
        t("bad kind line", k.line(99).isEmpty());
        t("ms 0", KeyTiming.ms(0).equals("0.0"));
        t("ms 1.2", KeyTiming.ms(1200000L).equals("1.2"));
        t("ms 12.5", KeyTiming.ms(12500000L).equals("12.5"));
        t("ms round", KeyTiming.ms(1260000L).equals("1.3"));
        k.add(3, 4000000L); k.add(3, 6000000L);
        String l = k.line(3);
        t("line name", l.startsWith("Language switch"));
        t("line avg", l.contains("avg 5.0"));
        t("line max", l.contains("max 6.0"));
        t("line n", l.contains("n=2"));
        k.reset();
        t("reset all", k.count(1) == 0 && k.count(3) == 0);
        t("names len", KeyTiming.NAMES.length == KeyTiming.KINDS);
        System.out.println("KeyTimingTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
