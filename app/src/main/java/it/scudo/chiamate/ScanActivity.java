package it.scudo.chiamate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class ScanActivity extends Activity {

    private LinearLayout col;
    private LinearLayout results;
    private boolean showAll = false;
    private List<AppScanner.Result> last;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        col = Ui.page(this);
        Ui.title(this, col, "Cerca app spia");
        Ui.text(this, col, "Controllo permessi, servizi speciali, app nascoste e provenienza di ogni app."
                + " Un punteggio alto non significa per forza spia: tocca un'app per aprirne le"
                + " impostazioni e disinstallarla se non la riconosci.", 14, Ui.GREY);
        Ui.button(this, col, "Ripeti scansione", v -> runScan());
        Ui.button(this, col, "Mostra anche rischio basso", v -> {
            showAll = !showAll;
            ((android.widget.Button) v).setText(showAll ? "Nascondi rischio basso" : "Mostra anche rischio basso");
            render();
        });
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        col.addView(results);
        runScan();
    }

    private void runScan() {
        results.removeAllViews();
        Ui.text(this, results, "Scansione in corso…", 16, Ui.BLUE);
        new Thread(() -> {
            List<AppScanner.Result> r = AppScanner.scan(getApplicationContext());
            runOnUiThread(() -> { last = r; render(); logScan(); });
        }).start();
    }

    private void render() {
        if (last == null) return;
        results.removeAllViews();
        int high = 0, mid = 0;
        for (AppScanner.Result r : last) {
            if ("ALTO".equals(r.level())) high++;
            else if ("MEDIO".equals(r.level())) mid++;
        }
        TextView sum = Ui.text(this, results,
                high == 0 && mid == 0
                        ? "✅  Nessuna app sospetta trovata."
                        : "Rischio alto: " + high + "   ·   Rischio medio: " + mid,
                17, high > 0 ? Ui.RED : (mid > 0 ? Ui.ORANGE : Ui.GREEN));
        sum.setTypeface(Typeface.DEFAULT_BOLD);

        for (AppScanner.Result r : last) {
            String lvl = r.level();
            if (!showAll && "BASSO".equals(lvl)) continue;
            int color = "ALTO".equals(lvl) ? Ui.RED : ("MEDIO".equals(lvl) ? Ui.ORANGE : Ui.GREY);
            LinearLayout card = Ui.card(this, results, color);
            TextView name = Ui.text(this, card, r.label, 17, android.graphics.Color.BLACK);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            Ui.text(this, card, r.pkg, 12, Ui.GREY);
            Ui.text(this, card, "Rischio " + lvl + " (" + r.score + " punti)", 15, color);
            StringBuilder sb = new StringBuilder();
            for (String why : r.reasons) sb.append("• ").append(why).append('\n');
            Ui.text(this, card, sb.toString().trim(), 14, android.graphics.Color.DKGRAY);
            card.setOnClickListener(v -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + r.pkg));
                startActivity(i);
            });
        }
    }

    private void logScan() {
        int high = 0, mid = 0;
        for (AppScanner.Result r : last) {
            if ("ALTO".equals(r.level())) high++;
            else if ("MEDIO".equals(r.level())) mid++;
        }
        EventLog.add(this, high > 0 ? EventLog.ALERT : (mid > 0 ? EventLog.WARN : EventLog.INFO),
                "Scansione app: " + high + " a rischio alto, " + mid + " a rischio medio");
    }
}
