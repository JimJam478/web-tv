package app.webtv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Downloads the ad lists into a folder as hosts.txt (one ad/tracker host per line) and hide.css (EasyList's
 * site-independent hide rules). Plain Java: the app runs it weekly, ./update-blocklist.sh runs it for the bundled copy.
 */
final class ListUpdater {
    private static final String STEVENBLACK = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    private static final String HAGEZI =
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/pro-onlydomains.txt";
    private static final String EASYLIST = "https://easylist.to/easylist/easylist.txt";
    // Adblock-only syntax isn't CSS; braces could smuggle in styles other than hiding.
    private static final Pattern NOT_CSS =
            Pattern.compile(":-abp-|:has-text|:xpath|:matches|:upward|:remove|:style|:contains|[{}]");
    // A download far below today's sizes (275k hosts, 13k rules) is broken: keep the old lists instead.
    private static final int MIN_HOSTS = 50_000, MIN_RULES = 1_000;

    /** Replaces dir/hosts.txt and dir/hide.css, or throws and leaves them as they were. */
    static String update(File dir) throws IOException {
        Set<String> hosts = new TreeSet<>();
        try (BufferedReader r = download(STEVENBLACK)) {
            for (String line; (line = r.readLine()) != null; ) {
                String[] f = line.trim().split("\\s+");
                if (f.length > 1 && f[0].equals("0.0.0.0") && !f[1].equals("0.0.0.0")) hosts.add(f[1].toLowerCase());
            }
        }
        try (BufferedReader r = download(HAGEZI)) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) hosts.add(line.toLowerCase());
            }
        }
        List<String> rules = new ArrayList<>();
        try (BufferedReader r = download(EASYLIST)) {
            for (String line; (line = r.readLine()) != null; ) {
                // One rule per selector: an unsupported selector then only drops itself, not a whole group.
                if (line.startsWith("##") && !NOT_CSS.matcher(line).find()) {
                    rules.add(line.substring(2) + "{display:none!important}");
                }
            }
        }
        if (hosts.size() < MIN_HOSTS || rules.size() < MIN_RULES) {
            throw new IOException("lists too small: " + hosts.size() + " hosts, " + rules.size() + " rules");
        }
        write(new File(dir, "hosts.txt"), hosts);
        write(new File(dir, "hide.css"), rules);
        return hosts.size() + " hosts, " + rules.size() + " hide rules";
    }

    /** Read line by line as it arrives: whole lists in memory would cost ~40 MB on the TV. */
    private static BufferedReader download(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        if (c.getResponseCode() != 200) {
            c.disconnect();
            throw new IOException(url + ": HTTP " + c.getResponseCode());
        }
        return new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
    }

    /** Written beside the target then renamed over it, so a reader never sees half a file. */
    private static void write(File target, Iterable<String> lines) throws IOException {
        File tmp = new File(target.getPath() + ".new");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8")) {
            for (String line : lines) w.write(line + "\n");
        }
        if (!tmp.renameTo(target)) throw new IOException("can't replace " + target);
    }

    public static void main(String[] a) throws IOException {
        System.out.println(update(new File(a[0])) + " in " + a[0]);
    }
}
