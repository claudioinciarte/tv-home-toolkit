package com.gathin.tvhome;

import java.util.Map;

final class PlexServerCredentials {
    private PlexServerCredentials() {}

    static boolean isJwt(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    }

    static String compatible(String serverId, boolean owned, String resourceToken,
                             Map<String, String> deviceTokens) {
        if (!owned || !isJwt(resourceToken)) return resourceToken;
        String deviceToken = deviceTokens.get(serverId);
        return deviceToken != null && !deviceToken.isEmpty() && !deviceToken.equals("null")
                && !isJwt(deviceToken) ? deviceToken : resourceToken;
    }
}
