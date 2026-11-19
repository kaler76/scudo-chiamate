package it.scudo.chiamate;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Ultimi risultati di ogni modulo, per la dashboard. Livelli: -1 mai fatto, 0 ok, 1 attenzione, 2 rischio, 3 critico. */
final class Results {
    static final String APPS = "apps";
    static final String FORWARD = "forward";
    static final String TRAFFIC = "traffic";
    static final String AUDIT = "audit";

    private Results() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("risultati", Context.MODE_PRIVATE);
    }

    static void save(Context c, String module, int level, String summary) {
        p(c).edit()
                .putInt(module + "_lvl", level)
                .putString(module + "_txt", summary)
                .putLong(module + "_time", System.currentTimeMillis())
                .apply();
    }

    static int level(Context c, String module) { return p(c).getInt(module + "_lvl", -1); }
    static String summary(Context c, String module) { return p(c).getString(module + "_txt", "Non ancora controllato"); }

    static void saveAuditScore(Context c, int score) { p(c).edit().putInt("audit_score", score).apply(); }

    static String lastTime(Context c) {
        long t = 0;
        for (String m : new String[]{APPS, FORWARD, TRAFFIC, AUDIT}) t = Math.max(t, p(c).getLong(m + "_time", 0));
        if (t == 0) return null;
        return new SimpleDateFormat("dd/MM 'alle' HH:mm", Locale.ITALY).format(new Date(t));
    }

    /** Punteggio complessivo 0-100, oppure -1 se non è mai stata fatta un'analisi. */
    static int overall(Context c) {
        boolean any = false;
        int score = 100;
        int audit = p(c).getInt("audit_score", -1);
        if (level(c, AUDIT) >= 0 && audit >= 0) { score = audit; any = true; }
        int apps = level(c, APPS);
        if (apps >= 0) any = true;
        if (apps == 3) score = Math.min(score, 10);
        else if (apps == 2) score -= 35;
        else if (apps == 1) score -= 10;
        int fwd = level(c, FORWARD);
        if (fwd >= 0) any = true;
        if (fwd >= 3) score -= 30;
        else if (fwd == 1) score -= 5;
        int tr = level(c, TRAFFIC);
        if (tr >= 0) any = true;
        if (tr >= 2) score -= 20;
        else if (tr == 1) score -= 8;
        return any ? Math.max(0, Math.min(100, score)) : -1;
    }

    static int color(int level) {
        switch (level) {
            case 0: return Ui.GREEN;
            case 1: return Ui.ORANGE;
            case 2:
            case 3: return Ui.RED;
            default: return Ui.SUB;
        }
    }

    // ---------------------------------------------------------------- sintesi per modulo

    static void saveApps(Context c, List<AppScanner.Result> rs) {
        int crit = 0, high = 0, mid = 0;
        String critName = null;
        for (AppScanner.Result r : rs) {
            switch (r.level()) {
                case "CRITICO": crit++; if (critName == null) critName = r.label; break;
                case "ALTO": high++; break;
                case "MEDIO": mid++; break;
                default: break;
            }
        }
        if (crit > 0) save(c, APPS, 3, "SPYWARE NOTO: " + critName);
        else if (high > 0) save(c, APPS, 2, high + " a rischio alto, " + mid + " medio");
        else if (mid > 0) save(c, APPS, 1, mid + " a rischio medio");
        else save(c, APPS, 0, "Nessuna app spia trovata");
        EventLog.add(c, crit > 0 ? EventLog.ALERT : high > 0 ? EventLog.ALERT : mid > 0 ? EventLog.WARN : EventLog.INFO,
                "Scansione app: " + crit + " spyware noti, " + high + " a rischio alto, " + mid + " medio");
    }

    static void saveForward(Context c, List<ForwardChecker.Item> items) {
        int worst = 0, answered = 0;
        String txt = "Nessuna deviazione attiva";
        for (ForwardChecker.Item it : items) {
            if (it.state != ForwardChecker.ERROR && it.state != ForwardChecker.UNKNOWN) answered++;
            if (it.risk() > worst) { worst = it.risk(); txt = it.shortText(); }
        }
        if (answered == 0) { save(c, FORWARD, -1, "L'operatore non ha risposto: usa il controllo manuale"); return; }
        save(c, FORWARD, worst, txt);
        EventLog.add(c, worst >= 3 ? EventLog.ALERT : worst >= 1 ? EventLog.WARN : EventLog.INFO,
                "Controllo deviazioni: " + txt);
    }

    static void saveTraffic(Context c, List<TrafficAnalyzer.AppTraffic> list) {
        int worst = 0;
        String txt = "Nessun invio sospetto in 7 giorni";
        for (TrafficAnalyzer.AppTraffic t : list) {
            if (t.level > worst) { worst = t.level; txt = t.label + ": " + TrafficAnalyzer.fmt(t.bgTx) + " inviati in background"; }
        }
        save(c, TRAFFIC, worst, txt);
        if (worst > 0) EventLog.add(c, worst >= 2 ? EventLog.ALERT : EventLog.WARN, "Traffico dati: " + txt);
    }

    static void saveAudit(Context c, List<DeviceAudit.Check> checks) {
        int score = DeviceAudit.score(checks);
        int bad = 0, warn = 0;
        for (DeviceAudit.Check k : checks) { if (k.state == 2) bad++; else if (k.state == 1) warn++; }
        saveAuditScore(c, score);
        save(c, AUDIT, bad > 0 ? 2 : warn > 0 ? 1 : 0,
                score + "/100 · " + (bad + warn == 0 ? "tutto in ordine" : bad + " problemi, " + warn + " avvisi"));
    }
}
