import com.mgb.keyboard.typing.ShaCheck;
import java.security.MessageDigest;

/** v0.10.8 (W3) ShaCheck tests (computer only). */
public class ShaCheckTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }
    static final String H = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    static final String HU = H.toUpperCase();

    public static void main(String[] a) throws Exception {
        // digest field
        t("digest sha256:", H.equals(ShaCheck.parseDigest("sha256:" + H)));
        t("digest upper -> lower", H.equals(ShaCheck.parseDigest("SHA256:" + HU)));
        t("digest bare", H.equals(ShaCheck.parseDigest(H)));
        t("digest spaces", H.equals(ShaCheck.parseDigest("  sha256: " + H + "  ")));
        t("digest md5 refused", ShaCheck.parseDigest("md5:" + H) == null);
        t("digest sha512 label refused", ShaCheck.parseDigest("sha512:" + H) == null);
        t("digest short", ShaCheck.parseDigest("sha256:abcd") == null);
        t("digest 65 chars", ShaCheck.parseDigest(H + "0") == null);
        t("digest non-hex", ShaCheck.parseDigest("sha256:" + H.substring(0, 63) + "g") == null);
        t("digest null / empty", ShaCheck.parseDigest(null) == null && ShaCheck.parseDigest("") == null);
        // notes
        t("notes table row", H.equals(ShaCheck.parseFromNotes("| SHA-256 of the APK | " + H + " |")));
        t("notes upper", H.equals(ShaCheck.parseFromNotes("SHA-256: " + HU)));
        t("notes sha256 spelling", H.equals(ShaCheck.parseFromNotes("sha256 " + H)));
        t("notes hash without label ignored", ShaCheck.parseFromNotes("v0.10.8 - NEW\n" + H + "\n") == null);
        t("notes label but no hash", ShaCheck.parseFromNotes("SHA-256 is shown on the page") == null);
        t("notes hash too far from label", ShaCheck.parseFromNotes("SHA-256" + " ".repeat(200) + H) == null);
        t("notes first label has none, second has", H.equals(ShaCheck.parseFromNotes("SHA-256 later\nSHA-256 of the APK | " + H)));
        t("notes bangla text around", H.equals(ShaCheck.parseFromNotes("বাংলা SHA-256 " + H + " শেষ")));
        t("notes null / empty", ShaCheck.parseFromNotes(null) == null && ShaCheck.parseFromNotes("") == null);
        t("notes 63 hex", ShaCheck.parseFromNotes("SHA-256 " + H.substring(1)) == null);
        // expected
        t("expected digest wins", H.equals(ShaCheck.expected("sha256:" + H, "SHA-256 " + "f".repeat(64))));
        t("expected falls back to notes", H.equals(ShaCheck.expected(null, "SHA-256 " + H)));
        t("expected bad digest -> notes", H.equals(ShaCheck.expected("junk", "SHA-256 " + H)));
        t("expected none", ShaCheck.expected(null, "plain notes") == null && ShaCheck.expected("", "") == null);
        // compare
        t("compare equal", ShaCheck.compare(H, H) == ShaCheck.OK);
        t("compare case-insensitive", ShaCheck.compare(H, HU) == ShaCheck.OK && ShaCheck.compare(HU, H) == ShaCheck.OK);
        t("compare differ", ShaCheck.compare(H, "f" + H.substring(1)) == ShaCheck.MISMATCH);
        t("compare unknown expected", ShaCheck.compare(null, H) == ShaCheck.UNKNOWN && ShaCheck.compare("", H) == ShaCheck.UNKNOWN && ShaCheck.compare("abc", H) == ShaCheck.UNKNOWN);
        t("compare unreadable actual = mismatch", ShaCheck.compare(H, null) == ShaCheck.MISMATCH && ShaCheck.compare(H, "") == ShaCheck.MISMATCH);
        t("compare one char changed", ShaCheck.compare(H, H.substring(0, 63) + "0") == ShaCheck.MISMATCH);
        // toHex vs MessageDigest
        byte[] d = MessageDigest.getInstance("SHA-256").digest("abc".getBytes("UTF-8"));
        String hx = ShaCheck.toHex(d);
        t("toHex abc", hx.equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        t("toHex length", hx.length() == 64);
        t("toHex null / empty", ShaCheck.toHex(null).isEmpty() && ShaCheck.toHex(new byte[0]).isEmpty());
        t("toHex negative bytes", ShaCheck.toHex(new byte[]{(byte) 0xff, (byte) 0x80, 0x00}).equals("ff8000"));
        t("round trip", ShaCheck.compare(hx, ShaCheck.toHex(d)) == ShaCheck.OK);
        // tamper
        byte[] d2 = d.clone(); d2[5] ^= 1;
        t("tamper = mismatch", ShaCheck.compare(hx, ShaCheck.toHex(d2)) == ShaCheck.MISMATCH);
        // messages
        t("message OK", ShaCheck.message(ShaCheck.OK).contains("matches"));
        t("message mismatch", ShaCheck.message(ShaCheck.MISMATCH).contains("nothing installed"));
        t("message unknown", ShaCheck.message(ShaCheck.UNKNOWN).contains("could not be checked"));
        System.out.println("ShaCheckTest: " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
