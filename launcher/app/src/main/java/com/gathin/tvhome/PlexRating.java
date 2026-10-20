package com.gathin.tvhome;

/** Formats only ratings whose Plex metadata identifies IMDb or Rotten Tomatoes as the source. */
final class PlexRating {
    private PlexRating() { }

    static String format(String image, String rawScore) {
        if (image == null) return "";
        String sourceId = image.toLowerCase(java.util.Locale.ROOT);
        String source = sourceId.contains("imdb") ? "IMDb"
                : sourceId.contains("rottentomatoes") ? "RT" : "";
        if (source.isEmpty()) return "";
        try {
            double score = Double.parseDouble(rawScore);
            if (score > 0 && score <= 10) return source + " "
                    + String.format(java.util.Locale.US, "%.1f", score) + (source.equals("RT") ? "/10" : "");
        } catch (NumberFormatException ignored) {
        }
        return "";
    }
}
