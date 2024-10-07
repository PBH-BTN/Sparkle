package com.ghostchu.btn.sparkle.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class TimeUtil {
    public static OffsetDateTime toUTC(long ts) {
        var instant = Instant.ofEpochMilli(ts);
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
