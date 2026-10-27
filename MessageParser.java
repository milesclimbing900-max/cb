package com.example.donutauction.chat;

import com.example.donutauction.config.ParseRule;
import com.example.donutauction.util.TimeParse;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-driven parser. It contains NO hard-coded DonutSMP formats: real formats must be verified from
 * actual chat captures and then added as rules in the config file. First enabled matching rule wins.
 */
public final class MessageParser {
    private static final Pattern AMOUNT = Pattern.compile("^(\\d+(?:\\.\\d+)?)([kmbt]?)$");

    private MessageParser() {}

    public static Optional<ParsedEvent> parse(String text, List<ParseRule> rules, long nowMs) {
        if (text == null || text.isBlank() || rules == null) return Optional.empty();
        for (ParseRule r : rules) {
            if (r == null || !r.enabled || r.type == null) continue;
            Pattern p = r.pattern();
            if (p == null) continue;
            try {
                Matcher m = p.matcher(text);
                if (!m.find()) continue;
                String user = group(m, "user");
                String recipient = group(m, "recipient");
                String amountS = group(m, "amount");
                String durS = group(m, "duration");
                Double amount = amountS == null ? null : parseAmount(amountS);
                Long dur = durS == null ? null : TimeParse.parseMs(durS);
                return Optional.of(new ParsedEvent(r.type, user, recipient, amount, dur, text, nowMs, r.name));
            } catch (RuntimeException e) {
                // A bad rule must never break chat handling: skip it.
            }
        }
        return Optional.empty();
    }

    private static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null; // rule does not define this group
        }
    }

    /** Accepts "1500", "1,500", "$1.5k", "2M", "3.25b". Returns null if not a number. */
    public static Double parseAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replace("$", "").replace(",", "").replace("_", "").replace(" ", "");
        Matcher m = AMOUNT.matcher(s);
        if (!m.matches()) return null;
        double v = Double.parseDouble(m.group(1));
        switch (m.group(2)) {
            case "k" -> v *= 1e3;
            case "m" -> v *= 1e6;
            case "b" -> v *= 1e9;
            case "t" -> v *= 1e12;
            default -> { }
        }
        return v;
    }
}
