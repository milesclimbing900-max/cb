package com.example.donutauction.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Translucent rounded "frosted glass" panel (dark tint, top sheen, thin light border).
 * This is a see-through look; true background blur needs shaders and is not attempted.
 */
public final class GlassPanel {
    public record Size(int w, int h) {}

    private static final int PAD = 6, ROW = 11, HEADER = 16, RADIUS = 4;

    private GlassPanel() {}

    public static Size measure(TextRenderer tr, List<InfoLines.Row> rows, String title, int minW) {
        int w = tr.getWidth(title) + PAD * 2 + 14;
        for (InfoLines.Row r : rows) {
            w = Math.max(w, tr.getWidth(r.label()) + tr.getWidth(r.value()) + 14 + PAD * 2);
        }
        return new Size(Math.max(w, minW), HEADER + rows.size() * ROW + PAD);
    }

    public static Size draw(DrawContext ctx, TextRenderer tr, int x, int y, List<InfoLines.Row> rows,
                            String title, float opacity, int minW) {
        Size s = measure(tr, rows, title, minW);
        int a = Math.max(0, Math.min(255, Math.round(opacity * 255)));

        roundRect(ctx, x, y, s.w(), s.h(), RADIUS, (a << 24) | 0x0E1118);

        int sheenA = Math.round(0x40 * opacity);
        ctx.fillGradient(x + 2, y + 1, x + s.w() - 2, y + s.h() / 2, (sheenA << 24) | 0xFFFFFF, 0x00FFFFFF);

        int bc = (Math.round(0x66 * Math.max(0.5f, opacity)) << 24) | 0xFFFFFF;
        ctx.fill(x + RADIUS, y, x + s.w() - RADIUS, y + 1, bc);
        ctx.fill(x + RADIUS, y + s.h() - 1, x + s.w() - RADIUS, y + s.h(), bc);
        ctx.fill(x, y + RADIUS, x + 1, y + s.h() - RADIUS, bc);
        ctx.fill(x + s.w() - 1, y + RADIUS, x + s.w(), y + s.h() - RADIUS, bc);

        ctx.fill(x + PAD, y + 6, x + PAD + 4, y + 10, 0xFF55AAFF);
        ctx.drawTextWithShadow(tr, title, x + PAD + 9, y + 4, 0xFFFFFFFF);
        ctx.fill(x + PAD, y + HEADER - 3, x + s.w() - PAD, y + HEADER - 2, 0x30FFFFFF);

        int ry = y + HEADER;
        for (InfoLines.Row r : rows) {
            ctx.drawTextWithShadow(tr, r.label(), x + PAD, ry, 0xFFAAAAAA);
            ctx.drawTextWithShadow(tr, r.value(), x + s.w() - PAD - tr.getWidth(r.value()), ry, r.color());
            ry += ROW;
        }
        return s;
    }

    /** Filled rectangle with rounded corners, built from horizontal strips. */
    private static void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x, y + r, x + w, y + h - r, color);
        for (int dy = 0; dy < r; dy++) {
            double d = r - dy - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            ctx.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
            ctx.fill(x + inset, y + h - 1 - dy, x + w - inset, y + h - dy, color);
        }
    }
}
