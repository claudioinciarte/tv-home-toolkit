package com.gathin.tvhome;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/** Builds a DNS-free local route while retaining the Plex Direct TLS identity. */
final class PlexLocalEndpoint {
    final String address, tlsHost, baseUrl;
    final int port;

    private PlexLocalEndpoint(String address, int port, String tlsHost) {
        this.address = address;
        this.port = port;
        this.tlsHost = tlsHost;
        this.baseUrl = "https://" + tlsHost + ":" + port;
    }

    static PlexLocalEndpoint create(List<String> serverUrls, String address, int port) throws IOException {
        if (!isPrivateIpv4(address)) throw new IOException("Introduce una dirección IPv4 privada de la red local.");
        if (port < 1 || port > 65535) throw new IOException("El puerto debe estar entre 1 y 65535.");
        for (String value : serverUrls) {
            try {
                URL url = new URL(value);
                String host = url.getHost().toLowerCase(java.util.Locale.ROOT);
                int dot = host.indexOf('.');
                if (!"https".equalsIgnoreCase(url.getProtocol()) || dot < 0
                        || !host.substring(0, dot).matches("(?:[0-9]{1,3}-){3}[0-9]{1,3}")) continue;
                String suffix = host.substring(dot + 1);
                if (suffix.endsWith(".plex.direct"))
                    return new PlexLocalEndpoint(address, port, address.replace('.', '-') + "." + suffix);
            } catch (Exception ignored) {
            }
        }
        throw new IOException("Plex no anunció un dominio plex.direct para validar este servidor por HTTPS.");
    }

    static boolean isPrivateIpv4(String address) {
        if (address == null) return false;
        String[] parts = address.trim().split("\\.", -1);
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("[0-9]{1,3}")) return false;
            try { octets[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException ignored) { return false; }
            if (octets[i] > 255) return false;
        }
        return octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }
}
