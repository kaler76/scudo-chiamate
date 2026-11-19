package it.scudo.chiamate;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class LogActivity extends Activity {

    private LinearLayout list;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);
        Ui.header(this, col, "Registro", "Chiamate controllate, avvisi e scansioni. I più recenti in alto.");
        Ui.primary(this, col, "Esporta rapporto PDF", Ui.ACCENT, v -> exportReport(this, (android.widget.TextView) v));
        Ui.text(this, col, "Un documento con chiamate, allarmi e controlli, da portare alle forze dell'ordine o all'avvocato."
                + " Viene salvato nella cartella Download.", 12, Ui.SUB);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        Ui.button(this, col, "Svuota registro", v -> { EventLog.clear(this); render(); });
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        list.removeAllViews();
        List<String[]> rows = EventLog.read(this);
        if (rows.isEmpty()) {
            TextView t = Ui.text(this, list, "Nessun evento registrato.\nAttiva la protezione e fai una chiamata di prova.",
                    15, Ui.SUB);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 40), 0, Ui.dp(this, 40));
            return;
        }
        int shown = 0;
        for (String[] r : rows) {
            if (shown++ >= 400) break;
            boolean alert = EventLog.ALERT.equals(r[1]);
            boolean warn = EventLog.WARN.equals(r[1]);
            int color = alert ? Ui.RED : warn ? Ui.ORANGE : Ui.ACCENT;
            LinearLayout card = Ui.card(this, list, color);
            TextView meta = Ui.text(this, card, r[0] + "   " + (alert ? "ALLARME" : warn ? "ATTENZIONE" : "INFO"), 12, color);
            meta.setTypeface(Typeface.DEFAULT_BOLD);
            Ui.text(this, card, r[2], 15, Ui.TEXT);
            if (r[3] != null) {
                String num = r[3];
                Ui.link(this, card, "Chi è questo numero? ›", Ui.ACCENT, v ->
                        startActivity(new Intent(this, LookupActivity.class).putExtra(LookupActivity.EXTRA_NUMBER, num)));
            }
        }
    }

    /** Crea il PDF in background e lo apre. */
    static void exportReport(Activity a, android.widget.TextView btn) {
        CharSequence label = btn.getText();
        btn.setEnabled(false);
        btn.setText("Preparo il rapporto…");
        new Thread(() -> {
            try {
                Uri uri = ReportPdf.create(a.getApplicationContext());
                a.runOnUiThread(() -> {
                    btn.setEnabled(true);
                    btn.setText(label);
                    Toast.makeText(a, "Rapporto salvato nella cartella Download", Toast.LENGTH_LONG).show();
                    if (Build.VERSION.SDK_INT >= 29) {
                        try {
                            a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                        } catch (Exception e) {
                            // nessun lettore PDF: il file resta comunque in Download
                        }
                    }
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> {
                    btn.setEnabled(true);
                    btn.setText(label);
                    Toast.makeText(a, "Rapporto non riuscito: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
