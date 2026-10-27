package com.example.donutauction.notify;

import com.example.donutauction.DonutAuctionClient;
import com.example.donutauction.calc.BidCalculator;
import com.example.donutauction.config.ConfigManager;
import com.example.donutauction.config.ModConfig;
import com.example.donutauction.timer.AuctionTimer;
import com.example.donutauction.util.Fmt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Chat line + action-bar message + sound + screen-edge flash. Every channel is individually configurable. */
public final class Notifier implements AuctionTimer.Listener {
    public static volatile long flashUntilMs;
    public static volatile int flashColor = 0xAAFFAA00;

    @Override
    public void onThreshold(int secondsLeft) {
        fire("Estimated server end in " + secondsLeft + "s (estimate)", secondsLeft <= 3 ? 1.5f : 1.0f, 0xAAFFAA00);
    }

    @Override
    public void onBidAlert() {
        BidCalculator.Suggestion s = DonutAuctionClient.TRACKER.suggestion();
        String extra = s.hasBase()
                ? "Suggested next bid " + Fmt.amount(s.suggested()) + (s.overBudget() ? " (OVER BUDGET)" : "")
                : "No bid observed yet";
        fire("Bid window (estimated): " + extra + ". Bid manually.", 1.3f, 0xAA00AAFF);
    }

    /** Called when a new, higher top bid is detected in chat. */
    public void onNewBid(String user, double amount) {
        fire("New top bid: " + Fmt.amount(amount) + " by " + user, 1.25f, 0xAA30D060);
    }

    @Override
    public void onEnd() {
        fire("Estimated auction end reached (not verified by server)", 0.7f, 0xAAFF3030);
    }

    /** Chat-only feedback line. */
    public static void info(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(prefix(msg), false);
    }

    private void fire(String msg, float pitch, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        ModConfig c = ConfigManager.get();
        if (c.chatNotifications) mc.player.sendMessage(prefix(msg), false);
        if (c.overlayNotifications) mc.player.sendMessage(Text.literal(msg).formatted(Formatting.YELLOW), true);
        if (c.soundEnabled && c.soundVolume > 0f) {
            mc.getSoundManager().play(PositionedSoundInstance.master(c.soundType.event(), pitch, c.soundVolume));
        }
        if (c.screenFlash) {
            flashColor = color;
            flashUntilMs = System.currentTimeMillis() + 400;
        }
    }

    private static Text prefix(String msg) {
        return Text.literal("[DSMPA] ").formatted(Formatting.AQUA)
                .append(Text.literal(msg).formatted(Formatting.WHITE));
    }
}
