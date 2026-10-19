package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlexCatalogTest {
    @Test public void deduplicatesWithinServerButKeepsOtherServersAndOrdersNewestFirst() {
        List<PlexManager.MediaItem> result = PlexCatalog.mergeAndLimit(Arrays.asList(
                item("local", "42", "movie", "Old copy", 1),
                item("remote", "42", "movie", "Remote copy", 4),
                item("local", "42", "movie", "New copy", 5),
                item("local", "9", "show", "Series", 3)
        ), 2);

        assertEquals(2, result.size());
        assertEquals("New copy", result.get(0).title);
        assertEquals("Remote copy", result.get(1).title);
    }

    @Test public void filtersCachedItemsBySelectedServerAndLibrary() {
        List<PlexManager.MediaItem> result = PlexCatalog.filterSelection(Arrays.asList(
                item("local", "42", "movie", "Selected library", 1),
                new PlexManager.MediaItem("local", "local", "shows", "9", "/library/metadata/9",
                        "Unselected library", "", "show", "", 2, null, null),
                item("remote", "42", "movie", "Unselected server", 3)
        ), new HashSet<>(Arrays.asList("local")), new HashSet<>(Arrays.asList("local|movies")), false);

        assertEquals(1, result.size());
        assertEquals("Selected library", result.get(0).title);
    }

    private PlexManager.MediaItem item(String server, String id, String type, String title, long addedAt) {
        return new PlexManager.MediaItem(server, server, "movies", id, "/library/metadata/" + id,
                title, "", type, "", addedAt, null, null);
    }
}
