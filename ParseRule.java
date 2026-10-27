package com.example.donutauction.config;

import com.example.donutauction.chat.EventType;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One user-editable parsing rule. The regex may define these NAMED groups:
 * (?<user>...) (?<amount>...) (?<duration>...). All are optional.
 */
public class ParseRule {
    public String name = "";
    public EventType type = EventType.BID;
    public String regex = "";
    public boolean enabled = false;

    private transient Pattern compiled;
    private transient boolean failed;

    public ParseRule() {}

    public ParseRule(String name, EventType type, String regex, boolean enabled) {
        this.name = name;
        this.type = type;
        this.regex = regex;
        this.enabled = enabled;
    }

    /** @return compiled pattern or null if the regex is invalid (rule is then ignored). */
    public Pattern pattern() {
        if (compiled == null && !failed) {
            try {
                compiled = Pattern.compile(regex);
            } catch (PatternSyntaxException | NullPointerException e) {
                failed = true;
            }
        }
        return compiled;
    }
}
