package com.local.dfcraftmonitor;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AmsProbeResultTest {
    @Test
    public void identifiesSuccessfulCraftingResponse() throws Exception {
        AmsProbeResult result = AmsProbeResult.fromCraftingBody("{"
                + "\"ret\":0,\"iRet\":0,\"sMsg\":\"succ\","
                + "\"jData\":{\"data\":{\"code\":0,\"msg\":\"ok\",\"data\":{\"placeData\":[],\"nowTime\":1}}}"
                + "}");

        assertTrue(result.isSuccess());
    }

    @Test
    public void identifiesLoginRequiredResponse() throws Exception {
        AmsProbeResult result = AmsProbeResult.fromCraftingBody("{\"ret\":101,\"iRet\":101,\"sMsg\":\"非常抱歉，请先登录！\"}");

        assertFalse(result.isSuccess());
    }
}
