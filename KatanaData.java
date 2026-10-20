package com.smp.katana;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Live state of one katana. Cached by serial; written to the item by KatanaManager. */
public final class KatanaData {

    public final KatanaType type;
    public final UUID serial;
    public int level = 1;
    public int xp = 0;
    public int mobKills = 0;
    public int playerKills = 0;
    public KatanaPath path;
    public final Map<String, Integer> progress = new HashMap<>();
    public final Set<String> done = new LinkedHashSet<>();
    public boolean dirty = false;

    public KatanaData(KatanaType type, UUID serial) {
        this.type = type;
        this.serial = serial;
        this.path = type.defaultPath;
    }

    public int progressOf(KatanaTask t) {
        return progress.getOrDefault(t.name(), 0);
    }

    public int doneIn(KatanaPath p) {
        int n = 0;
        for (KatanaTask t : KatanaTask.forPath(p)) {
            if (done.contains(t.name())) n++;
        }
        return n;
    }

    public String encodeProgress() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : progress.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    public void decodeProgress(String s) {
        progress.clear();
        if (s == null || s.isEmpty()) return;
        for (String part : s.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            try {
                progress.put(kv[0], Integer.parseInt(kv[1]));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public String encodeDone() {
        return String.join(",", done);
    }

    public void decodeDone(String s) {
        done.clear();
        if (s == null || s.isEmpty()) return;
        done.addAll(Arrays.asList(s.split(",")));
    }
}
