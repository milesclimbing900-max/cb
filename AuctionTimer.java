package com.example.donutauction.timer;

import com.example.donutauction.config.ModConfig;

import java.util.HashSet;
import java.util.Set;

/**
 * Local-clock auction countdown. EVERYTHING here is an ESTIMATE based on when the user pressed start
 * (or when a configured chat rule saw an auction start). The only "verified" information is a parsed
 * AUCTION_END chat message, see {@link #markVerifiedEnd()}. Client thread only.
 */
public final class AuctionTimer {
    public enum State { IDLE, RUNNING, PAUSED, FINISHED }
    public enum Origin { MANUAL_ESTIMATE, CHAT_DETECTED }

    public interface Listener {
        default void onThreshold(int secondsLeft) {}
        default void onBidAlert() {}
        default void onEnd() {}
    }

    private State state = State.IDLE;
    private Origin origin = Origin.MANUAL_ESTIMATE;
    private long durationMs;
    private long elapsedBeforeMs;
    private long runStartNanos;
    private long endNanos;
    private boolean verifiedEnd;
    private long verifiedErrorMs;
    private boolean bidAlertFired;
    private final Set<Integer> fired = new HashSet<>();

    public void start(long durationMs, Origin origin, ModConfig cfg) {
        this.durationMs = Math.max(0, durationMs);
        this.elapsedBeforeMs = 0;
        this.runStartNanos = System.nanoTime();
        this.origin = origin;
        this.verifiedEnd = false;
        this.verifiedErrorMs = 0;
        this.state = State.RUNNING;
        resync(cfg);
    }

    public void pause() {
        if (state == State.RUNNING) {
            elapsedBeforeMs = elapsedMs();
            state = State.PAUSED;
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            runStartNanos = System.nanoTime();
            state = State.RUNNING;
        }
    }

    public void reset() {
        state = State.IDLE;
        origin = Origin.MANUAL_ESTIMATE;
        durationMs = 0;
        elapsedBeforeMs = 0;
        verifiedEnd = false;
        verifiedErrorMs = 0;
        bidAlertFired = false;
        fired.clear();
    }

    /** Shifts the estimated end by deltaMs (positive = later). Ignored when idle or finished. */
    public void adjust(long deltaMs, ModConfig cfg) {
        if (state == State.IDLE || state == State.FINISHED) return;
        durationMs = Math.max(0, durationMs + deltaMs);
        resync(cfg);
    }

    /** Marks thresholds that are already behind us as "fired" so they don't all beep at once. */
    private void resync(ModConfig cfg) {
        fired.clear();
        long rem = remainingMs();
        for (int t : cfg.warningThresholdsSec) if (rem <= t * 1000L) fired.add(t);
        bidAlertFired = cfg.bidAlertMs <= 0 || rem <= cfg.bidAlertMs;
    }

    public void tick(ModConfig cfg, Listener l) {
        if (state != State.RUNNING) return;
        long rem = remainingMs();
        if (rem <= 0) {
            endNanos = runStartNanos + (durationMs - elapsedBeforeMs) * 1_000_000L;
            state = State.FINISHED;
            l.onEnd();
            return;
        }
        Integer toFire = null;
        for (int t : cfg.warningThresholdsSec) {
            if (rem <= t * 1000L && fired.add(t)) {
                if (toFire == null || t < toFire) toFire = t;
            }
        }
        if (toFire != null) l.onThreshold(toFire);
        if (!bidAlertFired && cfg.bidAlertMs > 0 && rem <= cfg.bidAlertMs) {
            bidAlertFired = true;
            l.onBidAlert();
        }
    }

    /** Call when a parsed AUCTION_END chat message arrives. Records how far off the estimate was. */
    public void markVerifiedEnd() {
        if (state == State.IDLE || verifiedEnd) return;
        if (state == State.FINISHED) {
            verifiedErrorMs = (System.nanoTime() - endNanos) / 1_000_000L;
        } else {
            verifiedErrorMs = elapsedMs() - durationMs;
            state = State.FINISHED;
        }
        endNanos = System.nanoTime();
        verifiedEnd = true;
    }

    public void pauseIfRunning() { pause(); }

    public long elapsedMs() {
        if (state == State.RUNNING) return elapsedBeforeMs + (System.nanoTime() - runStartNanos) / 1_000_000L;
        return elapsedBeforeMs;
    }

    public long remainingMs() {
        if (state == State.IDLE || state == State.FINISHED) return 0;
        return Math.max(0, durationMs - elapsedMs());
    }

    /** Estimated moment the STREAM will show the end: remaining server time + configured delay. */
    public long streamRemainingMs(long delayMs) {
        if (state == State.IDLE) return 0;
        if (state == State.FINISHED) return Math.max(0, delayMs - (System.nanoTime() - endNanos) / 1_000_000L);
        return remainingMs() + delayMs;
    }

    public State state() { return state; }
    public Origin origin() { return origin; }
    public long durationMs() { return durationMs; }
    public boolean isVerifiedEnd() { return verifiedEnd; }
    /** Positive = server ended later than the estimate, negative = earlier. */
    public long verifiedErrorMs() { return verifiedErrorMs; }
}
