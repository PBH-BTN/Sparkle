package com.ghostchu.btn.sparkle.util;

public class WarningSender {
    // A class that sends warnings to the console, but only once in period
    private final long minInterval;
    private long lastWarningTime = 0;

    public WarningSender(long minInterval) {
        this.minInterval = minInterval;
    }

    public boolean sendIfPossible() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastWarningTime > minInterval) {
            lastWarningTime = currentTime;
            return true;
        }
        return false;
    }
}
