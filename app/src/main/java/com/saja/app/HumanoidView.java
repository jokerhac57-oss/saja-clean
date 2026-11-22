package com.saja.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class HumanoidView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
    private int accent = 0xFF00E5FF;
    private int body = 0xFF0B1A24;
    private float pulse = 0f;

    public HumanoidView(Context c) {
        super(c);
        init();
    }

    public HumanoidView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.5f);
        fill.setStyle(Paint.Style.FILL);
        glow.setStyle(Paint.Style.FILL);
        anim.setDuration(2400);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.addUpdateListener(a -> {
            pulse = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    public void setColors(int accentColor, int bodyColor) {
        accent = accentColor;
        body = bodyColor;
        invalidate();
    }

    public void setFast(boolean fast) {
        anim.setDuration(fast ? 900 : 2400);
        if (isAttachedToWindow()) {
            anim.cancel();
            anim.start();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float s = Math.min(w / 200f, h / 260f);
        float ox = (w - 200f * s) / 2f;
        float oy = (h - 260f * s) / 2f;
        int glowAlpha = 90 + (int) (165 * pulse);

        canvas.save();
        canvas.translate(ox, oy);
        canvas.scale(s, s);

        stroke.setColor(accent);
        glow.setColor(accent);

        // antenna
        canvas.drawLine(100, 17, 100, 30, stroke);
        glow.setAlpha(glowAlpha);
        canvas.drawCircle(100, 12, 5, glow);

        // head
        fill.setColor(body);
        canvas.drawCircle(100, 58, 28, fill);
        canvas.drawCircle(100, 58, 28, stroke);

        // visor
        RectF visor = new RectF(80, 50, 120, 66);
        fill.setColor(accent);
        fill.setAlpha(40);
        canvas.drawRoundRect(visor, 8, 8, fill);

        // eyes
        glow.setAlpha(glowAlpha);
        canvas.drawCircle(92, 58, 4, glow);
        canvas.drawCircle(108, 58, 4, glow);

        // neck
        fill.setColor(body);
        canvas.drawRect(92, 86, 108, 96, fill);

        // torso
        RectF torso = new RectF(62, 96, 138, 174);
        canvas.drawRoundRect(torso, 14, 14, fill);
        canvas.drawRoundRect(torso, 14, 14, stroke);
        glow.setAlpha(glowAlpha);
        canvas.drawCircle(100, 130, 7, glow);

        // arms
        RectF leftArm = new RectF(42, 100, 58, 164);
        RectF rightArm = new RectF(142, 100, 158, 164);
        canvas.drawRoundRect(leftArm, 7, 7, fill);
        canvas.drawRoundRect(leftArm, 7, 7, stroke);
        canvas.drawRoundRect(rightArm, 7, 7, fill);
        canvas.drawRoundRect(rightArm, 7, 7, stroke);

        // legs
        RectF leftLeg = new RectF(74, 174, 96, 242);
        RectF rightLeg = new RectF(104, 174, 126, 242);
        canvas.drawRoundRect(leftLeg, 8, 8, fill);
        canvas.drawRoundRect(leftLeg, 8, 8, stroke);
        canvas.drawRoundRect(rightLeg, 8, 8, fill);
        canvas.drawRoundRect(rightLeg, 8, 8, stroke);

        canvas.restore();
    }
}
