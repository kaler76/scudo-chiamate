package it.scudo.chiamate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 10;
    private TextView status;
    private Button toggle;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);

        Ui.title(this, col, "Scudo Chiamate");
        Ui.text(this, col, "Controlla se sul tuo telefono ci sono app spia e se qualcosa ascolta"
                + " o devia le tue chiamate. Tutto resta sul telefono: l'app non invia dati a nessuno.", 15, Ui.GREY);

        LinearLayout card = Ui.card(this, col, Ui.BLUE);
        status = Ui.text(this, card, "", 17, Ui.BLUE);
        toggle = Ui.button(this, card, "", v -> onToggle());

        Ui.button(this, col, "🔍  Cerca app spia", v -> startActivity(new Intent(this, ScanActivity.class)));
        Ui.button(this, col, "📋  Registro chiamate controllate", v -> startActivity(new Intent(this, LogActivity.class)));
        Ui.button(this, col, "💡  Consigli e limiti", v -> startActivity(new Intent(this, TipsActivity.class)));
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean on = CallMonitorService.isEnabled(this);
        if (on) {
            status.setText("✅  Protezione chiamate ATTIVA");
            status.setTextColor(Ui.GREEN);
            toggle.setText("Disattiva protezione");
        } else {
            status.setText("⚪  Protezione chiamate spenta");
            status.setTextColor(Ui.GREY);
            toggle.setText("Attiva protezione chiamate");
        }
    }

    private void onToggle() {
        if (CallMonitorService.isEnabled(this)) {
            CallMonitorService.stop(this);
            refresh();
            return;
        }
        List<String> need = new ArrayList<>();
        need.add(Manifest.permission.READ_PHONE_STATE);
        need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS);
        List<String> missing = new ArrayList<>();
        for (String p : need) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (missing.isEmpty()) {
            startProtection();
        } else {
            requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_PERMS) return;
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Serve il permesso \"Telefono\" per sapere quando sei in chiamata.");
            status.setTextColor(Ui.RED);
            return;
        }
        startProtection();
    }

    private void startProtection() {
        CallMonitorService.start(this);
        refresh();
    }
}
