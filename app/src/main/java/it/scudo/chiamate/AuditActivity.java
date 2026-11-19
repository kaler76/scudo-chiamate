package it.scudo.chiamate;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;

import java.util.List;

public class AuditActivity extends Activity {

    private LinearLayout body;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);
        Ui.header(this, col, "Sicurezza telefono",
                "Le impostazioni che permettono di spiare un telefono anche senza un'app spia evidente:"
                        + " certificati e proxy per leggere il traffico, debug USB, root, servizi speciali.");
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
        new Thread(() -> {
            try {
                List<DeviceAudit.Check> checks = DeviceAudit.run(getApplicationContext());
                Results.saveAudit(getApplicationContext(), checks);
                runOnUiThread(() -> render(checks));
            } catch (Exception e) {
                runOnUiThread(() -> Ui.showError(this, body, e));
            }
        }).start();
    }

    private void render(List<DeviceAudit.Check> checks) {
        body.removeAllViews();
        int score = DeviceAudit.score(checks);
        int c = score >= 80 ? Ui.GREEN : score >= 50 ? Ui.ORANGE : Ui.RED;
        LinearLayout g = new LinearLayout(this);
        g.setGravity(Gravity.CENTER_HORIZONTAL);
        g.setBackground(Ui.rounded(this, Ui.CARD, 24));
        Gauge gauge = new Gauge(this);
        g.addView(gauge, new LinearLayout.LayoutParams(Ui.dp(this, 190), Ui.dp(this, 190)));
        body.addView(g, Ui.full(this, 16));
        gauge.set(score, c, score >= 80 ? "buona" : score >= 50 ? "migliorabile" : "debole");

        // prima i problemi, poi gli avvisi, poi ciò che è in ordine
        for (int want = 2; want >= 0; want--) {
            for (DeviceAudit.Check k : checks) {
                if (k.state != want) continue;
                LinearLayout texts = Ui.checkRow(this, body, k.state, k.title, k.detail);
                if (k.state > 0 && k.fix != null) {
                    Intent fix = k.fix;
                    Ui.link(this, texts, "Risolvi ›", k.state == 2 ? Ui.RED : Ui.ORANGE, v -> {
                        try { startActivity(fix); } catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                    });
                }
            }
        }
    }
}
