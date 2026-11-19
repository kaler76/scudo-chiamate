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
        Ui.header(this, col, "App spia",
                "Ogni app viene confrontata con " + IocDb.get(this).size() + " indicatori di spyware noti"
                        + " (nome, certificato di firma, impronta del file) e poi valutata per permessi,"
                        + " servizi speciali, icona nascosta e provenienza.");
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        col.addView(results);
        runScan();
    }

    private void runScan() {
        results.removeAllViews();
        TextView t = Ui.text(this, results, "Avvio…", 16, Ui.ACCENT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 40), 0, Ui.dp(this, 40));
        new Thread(() -> {
            try {
                List<AppScanner.Result> r = AppScanner.scan(getApplicationContext(),
                        (d, tot) -> runOnUiThread(() -> t.setText("Analizzo le app… " + d + " / " + tot)));
                Results.saveApps(getApplicationContext(), r);
                runOnUiThread(() -> { last = r; render(); });
            } catch (Exception e) {
                runOnUiThread(() -> { results.removeAllViews(); Ui.showError(this, results, e); });
            }
        }).start();
    }

    private int count(String level) {
        int n = 0;
        for (AppScanner.Result r : last) if (level.equals(r.level())) n++;
        return n;
    }

    private void render() {
        results.removeAllViews();
        int crit = count("CRITICO"), high = count("ALTO"), mid = count("MEDIO"), low = count("BASSO");

        LinearLayout stats = Ui.hRow(this, results);
        Ui.stat(this, stats, String.valueOf(crit), "spyware noti", crit > 0 ? Ui.RED : Ui.SUB);
        Ui.stat(this, stats, String.valueOf(high), "rischio alto", high > 0 ? Ui.RED : Ui.SUB);
        Ui.stat(this, stats, String.valueOf(mid), "rischio medio", mid > 0 ? Ui.ORANGE : Ui.SUB);

        if (crit + high + mid == 0) {
            LinearLayout ok = Ui.card(this, results, Ui.GREEN);
            TextView t = Ui.text(this, ok, "Nessuna app spia trovata", 17, Ui.GREEN);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            Ui.text(this, ok, "Nessuna app corrisponde al database e nessuna ha i segnali tipici delle app spia.",
                    14, Ui.SUB);
        }

        for (AppScanner.Result r : last) {
            String lvl = r.level();
            if (!showAll && "BASSO".equals(lvl)) continue;
            int color = "CRITICO".equals(lvl) || "ALTO".equals(lvl) ? Ui.RED
                    : "MEDIO".equals(lvl) ? Ui.ORANGE : Ui.SUB;
            LinearLayout card = Ui.card(this, results, color);
            Ui.pill(this, card, "CRITICO".equals(lvl) ? "SPYWARE NOTO" : "RISCHIO " + lvl + " · " + r.score + " PT", color);
            TextView name = Ui.text(this, card, r.label, 18, Ui.TEXT);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            Ui.text(this, card, r.pkg, 12, Ui.SUB);
            StringBuilder sb = new StringBuilder();
            for (String why : r.reasons) sb.append("•  ").append(why).append('\n');
            TextView reasons = Ui.text(this, card, sb.toString().trim(), 14, Ui.TEXT);
            reasons.setAlpha(0.85f);
            reasons.setPadding(0, Ui.dp(this, 6), 0, 0);
            if ("CRITICO".equals(lvl)) {
                Ui.text(this, card, "Prima di disinstallarla fai degli screenshot: se pensi di denunciare servono"
                        + " come prova. Poi rivolgiti alla Polizia Postale.", 13, Ui.ORANGE);
            }
            Ui.link(this, card, "Apri impostazioni app ›", Ui.ACCENT, v ->
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + r.pkg))));
        }

        if (low > 0) {
            Ui.button(this, results, showAll ? "Nascondi rischio basso" : "Mostra anche rischio basso (" + low + ")",
                    v -> { showAll = !showAll; render(); });
        }
        Ui.primary(this, results, "Ripeti scansione", Ui.ACCENT, v -> runScan());
    }
}
