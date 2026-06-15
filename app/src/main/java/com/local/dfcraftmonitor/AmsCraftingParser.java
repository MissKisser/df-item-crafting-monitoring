package com.local.dfcraftmonitor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class AmsCraftingParser {
    private AmsCraftingParser() {
    }

    public static CraftingSnapshot parse(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        JSONObject business = root
                .getJSONObject("jData")
                .getJSONObject("data");
        JSONObject payload = business.getJSONObject("data");
        JSONObject relateMap = payload.optJSONObject("relateMap");
        JSONArray placeData = payload.optJSONArray("placeData");
        List<CraftingStation> stations = new ArrayList<>();

        if (placeData != null) {
            for (int index = 0; index < placeData.length(); index++) {
                JSONObject item = placeData.getJSONObject(index);
                Long objectId = optLongObject(item, "objectId");
                JSONObject objectInfo = null;
                if (relateMap != null && objectId != null) {
                    objectInfo = relateMap.optJSONObject(String.valueOf(objectId));
                }
                stations.add(new CraftingStation(
                        StationType.fromPlaceType(item.optString("placeType", "")),
                        item.optString("placeName", ""),
                        item.optString("Status", ""),
                        objectId,
                        objectInfo == null ? null : objectInfo.optString("objectName", null),
                        objectInfo == null ? null : objectInfo.optString("pic", null),
                        objectInfo == null ? null : optLongObject(objectInfo, "avgPrice"),
                        optLongObject(item, "leftTime"),
                        optLongObject(item, "pushTime")
                ));
            }
        }

        return new CraftingSnapshot(
                payload.optLong("nowTime", 0L),
                System.currentTimeMillis(),
                stations
        );
    }

    private static Long optLongObject(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            return null;
        }
        return object.optLong(key);
    }
}
