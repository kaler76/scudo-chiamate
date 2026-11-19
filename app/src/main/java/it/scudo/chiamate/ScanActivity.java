package it.scudo.chiamate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class ScanActivity extends Activity {

    private LinearLayout results;
    private boolean showAll = false;
    private List<AppScanner.Result> last;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);
        Ui.header(this, col, "Cerca app spia",
                "Controllo permessi, servizi speciali, app nascoste e provenienza. Le app di sistema"
                        + " Samsung e Google sono escluse. Tocca un'app per aprirne le impostazioni.");
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        col.addView(results);
        runScan();
    }

    private void runScan() {
        results.removeAllViews();
        TextView t = Ui.text(this, results, "Scansione in corso…", 16, Ui.ACCENT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 40), 0, Ui.dp(this, 40));
        new Thread(() -> {
            List<AppScanner.Result> r = AppScanner.scan(getApplicationContext());
            runOnUiThread(() -> { last = r; render(); logScan(); });
        }).start();
    }

    private int count(String level) {
        int n = 0;
        for (AppScanner.Result r : last) if (level.equals(r.level())) n++;
        return n;
    }

    private void render() {
        if (last == null) return;
        results.removeAllViews();
        int high = count("ALTO"), mid = count("MEDIO"), low = count("BASSO");

        LinearLayout stats = Ui.hRow(this, results);
        Ui.stat(this, stats, String.valueOf(high), "rischio alto", high > 0 ? Ui.RED : Ui.SUB);
        Ui.stat(this, stats, String.valueOf(mid), "rischio medio", mid > 0 ? Ui.ORANGE : Ui.SUB);
        Ui.stat(this, stats, String.valueOf(low), "da controllare", Ui.SUB);

        if (high == 0 && mid == 0) {
            LinearLayout ok = Ui.card(this, results, Ui.GREEN);
            TextView t = Ui.text(this, ok, "Nessuna app sospetta", 17, Ui.GREEN);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            Ui.text(this, ok, "Non ho trovato app con i segnali tipici delle app spia.", 14, Ui.SUB);
        }

        for (AppScanner.Result r : last) {
            String lvl = r.level();
            if (!showAll && "BASSO".equals(lvl)) continue;
            int color = "ALTO".equals(lvl) ? Ui.RED : ("MEDIO".equals(lvl) ? Ui.ORANGE : Ui.SUB);
            LinearLayout card = Ui.card(this, results, color);
            Ui.pill(this, card, "RISCHIO " + lvl + " · " + r.score + " PT", color);
            TextView name = Ui.text(this, card, r.label, 18, Ui.TEXT);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            Ui.text(this, card, r.pkg, 12, Ui.SUB);
            StringBuilder sb = new StringBuilder();
            for (String why : r.reasons) sb.append("•  ").append(why).append('\n');
            TextView reasons = Ui.text(this, card, sb.toString().trim(), 14, Ui.TEXT);
            reasons.setAlpha(0.85f);
            reasons.setPadding(0, Ui.dp(this, 6), 0, 0);
            ((LinearLayout) card.getParent()).setOnClickListener(v ->
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + r.pkg))));
        }

        if (low > 0) {
            Ui.button(this, results, showAll ? "Nascondi rischio basso" : "Mostra anche rischio basso (" + low + ")",
                    v -> { showAll = !showAll; render(); });
        }
        Ui.primary(this, results, "Ripeti scansione", Ui.ACCENT, v -> runScan());
    }

    private void logScan() {
        int high = count("ALTO"), mid = count("MEDIO");
        EventLog.add(this, high > 0 ? EventLog.ALERT : (mid > 0 ? EventLog.WARN : EventLog.INFO),
                "Scansione app: " + high + " a rischio alto, " + mid + " a rischio medio");
    }
}
