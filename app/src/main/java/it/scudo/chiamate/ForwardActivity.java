package it.scudo.chiamate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class ForwardActivity extends Activity {

    private static final int REQ = 20;
    private LinearLayout list;
    private TextView runBtn;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);
        Ui.header(this, col, "Deviazioni chiamate",
                "Chiedo direttamente al tuo operatore se le chiamate vengono trasferite a un altro numero."
                        + " Chi devia le tue chiamate può riceverle al posto tuo, senza che il telefono squilli.");
        runBtn = Ui.primary(this, col, "Verifica ora", Ui.ACCENT, v -> start());
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);

        Ui.sectionLabel(this, col, "Controllo manuale");
        LinearLayout man = Ui.card(this, col, Ui.ACCENT);
        Ui.text(this, man, "Se l'operatore non risponde alla verifica automatica, componi i codici a mano"
                + " dal tastierino e premi chiama. Il risultato compare sullo schermo.", 14, Ui.TEXT).setAlpha(0.85f);
        for (String[] c : ForwardChecker.CODES) {
            Ui.link(this, man, c[0] + "  ·  " + c[1] + " ›", Ui.ACCENT, v -> dial(c[0]));
        }

        Ui.sectionLabel(this, col, "Annulla tutte le deviazioni");
        LinearLayout off = Ui.card(this, col, Ui.RED);
        Ui.text(this, off, "Il codice ##002# cancella ogni deviazione attiva, compresa quella verso la segreteria."
                + " Si apre il tastierino: per eseguirlo devi premere tu il tasto di chiamata.", 14, Ui.TEXT).setAlpha(0.85f);
        Ui.link(this, off, "Componi ##002# ›", Ui.RED, v -> dial("##002#"));
    }

    private void dial(String code) {
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))));
    }

    private void start() {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ);
            return;
        }
        run();
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ && ForwardChecker.canRun(this)) run();
        else if (code == REQ) {
            list.removeAllViews();
            Ui.text(this, list, "Serve il permesso \"Telefono\" per interrogare l'operatore. Puoi usare il controllo manuale.",
                    14, Ui.ORANGE);
        }
    }

    private void run() {
        runBtn.setEnabled(false);
        runBtn.setText("Chiedo all'operatore…");
        list.removeAllViews();
        ForwardChecker.run(this, new ForwardChecker.Callback() {
            @Override public void progress(ForwardChecker.Item it, int i) {
                show(it);
                runBtn.setText("Verifica " + (i + 1) + " / " + ForwardChecker.CODES.length + "…");
            }

            @Override public void done(List<ForwardChecker.Item> items) {
                Results.saveForward(ForwardActivity.this, items);
                runBtn.setEnabled(true);
                runBtn.setText("Verifica di nuovo");
            }
        });
    }

    private void show(ForwardChecker.Item it) {
        int risk = it.risk();
        int color = it.state == ForwardChecker.ON ? (risk >= 3 ? Ui.RED : Ui.ORANGE)
                : it.state == ForwardChecker.OFF ? Ui.GREEN : Ui.SUB;
        String pill = it.state == ForwardChecker.ON ? "ATTIVA" : it.state == ForwardChecker.OFF ? "NON ATTIVA"
                : it.state == ForwardChecker.ERROR ? "NON VERIFICABILE" : "RISPOSTA NON CHIARA";
        LinearLayout card = Ui.card(this, list, color);
        Ui.pill(this, card, pill, color);
        TextView t = Ui.text(this, card, it.label, 17, Ui.TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        Ui.text(this, card, it.code + " · " + it.desc, 12, Ui.SUB);
        if (it.state == ForwardChecker.ON) {
            String to = it.number != null ? "verso " + it.number : "";
            Ui.text(this, card, risk >= 3
                    ? "TUTTE le tue chiamate vengono trasferite " + to + ". Se non l'hai impostato tu, annullala subito con ##002#."
                    : "Trasferita " + to + ". Di solito è il numero della tua segreteria: se non lo riconosci, annullala.",
                    14, color);
        }
        if (it.raw != null && !it.raw.isEmpty()) {
            TextView raw = Ui.text(this, card, "Risposta operatore: " + it.raw, 13, Ui.SUB);
            raw.setPadding(0, Ui.dp(this, 4), 0, 0);
        }
    }
}
