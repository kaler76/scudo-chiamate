package it.scudo.chiamate;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Archivio delle chiamate controllate: una voce per chiamata, con tutto quello che è successo. */
final class CallStore {
    static final String IN = "in", OUT = "out", MISSED = "persa", REJECTED = "rifiutata", BLOCKED = "bloccata",
            UNKNOWN = "?";
    private static final String FILE = "chiamate.jsonl";
    private static final int MAX = 1500;

    static final class Call {
        long start, end, durationSec;
        boolean voip;
        String dir = UNKNOWN;
        String number;          // null = nascosto o non disponibile
        String network;         // es. "4G (VoLTE)"
        boolean monitored = true; // false = presa dal registro di Android, protezione non attiva in quel momento
        final List<String> networks = new ArrayList<>();
        final List<String> cells = new ArrayList<>();
        final List<String[]> alerts = new ArrayList<>(); // {livello, testo}

        /** Segnalazioni vere (allarmi e attenzioni), escluse le note informative. */
        int warnCount() {
            int n = 0;
            for (String[] a : alerts) if (!EventLog.INFO.equals(a[0])) n++;
            return n;
        }

        boolean hasAlarm() {
            for (String[] a : alerts) if (EventLog.ALERT.equals(a[0])) return true;
            return false;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("s", start); o.put("e", end); o.put("d", durationSec); o.put("v", voip);
            o.put("dir", dir);
            if (number != null) o.put("n", number);
            if (network != null) o.put("net", network);
            o.put("nets", new JSONArray(networks));
            o.put("cells", new JSONArray(cells));
            JSONArray al = new JSONArray();
            for (String[] a : alerts) al.put(new JSONArray().put(a[0]).put(a[1]));
            o.put("al", al);
            return o;
        }

        static Call fromJson(JSONObject o) {
            Call c = new Call();
            c.start = o.optLong("s"); c.end = o.optLong("e"); c.durationSec = o.optLong("d");
            c.voip = o.optBoolean("v");
            c.dir = o.optString("dir", UNKNOWN);
            c.number = o.has("n") ? o.optString("n") : null;
            c.network = o.has("net") ? o.optString("net") : null;
            JSONArray a = o.optJSONArray("nets");
            if (a != null) for (int i = 0; i < a.length(); i++) c.networks.add(a.optString(i));
            a = o.optJSONArray("cells");
            if (a != null) for (int i = 0; i < a.length(); i++) c.cells.add(a.optString(i));
            a = o.optJSONArray("al");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONArray x = a.optJSONArray(i);
                if (x != null) c.alerts.add(new String[]{x.optString(0), x.optString(1)});
            }
            return c;
        }
    }

    private CallStore() {}

    static synchronized void save(Context ctx, Call c) {
        File f = new File(ctx.getFilesDir(), FILE);
        try (FileWriter w = new FileWriter(f, true)) {
            w.write(c.toJson().toString());
            w.write('\n');
        } catch (Exception ignored) {
        }
        if (f.length() > 1_500_000) trim(f);
    }

    /** Più recenti per prime. */
    static synchronized List<Call> read(Context ctx) {
        List<Call> out = new ArrayList<>();
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                try { out.add(Call.fromJson(new JSONObject(line))); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) {
        }
        Collections.reverse(out);
        return out;
    }

    static synchronized void clear(Context ctx) {
        //noinspection ResultOfMethodCallIgnored
        new File(ctx.getFilesDir(), FILE).delete();
    }

    private static void trim(File f) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
        } catch (Exception e) {
            return;
        }
        int from = Math.max(0, lines.size() - MAX);
        try (FileWriter w = new FileWriter(f, false)) {
            for (int i = from; i < lines.size(); i++) { w.write(lines.get(i)); w.write('\n'); }
        } catch (Exception ignored) {
        }
    }
    /** Chiamate del registro di Android unite a quelle controllate dalla protezione (allarmi, rete, celle). */
    static List<Call> all(Context ctx) {
        List<Call> mon = read(ctx);
        List<Call> out = new ArrayList<>();
        boolean[] used = new boolean[mon.size()];
        for (PhoneHistory.Entry e : PhoneHistory.read(ctx, 1500)) {
            Call match = null;
            for (int i = 0; i < mon.size(); i++) {
                Call m = mon.get(i);
                if (used[i] || m.voip || Math.abs(m.start - e.date) > 120_000) continue;
                if (m.number != null && e.number != null && !PhoneHistory.same(m.number, e.number)) continue;
                match = m; used[i] = true; break;
            }
            if (match == null) {
                match = new Call();
                match.monitored = false;
                match.start = e.date;
                match.end = e.date + e.duration * 1000;
            }
            if (match.number == null) match.number = e.number;
            match.dir = e.dir;
            match.durationSec = e.duration;
            out.add(match);
        }
        for (int i = 0; i < mon.size(); i++) if (!used[i]) out.add(mon.get(i));
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.start, a.start));
        return out;
    }

}
