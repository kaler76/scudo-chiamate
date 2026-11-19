package it.scudo.chiamate;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.util.List;

public class LogActivity extends Activity {

    private LinearLayout list;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);
        Ui.title(this, col, "Registro");
        Ui.text(this, col, "Cosa è successo durante le chiamate controllate e le scansioni."
                + " Le voci più recenti sono in alto.", 14, Ui.GREY);
        Ui.button(this, col, "Svuota registro", v -> { EventLog.clear(this); render(); });
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        list.removeAllViews();
        List<String[]> rows = EventLog.read(this);
        if (rows.isEmpty()) {
            Ui.text(this, list, "Nessun evento registrato.", 15, Ui.GREY);
            return;
        }
        int shown = 0;
        for (String[] r : rows) {
            if (shown++ >= 400) break;
            int color = EventLog.ALERT.equals(r[1]) ? Ui.RED
                    : EventLog.WARN.equals(r[1]) ? Ui.ORANGE : Ui.GREY;
            LinearLayout card = Ui.card(this, list, color);
            Ui.text(this, card, r[0] + "  ·  " + r[1], 12, color);
            Ui.text(this, card, r[2], 15, Color.BLACK);
        }
    }
}
