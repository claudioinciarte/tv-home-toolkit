package com.gathin.tvhome;

import org.junit.Test;
import java.util.Collections;
import java.util.Map;
import static org.junit.Assert.assertEquals;

public class PlexServerCredentialsTest {
    private static final String JWT = "header.payload.signature";

    @Test public void ownedServerCanUseItsMatchingLegacyDeviceCredential() {
        assertEquals("device-token", PlexServerCredentials.compatible("owned", true, JWT,
                Collections.singletonMap("owned", "device-token")));
    }

    @Test public void anotherServersCredentialIsNeverSubstituted() {
        assertEquals(JWT, PlexServerCredentials.compatible("owned", true, JWT,
                Collections.singletonMap("other", "other-token")));
    }

    @Test public void sharedServerRetainsItsScopedResourceCredential() {
        assertEquals(JWT, PlexServerCredentials.compatible("shared", false, JWT,
                Collections.singletonMap("shared", "device-token")));
    }

    @Test public void existingLegacyResourceCredentialRemainsPreferred() {
        assertEquals("resource-token", PlexServerCredentials.compatible("owned", true, "resource-token",
                Collections.singletonMap("owned", "device-token")));
    }

    @Test public void missingOrUnusableDeviceCredentialRetainsTheOriginal() {
        for (Map<String, String> devices : java.util.Arrays.asList(Collections.<String, String>emptyMap(),
                Collections.singletonMap("owned", ""), Collections.singletonMap("owned", "null"),
                Collections.singletonMap("owned", JWT))) {
            assertEquals(JWT, PlexServerCredentials.compatible("owned", true, JWT, devices));
        }
    }
}
