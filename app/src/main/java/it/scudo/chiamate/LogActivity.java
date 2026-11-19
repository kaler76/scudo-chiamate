package it.scudo.chiamate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registro: chiamate (una voce per chiamata, raggruppate per giorno) ed eventi/allarmi. */
public class LogActivity extends Activity {

    private static final int TAB_CALLS = 0, TAB_EVENTS = 1;
    private int tab = TAB_CALLS;
    private int filter = 0; // chiamate: 0 tutte, 1 con allarmi, 2 sconosciuti, 3 perse/bloccate; eventi: 0 tutti, 1 solo allarmi
    private String query = "";

    private LinearLayout tabs, filters, stats, list;
    private final Map<String, String> nameCache = new HashMap<>();
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ITALY);
    private final SimpleDateFormat day = new SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALY);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);
        Ui.header(this, col, "Registro", null);

        stats = Ui.hRow(this, col);
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(tabs, Ui.full(this, 10));

        EditText search = new EditText(this);
        search.setHint("Cerca nome o numero");
        search.setHintTextColor(Ui.SUB);
        search.setTextColor(Ui.TEXT);
        search.setTextSize(15);
        search.setSingleLine(true);
        search.setBackground(Ui.rounded(this, Ui.CARD, 14));
        search.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable e) { query = e.toString().trim().toLowerCase(Locale.ROOT); render(); }
        });
        col.addView(search, Ui.full(this, 10));

        filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(filters, Ui.full(this, 6));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);

        Ui.sectionLabel(this, col, "Documenti");
        Ui.primary(this, col, "Esporta rapporto PDF", Ui.ACCENT, v -> exportReport(this, (TextView) v));
        Ui.text(this, col, "Chiamate, allarmi e controlli in un documento da portare a forze dell'ordine o avvocato."
                + " Viene salvato nella cartella Download.", 12, Ui.SUB);
        Ui.button(this, col, "Svuota registro", v -> {
            CallStore.clear(this);
            EventLog.clear(this);
            render();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    // ------------------------------------------------------------------ testata

    private void renderHeader(List<CallStore.Call> calls, List<String[]> events) {
        stats.removeAllViews();
        long today = startOfDay(System.currentTimeMillis());
        int nToday = 0, nAlarm = 0, nUnknown = 0;
        for (CallStore.Call c : calls) {
            if (c.start >= today) nToday++;
            if (c.hasAlarm()) nAlarm++;
            if (!c.voip && name(c.number) == null) nUnknown++;
        }
        Ui.stat(this, stats, String.valueOf(nToday), "chiamate oggi", Ui.ACCENT);
        Ui.stat(this, stats, String.valueOf(nAlarm), "con allarmi", nAlarm > 0 ? Ui.RED : Ui.SUB);
        Ui.stat(this, stats, String.valueOf(nUnknown), "non in rubrica", nUnknown > 0 ? Ui.ORANGE : Ui.SUB);

        tabs.removeAllViews();
        segment(tabs, "Chiamate (" + calls.size() + ")", tab == TAB_CALLS, v -> { tab = TAB_CALLS; filter = 0; render(); });
        segment(tabs, "Avvisi ed eventi (" + events.size() + ")", tab == TAB_EVENTS, v -> { tab = TAB_EVENTS; filter = 0; render(); });

        filters.removeAllViews();
        String[] f = tab == TAB_CALLS
                ? new String[]{"Tutte", "Con allarmi", "Non in rubrica", "Perse/bloccate"}
                : new String[]{"Tutti", "Solo allarmi"};
        for (int i = 0; i < f.length; i++) {
            int idx = i;
            chip(filters, f[i], filter == i, v -> { filter = idx; render(); });
        }
    }

    private void segment(LinearLayout row, String text, boolean on, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setTextColor(on ? android.graphics.Color.WHITE : Ui.SUB);
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 11), Ui.dp(this, 8), Ui.dp(this, 11));
        t.setBackground(Ui.pressable(this, on ? Ui.ACCENT : Ui.CARD, 12));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(row.getChildCount() == 0 ? 0 : Ui.dp(this, 8));
        row.addView(t, lp);
    }

    private void chip(LinearLayout row, String text, boolean on, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(on ? Ui.ACCENT : Ui.SUB);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        t.setBackground(Ui.pressable(this, on ? Ui.alpha(Ui.ACCENT, 50) : Ui.CARD, 20));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(this, 6));
        row.addView(t, lp);
    }

    // ------------------------------------------------------------------ elenco

    private void render() {
        List<CallStore.Call> calls = CallStore.read(this);
        List<String[]> events = EventLog.read(this);
        renderHeader(calls, events);
        list.removeAllViews();
        if (tab == TAB_CALLS) renderCalls(calls); else renderEvents(events);
    }

    private void renderCalls(List<CallStore.Call> calls) {
        long lastDay = -1;
        int shown = 0;
        for (CallStore.Call c : calls) {
            String nm = name(c.number);
            if (filter == 1 && c.alerts.isEmpty()) continue;
            if (filter == 2 && (c.voip || nm != null)) continue;
            if (filter == 3 && !(CallStore.MISSED.equals(c.dir) || CallStore.REJECTED.equals(c.dir)
                    || CallStore.BLOCKED.equals(c.dir))) continue;
            if (!query.isEmpty()) {
                String hay = ((nm != null ? nm : "") + " " + (c.number != null ? c.number : "")).toLowerCase(Locale.ROOT);
                if (!hay.contains(query) && !hay.replace(" ", "").contains(query.replace(" ", ""))) continue;
            }
            if (shown++ >= 300) {
                Ui.text(this, list, "Mostrate le ultime 300 chiamate: usa la ricerca per trovare le altre.", 12, Ui.SUB);
                break;
            }
            long d = startOfDay(c.start);
            if (d != lastDay) { dayHeader(d); lastDay = d; }
            callRow(c, nm);
        }
        if (shown == 0) empty(calls.isEmpty()
                ? "Nessuna chiamata registrata.\nAttiva la protezione: da quel momento ogni chiamata compare qui."
                : "Nessuna chiamata con questo filtro.");
    }

    private void dayHeader(long d) {
        long today = startOfDay(System.currentTimeMillis());
        String label = d == today ? "Oggi" : d == today - 86_400_000L ? "Ieri" : day.format(new Date(d));
        TextView t = Ui.text(this, list, label.substring(0, 1).toUpperCase(Locale.ITALY) + label.substring(1), 13, Ui.SUB);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), 0, Ui.dp(this, 2));
    }

    private void callRow(CallStore.Call c, String nm) {
        int icon, color;
        String dirTxt;
        if (c.voip) { icon = R.drawable.ic_chat; color = Ui.ACCENT; dirTxt = "via internet"; }
        else switch (c.dir) {
            case CallStore.IN: icon = R.drawable.ic_call_in; color = Ui.GREEN; dirTxt = "in entrata"; break;
            case CallStore.OUT: icon = R.drawable.ic_call_out; color = Ui.ACCENT; dirTxt = "in uscita"; break;
            case CallStore.MISSED: icon = R.drawable.ic_call_missed; color = Ui.RED; dirTxt = "persa"; break;
            case CallStore.REJECTED: icon = R.drawable.ic_call_missed; color = Ui.ORANGE; dirTxt = "rifiutata"; break;
            case CallStore.BLOCKED: icon = R.drawable.ic_block; color = Ui.SUB; dirTxt = "bloccata dal telefono"; break;
            default: icon = R.drawable.ic_phone; color = Ui.SUB; dirTxt = "chiamata"; break;
        }
        boolean alarm = c.hasAlarm();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.pressable(this, Ui.CARD, 16));
        box.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        list.addView(box, Ui.full(this, 8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(row);

        FrameLayout bubble = new FrameLayout(this);
        bubble.setBackground(Ui.rounded(this, Ui.alpha(color, 40), 20));
        bubble.addView(Ui.icon(this, icon, color, 20), new FrameLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20), Gravity.CENTER));
        row.addView(bubble, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        TextView title = new TextView(this);
        title.setText(c.voip ? "Chiamata WhatsApp / internet" : nm != null ? nm : c.number != null ? c.number : "Numero nascosto");
        title.setTextSize(16);
        title.setTextColor(Ui.TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setSingleLine(true);
        texts.addView(title);
        TextView sub = new TextView(this);
        StringBuilder sb = new StringBuilder(hm.format(new Date(c.start))).append(" · ").append(dirTxt);
        if (c.durationSec > 0) sb.append(" · ").append(dur(c.durationSec));
        if (c.network != null) sb.append(" · ").append(c.network);
        sub.setText(sb);
        sub.setTextSize(12);
        sub.setTextColor(Ui.SUB);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (!c.alerts.isEmpty()) {
            TextView badge = new TextView(this);
            badge.setText(alarm ? "⚠ " + c.alerts.size() : "! " + c.alerts.size());
            badge.setTextSize(12);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            int bc = alarm ? Ui.RED : Ui.ORANGE;
            badge.setTextColor(bc);
            badge.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
            badge.setBackground(Ui.rounded(this, Ui.alpha(bc, 45), 12));
            row.addView(badge);
        }

        // dettagli, aperti al tocco
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(Ui.dp(this, 52), Ui.dp(this, 10), 0, 0);
        details.setVisibility(View.GONE);
        box.addView(details);
        box.setOnClickListener(v -> {
            if (details.getChildCount() == 0) fillDetails(details, c);
            details.setVisibility(details.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        });
    }

    private void fillDetails(LinearLayout d, CallStore.Call c) {
        if (c.number != null) {
            TextView n = Ui.text(this, d, c.number, 15, Ui.TEXT);
            n.setTextIsSelectable(true);
            Ui.text(this, d, NumberInfo.kind(c.number), 12, Ui.SUB);
        }
        Ui.text(this, d, "Inizio " + new SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(new Date(c.start))
                + (c.end > 0 ? " · fine " + new SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(new Date(c.end)) : ""), 12, Ui.SUB);
        if (!c.networks.isEmpty()) Ui.text(this, d, "Rete: " + String.join(" → ", c.networks), 12, Ui.SUB);
        if (!c.cells.isEmpty()) {
            Ui.text(this, d, "Celle del tuo telefono (" + c.cells.size() + "): " + String.join(" → ", c.cells), 12, Ui.SUB);
        }
        if (c.alerts.isEmpty()) {
            Ui.text(this, d, "✓ Nessun segnale sospetto durante questa chiamata", 13, Ui.GREEN);
        } else {
            for (String[] a : c.alerts) {
                Ui.text(this, d, (EventLog.ALERT.equals(a[0]) ? "⚠ " : "! ") + a[1], 13,
                        EventLog.ALERT.equals(a[0]) ? Ui.RED : Ui.ORANGE);
            }
        }
        if (c.number != null) {
            Ui.link(this, d, "Chi è questo numero? ›", Ui.ACCENT, v -> startActivity(
                    new Intent(this, LookupActivity.class).putExtra(LookupActivity.EXTRA_NUMBER, c.number)));
            Ui.link(this, d, "Richiama ›", Ui.GREEN, v -> startActivity(
                    new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.number)))));
        }
    }

    private void renderEvents(List<String[]> events) {
        int shown = 0;
        for (String[] r : events) {
            if (filter == 1 && !EventLog.ALERT.equals(r[1])) continue;
            if (!query.isEmpty() && !(r[2] + " " + (r[3] != null ? r[3] : "")).toLowerCase(Locale.ROOT).contains(query)) continue;
            if (shown++ >= 300) break;
            boolean alert = EventLog.ALERT.equals(r[1]);
            boolean warn = EventLog.WARN.equals(r[1]);
            int color = alert ? Ui.RED : warn ? Ui.ORANGE : Ui.ACCENT;
            LinearLayout card = Ui.card(this, list, color);
            TextView meta = Ui.text(this, card, r[0] + "   " + (alert ? "ALLARME" : warn ? "ATTENZIONE" : "INFO"), 11, color);
            meta.setTypeface(Typeface.DEFAULT_BOLD);
            Ui.text(this, card, r[2], 14, Ui.TEXT);
            if (r[3] != null) {
                String num = r[3];
                Ui.link(this, card, "Chi è questo numero? ›", Ui.ACCENT, v ->
                        startActivity(new Intent(this, LookupActivity.class).putExtra(LookupActivity.EXTRA_NUMBER, num)));
            }
        }
        if (shown == 0) empty(events.isEmpty() ? "Nessun evento registrato." : "Nessun evento con questo filtro.");
    }

    private void empty(String msg) {
        TextView t = Ui.text(this, list, msg, 15, Ui.SUB);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 36), 0, Ui.dp(this, 36));
    }

    // ------------------------------------------------------------------ utilità

    private String name(String number) {
        if (number == null) return null;
        if (nameCache.containsKey(number)) return nameCache.get(number);
        String n = NumberInfo.contactName(this, number);
        nameCache.put(number, n);
        return n;
    }

    private static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static String dur(long s) {
        if (s >= 3600) return String.format(Locale.ITALY, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        return String.format(Locale.ITALY, "%d:%02d", s / 60, s % 60);
    }

    /** Crea il PDF in background e lo apre. */
    static void exportReport(Activity a, TextView btn) {
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
