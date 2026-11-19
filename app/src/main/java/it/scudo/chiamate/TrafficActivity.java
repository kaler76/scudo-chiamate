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

public class TrafficActivity extends Activity {

    private LinearLayout body;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);
        Ui.header(this, col, "Traffico dati",
                "Uno spyware deve spedire da qualche parte registrazioni, foto e messaggi. Qui vedi quali app"
                        + " hanno inviato dati in background negli ultimi " + TrafficAnalyzer.DAYS
                        + " giorni, e quanto le hai usate davvero.");
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        col.addView(body);
    }

    @Override protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        body.removeAllViews();
        if (!TrafficAnalyzer.hasAccess(this)) {
            LinearLayout c = Ui.card(this, body, Ui.ORANGE);
            TextView t = Ui.text(this, c, "Serve un permesso speciale", 17, Ui.TEXT);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            Ui.text(this, c, "Nella schermata che si apre cerca \"Scudo Chiamate\" e attiva \"Consenti accesso"
                    + " all'utilizzo\". Poi torna qui.", 14, Ui.SUB);
            Ui.primary(this, c, "Apri impostazioni", Ui.ACCENT,
                    v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
            return;
        }
        TextView wait = Ui.text(this, body, "Analizzo il traffico…", 16, Ui.ACCENT);
        wait.setGravity(Gravity.CENTER);
        wait.setPadding(0, Ui.dp(this, 40), 0, Ui.dp(this, 40));
        new Thread(() -> {
            try {
                List<TrafficAnalyzer.AppTraffic> list = TrafficAnalyzer.analyze(getApplicationContext());
                Results.saveTraffic(getApplicationContext(), list);
                runOnUiThread(() -> render(list));
            } catch (Exception e) {
                runOnUiThread(() -> Ui.showError(this, body, e));
            }
        }).start();
    }

    private void render(List<TrafficAnalyzer.AppTraffic> list) {
        body.removeAllViews();
        int bad = 0, warn = 0;
        long max = 1;
        for (TrafficAnalyzer.AppTraffic t : list) {
            if (t.level == 2) bad++; else if (t.level == 1) warn++;
            max = Math.max(max, t.bgTx);
        }
        LinearLayout stats = Ui.hRow(this, body);
        Ui.stat(this, stats, String.valueOf(bad), "sospette", bad > 0 ? Ui.RED : Ui.SUB);
        Ui.stat(this, stats, String.valueOf(warn), "da verificare", warn > 0 ? Ui.ORANGE : Ui.SUB);
        Ui.stat(this, stats, String.valueOf(list.size()), "app analizzate", Ui.ACCENT);

        if (list.isEmpty()) {
            Ui.text(this, body, "Nessuna app installata da te ha usato la rete in questi giorni.", 14, Ui.SUB);
        }
        Ui.sectionLabel(this, body, "Dati inviati in background");
        for (TrafficAnalyzer.AppTraffic t : list) {
            int color = t.level == 2 ? Ui.RED : t.level == 1 ? Ui.ORANGE : Ui.ACCENT;
            LinearLayout card = Ui.card(this, body, t.level == 0 ? Ui.CARD2 : color);
            TextView name = Ui.text(this, card, t.label, 16, Ui.TEXT);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            Ui.bar(this, card, (float) t.bgTx / max, color);
            long min = t.fgTimeMs / 60_000;
            Ui.text(this, card, "↑ " + TrafficAnalyzer.fmt(t.bgTx) + " inviati in background   ↓ "
                    + TrafficAnalyzer.fmt(t.bgRx) + " ricevuti\nUsata " + (min < 1 ? "meno di 1 minuto" : min + " minuti")
                    + " in " + TrafficAnalyzer.DAYS + " giorni" + (t.fromStore ? " · da store" : " · NON da store"),
                    12, Ui.SUB);
            if (t.level > 0) Ui.text(this, card, t.why, 13, color);
            Ui.link(this, card, "Apri impostazioni app ›", Ui.ACCENT, v ->
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + t.pkg))));
        }
        Ui.primary(this, body, "Aggiorna", Ui.ACCENT, v -> load());
    }
}
