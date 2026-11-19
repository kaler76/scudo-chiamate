package it.scudo.chiamate;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Legge il registro chiamate di Android: base del Registro e dello storico di ogni numero. */
final class PhoneHistory {

    static final class Entry {
        long date, duration;
        String number;  // null = nascosto
        String dir;     // CallStore.IN / OUT / MISSED / REJECTED / BLOCKED
    }

    static final class Stats {
        int total, missed, in, out, blocked;
        long first, last;
        final List<Entry> recent = new ArrayList<>();

        /** Una riga leggibile, es. "Ti ha chiamato 5 volte in 30 giorni (3 senza risposta) · ultima ieri 18:02". */
        String summary() {
            if (total == 0) return "Prima volta che ti chiama (negli ultimi 30 giorni)";
            StringBuilder sb = new StringBuilder("Già ").append(total).append(total == 1 ? " chiamata" : " chiamate")
                    .append(" in 30 giorni");
            if (missed > 0) sb.append(" (").append(missed).append(" senza risposta)");
            sb.append(" · ultima ").append(when(last));
            return sb.toString();
        }
    }

    private PhoneHistory() {}

    static boolean allowed(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
    }

    static List<Entry> read(Context ctx, int limit) {
        List<Entry> out = new ArrayList<>();
        if (!allowed(ctx)) return out;
        String[] cols = {CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DURATION,
                CallLog.Calls.DATE, CallLog.Calls.NUMBER_PRESENTATION};
        try (Cursor c = ctx.getContentResolver().query(CallLog.Calls.CONTENT_URI, cols, null, null,
                CallLog.Calls.DATE + " DESC")) {
            while (c != null && c.moveToNext() && out.size() < limit) {
                Entry e = new Entry();
                e.number = c.getInt(4) == CallLog.Calls.PRESENTATION_ALLOWED ? c.getString(0) : null;
                if (e.number != null && e.number.trim().isEmpty()) e.number = null;
                e.duration = c.getLong(2);
                e.date = c.getLong(3);
                e.dir = dir(c.getInt(1));
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static String dir(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE: return CallStore.IN;
            case CallLog.Calls.OUTGOING_TYPE: return CallStore.OUT;
            case CallLog.Calls.MISSED_TYPE: return CallStore.MISSED;
            case CallLog.Calls.REJECTED_TYPE: return CallStore.REJECTED;
            case CallLog.Calls.BLOCKED_TYPE: return CallStore.BLOCKED;
            default: return CallStore.UNKNOWN;
        }
    }

    /** Ultime 9 cifre: confronta "+39 347..." con "347..." senza badare al prefisso. */
    static String key(String number) {
        if (number == null) return null;
        String d = number.replaceAll("[^0-9]", "");
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    static boolean same(String a, String b) {
        String ka = key(a), kb = key(b);
        return ka != null && kb != null && !ka.isEmpty() && ka.equals(kb);
    }

    /** Storico degli ultimi 30 giorni con un numero (escludendo la chiamata in corso, se excludeAfter > 0). */
    static Stats stats(Context ctx, String number, long excludeAfter) {
        Stats s = new Stats();
        long from = System.currentTimeMillis() - 30L * 86_400_000L;
        for (Entry e : read(ctx, 3000)) {
            if (e.date < from) break;
            if (!same(e.number, number)) continue;
            if (excludeAfter > 0 && e.date >= excludeAfter) continue;
            if (CallStore.OUT.equals(e.dir)) { s.out++; }
            else {
                s.total++;
                if (CallStore.IN.equals(e.dir)) s.in++;
                else if (CallStore.BLOCKED.equals(e.dir)) s.blocked++;
                else s.missed++;
            }
            if (s.last == 0) s.last = e.date;
            s.first = e.date;
            if (s.recent.size() < 15) s.recent.add(e);
        }
        return s;
    }

    static String when(long t) {
        long now = System.currentTimeMillis();
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ITALY);
        java.util.Calendar a = java.util.Calendar.getInstance(), b = java.util.Calendar.getInstance();
        a.setTimeInMillis(now); b.setTimeInMillis(t);
        int dd = a.get(java.util.Calendar.DAY_OF_YEAR) - b.get(java.util.Calendar.DAY_OF_YEAR)
                + 365 * (a.get(java.util.Calendar.YEAR) - b.get(java.util.Calendar.YEAR));
        if (dd == 0) return "oggi alle " + hm.format(new Date(t));
        if (dd == 1) return "ieri alle " + hm.format(new Date(t));
        return new SimpleDateFormat("d MMM 'alle' HH:mm", Locale.ITALY).format(new Date(t));
    }
}
