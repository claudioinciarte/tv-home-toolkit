package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

final class PlexPosterSources {
    private PlexPosterSources() {}

    static List<String> ordered(String preferred, List<String> available) {
        List<String> result = new ArrayList<>();
        add(result, preferred);
        if (available != null) for (String url : available) add(result, url);
        return result;
    }

    private static void add(List<String> urls, String url) {
        if (url != null && !url.isEmpty() && !urls.contains(url)) urls.add(url);
    }
}