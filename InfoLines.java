package com.example.donutauction.ui;

import com.example.donutauction.DonutAuctionClient;
import com.example.donutauction.calc.BidCalculator;
import com.example.donutauction.chat.ChatTracker;
import com.example.donutauction.config.ConfigManager;
import com.example.donutauction.config.ModConfig;
import com.example.donutauction.config.ParseRule;
import com.example.donutauction.config.StreamerProfile;
import com.example.donutauction.timer.AuctionTimer;
import com.example.donutauction.util.Fmt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.ArrayList;
import java.util.List;

/** Builds the label/value rows shown in the glass panel (HUD and main screen). */
public final class InfoLines {
    public record Row(String label, String value, int color) {}

    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, GREEN = 0xFF55FF55,
            YELLOW = 0xFFFFFF55, RED = 0xFFFF5555, AQUA = 0xFF55FFFF;

    private InfoLines() {}

    public static List<Row> build() {
        ModConfig c = ConfigManager.get();
        AuctionTimer t = DonutAuctionClient.TIMER;
        ChatTracker tr = DonutAuctionClient.TRACKER;
        List<Row> out = new ArrayList<>();

        StreamerProfile sp = c.selected();
        long delay = sp == null ? 0 : sp.delayMs;
        out.add(new Row("Streamer", sp == null ? "none selected" : sp.name + " (" + Fmt.delay(delay) + " delay)", WHITE));

        switch (t.state()) {
            case IDLE -> out.add(new Row("Timer", "idle", GRAY));
            case RUNNING, PAUSED -> {
                long rem = t.remainingMs();
                int col = rem <= 3000 ? RED : rem <= 10_000 ? YELLOW : GREEN;
                String paused = t.state() == AuctionTimer.State.PAUSED ? " (paused)" : "";
                out.add(new Row("Server end [est]", Fmt.time(rem, c.preciseBelowSec) + paused, col));
                out.add(new Row("Stream shows [est]", Fmt.time(t.streamRemainingMs(delay), c.preciseBelowSec), AQUA));
            }
            case FINISHED -> {
                if (t.isVerifiedEnd()) {
                    long err = t.verifiedErrorMs();
                    out.add(new Row("Auction", "end verified (estimate " + (err >= 0 ? "+" : "") + err + " ms)", GREEN));
                } else {
                    out.add(new Row("Auction", "est. end reached, not verified", YELLOW));
                }
                long sr = t.streamRemainingMs(delay);
                if (sr > 0) out.add(new Row("Stream shows end in", Fmt.time(sr, c.preciseBelowSec), AQUA));
            }
        }

        ChatTracker.AmountRecord b = tr.latestBid();
        if (b == null) {
            out.add(new Row("Top bid", "none observed", GRAY));
        } else {
            out.add(new Row("Top bid", Fmt.amount(b.amount()) + " by " + b.user(), WHITE));
            ChatTracker.AmountRecord pb = tr.previousBid();
            if (pb != null) out.add(new Row("Previous", Fmt.amount(pb.amount()) + " by " + pb.user(), GRAY));
        }
        ChatTracker.AmountRecord p = tr.latestPayment();
        if (p != null) out.add(new Row("Payment (not a bid)", Fmt.amount(p.amount()) + " from " + p.user(), GRAY));

        BidCalculator.Suggestion s = tr.suggestion();
        if (s.hasBase()) {
            String note = c.maxBudget > 0 ? (s.overBudget() ? "  OVER BUDGET" : "  in budget") : "";
            out.add(new Row("Suggested next", Fmt.withShort(s.suggested()) + note, s.overBudget() ? RED : YELLOW));
        } else {
            out.add(new Row("Suggested next", "waiting for a bid", GRAY));
        }
        if (!tr.lastResult().isEmpty()) out.add(new Row("Result", tr.lastResult(), WHITE));

        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo si = mc.getCurrentServerEntry();
        String where = !tr.connected() ? "offline" : si != null ? si.address : "singleplayer";
        int active = 0;
        for (ParseRule r : c.rules) if (r.enabled && r.pattern() != null) active++;
        long now = System.currentTimeMillis();
        out.add(new Row("Data", where + ", " + active + " rules, last match "
                + Fmt.age(tr.lastMatchAtMs() < 0 ? -1 : now - tr.lastMatchAtMs()), tr.connected() ? GRAY : RED));
        out.add(new Row("Chat seen", "game " + tr.seenGame() + ", chat " + tr.seenChat() + ", bar "
                + tr.seenOverlay() + " | matched " + tr.matched(), tr.matched() > 0 ? GREEN : GRAY));
        if (active == 0 || !c.parsingEnabled) out.add(new Row("Warning", "no parsing rules active", YELLOW));
        return out;
    }
}
