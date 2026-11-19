package it.scudo.chiamate;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Piccoli aiuti per costruire le schermate senza file di layout. */
final class Ui {
    static final int BLUE = Color.parseColor("#1E4D8C");
    static final int RED = Color.parseColor("#C62828");
    static final int ORANGE = Color.parseColor("#E65100");
    static final int GREEN = Color.parseColor("#2E7D32");
    static final int GREY = Color.parseColor("#616161");

    private Ui() {}

    static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    /** Crea una pagina scorrevole e restituisce il contenitore verticale. */
    static LinearLayout page(Activity a) {
        ScrollView sv = new ScrollView(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 16);
        col.setPadding(p, p, p, p);
        sv.addView(col);
        a.setContentView(sv);
        return col;
    }

    static TextView title(Activity a, LinearLayout parent, String text) {
        TextView t = text(a, parent, text, 22, Color.BLACK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView text(Activity a, LinearLayout parent, String text, int sp, int color) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(a, 8);
        parent.addView(t, lp);
        return t;
    }

    static Button button(Activity a, LinearLayout parent, String text, View.OnClickListener l) {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 6);
        lp.bottomMargin = dp(a, 6);
        parent.addView(b, lp);
        return b;
    }

    /** Riquadro con bordo colorato. */
    static LinearLayout card(Activity a, LinearLayout parent, int borderColor) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 12);
        c.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(a, 10));
        bg.setStroke(dp(a, 2), borderColor);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(a, 10);
        parent.addView(c, lp);
        return c;
    }

    static void center(TextView t) {
        t.setGravity(Gravity.CENTER_HORIZONTAL);
    }
}
