package com.example.donutauction.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class Fmt {
    private Fmt() {}

    /** Countdown text: s.mmm below the precision threshold, m:ss otherwise. */
    public static String time(long ms, int preciseBelowSec) {
        if (ms < 0) ms = 0;
        if (ms < preciseBelowSec * 1000L) {
            return String.format(Locale.ROOT, "%d.%03d s", ms / 1000, ms % 1000);
        }
        long s = (ms + 999) / 1000;
        if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    public static String amount(double v) {
        if (Double.isNaN(v)) return "-";
        if (v == Math.rint(v)) return String.format(Locale.US, "%,.0f", v);
        return String.format(Locale.US, "%,.2f", v);
    }

    /** Plain number for the clipboard (no separators). */
    public static String raw(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v);
    }

    /** 101000000 -> "101M", 1100000000 -> "1.1B", 1.1 -> "1.1". */
    public static String shortAmt(double v) {
        double[] units = {1e12, 1e9, 1e6, 1e3};
        String[] names = {"T", "B", "M", "K"};
        for (int i = 0; i < units.length; i++) {
            if (v >= units[i]) {
                return BigDecimal.valueOf(v / units[i]).setScale(3, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString() + names[i];
            }
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** "101,000,000 (101M)" for display. */
    public static String withShort(double v) {
        return v >= 1000 ? amount(v) + " (" + shortAmt(v) + ")" : amount(v);
    }

    /** Clipboard text for the copy-bid action. */
    public static String copy(double v, boolean shortForm) {
        return shortForm ? shortAmt(v) : raw(v);
    }

    public static String delay(long ms) {
        return ms % 1000 == 0 ? (ms / 1000) + "s" : ms + "ms";
    }

    public static String age(long ms) {
        if (ms < 0) return "never";
        if (ms < 1000) return "<1s ago";
        if (ms < 60_000) return (ms / 1000) + "s ago";
        return (ms / 60_000) + "m ago";
    }
}
