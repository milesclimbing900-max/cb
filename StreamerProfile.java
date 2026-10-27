package com.example.donutauction.config;

public class StreamerProfile {
    public String name = "";
    /** Estimated livestream delay in milliseconds (user-supplied, NOT measured). */
    public long delayMs = 0;

    public StreamerProfile() {}

    public StreamerProfile(String name, long delayMs) {
        this.name = name;
        this.delayMs = delayMs;
    }
}
