package com.example.donutauction.config;

import com.example.donutauction.chat.EventType;
import com.example.donutauction.notify.NotificationSound;

import java.util.*;

public class ModConfig {
    public enum IncrementMode { FIXED, PERCENT, SCALED }

    /** Bumped by sanitize() when old config files are migrated. */
    public int configVersion = 0;
    /** Chat/sound/flash notification whenever a new top bid is detected. */
    public boolean notifyOnBid = true;

    // ---- streamers ----
    public List<StreamerProfile> streamers = new ArrayList<>();
    public String selectedStreamer = "";

    // ---- timer / alerts ----
    public long defaultDurationMs = 60_000;
    public List<Integer> warningThresholdsSec = new ArrayList<>(List.of(30, 10, 5, 3, 2, 1));
    /** Final alert fires this many MILLISECONDS before the estimated end (0 = off). */
    public int bidAlertMs = 800;
    public int preciseBelowSec = 10;
    public boolean soundEnabled = true;
    public NotificationSound soundType = NotificationSound.PLING;
    public float soundVolume = 0.75f;
    public boolean chatNotifications = true;
    public boolean overlayNotifications = true;
    public boolean screenFlash = true;

    // ---- bidding ----
    public IncrementMode incrementMode = IncrementMode.SCALED;
    /**
     * SCALED mode: the bid is split into a unit (1, K, M, B, T) and a number of that unit.
     * Below scaledThreshold units the step is scaledSmallStep units (1M -> 1.1M, 1B -> 1.1B, 1 -> 1.1);
     * at or above it the step is scaledLargeStep units (100M -> 101M).
     */
    public double scaledSmallStep = 0.1;
    public double scaledLargeStep = 1.0;
    public double scaledThreshold = 10.0;
    /** Copy-bid puts "101M" on the clipboard instead of "101000000" (only if the pay command accepts suffixes). */
    public boolean copyShortForm = false;
    public double incrementFixed = 1000;
    public double incrementPercent = 5;
    /** 0 = no budget limit. */
    public double maxBudget = 0;
    /** If false, PAYMENT events never feed the bid calculator. */
    public boolean paymentsCountAsBids = false;

    // ---- display ----
    public boolean hudEnabled = true;
    public boolean hudShowWhenIdle = false;
    public int hudX = 6;
    public int hudY = 6;
    /** Panel background opacity 0.15 - 1.0 (text stays solid). */
    public float hudOpacity = 0.55f;

    // ---- parsing ----
    public boolean parsingEnabled = true;
    public boolean autoStartTimerFromChat = false;
    /** Only count payments whose recipient is the selected streamer (needs a (?<recipient>) group). */
    public boolean onlySelectedStreamer = false;
    /** Writes unmatched chat lines containing one of logKeywords to donutauction-chatlog.txt (local only). */
    public boolean logUnmatchedChat = false;
    public List<String> logKeywords = new ArrayList<>(List.of("auction", "bid", "paid", "payment", "sold", "won"));
    public List<ParseRule> rules = defaultRules();

    public static List<ParseRule> defaultRules() {
        List<ParseRule> l = new ArrayList<>();
        // SELFTEST rules: only match lines that START with "TEST ", used by `/dsmpa test ...`.
        l.add(new ParseRule("SELFTEST start", EventType.AUCTION_START, "^TEST START (?<user>\\w+) (?<duration>\\S+)$", true));
        l.add(new ParseRule("SELFTEST bid", EventType.BID, "^TEST BID (?<user>\\w+) (?<amount>\\S+)$", true));
        l.add(new ParseRule("SELFTEST payment", EventType.PAYMENT, "^TEST PAY (?<user>\\w+) (?<amount>\\S+)$", true));
        l.add(new ParseRule("SELFTEST end", EventType.AUCTION_END, "^TEST END(?: (?<user>\\w+) (?<amount>\\S+))?$", true));
        // Real DonutSMP line seen in chat: "IcyL0 was paid $6.4M by freebushr1"
        // recipient = the streamer, user = the bidder, amount supports K/M/B/T suffixes.
        l.add(paidRule());
        // EXAMPLE rules: GUESSES, disabled. Replace with REAL DonutSMP messages once verified.
        l.add(new ParseRule("EXAMPLE bid (UNVERIFIED)", EventType.BID,
                "(?i)(?<user>\\w{3,16}) (?:placed a bid of|bid) \\$?(?<amount>[\\d.,]+[kmbt]?)", false));
        l.add(new ParseRule("EXAMPLE payment (UNVERIFIED)", EventType.PAYMENT,
                "(?i)(?<user>\\w{3,16}) (?:paid you|has paid you|sent you) \\$?(?<amount>[\\d.,]+[kmbt]?)", false));
        l.add(new ParseRule("EXAMPLE end (UNVERIFIED)", EventType.AUCTION_END,
                "(?i)auction (?:ended|has ended)", false));
        return l;
    }

