package it.scudo.chiamate;

import android.app.Activity;
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
        }
    }
}
