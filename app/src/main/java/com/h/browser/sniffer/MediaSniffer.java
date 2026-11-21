package com.h.browser.sniffer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class MediaSniffer {

    private static final LinkedHashSet<String> URLS = new LinkedHashSet<>();

    private MediaSniffer() {
    }

    public static synchronized void add(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        URLS.add(url);
        while (URLS.size() > 100) {
            String first = URLS.iterator().next();
            URLS.remove(first);
        }
    }

    public static synchronized List<String> list() {
        return new ArrayList<>(URLS);
    }

    public static synchronized void clear() {
        URLS.clear();
    }
}
