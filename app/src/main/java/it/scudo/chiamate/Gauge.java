package it.scudo.chiamate;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Indicatore circolare animato del punteggio di sicurezza. */
public class Gauge extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float shown = 0;
    private int score = -1;
    private int color = Ui.SUB;
    private String label = "";
    private final float d;

    public Gauge(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setStrokeWidth(14 * d);
        track.setColor(Ui.CARD2);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setStrokeWidth(14 * d);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setStrokeWidth(26 * d);
        big.setColor(Ui.TEXT);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        big.setTextSize(54 * d);
        small.setColor(Ui.SUB);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(13 * d);
        small.setLetterSpacing(0.1f);
    }

    /** score -1 = nessuna analisi. */
    public void set(int score, int color, String label) {
        this.score = score;
        this.color = color;
        this.label = label;
        float target = Math.max(0, score);
        ValueAnimator va = ValueAnimator.ofFloat(0, target);
        va.setDuration(1100);
        va.setInterpolator(new DecelerateInterpolator(1.6f));
        va.addUpdateListener(a -> { shown = (float) a.getAnimatedValue(); invalidate(); });
        va.start();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h) - 30 * d;
        box.set((w - size) / 2, (h - size) / 2, (w + size) / 2, (h + size) / 2);
        c.drawArc(box, 135, 270, false, track);
        float sweep = 270f * shown / 100f;
        if (score >= 0 && sweep > 0) {
            glow.setColor(Ui.alpha(color, 45));
            c.drawArc(box, 135, sweep, false, glow);
            arc.setColor(color);
            c.drawArc(box, 135, sweep, false, arc);
        }
        float cy = h / 2;
        big.setColor(score >= 0 ? Ui.TEXT : Ui.SUB);
        c.drawText(score >= 0 ? String.valueOf(Math.round(shown)) : "—", w / 2, cy + 16 * d, big);
        small.setColor(score >= 0 ? color : Ui.SUB);
        c.drawText(label.toUpperCase(), w / 2, cy + 44 * d, small);
    }
}
