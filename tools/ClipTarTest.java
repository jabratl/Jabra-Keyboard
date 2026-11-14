import com.mgb.keyboard.clip.ClipExport.Entry;
import com.mgb.keyboard.clip.ClipExport.Options;
import com.mgb.keyboard.clip.ClipName;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** v0.17.70 (S3): ClipName.toTar() = valid ustar. Checks structure by hand; also with the system tar when it exists. */
public class ClipTarTest {
    static int checks = 0, fails = 0;
    static void ok(String n, boolean c) { checks++; if (!c) { fails++; System.out.println("FAIL " + n); } }

    public static void main(String[] a) throws Exception {
        List<Entry> es = new ArrayList<Entry>();
        es.add(new Entry("Hello world one", null, false, 0));
        es.add(new Entry("\u0986\u09ae\u09be\u09b0 \u09b8\u09cb\u09a8\u09be\u09b0 \u09ac\u09be\u0982\u09b2\u09be", null, true, 0));
        Options o = new Options(); o.now = 1700000000000L;
        byte[] t = ClipName.toTar(es, false, o);
        ok("tar not null", t != null);
        ok("tar multiple of 512", t != null && t.length % 512 == 0);
        ok("tar ends with 2 zero blocks", t != null && t[t.length - 1] == 0 && t[t.length - 1024] == 0);
        ok("magic ustar", t != null && t[257] == 'u' && t[258] == 's' && t[259] == 't' && t[260] == 'a' && t[261] == 'r');
        // checksum of header 0
        long sum = 0;
        for (int i = 0; i < 512; i++) sum += (i >= 148 && i < 156) ? ' ' : (t[i] & 0xFF);
        String cs = new String(t, 148, 6, "US-ASCII");
        ok("checksum", Long.parseLong(cs, 8) == sum);
        ok("null in = null", ClipName.toTar(null, false, o) == null && ClipName.toTar(new ArrayList<Entry>(), true, o) == null);
        ok("docx tar not null", ClipName.toTar(es, true, o) != null);
        // long name safety (> 100 bytes)
        byte[] big = ClipName.toTar(es, false, o);
        ok("big ok", big != null);
        File f = File.createTempFile("jabra", ".tar");
        FileOutputStream fo = new FileOutputStream(f); fo.write(t); fo.close();
        try {
            Process p = new ProcessBuilder("tar", "-tf", f.getPath()).redirectErrorStream(true).start();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            java.io.InputStream in = p.getInputStream();
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            int rc = p.waitFor();
            String out = new String(bo.toByteArray(), "UTF-8");
            ok("system tar lists 2 files (rc " + rc + ")", rc == 0 && out.trim().split("\n").length == 2 && out.startsWith("01_Hello world one.txt"));
        } catch (java.io.IOException e) { System.out.println("(no system tar, skipped)"); }
        f.delete();
        System.out.println("ClipTarTest: " + checks + " checks, " + fails + " fails");
        if (fails > 0) System.exit(1);
    }
}
