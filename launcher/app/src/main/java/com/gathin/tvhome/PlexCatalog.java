package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure catalogue ordering and identity rules shared by movies and grouped series. */
final class PlexCatalog {
    private PlexCatalog() {}

    static List<PlexManager.MediaItem> mergeAndLimit(List<PlexManager.MediaItem> source, int limit) {
        Map<String, PlexManager.MediaItem> unique = new LinkedHashMap<>();
        for (PlexManager.MediaItem item : source) {
            String identity = item.serverId + "|" + item.ratingKey + "|" + item.type;
            PlexManager.MediaItem current = unique.get(identity);
            if (current == null || item.addedAt > current.addedAt) unique.put(identity, item);
        }
        List<PlexManager.MediaItem> result = new ArrayList<>(unique.values());
        Collections.sort(result, (a, b) -> Long.compare(b.addedAt, a.addedAt));
        if (limit >= 0 && result.size() > limit) return new ArrayList<>(result.subList(0, limit));
        return result;
    }

    static List<PlexManager.MediaItem> filterSelection(List<PlexManager.MediaItem> source,
                                                        Set<String> servers, Set<String> libraries,
                                                        boolean allLibraries) {
        List<PlexManager.MediaItem> result = new ArrayList<>();
        for (PlexManager.MediaItem item : source)
            if (servers.contains(item.serverId)
                    && (allLibraries || libraries.contains(item.serverId + "|" + item.libraryId)))
                result.add(item);
        return result;
    }
}
