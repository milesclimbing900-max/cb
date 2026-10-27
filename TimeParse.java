package com.example.donutauction.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses "90", "90s", "1m30s", "2.5s", "2500ms", "1:30", "1:30.250". A bare number means SECONDS. */
public final class TimeParse {
    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,3}):(\\d{1,2})(?:\\.(\\d{1,3}))?$");
    private static final Pattern TOKEN = Pattern.compile("(\\d{1,7}(?:\\.\\d{1,3})?)\\s*(ms|s|m|h)?");

    private TimeParse() {}

    /** @return milliseconds, or null if the text is not a valid duration (max 24h). */
    public static Long parseMs(String in) {
        if (in == null) return null;
        String s = in.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.length() > 24) return null;

        Matcher c = CLOCK.matcher(s);
        if (c.matches()) {
            long min = Long.parseLong(c.group(1));
            long sec = Long.parseLong(c.group(2));
            if (sec > 59) return null;
            long ms = (min * 60 + sec) * 1000;
            if (c.group(3) != null) ms += Integer.parseInt((c.group(3) + "00").substring(0, 3));
            return ms;
        }

        Matcher t = TOKEN.matcher(s);
        int pos = 0;
        double total = 0;
        boolean any = false;
        while (pos < s.length()) {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
            if (pos >= s.length()) break;
            t.region(pos, s.length());
            if (!t.lookingAt()) return null;
            double v = Double.parseDouble(t.group(1));
            String u = t.group(2);
            double mult;
            if (u == null || u.equals("s")) mult = 1000;
            else if (u.equals("ms")) mult = 1;
            else if (u.equals("m")) mult = 60_000;
            else mult = 3_600_000;
            total += v * mult;
            any = true;
            pos = t.end();
        }
        if (!any) return null;
        long r = Math.round(total);
        return (r < 0 || r > 86_400_000L) ? null : r;
    }
}
