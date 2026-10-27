package com.example.donutauction.chat;

/** Result of one matched parsing rule. Fields are null when the rule has no such group. */
public record ParsedEvent(EventType type, String user, String recipient, Double amount, Long durationMs,
                          String rawText, long timestampMs, String ruleName) {}