    /** Matches: "IcyL0 was paid $6.4M by freebushr1" (recipient = streamer, user = bidder). */
    public static ParseRule paidRule() {
        return new ParseRule("DonutSMP paid", EventType.BID,
                "(?i)(?<recipient>[\\w.]+)\\s+was\\s+paid\\s+\\$?\\s*(?<amount>[\\d.,]+\\s?[kmbt]?)\\s+by\\s+(?<user>[\\w.*]+)", true);
    }

    public StreamerProfile selected() {
        return find(selectedStreamer);
    }

    public StreamerProfile find(String name) {
        if (name == null) return null;
        for (StreamerProfile s : streamers) if (s.name.equalsIgnoreCase(name)) return s;
        return null;
    }

    /** Repairs anything a hand-edited or old config file might have broken. */
    public void sanitize() {
        if (streamers == null) streamers = new ArrayList<>();
        streamers.removeIf(s -> s == null || s.name == null || s.name.isBlank());
        for (StreamerProfile s : streamers) s.delayMs = Math.max(0, Math.min(600_000, s.delayMs));

        if (warningThresholdsSec == null) warningThresholdsSec = new ArrayList<>(List.of(30, 10, 5, 3, 2, 1));
        warningThresholdsSec.removeIf(v -> v == null || v <= 0 || v > 3600);
        warningThresholdsSec = new ArrayList<>(new TreeSet<>(warningThresholdsSec).descendingSet());

        if (rules == null) rules = defaultRules();
        rules.removeIf(Objects::isNull);
        if (configVersion < 3) {
            // Replace any older copy of the DonutSMP rule with the current, more tolerant one.
            rules.removeIf(r -> "DonutSMP paid".equals(r.name));
            rules.add(0, paidRule());
            if (configVersion < 2) onlySelectedStreamer = false;
            configVersion = 3;
        }
        if (configVersion < 4) {
            incrementMode = IncrementMode.SCALED; // new default; old saved FIXED/PERCENT did not fit big bids
            configVersion = 4;
        }
        if (logKeywords == null) logKeywords = new ArrayList<>(List.of("auction", "bid"));
        if (incrementMode == null) incrementMode = IncrementMode.FIXED;
        if (soundType == null) soundType = NotificationSound.PLING;

        soundVolume = Math.max(0f, Math.min(1f, soundVolume));
        bidAlertMs = Math.max(0, Math.min(60_000, bidAlertMs));
        hudOpacity = Math.max(0.15f, Math.min(1f, hudOpacity));
        preciseBelowSec = Math.max(0, Math.min(120, preciseBelowSec));
        defaultDurationMs = Math.max(1000, Math.min(86_400_000L, defaultDurationMs));
        scaledSmallStep = Math.max(0.0001, scaledSmallStep);
        scaledLargeStep = Math.max(0.0001, scaledLargeStep);
        scaledThreshold = Math.max(1, scaledThreshold);
        incrementFixed = Math.max(0, incrementFixed);
        incrementPercent = Math.max(0, Math.min(1000, incrementPercent));
        maxBudget = Math.max(0, maxBudget);
        hudX = Math.max(0, hudX);
        hudY = Math.max(0, hudY);

        if (selected() == null) selectedStreamer = streamers.isEmpty() ? "" : streamers.get(0).name;
    }
}
