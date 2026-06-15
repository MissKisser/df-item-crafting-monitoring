package com.local.dfcraftmonitor;

import org.json.JSONObject;

public final class AmsProbeResult {
    public final int ret;
    public final int iRet;
    public final String message;
    public final String rawBody;
    public final CraftingSnapshot snapshot;

    private AmsProbeResult(int ret, int iRet, String message, String rawBody, CraftingSnapshot snapshot) {
        this.ret = ret;
        this.iRet = iRet;
        this.message = message;
        this.rawBody = rawBody;
        this.snapshot = snapshot;
    }

    public static AmsProbeResult fromCraftingBody(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        int ret = root.optInt("ret", Integer.MIN_VALUE);
        int iRet = root.optInt("iRet", Integer.MIN_VALUE);
        String message = root.optString("sMsg", root.optString("msg", ""));
        CraftingSnapshot snapshot = null;
        if (ret == 0 && iRet == 0 && root.has("jData")) {
            snapshot = AmsCraftingParser.parse(body);
        }
        return new AmsProbeResult(ret, iRet, message, body, snapshot);
    }

    public boolean isSuccess() {
        return ret == 0 && iRet == 0 && snapshot != null;
    }
}

