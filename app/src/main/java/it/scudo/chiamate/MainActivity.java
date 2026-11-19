package it.scudo.chiamate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 10;

    private FrameLayout halo;
    private ImageView shield;
    private TextView statusTitle;
    private TextView statusSub;
    private TextView toggle;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);

        TextView brand = Ui.text(this, col, "SCUDO CHIAMATE", 13, Ui.ACCENT);
        brand.setLetterSpacing(0.2f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);

        // --- Pannello di stato
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 28), Ui.dp(this, 20), Ui.dp(this, 20));
        hero.setBackground(Ui.rounded(this, Ui.CARD, 24));
        LinearLayout.LayoutParams hlp = Ui.full(this, 22);
        hlp.topMargin = Ui.dp(this, 10);
        col.addView(hero, hlp);

        halo = new FrameLayout(this);
        shield = Ui.icon(this, R.drawable.ic_shield, Ui.SUB, 64);
        halo.addView(shield, new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), Gravity.CENTER));
        LinearLayout.LayoutParams halp = new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 120));
        halp.bottomMargin = Ui.dp(this, 16);
        hero.addView(halo, halp);

        statusTitle = new TextView(this);
        statusTitle.setTextSize(22);
        statusTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        statusTitle.setGravity(Gravity.CENTER);
        hero.addView(statusTitle);

        statusSub = new TextView(this);
        statusSub.setTextSize(14);
        statusSub.setTextColor(Ui.SUB);
        statusSub.setGravity(Gravity.CENTER);
        statusSub.setLineSpacing(0, 1.2f);
        statusSub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        hero.addView(statusSub);

        toggle = Ui.primary(this, hero, "", Ui.ACCENT, v -> onToggle());

        // --- Menu
        Ui.sectionLabel(this, col, "Strumenti");
        Ui.menuRow(this, col, R.drawable.ic_search, Ui.ACCENT, "Cerca app spia",
                "Analizza le app installate", v -> startActivity(new Intent(this, ScanActivity.class)));
        Ui.menuRow(this, col, R.drawable.ic_list, Ui.GREEN, "Registro",
                "Chiamate controllate e avvisi", v -> startActivity(new Intent(this, LogActivity.class)));
        Ui.menuRow(this, col, R.drawable.ic_bulb, Ui.ORANGE, "Consigli e limiti",
                "Cosa si può scoprire e cosa no", v -> startActivity(new Intent(this, TipsActivity.class)));

        TextView foot = Ui.text(this, col, "Tutto resta sul telefono: l'app non invia dati a nessuno.", 12, Ui.SUB);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 14), 0, 0);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean on = CallMonitorService.isEnabled(this);
        int c = on ? Ui.GREEN : Ui.SUB;
        halo.setBackground(Ui.rounded(this, Ui.alpha(c, 38), 60));
        shield.setImageResource(on ? R.drawable.ic_shield_ok : R.drawable.ic_shield);
        shield.setImageTintList(android.content.res.ColorStateList.valueOf(c));
        statusTitle.setTextColor(on ? Ui.GREEN : Ui.TEXT);
        statusTitle.setText(on ? "Protezione attiva" : "Protezione spenta");
        statusSub.setText(on
                ? "Ogni chiamata viene controllata: chi chiama, microfono, rete 2G e cambi di cella."
                : "Attivala per controllare microfono e rete durante le chiamate.");
        toggle.setText(on ? "Disattiva protezione" : "Attiva protezione");
        toggle.setBackground(Ui.pressable(this, on ? Ui.CARD2 : Ui.ACCENT, 14));
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
        need.add(Manifest.permission.READ_CALL_LOG);
        need.add(Manifest.permission.READ_CONTACTS);
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
            statusSub.setText("Serve il permesso \"Telefono\" per sapere quando sei in chiamata.");
            statusSub.setTextColor(Ui.RED);
            return;
        }
        startProtection();
    }

    private void startProtection() {
        CallMonitorService.start(this);
        refresh();
    }
}
