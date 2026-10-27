package com.example.donutauction.calc;

import com.example.donutauction.config.ModConfig;

/** Pure decision-support maths. It suggests a number; it never sends anything and guarantees nothing. */
public final class BidCalculator {
    public record Suggestion(boolean hasBase, double base, double suggested, boolean overBudget) {}

    private BidCalculator() {}

    public static Suggestion suggest(ModConfig c, double latestBid) {
        if (Double.isNaN(latestBid)) return new Suggestion(false, Double.NaN, Double.NaN, false);
        double next;
        switch (c.incrementMode) {
            case FIXED -> next = latestBid + c.incrementFixed;
            case PERCENT -> next = latestBid * (1.0 + c.incrementPercent / 100.0);
            default -> { // SCALED
                double unit = unitFor(latestBid);
                double inUnits = latestBid / unit;
                double step = inUnits < c.scaledThreshold ? c.scaledSmallStep : c.scaledLargeStep;
                next = latestBid + step * unit;
            }
        }
        next = Math.round(next * 100.0) / 100.0; // whole cents, never rounded up to a whole dollar
        if (next <= latestBid) next = Math.round((latestBid + 0.01) * 100.0) / 100.0; // always strictly higher
        boolean over = c.maxBudget > 0 && next > c.maxBudget;
        return new Suggestion(true, latestBid, next, over);
    }

    /** Largest of 1, K, M, B, T that is not bigger than the value. */
    static double unitFor(double v) {
        if (v >= 1e12) return 1e12;
        if (v >= 1e9) return 1e9;
        if (v >= 1e6) return 1e6;
        if (v >= 1e3) return 1e3;
        return 1;
    }
}
