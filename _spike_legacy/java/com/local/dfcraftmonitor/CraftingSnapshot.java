package com.local.dfcraftmonitor;

import java.util.Collections;
import java.util.List;

public final class CraftingSnapshot {
    public final long serverNowEpochSeconds;
    public final long fetchedAtEpochMillis;
    public final List<CraftingStation> stations;

    public CraftingSnapshot(long serverNowEpochSeconds, long fetchedAtEpochMillis, List<CraftingStation> stations) {
        this.serverNowEpochSeconds = serverNowEpochSeconds;
        this.fetchedAtEpochMillis = fetchedAtEpochMillis;
        this.stations = Collections.unmodifiableList(stations);
    }
}

