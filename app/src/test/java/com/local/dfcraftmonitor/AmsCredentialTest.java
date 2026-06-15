package com.local.dfcraftmonitor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AmsCredentialTest {
    @Test
    public void qqCredentialBuildsAmsCookieHeader() {
        AmsCredential credential = AmsCredential.qq("openid123", "1112438254", "token456");

        assertEquals(
                "openid=openid123; acctype=qc; appid=1112438254; access_token=token456",
                credential.cookieHeader()
        );
    }
}

