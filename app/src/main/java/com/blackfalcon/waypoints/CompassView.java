package com.blackfalcon.waypoints;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Compass dial. The dial turns with the phone (N marks north); the big arrow always points to the waypoint,
 * measured from where the TOP of the phone points. When the arrow points straight up, you are heading to it.
 */
public class CompassView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float dialHeading = 0f, rel = 0f;
    private boolean hasTarget = false, arrived = false;
    private String emoji = "";
    private int color = 0xFF2ECC71;
    private float[] othersRel = new float[0];
    private int[] othersColor = new int[0];

    public CompassView(Context c) { super(c); }

    public void setHeading(float dialHeading) { this.dialHeading = dialHeading; invalidate(); }

    public void setTarget(boolean has, float rel, String emoji, int color, boolean arrived) {
        this.hasTarget = has; this.rel = rel; this.emoji = emoji; this.color = color; this.arrived = arrived;
        invalidate();
    }

    public void setOthers(float[] rel, int[] colors) { othersRel = rel; othersColor = colors; invalidate(); }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - dp(30);
        cv.drawColor(0xFF000000);

        boolean aligned = hasTarget && !arrived && Math.abs(((rel + 540f) % 360f) - 180f) <= 6f;
        int good = arrived ? 0xFFFFD54F : (aligned ? 0xFFFFD54F : color);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0C0C1E);
        cv.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(aligned || arrived ? 4 : 2));
        p.setColor(aligned || arrived ? 0xFF2ECC71 : 0xFF3A3A60);
        cv.drawCircle(cx, cy, r, p);

        // dial: ticks and letters turn with the phone
        p.setStrokeWidth(dp(1.5f));
        for (int deg = 0; deg < 360; deg += 10) {
            double a = Math.toRadians(deg - dialHeading);
            float len = dp(deg % 90 == 0 ? 14 : (deg % 30 == 0 ? 10 : 5));
            float sx = (float) Math.sin(a), sy = -(float) Math.cos(a);
            p.setColor(deg == 0 ? 0xFFE57373 : 0xFF6A6A90);
            cv.drawLine(cx + sx * (r - len), cy + sy * (r - len), cx + sx * r, cy + sy * r, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dp(16));
        String[] card = {"N", "E", "S", "W"};
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(k * 90 - dialHeading);
            p.setColor(k == 0 ? 0xFFE57373 : 0xFF9E9E9E);
            cv.drawText(card[k], cx + (float) (Math.sin(a) * (r - dp(30))), cy - (float) (Math.cos(a) * (r - dp(30))) + dp(5), p);
        }

        // other waypoints as small dots
        for (int i = 0; i < othersRel.length; i++) {
            double a = Math.toRadians(othersRel[i]);
            p.setColor(othersColor[i]);
            cv.drawCircle(cx + (float) (Math.sin(a) * r * 0.62f), cy - (float) (Math.cos(a) * r * 0.62f), dp(5), p);
        }

        if (hasTarget) {
            cv.save();
            cv.rotate(rel, cx, cy);
            float tip = cy - r * 0.74f, headBase = tip + dp(38), half = dp(22), shaft = dp(7);
            Path arrow = new Path();
            arrow.moveTo(cx, tip);
            arrow.lineTo(cx + half, headBase);
            arrow.lineTo(cx + shaft, headBase);
            arrow.lineTo(cx + shaft, cy + dp(26));
            arrow.lineTo(cx - shaft, cy + dp(26));
            arrow.lineTo(cx - shaft, headBase);
            arrow.lineTo(cx - half, headBase);
            arrow.close();
            p.setColor(good);
            cv.drawPath(arrow, p);
            cv.restore();

            // the waypoint's icon on the ring
            double a = Math.toRadians(rel);
            float kx = cx + (float) (Math.sin(a) * r), ky = cy - (float) (Math.cos(a) * r);
            p.setColor(0xFF02030A);
            cv.drawCircle(kx, ky, dp(23), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(good);
            cv.drawCircle(kx, ky, dp(23), p);
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(dp(22));
            p.setColor(0xFFFFFFFF);
            cv.drawText(emoji, kx, ky + dp(8), p);
        }

        // hub
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(cx, cy, dp(7), p);
        p.setColor(0xFF0C0C1E);
        cv.drawCircle(cx, cy, dp(3), p);

        // marker for the top of the phone
        Path tri = new Path();
        tri.moveTo(cx, cy - r - dp(8));
        tri.lineTo(cx - dp(9), cy - r - dp(26));
        tri.lineTo(cx + dp(9), cy - r - dp(26));
        tri.close();
        p.setColor(0xFFFFFFFF);
        cv.drawPath(tri, p);
    }
}
