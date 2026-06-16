package com.local.dfcraftmonitor;

public final class CraftingStation {
    public final StationType type;
    public final String placeName;
    public final String status;
    public final Long itemId;
    public final String itemName;
    public final String iconUrl;
    public final Long avgPrice;
    public final Long remainingSeconds;
    public final Long finishAtEpochSeconds;

    public CraftingStation(
            StationType type,
            String placeName,
            String status,
            Long itemId,
            String itemName,
            String iconUrl,
            Long avgPrice,
            Long remainingSeconds,
            Long finishAtEpochSeconds
    ) {
        this.type = type;
        this.placeName = placeName;
        this.status = status;
        this.itemId = itemId;
        this.itemName = itemName;
        this.iconUrl = iconUrl;
        this.avgPrice = avgPrice;
        this.remainingSeconds = remainingSeconds;
        this.finishAtEpochSeconds = finishAtEpochSeconds;
    }
}

