package it.scudo.chiamate;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Stile grafico dell'app (tema scuro) e componenti costruiti da codice. */
final class Ui {
    static final int BG = Color.parseColor("#0E131D");
    static final int CARD = Color.parseColor("#172030");
    static final int CARD2 = Color.parseColor("#202B3F");
    static final int TEXT = Color.parseColor("#F2F5FA");
    static final int SUB = Color.parseColor("#94A1B5");
    static final int ACCENT = Color.parseColor("#4C8DFF");
    static final int GREEN = Color.parseColor("#34C77B");
    static final int ORANGE = Color.parseColor("#FFA94D");
    static final int RED = Color.parseColor("#FF5D5D");
    // Nomi usati nelle schermate
    static final int BLUE = ACCENT;
    static final int GREY = SUB;

    private Ui() {}

    static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    static GradientDrawable rounded(Activity a, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(a, radiusDp));
        return g;
    }

    static Drawable pressable(Activity a, int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(alpha(Color.WHITE, 40)),
                rounded(a, color, radiusDp), rounded(a, Color.WHITE, radiusDp));
    }

    static LinearLayout.LayoutParams full(Activity a, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(a, bottomDp);
        return lp;
    }

    /** Pagina scorrevole a tema scuro; restituisce la colonna dei contenuti. */
    static LinearLayout page(Activity a) {
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a, 20), dp(a, 28), dp(a, 20), dp(a, 28));
        sv.addView(col);
        a.setContentView(sv);
        return col;
    }

    /** Intestazione con freccia indietro (per le schermate secondarie). */
    static void header(Activity a, LinearLayout parent, String title, String subtitle) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = icon(a, R.drawable.ic_back, TEXT, 26);
        back.setPadding(dp(a, 6), dp(a, 6), dp(a, 6), dp(a, 6));
        back.setBackground(pressable(a, CARD, 22));
        back.setOnClickListener(v -> a.finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(a, 40), dp(a, 40)));
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(24);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setPadding(dp(a, 14), 0, 0, 0);
        row.addView(t);
        parent.addView(row, full(a, 10));
        if (subtitle != null) text(a, parent, subtitle, 14, SUB).setPadding(0, 0, 0, dp(a, 8));
    }

    static TextView title(Activity a, LinearLayout parent, String text) {
        TextView t = text(a, parent, text, 28, TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    static TextView text(Activity a, LinearLayout parent, String text, int sp, int color) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.2f);
        parent.addView(t, full(a, 6));
        return t;
    }

    static ImageView icon(Activity a, int res, int color, int sizeDp) {
        ImageView iv = new ImageView(a);
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(color));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(a, sizeDp), dp(a, sizeDp)));
        return iv;
    }

    /** Pulsante principale pieno. */
    static TextView primary(Activity a, LinearLayout parent, String text, int color, View.OnClickListener l) {
        TextView b = new TextView(a);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(a, 16), dp(a, 15), dp(a, 16), dp(a, 15));
        b.setBackground(pressable(a, color, 14));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = full(a, 8);
        lp.topMargin = dp(a, 8);
        parent.addView(b, lp);
        return b;
    }

    /** Pulsante secondario. */
    static TextView button(Activity a, LinearLayout parent, String text, View.OnClickListener l) {
        TextView b = primary(a, parent, text, CARD2, l);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        return b;
    }

    /** Riquadro con striscia colorata a sinistra; restituisce il contenitore interno. */
    static LinearLayout card(Activity a, LinearLayout parent, int accent) {
        LinearLayout outer = new LinearLayout(a);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setBackground(pressable(a, CARD, 16));
        outer.setClipToOutline(true);
        View stripe = new View(a);
        stripe.setBackgroundColor(accent);
        outer.addView(stripe, new LinearLayout.LayoutParams(dp(a, 5), LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout inner = new LinearLayout(a);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 10));
        outer.addView(inner, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(outer, full(a, 12));
        return inner;
    }

    /** Etichetta colorata piccola (es. livello di rischio). */
    static TextView pill(Activity a, LinearLayout parent, String text, int color) {
        TextView p = new TextView(a);
        p.setText(text);
        p.setTextSize(12);
        p.setTextColor(color);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setLetterSpacing(0.05f);
        p.setPadding(dp(a, 10), dp(a, 4), dp(a, 10), dp(a, 4));
        p.setBackground(rounded(a, alpha(color, 45), 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(a, 8);
        parent.addView(p, lp);
        return p;
    }

    /** Riga di menu: icona in cerchio, titolo, sottotitolo, freccia. */
    static void menuRow(Activity a, LinearLayout parent, int iconRes, int color, String title,
                        String subtitle, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 14), dp(a, 14), dp(a, 10), dp(a, 14));
        row.setBackground(pressable(a, CARD, 16));
        row.setOnClickListener(l);

        FrameLayout bubble = new FrameLayout(a);
        bubble.setBackground(rounded(a, alpha(color, 40), 14));
        ImageView ic = icon(a, iconRes, color, 24);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(a, 24), dp(a, 24), Gravity.CENTER);
        bubble.addView(ic, ilp);
        row.addView(bubble, new LinearLayout.LayoutParams(dp(a, 46), dp(a, 46)));

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(a, 14), 0, dp(a, 6), 0);
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        texts.addView(t);
        TextView s = new TextView(a);
        s.setText(subtitle);
        s.setTextSize(13);
        s.setTextColor(SUB);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        row.addView(icon(a, R.drawable.ic_chevron, SUB, 22));
        parent.addView(row, full(a, 10));
    }

    /** Riquadro numerico (es. "3 / rischio alto"). */
    static void stat(Activity a, LinearLayout row, String value, String label, int color) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(a, 8), dp(a, 14), dp(a, 8), dp(a, 14));
        box.setBackground(rounded(a, CARD, 16));
        TextView v = new TextView(a);
        v.setText(value);
        v.setTextSize(28);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        v.setGravity(Gravity.CENTER);
        box.addView(v);
        TextView l = new TextView(a);
        l.setText(label);
        l.setTextSize(12);
        l.setTextColor(SUB);
        l.setGravity(Gravity.CENTER);
        box.addView(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(row.getChildCount() == 0 ? 0 : dp(a, 10));
        row.addView(box, lp);
    }

    static LinearLayout hRow(Activity a, LinearLayout parent) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, full(a, 14));
        return row;
    }

    static void sectionLabel(Activity a, LinearLayout parent, String text) {
        TextView t = text(a, parent, text.toUpperCase(), 12, SUB);
        t.setLetterSpacing(0.12f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(a, 4), dp(a, 12), 0, dp(a, 2));
    }
}
