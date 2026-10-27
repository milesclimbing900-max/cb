package com.example.donutauction.notify;

import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public enum NotificationSound {
    PLING, BELL, HARP;

    public SoundEvent event() {
        return switch (this) {
            case PLING -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case BELL -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case HARP -> SoundEvents.BLOCK_NOTE_BLOCK_HARP.value();
        };
    }

    public NotificationSound next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
