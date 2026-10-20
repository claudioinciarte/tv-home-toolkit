package com.gathin.tvhome;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.List;
import java.util.Collections;
import java.util.Map;

/** Explicit debug-only probe; reports status and credential format, never credential values. */
public final class PlexAuthDiagnostics extends Instrumentation {
    private Bundle arguments;
    private PlexManager plex;
    private Method request;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args;
        start();
    }

    @Override public void onStart() {
        Bundle report = new Bundle();
        try {
            plex = new PlexManager(getTargetContext());
            request = PlexManager.class.getDeclaredMethod("requestJson", URL.class, String.class,
                    JSONObject.class, String.class, PlexLocalEndpoint.class);
            request.setAccessible(true);
            Method current = PlexManager.class.getDeclaredMethod("currentToken");
            current.setAccessible(true);
            String account = (String) invoke(current);
            report.putString("account_credential", kind(account));
            get("https://plex.tv/api/v2/user", account, null);
            report.putString("account_identity", "HTTP 200");
            JSONObject resources = get("https://clients.plex.tv/api/v2/resources?includeHttps=1&includeRelay=1&includeIPv6=1", account, null);
            report.putString("account_resources", "HTTP 200");
            Method parse = PlexManager.class.getDeclaredMethod("parseServers", JSONObject.class, Map.class);
            parse.setAccessible(true);
            @SuppressWarnings("unchecked") List<PlexManager.Server> servers =
                    (List<PlexManager.Server>) invoke(parse, resources, Collections.emptyMap());
            Method fetch = PlexManager.class.getDeclaredMethod("fetchServers", String.class);
            fetch.setAccessible(true);
            @SuppressWarnings("unchecked") List<PlexManager.Server> compatible =
                    (List<PlexManager.Server>) invoke(fetch, account);
            report.putString("compatible_resources", "HTTP 200");
            JSONObject devices = null;
            try {
                devices = get("https://clients.plex.tv/api/v2/devices", account, null);
                report.putString("account_devices", "HTTP 200");
            } catch (Exception e) { report.putString("account_devices", safeError(e)); }
            int index = 0;
            for (PlexManager.Server server : servers) {
                boolean selected = plex.selectedServers().contains(server.id);
                boolean localMatches = server.id.equals(arguments.getString("localMachineId"));
                if (!selected && !localMatches) continue;
                String key = "server_" + (++index) + "_";
                report.putBoolean(key + "selected", selected);
                report.putBoolean(key + "local_machine_matches", localMatches);
                report.putString(key + "resource_credential", kind(server.accessToken));
                String deviceToken = findDeviceToken(devices, server.id);
                report.putString(key + "device_credential", kind(deviceToken));
                report.putBoolean(key + "credentials_equal", server.accessToken.equals(deviceToken));
                PlexManager.Server fixed = server;
                for (PlexManager.Server candidate : compatible) if (candidate.id.equals(server.id)) { fixed = candidate; break; }
                report.putString(key + "production_credential", kind(fixed.accessToken));
                PlexLocalEndpoint endpoint = PlexLocalEndpoint.create(server.urls,
                        arguments.getString("localAddress"), Integer.parseInt(arguments.getString("localPort", "32400")));
                probeServer(report, key + "local_", server, endpoint.baseUrl, endpoint, deviceToken, fixed.accessToken);
                if (localMatches) {
                    try {
                        plex.testLocalRoute(fixed, endpoint.address, endpoint.port);
                        report.putString(key + "production_local_test", "HTTP 200");
                    } catch (Exception e) { report.putString(key + "production_local_test", safeError(e)); }
                }
                int route = 0;
                for (String base : server.urls)
                    probeServer(report, key + "advertised_" + (++route) + "_", server, base, null, deviceToken, fixed.accessToken);
            }
            report.putInt("probed_count", index);
        } catch (Exception e) { report.putString("error", safeError(e)); }
        finish(Activity.RESULT_OK, report);
    }

    private void probeServer(Bundle report, String key, PlexManager.Server server, String base,
                             PlexLocalEndpoint endpoint, String deviceToken, String productionToken) {
        try {
            JSONObject identity = get(base + "/identity", null, endpoint).getJSONObject("MediaContainer");
            boolean matches = server.id.equals(identity.optString("machineIdentifier"));
            report.putBoolean(key + "identity_matches", matches);
            report.putString(key + "server_version", identity.optString("version"));
            if (!matches) return;
        } catch (Exception e) { report.putString(key + "identity", safeError(e)); return; }
        report.putString(key + "resource_library", probe(base, endpoint, server.accessToken));
        report.putString(key + "production_library", productionToken.equals(server.accessToken)
                ? report.getString(key + "resource_library") : probe(base, endpoint, productionToken));
        if (!deviceToken.isEmpty() && !deviceToken.equals(server.accessToken) && !deviceToken.equals(productionToken))
            report.putString(key + "device_library", probe(base, endpoint, deviceToken));
        if (endpoint != null) {
            report.putString(key + "anonymous_library", probe(base, endpoint, null));
            report.putString(key + "invalid_library", probe(base, endpoint, "tvhome-invalid-credential"));
        }
    }

    private String probe(String base, PlexLocalEndpoint endpoint, String token) {
        try {
            get(base + "/library/sections", token, endpoint);
            return "HTTP 200";
        } catch (Exception e) { return safeError(e); }
    }

    private JSONObject get(String url, String token, PlexLocalEndpoint endpoint) throws Exception {
        return (JSONObject) invoke(request, new URL(url), token, null, "GET", endpoint);
    }

    private Object invoke(Method method, Object... args) throws Exception {
        try { return method.invoke(plex, args); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw e;
        }
    }

    private static String findDeviceToken(JSONObject response, String id) {
        if (response == null) return "";
        JSONArray devices = response.optJSONArray("resources");
        if (devices == null) devices = response.optJSONArray("devices");
        JSONObject container = response.optJSONObject("MediaContainer");
        if (devices == null && container != null) devices = container.optJSONArray("Device");
        if (devices == null) return "";
        for (int i = 0; i < devices.length(); i++) {
            JSONObject device = devices.optJSONObject(i);
            if (device == null || !id.equals(device.optString("clientIdentifier"))
                    || !device.optString("provides").contains("server")) continue;
            return device.optString("token", device.optString("accessToken", ""));
        }
        return "";
    }

    private static String kind(String token) {
        if (token == null || token.isEmpty() || token.equals("null")) return "missing";
        return token.split("\\.").length == 3 ? "JWT" : "legacy";
    }

    private static String safeError(Exception error) {
        String message = error.getMessage();
        if (message != null && message.matches("Plex respondió HTTP [0-9]{3}\\.")) return message;
        StringBuilder types = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (types.length() > 0) types.append(" -> ");
            types.append(cause.getClass().getSimpleName());
        }
        return types.toString();
    }
}
