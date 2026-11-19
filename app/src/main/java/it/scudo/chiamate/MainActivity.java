package it.scudo.chiamate;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Dashboard: punteggio complessivo, analisi completa, quattro controlli, protezione in tempo reale. */
public class MainActivity extends Activity {

    private static final int REQ_PROTECT = 10;
    private static final int REQ_FULL = 11;

    private Gauge gauge;
    private TextView verdict, lastRun, fullBtn, protTitle, protSub, protBtn;
    private LinearLayout tilesTop, tilesBottom, tilesThird;
    private boolean running = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);

        showLastCrash(col);
        TextView brand = Ui.text(this, col, "SCUDO CHIAMATE", 13, Ui.ACCENT);
        brand.setLetterSpacing(0.22f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        lastRun = Ui.text(this, col, "", 13, Ui.SUB);

        // ---------------------------------------------------------------- punteggio
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 18));
        hero.setBackground(Ui.rounded(this, Ui.CARD, 24));
        LinearLayout.LayoutParams hlp = Ui.full(this, 22);
        hlp.topMargin = Ui.dp(this, 8);
        col.addView(hero, hlp);

        gauge = new Gauge(this);
        hero.addView(gauge, new LinearLayout.LayoutParams(Ui.dp(this, 230), Ui.dp(this, 230)));
        verdict = new TextView(this);
        verdict.setTextSize(15);
        verdict.setTextColor(Ui.SUB);
        verdict.setGravity(Gravity.CENTER);
        verdict.setLineSpacing(0, 1.2f);
        verdict.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 6));
        hero.addView(verdict);
        fullBtn = Ui.primary(this, hero, "Avvia analisi completa", Ui.ACCENT, v -> startFull());

        // ---------------------------------------------------------------- controlli
        Ui.sectionLabel(this, col, "Controlli");
        tilesTop = Ui.hRow(this, col);
        tilesBottom = Ui.hRow(this, col);
        tilesThird = Ui.hRow(this, col);

        // ---------------------------------------------------------------- protezione
        Ui.sectionLabel(this, col, "Protezione durante le chiamate");
        LinearLayout prot = Ui.card(this, col, Ui.ACCENT);
        protTitle = Ui.text(this, prot, "", 17, Ui.TEXT);
        protTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        protSub = Ui.text(this, prot, "", 13, Ui.SUB);
        protBtn = Ui.primary(this, prot, "", Ui.ACCENT, v -> onToggle());

        // ---------------------------------------------------------------- altro
        Ui.sectionLabel(this, col, "Altro");
        Ui.menuRow(this, col, R.drawable.ic_bulb, Ui.ORANGE, "Consigli e limiti",
                "Cosa si può scoprire e cosa no", v -> startActivity(new Intent(this, TipsActivity.class)));

        TextView foot = Ui.text(this, col, "Database spyware: " + IocDb.get(this).size()
                + " indicatori pubblici (Amnesty, Citizen Lab, Lookout, Echap).\nTutto resta sul telefono.", 11, Ui.SUB);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(this, 12), 0, 0);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!running) refresh();
        handleTestExtras();
    }

    /** Se l'ultima volta l'app si è chiusa per un errore, ne mostra il dettaglio (da mandare allo sviluppatore). */
    private void showLastCrash(LinearLayout col) {
        java.io.File f = new java.io.File(getFilesDir(), App.CRASH_FILE);
        if (!f.exists()) return;
        String txt;
        try {
            txt = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
        } catch (Exception e) {
            return;
        }
        LinearLayout c = Ui.card(this, col, Ui.RED);
        TextView t = Ui.text(this, c, "L'app si è chiusa per un errore", 16, Ui.RED);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        Ui.text(this, c, "Fai uno screenshot di questo riquadro e mandalo: serve a correggere il problema.", 13, Ui.SUB);
        String shortTxt = txt.length() > 1500 ? txt.substring(0, 1500) : txt;
        TextView d = Ui.text(this, c, shortTxt, 10, Ui.TEXT);
        d.setTypeface(Typeface.MONOSPACE);
        d.setTextIsSelectable(true);
        Ui.link(this, c, "Copia testo", Ui.ACCENT, v -> {
            android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("errore", txt));
        });
        Ui.link(this, c, "Chiudi avviso", Ui.SUB, v -> { f.delete(); ((android.view.ViewGroup) c.getParent().getParent()).removeView((android.view.View) c.getParent()); });
    }

    /** Usato dal test automatico su telefono virtuale (adb): apre una schermata o avvia un'azione. */
    private void handleTestExtras() {
        Intent i = getIntent();
        String what = i != null ? i.getStringExtra("test") : null;
        if (what == null) return;
        i.removeExtra("test");
        switch (what) {
            case "traffico": startActivity(new Intent(this, TrafficActivity.class)); break;
            case "app": startActivity(new Intent(this, ScanActivity.class)); break;
            case "sicurezza": startActivity(new Intent(this, AuditActivity.class)); break;
            case "whatsapp": startActivity(new Intent(this, WhatsAppActivity.class)); break;
            case "deviazioni": startActivity(new Intent(this, ForwardActivity.class)); break;
            case "registro": startActivity(new Intent(this, LogActivity.class)); break;
            case "consigli": startActivity(new Intent(this, TipsActivity.class)); break;
            case "protezione": CallMonitorService.start(this); break;
            case "completa": runFull(); break;
            default: break;
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void refresh() {
        int score = Results.overall(this);
        int c = score < 0 ? Ui.SUB : score >= 80 ? Ui.GREEN : score >= 50 ? Ui.ORANGE : Ui.RED;
        String lbl = score < 0 ? "nessuna analisi" : score >= 80 ? "sicuro" : score >= 50 ? "da controllare" : "a rischio";
        gauge.set(score, c, lbl);
        verdict.setText(score < 0
                ? "Avvia l'analisi completa: controlla app spia, deviazioni, traffico e sicurezza del telefono."
                : score >= 80 ? "Nessun segnale serio trovato."
                : score >= 50 ? "Ci sono punti da verificare: tocca le schede qui sotto."
                : "Trovati segnali importanti: guarda le schede in rosso.");
        String t = Results.lastTime(this);
        lastRun.setText(t == null ? "Mai analizzato" : "Ultima analisi: " + t);

        tilesTop.removeAllViews();
        tilesBottom.removeAllViews();
        tile(tilesTop, R.drawable.ic_bug, "App spia", Results.APPS, ScanActivity.class);
        tile(tilesTop, R.drawable.ic_forward, "Deviazioni", Results.FORWARD, ForwardActivity.class);
        tile(tilesBottom, R.drawable.ic_data, "Traffico dati", Results.TRAFFIC, TrafficActivity.class);
        tile(tilesBottom, R.drawable.ic_phone, "Sicurezza telefono", Results.AUDIT, AuditActivity.class);
        tilesThird.removeAllViews();
        tile(tilesThird, R.drawable.ic_chat, "WhatsApp", Results.WHATSAPP, WhatsAppActivity.class);
        int alarms = 0;
        for (String[] r : EventLog.read(this)) if (EventLog.ALERT.equals(r[1])) alarms++;
        Ui.tile(this, tilesThird, R.drawable.ic_list, "Registro",
                alarms == 0 ? "Nessun allarme" : alarms + " allarmi registrati", alarms == 0 ? Ui.GREEN : Ui.RED,
                v -> startActivity(new Intent(this, LogActivity.class)));

        boolean on = CallMonitorService.isEnabled(this);
        protTitle.setText(on ? "Attiva" : "Spenta");
        protTitle.setTextColor(on ? Ui.GREEN : Ui.TEXT);
        protSub.setText(on ? "Chiamate normali e WhatsApp: chi chiama, uso del microfono, rete 2G, cambi di cella."
                : "Controlla in tempo reale microfono e rete durante le chiamate e ti dice chi sta chiamando.");
        protBtn.setText(on ? "Disattiva" : "Attiva protezione");
        protBtn.setBackground(Ui.pressable(this, on ? Ui.CARD2 : Ui.ACCENT, 14));
    }

    private void tile(LinearLayout row, int icon, String title, String module, Class<?> cls) {
        int lvl = Results.level(this, module);
        Ui.tile(this, row, icon, title, Results.summary(this, module), Results.color(lvl),
                v -> startActivity(new Intent(this, cls)));
    }

    // ---------------------------------------------------------------- analisi completa

    private void startFull() {
        if (running) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_FULL);
            return;
        }
        runFull();
    }

    private void runFull() {
        running = true;
        fullBtn.setText("Analisi app…");
        fullBtn.setEnabled(false);
        new Thread(() -> {
            List<AppScanner.Result> apps = AppScanner.scan(getApplicationContext(),
                    (d, t) -> runOnUiThread(() -> fullBtn.setText("Analisi app " + (100 * d / Math.max(1, t)) + "%")));
            Results.saveApps(this, apps);
            runOnUiThread(() -> fullBtn.setText("Sicurezza del telefono…"));
            Results.saveAudit(this, DeviceAudit.run(getApplicationContext()));
            runOnUiThread(() -> fullBtn.setText("WhatsApp…"));
            Results.saveWhatsApp(this, WhatsAppCheck.run(getApplicationContext()),
                    getSharedPreferences("whatsapp", MODE_PRIVATE).getLong("linked", 0) > 0
                            && getSharedPreferences("whatsapp", MODE_PRIVATE).getLong("twostep", 0) > 0);
            if (TrafficAnalyzer.hasAccess(this)) {
                runOnUiThread(() -> fullBtn.setText("Traffico dati…"));
                Results.saveTraffic(this, TrafficAnalyzer.analyze(getApplicationContext()));
            } else {
                Results.save(this, Results.TRAFFIC, -1, "Tocca per dare il permesso");
            }
            runOnUiThread(() -> {
                if (ForwardChecker.canRun(this)) {
                    fullBtn.setText("Chiedo all'operatore le deviazioni…");
                    ForwardChecker.run(this, new ForwardChecker.Callback() {
                        @Override public void progress(ForwardChecker.Item it, int i) {
                            fullBtn.setText("Deviazioni " + (i + 1) + "/" + ForwardChecker.CODES.length + "…");
                        }

                        @Override public void done(List<ForwardChecker.Item> items) {
                            Results.saveForward(MainActivity.this, items);
                            finishFull();
                        }
                    });
                } else {
                    finishFull();
                }
            });
        }).start();
    }

    private void finishFull() {
        running = false;
        fullBtn.setEnabled(true);
        fullBtn.setText("Ripeti analisi completa");
        refresh();
    }

    // ---------------------------------------------------------------- protezione

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
        for (String p : need) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (missing.isEmpty()) startProtection();
        else requestPermissions(missing.toArray(new String[0]), REQ_PROTECT);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_FULL) {
            runFull(); // senza permesso telefono salta solo il controllo deviazioni
            return;
        }
        if (code != REQ_PROTECT) return;
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            protSub.setText("Serve il permesso \"Telefono\" per sapere quando sei in chiamata.");
            protSub.setTextColor(Ui.RED);
            return;
        }
        startProtection();
    }

    private void startProtection() {
        CallMonitorService.start(this);
        refresh();
    }
}
