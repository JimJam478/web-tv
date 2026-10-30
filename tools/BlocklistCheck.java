package app.webtv;

import java.io.FileInputStream;

/** Checks Blocklist against the real asset. Run from repo root: javac -d out/check app/src/app/webtv/Blocklist.java tools/BlocklistCheck.java && java -cp out/check app.webtv.BlocklistCheck */
class BlocklistCheck {
    public static void main(String[] a) throws Exception {
        Blocklist b = new Blocklist(new FileInputStream("app/assets/hosts.txt"));
        check(b.blocks("doubleclick.net"), "listed host");
        check(b.blocks("ads.Doubleclick.net"), "subdomain of listed host, any case");
        check(b.blocks("pagead2.googlesyndication.com"), "ad host");
        check(b.blocks("telemetry.001-studio.com"), "HaGeZi-only host");
        check(!b.blocks("en.wikipedia.org"), "wikipedia allowed");
        check(!b.blocks("upload.wikimedia.org"), "wikipedia images allowed");
        check(!b.blocks("com"), "bare TLD allowed");
        check(!b.blocks(null), "no host allowed");
        System.out.println("OK, " + b.size() + " hosts");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
