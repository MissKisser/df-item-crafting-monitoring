package com.local.dfcraftmonitor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AmsCraftingParserTest {
    @Test
    public void parsesStationsAndMergesRelateMapItemNames() throws Exception {
        String json = "{"
                + "\"ret\":0,\"iRet\":0,\"sMsg\":\"succ\","
                + "\"jData\":{\"data\":{\"code\":0,\"msg\":\"ok\",\"data\":{"
                + "\"nowTime\":1781500152,"
                + "\"placeData\":["
                + "{\"placeType\":\"workbench\",\"placeName\":\"工作台\",\"leftTime\":4971,\"pushTime\":1781505123,\"objectId\":37120500001}"
                + "],"
                + "\"relateMap\":{\"37120500001\":{\"objectName\":\"5.45x39mm BS\",\"pic\":\"https://example.test/ammo.png\",\"avgPrice\":4122}}"
                + "}}}"
                + "}";

        CraftingSnapshot snapshot = AmsCraftingParser.parse(json);

        assertEquals(1781500152L, snapshot.serverNowEpochSeconds);
        assertEquals(1, snapshot.stations.size());
        CraftingStation station = snapshot.stations.get(0);
        assertEquals(StationType.WORKBENCH, station.type);
        assertEquals("工作台", station.placeName);
        assertEquals("5.45x39mm BS", station.itemName);
        assertEquals("https://example.test/ammo.png", station.iconUrl);
        assertEquals(Long.valueOf(4122), station.avgPrice);
        assertNotNull(station.finishAtEpochSeconds);
        assertEquals(Long.valueOf(1781505123L), station.finishAtEpochSeconds);
    }
}
