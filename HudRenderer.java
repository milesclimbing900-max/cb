package com.example.donutauction.ui;

import com.example.donutauction.DonutAuctionClient;
import com.example.donutauction.config.ConfigManager;
import com.example.donutauction.config.ModConfig;
import com.example.donutauction.notify.Notifier;
import com.example.donutauction.timer.AuctionTimer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

import java.util.List;

/** Non-interactive glass overlay. Draws only; never captures input. Move it via the "Move HUD" screen. */
public final class HudRenderer {
    private HudRenderer() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.of(DonutAuctionClient.MOD_ID, "hud"), HudRenderer::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        ModConfig c = ConfigManager.get();

        // Per-frame tick gives alerts better than 50 ms resolution; the END_CLIENT_TICK call is a fallback.
        DonutAuctionClient.TIMER.tick(c, DonutAuctionClient.NOTIFIER);

        long now = System.currentTimeMillis();
        if (c.screenFlash && now < Notifier.flashUntilMs) {
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight(), t = 5, col = Notifier.flashColor;
            ctx.fill(0, 0, w, t, col);
            ctx.fill(0, h - t, w, h, col);
            ctx.fill(0, t, t, h - t, col);
            ctx.fill(w - t, t, w, h - t, col);
        }

        if (!c.hudEnabled) return;
        if (mc.currentScreen instanceof MainScreen || mc.currentScreen instanceof HudEditScreen) return;
        if (DonutAuctionClient.TIMER.state() == AuctionTimer.State.IDLE
                && !c.hudShowWhenIdle && DonutAuctionClient.TRACKER.latestBid() == null) return;

        List<InfoLines.Row> rows = InfoLines.build();
        GlassPanel.Size size = GlassPanel.measure(mc.textRenderer, rows, "Auction Assistant", 190);
        int x = Math.max(0, Math.min(c.hudX, ctx.getScaledWindowWidth() - size.w()));
        int y = Math.max(0, Math.min(c.hudY, ctx.getScaledWindowHeight() - size.h()));
        GlassPanel.draw(ctx, mc.textRenderer, x, y, rows, "Auction Assistant", c.hudOpacity, 190);
    }
}
