package com.example.donutauction.chat;

import com.example.donutauction.DonutAuctionClient;
import com.example.donutauction.calc.BidCalculator;
import com.example.donutauction.config.ConfigManager;
import com.example.donutauction.config.ModConfig;
import com.example.donutauction.notify.Notifier;
import com.example.donutauction.timer.AuctionTimer;
import com.example.donutauction.util.Fmt;

import java.util.*;

/** Holds everything observed from chat. Client thread only. */
public final class ChatTracker {
    public record AmountRecord(String user, double amount, long atMs, String streamer) {}

    private AmountRecord latestBid, latestPayment;
    private final Deque<AmountRecord> previousBids = new ArrayDeque<>();
    private final Deque<AmountRecord> previousPayments = new ArrayDeque<>();
    private final Deque<String> eventLog = new ArrayDeque<>();
    private final Deque<String> recentChat = new ArrayDeque<>();
    private long lastChatAtMs = -1, lastMatchAtMs = -1;
    private int matched, unmatched;
    private boolean connected;
    private String lastResult = "";

    public enum Source { GAME, CHAT, OVERLAY, TEST }

    private int seenGame, seenChat, seenOverlay;
    private String lastOverlay = "";

    /** Used by /dsmpa test. */
    public void onMessage(String raw) { onMessage(raw, Source.TEST); }

    public void onMessage(String raw, Source src) {
        if (raw == null) return;
        String text = normalize(raw);
        if (text.isEmpty() || text.startsWith("[DSMPA]")) return; // ignore our own output
        if (src == Source.OVERLAY) {
            if (text.equals(lastOverlay)) return; // servers often resend the same action-bar text
            lastOverlay = text;
            seenOverlay++;
        } else if (src == Source.GAME) {
            seenGame++;
        } else {
            seenChat++;
        }
        long now = System.currentTimeMillis();
        ModConfig c = ConfigManager.get();
        lastChatAtMs = now;
        if (src != Source.OVERLAY) push(recentChat, src.name().toLowerCase(Locale.ROOT) + "| " + text, 40);
        if (!c.parsingEnabled) return;

        Optional<ParsedEvent> ev = MessageParser.parse(text, c.rules, now);
        if (ev.isEmpty()) {
            if (src == Source.OVERLAY) return;
            unmatched++;
            if (c.logUnmatchedChat) {
                String lower = text.toLowerCase(Locale.ROOT);
                for (String kw : c.logKeywords) {
                    if (kw != null && !kw.isBlank() && lower.contains(kw.toLowerCase(Locale.ROOT))) {
                        ConfigManager.appendChatLog(text);
                        break;
                    }
                }
            }
            return;
        }
        matched++;
        lastMatchAtMs = now;
        handle(c, ev.get());
    }

    private void handle(ModConfig c, ParsedEvent e) {
        String streamer = c.selectedStreamer;
        switch (e.type()) {
            case AUCTION_START -> {
                clearBids();
                log("Auction start" + (e.user() != null ? " by " + e.user() : "")
                        + (e.durationMs() != null ? " (" + Fmt.delay(e.durationMs()) + ")" : ""));
                if (c.autoStartTimerFromChat && e.durationMs() != null) {
                    DonutAuctionClient.TIMER.start(e.durationMs(), AuctionTimer.Origin.CHAT_DETECTED, c);
                }
            }
            case BID -> {
                if (e.amount() == null) { log("Bid message without readable amount: " + e.rawText()); return; }
                if (!recipientOk(c, e)) {
                    log("Ignored payment to " + e.recipient() + " (selected streamer: " + c.selectedStreamer + ")");
                    Notifier.info("Ignored payment to " + e.recipient() + " (selected streamer is " + c.selectedStreamer
                            + "). Change the streamer or turn off \"Only selected streamer\".");
                    return;
                }
                setBid(new AmountRecord(e.user(), e.amount(), e.timestampMs(), streamer));
            }
            case PAYMENT -> {
                if (e.amount() == null) { log("Payment message without readable amount: " + e.rawText()); return; }
                AmountRecord r = new AmountRecord(e.user(), e.amount(), e.timestampMs(), streamer);
                if (latestPayment != null) push(previousPayments, latestPayment, 10);
                latestPayment = r;
                log("Payment " + Fmt.amount(r.amount()) + " from " + r.user() + " (not necessarily a bid)");
                if (c.paymentsCountAsBids) setBid(r);
            }
            case AUCTION_END -> {
                DonutAuctionClient.TIMER.markVerifiedEnd();
                lastResult = "Ended" + (e.user() != null ? ": " + e.user() : "")
                        + (e.amount() != null ? " @ " + Fmt.amount(e.amount()) : "");
                log("Auction end observed. " + lastResult);
            }
        }
    }

    private static boolean recipientOk(ModConfig c, ParsedEvent e) {
        return !c.onlySelectedStreamer || e.recipient() == null || c.selectedStreamer == null
                || c.selectedStreamer.isBlank() || e.recipient().equalsIgnoreCase(c.selectedStreamer);
    }

    private void setBid(AmountRecord r) {
        // Only a HIGHER amount replaces the current top bid.
        if (latestBid != null && r.amount() <= latestBid.amount()) {
            log("Ignored non-higher bid " + Fmt.amount(r.amount()) + " by " + r.user());
            return;
        }
        if (latestBid != null) push(previousBids, latestBid, 10);
        latestBid = r;
        log("Bid " + Fmt.amount(r.amount()) + " by " + r.user());
        if (ConfigManager.get().notifyOnBid) DonutAuctionClient.NOTIFIER.onNewBid(r.user(), r.amount());
    }

    public void clearBids() {
        latestBid = null;
        latestPayment = null;
        previousBids.clear();
        previousPayments.clear();
        lastResult = "";
    }

    public void clearAll() {
        clearBids();
        eventLog.clear();
        matched = 0;
        unmatched = 0;
        seenGame = 0;
        seenChat = 0;
        seenOverlay = 0;
    }

    public BidCalculator.Suggestion suggestion() {
        return BidCalculator.suggest(ConfigManager.get(), latestBid == null ? Double.NaN : latestBid.amount());
    }

    /** Removes colour codes, zero-width characters and odd space characters servers like to use. */
    private static String normalize(String raw) {
        String s = raw.replaceAll("\u00A7.", "")
                .replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ')
                .replaceAll("[\u200B-\u200F\u2060\uFEFF]", "");
        return s.replaceAll("\\s+", " ").strip();
    }

    public int seenGame() { return seenGame; }
    public int seenChat() { return seenChat; }
    public int seenOverlay() { return seenOverlay; }

    private void log(String s) { push(eventLog, s, 50); }

    private static <T> void push(Deque<T> d, T v, int max) {
        d.addFirst(v);
        while (d.size() > max) d.removeLast();
    }

    public void setConnected(boolean b) { connected = b; }
    public boolean connected() { return connected; }
    public AmountRecord latestBid() { return latestBid; }
    public AmountRecord previousBid() { return previousBids.peekFirst(); }
    public AmountRecord latestPayment() { return latestPayment; }
    public AmountRecord previousPayment() { return previousPayments.peekFirst(); }
    public List<String> eventLog() { return new ArrayList<>(eventLog); }
    public List<String> recentChat() { return new ArrayList<>(recentChat); }
    public long lastChatAtMs() { return lastChatAtMs; }
    public long lastMatchAtMs() { return lastMatchAtMs; }
    public int matched() { return matched; }
    public int unmatched() { return unmatched; }
    public String lastResult() { return lastResult; }
}
