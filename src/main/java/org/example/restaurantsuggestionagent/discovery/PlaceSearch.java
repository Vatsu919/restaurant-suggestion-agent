package org.example.restaurantsuggestionagent.discovery;

import java.util.List;

/** Finds restaurants matching a free-text query. */
public interface PlaceSearch {

    /**
     * @param languageCode language for names and review translations, e.g. "en"
     * @param maxResults   upper bound on returned places; fewer may exist
     */
    List<PlaceCandidate> search(String textQuery, String languageCode, int maxResults);
}
