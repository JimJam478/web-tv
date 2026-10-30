package app.webtv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/** Ad and tracker hosts, one per line; a listed host blocks its subdomains too. Plain Java so it runs off-device. */
final class Blocklist {
    private final Set<String> hosts = new HashSet<>(100_000);

    Blocklist(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                if (!line.isEmpty()) hosts.add(line.toLowerCase());
            }
        }
    }

    boolean blocks(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        // ads.example.com -> example.com -> com: any listed suffix blocks it.
        for (int dot = -1; ; ) {
            if (hosts.contains(host.substring(dot + 1))) return true;
            dot = host.indexOf('.', dot + 1);
            if (dot < 0) return false;
        }
    }

    int size() {
        return hosts.size();
    }
}
