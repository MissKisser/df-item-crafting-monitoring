package com.local.dfcraftmonitor;

public final class AmsCredential {
    public final String openid;
    public final String acctype;
    public final String appid;
    public final String accessToken;

    private AmsCredential(String openid, String acctype, String appid, String accessToken) {
        this.openid = openid;
        this.acctype = acctype;
        this.appid = appid;
        this.accessToken = accessToken;
    }

    public static AmsCredential qq(String openid, String appid, String accessToken) {
        return new AmsCredential(openid, "qc", appid, accessToken);
    }

    public boolean isComplete() {
        return hasText(openid) && hasText(acctype) && hasText(appid) && hasText(accessToken);
    }

    public String cookieHeader() {
        return "openid=" + openid
                + "; acctype=" + acctype
                + "; appid=" + appid
                + "; access_token=" + accessToken;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

