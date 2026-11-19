package it.scudo.chiamate;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WhatsAppActivity extends Activity {

    private LinearLayout auto, guided;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);
        Ui.header(this, col, "WhatsApp",
                "Nessuna app può entrare dentro WhatsApp: Android isola ogni app. Qui controllo tutto quello che"
                        + " gli sta intorno e ti guido nei due controlli che puoi fare solo tu.");

        Ui.sectionLabel(this, col, "Controlli automatici");
        auto = new LinearLayout(this);
        auto.setOrientation(LinearLayout.VERTICAL);
        col.addView(auto);

        Ui.sectionLabel(this, col, "Controlli da fare in WhatsApp");
        guided = new LinearLayout(this);
        guided.setOrientation(LinearLayout.VERTICAL);
        col.addView(guided);

        Ui.sectionLabel(this, col, "Chiamate WhatsApp");
        LinearLayout calls = Ui.card(this, col, Ui.ACCENT);
        Ui.text(this, calls, "Con la protezione attiva, Scudo Chiamate controlla anche le chiamate WhatsApp e delle"
                + " altre app via internet: se durante la chiamata un'altra app usa il microfono, ti avvisa.",
                14, Ui.TEXT).setAlpha(0.85f);
        Ui.text(this, calls, CallMonitorService.isEnabled(this) ? "Protezione attiva ✓" : "Protezione spenta: attivala dalla schermata principale.",
                13, CallMonitorService.isEnabled(this) ? Ui.GREEN : Ui.ORANGE);
    }

    @Override protected void onResume() {
        super.onResume();
        renderAuto();
        renderGuided();
    }

    private void renderAuto() {
        auto.removeAllViews();
        if (!WhatsAppCheck.installed(this)) {
            Ui.text(this, auto, "WhatsApp non è installato su questo telefono.", 14, Ui.SUB);
        }
        new Thread(() -> {
            List<DeviceAudit.Check> checks;
            try {
                checks = WhatsAppCheck.run(getApplicationContext());
                Results.saveWhatsApp(getApplicationContext(), checks, guidedDone() == 2);
            } catch (Exception e) {
                runOnUiThread(() -> Ui.showError(this, auto, e));
                return;
            }
            runOnUiThread(() -> {
                for (int want = 2; want >= 0; want--) {
                    for (DeviceAudit.Check k : checks) {
                        if (k.state != want) continue;
                        LinearLayout t = Ui.checkRow(this, auto, k.state, k.title, k.detail);
                        if (k.state > 0 && k.fix != null) {
                            Intent fix = k.fix;
                            Ui.link(this, t, "Risolvi ›", k.state == 2 ? Ui.RED : Ui.ORANGE, v -> {
                                try { startActivity(fix); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                            });
                        }
                    }
                }
            });
        }).start();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("whatsapp", MODE_PRIVATE);
    }

    private int guidedDone() {
        int n = 0;
        if (prefs().getLong("linked", 0) > 0) n++;
        if (prefs().getLong("twostep", 0) > 0) n++;
        return n;
    }

    private void renderGuided() {
        guided.removeAllViews();
        step("linked", "1. Dispositivi collegati",
                "È il modo più usato per spiare WhatsApp: qualcuno prende il tuo telefono per un minuto e collega"
                        + " WhatsApp Web al suo computer. Da lì legge tutte le chat in tempo reale.\n\n"
                        + "In WhatsApp tocca ⋮ in alto a destra › Dispositivi collegati. Se c'è un computer, un"
                        + " browser o un tablet che non riconosci, toccalo e scegli Disconnetti.");
        step("twostep", "2. Verifica in due passaggi",
                "Impedisce a qualcuno di prendersi il tuo account con il codice SMS (per esempio con una SIM"
                        + " duplicata).\n\nIn WhatsApp: ⋮ › Impostazioni › Account › Verifica in due passaggi ›"
                        + " Attiva, e scegli un PIN di 6 cifre che non usi altrove.");
    }

    private void step(String key, String title, String body) {
        long done = prefs().getLong(key, 0);
        LinearLayout card = Ui.card(this, guided, done > 0 ? Ui.GREEN : Ui.ORANGE);
        Ui.pill(this, card, done > 0 ? "FATTO IL " + new SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(new Date(done))
                : "DA FARE", done > 0 ? Ui.GREEN : Ui.ORANGE);
        TextView t = Ui.text(this, card, title, 17, Ui.TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        Ui.text(this, card, body, 14, Ui.TEXT).setAlpha(0.85f);
        Ui.link(this, card, "Apri WhatsApp ›", Ui.ACCENT, v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(WhatsAppCheck.WA);
            if (i == null) i = getPackageManager().getLaunchIntentForPackage(WhatsAppCheck.WA_BUSINESS);
            if (i != null) startActivity(i);
        });
        Ui.link(this, card, done > 0 ? "Segna come da rifare" : "Segna come fatto ✓", done > 0 ? Ui.SUB : Ui.GREEN, v -> {
            prefs().edit().putLong(key, done > 0 ? 0 : System.currentTimeMillis()).apply();
            renderGuided();
            renderAuto();
        });
    }
}
