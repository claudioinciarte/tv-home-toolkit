package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlexRatingTest {
    @Test public void formatsOnlyPlexImdbAndRottenTomatoesRatings() {
        assertEquals("IMDb 8.4", PlexRating.format("imdb://image.rating", "8.4"));
        assertEquals("RT 9.1/10", PlexRating.format("rottentomatoes://image.rating.ripe", "9.1"));
        assertEquals("", PlexRating.format("themoviedb://image.rating", "8.4"));
        assertEquals("", PlexRating.format("imdb://image.rating", "0"));
    }
}
