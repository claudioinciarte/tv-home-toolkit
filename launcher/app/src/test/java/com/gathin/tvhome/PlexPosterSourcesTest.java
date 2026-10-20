package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class PlexPosterSourcesTest {
    @Test public void prefersTheWorkingUrlThenTriesUniqueFallbacks() {
        assertEquals(Arrays.asList("https://working", "https://local", "https://relay"),
                PlexPosterSources.ordered("https://working",
                        Arrays.asList("https://local", "https://working", "", "https://relay")));
    }

    @Test public void usesAvailableUrlsWhenCachedItemHasNoWorkingUrl() {
        assertEquals(Collections.singletonList("https://local"),
                PlexPosterSources.ordered(null, Collections.singletonList("https://local")));
    }
}