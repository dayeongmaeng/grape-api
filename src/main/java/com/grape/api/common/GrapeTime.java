package com.grape.api.common;

import java.time.ZoneId;

/**
 * Single source of truth for the calendar-day boundary used across the app.
 * "Today" (e.g. bunch_fill_events.fill_date) is always computed in this zone, regardless of the
 * server OS/JVM default timezone. See server-design-draft.md §3-3.
 */
public final class GrapeTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private GrapeTime() {
    }
}
