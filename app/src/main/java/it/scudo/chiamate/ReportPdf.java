package it.scudo.chiamate;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Rapporto PDF da consegnare a forze dell'ordine o avvocato. Salvato nella cartella Download. */
final class ReportPdf {
    private static final int W = 595, H = 842, M = 42; // A4 in punti

    private final PdfDocument doc = new PdfDocument();
    private PdfDocument.Page page;
    private Canvas c;
    private float y;
    private int pageNo = 0;
    private final Paint title = paint(18, true, Color.rgb(20, 40, 80));
    private final Paint h2 = paint(13, true, Color.rgb(30, 77, 140));
    private final Paint body = paint(9.5f, false, Color.rgb(30, 30, 30));
    private final Paint small = paint(8, false, Color.rgb(110, 110, 110));
    private final Paint red = paint(9.5f, true, Color.rgb(198, 40, 40));
    private final Paint orange = paint(9.5f, true, Color.rgb(200, 100, 0));
    private final Paint line = new Paint();

    private static Paint paint(float size, boolean bold, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }

    /** Crea il PDF e restituisce dove è stato salvato (Uri) . */
    static Uri create(Context ctx) throws Exception {
        ReportPdf r = new ReportPdf();
        r.build(ctx);
        String name = "Rapporto_ScudoChiamate_"
                + new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ITALY).format(new Date()) + ".pdf";
        return r.save(ctx, name);
    }

    private void build(Context ctx) {
        line.setColor(Color.rgb(200, 200, 200));
        newPage();
        String now = new SimpleDateFormat("dd/MM/yyyy 'ore' HH:mm", Locale.ITALY).format(new Date());

        text("Rapporto di sicurezza del telefono", title, 6);
        text("Generato da Scudo Chiamate il " + now, small, 2);
        text("Telefono: " + Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                + " · patch di sicurezza " + Build.VERSION.SECURITY_PATCH, small, 10);

        // ---------------- riepilogo
        section("Riepilogo");
        int score = Results.overall(ctx);
        text("Punteggio complessivo: " + (score < 0 ? "non calcolato" : score + "/100"), body, 4);
        row("App spia", Results.level(ctx, Results.APPS), Results.summary(ctx, Results.APPS));
        row("Deviazioni chiamate", Results.level(ctx, Results.FORWARD), Results.summary(ctx, Results.FORWARD));
        row("Traffico dati", Results.level(ctx, Results.TRAFFIC), Results.summary(ctx, Results.TRAFFIC));
        row("Sicurezza telefono", Results.level(ctx, Results.AUDIT), Results.summary(ctx, Results.AUDIT));
        row("WhatsApp", Results.level(ctx, Results.WHATSAPP), Results.summary(ctx, Results.WHATSAPP));
        y += 6;

        // ---------------- app sospette (scansione eseguita ora)
        section("App sospette (scansione eseguita ora)");
        try {
            List<AppScanner.Result> apps = AppScanner.scan(ctx, null);
            int shown = 0;
            for (AppScanner.Result a : apps) {
                if ("BASSO".equals(a.level())) continue;
                shown++;
                Paint p = "MEDIO".equals(a.level()) ? orange : red;
                text(a.level() + " · " + a.label + " (" + a.pkg + ")", p, 1);
                for (String why : a.reasons) text("   – " + why, body, 0);
                y += 4;
            }
            if (shown == 0) text("Nessuna app sospetta trovata.", body, 4);
        } catch (Exception e) {
            text("Scansione non riuscita: " + e, body, 4);
        }

        // ---------------- sicurezza telefono
        section("Impostazioni di sicurezza (controllo eseguito ora)");
        try {
            for (DeviceAudit.Check k : DeviceAudit.run(ctx)) {
                Paint p = k.state == 2 ? red : k.state == 1 ? orange : body;
                text((k.state == 0 ? "OK  " : k.state == 1 ? "ATTENZIONE  " : "PROBLEMA  ") + k.title, p, 0);
                text("   " + k.detail, small, 3);
            }
        } catch (Exception e) {
            text("Controllo non riuscito: " + e, body, 4);
        }

        // ---------------- registro
        section("Registro eventi (chiamate, allarmi, controlli) — dal più recente");
        List<String[]> rows = EventLog.read(ctx);
        if (rows.isEmpty()) text("Nessun evento registrato.", body, 4);
        int n = 0;
        for (String[] r : rows) {
            if (n++ >= 600) { text("… (registro troncato alle ultime 600 voci)", small, 2); break; }
            Paint p = EventLog.ALERT.equals(r[1]) ? red : EventLog.WARN.equals(r[1]) ? orange : body;
            text(r[0] + "  [" + r[1] + "]  " + r[2], p, 2);
        }

        y += 10;
        text("Nota: documento generato automaticamente sul telefono dall'app Scudo Chiamate. Gli avvisi sono"
                + " indizi tecnici, non prove. La cella e la posizione di chi chiama non sono visibili a chi riceve"
                + " la chiamata: possono essere acquisite solo dall'autorità giudiziaria tramite i tabulati dell'operatore.",
                small, 0);
        doc.finishPage(page);
    }

    private void newPage() {
        if (page != null) doc.finishPage(page);
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        c = page.getCanvas();
        y = M;
        c.drawText("Scudo Chiamate · pagina " + pageNo, M, H - 20, small);
    }

    private void section(String s) {
        if (y > H - M - 60) newPage();
        y += 8;
        text(s, h2, 2);
        c.drawLine(M, y, W - M, y, line);
        y += 8;
    }

    private void row(String name, int level, String summary) {
        String st = level < 0 ? "non controllato" : level == 0 ? "OK" : level == 1 ? "ATTENZIONE" : level == 2 ? "RISCHIO" : "CRITICO";
        Paint p = level >= 2 ? red : level == 1 ? orange : body;
        text(name + ": " + st + " — " + summary, p, 2);
    }

    /** Testo con a capo automatico e cambio pagina. */
    private void text(String s, Paint p, float after) {
        float lh = p.getTextSize() * 1.35f;
        float maxW = W - 2 * M;
        for (String para : s.split("\n")) {
            String rest = para;
            do {
                int cnt = p.breakText(rest, true, maxW, null);
                if (cnt < rest.length()) {
                    int sp = rest.lastIndexOf(' ', cnt);
                    if (sp > 0) cnt = sp + 1;
                }
                if (y + lh > H - M) newPage();
                y += lh;
                c.drawText(rest.substring(0, cnt).trim(), M, y, p);
                rest = rest.substring(cnt);
            } while (!rest.isEmpty());
        }
        y += after;
    }

    private Uri save(Context ctx, String name) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException("impossibile creare il file");
            try (OutputStream out = cr.openOutputStream(uri)) {
                doc.writeTo(out);
            }
            doc.close();
            return uri;
        }
        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            doc.writeTo(out);
        }
        doc.close();
        return Uri.fromFile(f);
    }
}
