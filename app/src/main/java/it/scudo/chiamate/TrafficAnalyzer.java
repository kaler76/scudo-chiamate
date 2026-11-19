package it.scudo.chiamate;

import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cerca app che inviano dati in background senza essere usate:
 * il comportamento tipico di uno spyware che carica registrazioni, foto e messaggi.
 */
final class TrafficAnalyzer {
    static final int DAYS = 7;
    private static final long MB = 1024L * 1024L;

    static final class AppTraffic {
        String pkg, label, why;
        long bgTx, bgRx, fgTx, fgRx, fgTimeMs;
        boolean fromStore;
        int level;
    }

    private TrafficAnalyzer() {}

    @SuppressWarnings("deprecation")
    static boolean hasAccess(Context ctx) {
        AppOpsManager aom = ctx.getSystemService(AppOpsManager.class);
        int mode = Build.VERSION.SDK_INT >= 29
                ? aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName())
                : aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static List<AppTraffic> analyze(Context ctx) {
        long end = System.currentTimeMillis();
        long start = end - DAYS * 24L * 3600_000L;
        Map<Integer, long[]> byUid = new HashMap<>(); // bgTx, bgRx, fgTx, fgRx
        NetworkStatsManager nsm = ctx.getSystemService(NetworkStatsManager.class);
        for (int type : new int[]{ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE}) {
            NetworkStats st = null;
            try {
                st = nsm.querySummary(type, null, start, end);
                NetworkStats.Bucket b = new NetworkStats.Bucket();
                while (st.hasNextBucket()) {
                    st.getNextBucket(b);
                    long[] v = byUid.get(b.getUid());
                    if (v == null) { v = new long[4]; byUid.put(b.getUid(), v); }
                    boolean fg = b.getState() == NetworkStats.Bucket.STATE_FOREGROUND;
                    v[fg ? 2 : 0] += b.getTxBytes();
                    v[fg ? 3 : 1] += b.getRxBytes();
                }
            } catch (Exception ignored) {
                // rete non disponibile o permesso mancante: si prosegue con quello che c'è
            } finally {
                if (st != null) st.close();
            }
        }

        Map<String, UsageStats> usage = new HashMap<>();
        try {
            UsageStatsManager usm = ctx.getSystemService(UsageStatsManager.class);
            Map<String, UsageStats> m = usm.queryAndAggregateUsageStats(start, end);
            if (m != null) usage.putAll(m);
        } catch (Exception ignored) {
        }

        PackageManager pm = ctx.getPackageManager();
        List<AppTraffic> out = new ArrayList<>();
        for (Map.Entry<Integer, long[]> e : byUid.entrySet()) {
            int uid = e.getKey();
            if (uid < Process.FIRST_APPLICATION_UID || uid == Process.myUid()) continue;
            String[] pkgs = pm.getPackagesForUid(uid);
            if (pkgs == null || pkgs.length == 0) continue;
            String pkg = pkgs[0];
            ApplicationInfo ai;
            try {
                ai = pm.getApplicationInfo(pkg, 0);
            } catch (PackageManager.NameNotFoundException ex) {
                continue;
            }
            if ((ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;

            AppTraffic t = new AppTraffic();
            t.pkg = pkg;
            t.label = String.valueOf(ai.loadLabel(pm));
            long[] v = e.getValue();
            t.bgTx = v[0]; t.bgRx = v[1]; t.fgTx = v[2]; t.fgRx = v[3];
            UsageStats us = usage.get(pkg);
            t.fgTimeMs = us != null ? us.getTotalTimeInForeground() : 0;
            String inst = AppScanner.installerOf(pm, pkg);
            t.fromStore = "com.android.vending".equals(inst) || "com.sec.android.app.samsungapps".equals(inst);

            long minutes = t.fgTimeMs / 60_000;
            if (t.bgTx >= 10 * MB && minutes < 2) {
                t.level = t.fromStore ? 1 : 2;
                t.why = "Ha inviato " + fmt(t.bgTx) + " in background ma l'hai aperta meno di 2 minuti in "
                        + DAYS + " giorni";
            } else if (t.bgTx >= 50 * MB && !t.fromStore) {
                t.level = 2;
                t.why = "Invia molti dati in background e non viene da uno store";
            } else if (t.bgTx >= 50 * MB) {
                t.level = 1;
                t.why = "Invia molti dati in background (normale per backup e cloud)";
            } else if (t.bgTx >= 5 * MB && !t.fromStore && minutes < 2) {
                t.level = 1;
                t.why = "Invia dati in background senza essere usata";
            } else {
                t.level = 0;
                t.why = "Uso in background normale";
            }
            if (t.bgTx + t.fgTx > 0) out.add(t);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.bgTx, a.bgTx));
        return out.size() > 15 ? new ArrayList<>(out.subList(0, 15)) : out;
    }

    static String fmt(long bytes) {
        if (bytes >= 1024L * MB) return String.format(Locale.ITALY, "%.1f GB", bytes / (1024.0 * MB));
        if (bytes >= MB) return String.format(Locale.ITALY, "%.1f MB", bytes / (double) MB);
        return String.format(Locale.ITALY, "%d KB", bytes / 1024);
    }
}
