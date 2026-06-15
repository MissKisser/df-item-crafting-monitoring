package com.local.dfcraftmonitor;

public enum StationType {
    TECHNOLOGY_CENTER,
    WORKBENCH,
    PHARMACY,
    ARMORY,
    UNKNOWN;

    public static StationType fromPlaceType(String placeType) {
        if ("tech".equals(placeType)) {
            return TECHNOLOGY_CENTER;
        }
        if ("workbench".equals(placeType)) {
            return WORKBENCH;
        }
        if ("pharmacy".equals(placeType)) {
            return PHARMACY;
        }
        if ("armory".equals(placeType)) {
            return ARMORY;
        }
        return UNKNOWN;
    }
}

