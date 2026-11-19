package it.scudo.chiamate;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chiede all'operatore (codici USSD GSM standard) se le chiamate vengono deviate. */
final class ForwardChecker {
    static final int OFF = 0, ON = 1, UNKNOWN = 2, ERROR = 3;

    static final String[][] CODES = {
            {"*#21#", "Tutte le chiamate", "deviazione incondizionata"},
            {"*#61#", "Se non rispondi", "deviazione su mancata risposta"},
            {"*#62#", "Se non sei raggiungibile", "deviazione su irraggiungibile"},
            {"*#67#", "Se sei occupato", "deviazione su occupato"},
    };

    static final class Item {
        String code, label, desc, raw, number;
        int state = UNKNOWN;

        /** 0 ok, 1 da verificare, 3 critico. */
        int risk() {
            if (state != ON) return 0;
            return "*#21#".equals(code) ? 3 : 1;
        }

        String shortText() {
            if (state != ON) return "Nessuna deviazione attiva";
            String to = number != null ? " verso " + number : "";
            return "*#21#".equals(code) ? "TUTTE le chiamate deviate" + to : label + to;
        }
    }

    interface Callback {
        void progress(Item it, int index);
        void done(List<Item> items);
    }

    private ForwardChecker() {}

    static boolean canRun(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    static void run(Context ctx, Callback cb) {
        List<Item> items = new ArrayList<>();
        for (String[] c : CODES) {
            Item it = new Item();
            it.code = c[0]; it.label = c[1]; it.desc = c[2];
            items.add(it);
        }
        next(ctx.getApplicationContext(), items, 0, cb, new Handler(Looper.getMainLooper()));
    }

    @SuppressWarnings("MissingPermission")
    private static void next(Context ctx, List<Item> items, int i, Callback cb, Handler h) {
        if (i >= items.size()) { cb.done(items); return; }
        Item it = items.get(i);
        final boolean[] finished = {false};
        Runnable advance = () -> {
            if (finished[0]) return;
            finished[0] = true;
            cb.progress(it, i);
            h.postDelayed(() -> next(ctx, items, i + 1, cb, h), 1200);
        };
        h.postDelayed(() -> {
            if (!finished[0]) { it.state = ERROR; it.raw = "Nessuna risposta dall'operatore"; advance.run(); }
        }, 25_000);
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            tm.sendUssdRequest(it.code, new TelephonyManager.UssdResponseCallback() {
                @Override public void onReceiveUssdResponse(TelephonyManager t, String req, CharSequence resp) {
                    parse(it, resp == null ? "" : resp.toString());
                    advance.run();
                }

                @Override public void onReceiveUssdResponseFailed(TelephonyManager t, String req, int code) {
                    it.state = ERROR;
                    it.raw = "L'operatore non permette la verifica automatica (codice " + code + ")";
                    advance.run();
                }
            }, h);
        } catch (Exception e) {
            it.state = ERROR;
            it.raw = "Verifica non possibile: " + e.getClass().getSimpleName();
            advance.run();
        }
    }

    private static final Pattern NUMBER = Pattern.compile("\\+?\\d[\\d ]{6,}\\d");

    static void parse(Item it, String text) {
        it.raw = text.trim();
        String l = text.toLowerCase(Locale.ROOT);
        Matcher m = NUMBER.matcher(text);
        boolean neg = l.contains("non attiv") || l.contains("disattiv") || l.contains("inattiv")
                || l.contains("non è attiv") || l.contains("not active") || l.contains("deactivated")
                || l.contains("disabled") || l.contains("not forwarded") || l.contains("non deviat");
        if (m.find()) {
            it.state = ON;
            it.number = m.group().replace(" ", "");
        } else if (neg) {
            it.state = OFF;
        } else if (l.contains("attiv") || l.contains("active") || l.contains("enabled") || l.contains("forwarded")) {
            it.state = ON;
        } else {
            it.state = UNKNOWN;
        }
    }
}
