package com.h.browser.adblock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class Rules {

    public static final class Counts {
        public final int hostRules;
        public final int netRules;
        public final int exceptions;
        public final int cosmeticRules;
        public final int unsupported;

        Counts(int hostRules, int netRules, int exceptions, int cosmeticRules, int unsupported) {
            this.hostRules = hostRules;
            this.netRules = netRules;
            this.exceptions = exceptions;
            this.cosmeticRules = cosmeticRules;
            this.unsupported = unsupported;
        }

        public int total() {
            return hostRules + netRules + cosmeticRules;
        }
    }

    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            "image", "script", "stylesheet", "object", "xmlhttprequest", "subdocument",
            "document", "media", "font", "other", "ping", "popup", "websocket"));

    static final class NetRule {
        final String hostKey;
        final Pattern pattern;
        final String[] includeDomains;
        final String[] excludeDomains;
        final boolean thirdParty;
        final boolean notThirdParty;
        final Set<String> allowTypes;
        final Set<String> denyTypes;

        NetRule(String hostKey, Pattern pattern, String[] includeDomains, String[] excludeDomains,
                boolean thirdParty, boolean notThirdParty,
                Set<String> allowTypes, Set<String> denyTypes) {
            this.hostKey = hostKey;
            this.pattern = pattern;
            this.includeDomains = includeDomains;
            this.excludeDomains = excludeDomains;
            this.thirdParty = thirdParty;
            this.notThirdParty = notThirdParty;
            this.allowTypes = allowTypes;
            this.denyTypes = denyTypes;
        }

        boolean matches(String url, String host, String docHost, String type) {
            if (hostKey != null && !suffixMatch(host, hostKey)) {
                return false;
            }
            if (pattern != null && !pattern.matcher(url).find()) {
                return false;
            }
            if (thirdParty && !isThirdParty(host, docHost)) {
                return false;
            }
            if (notThirdParty && isThirdParty(host, docHost)) {
                return false;
            }
            if (includeDomains.length > 0 && !matchesAny(docHost, includeDomains)) {
                return false;
            }
            if (matchesAny(docHost, excludeDomains)) {
                return false;
            }
            if (!allowTypes.isEmpty() && !allowTypes.contains(type)) {
                return false;
            }
            return !denyTypes.contains(type);
        }
    }

    public static final Rules EMPTY = new Builder().build();

    private final Set<String> blockHosts;
    private final Map<String, List<NetRule>> blockByHost;
    private final List<NetRule> blockAny;
    private final Set<String> exceptionHosts;
    private final Map<String, List<NetRule>> exceptionByHost;
    private final List<NetRule> exceptionAny;
    private final List<String> genericCosmetic;
    private final Set<String> genericCosmeticExceptions;
    private final Map<String, List<String>> domainCosmetic;
    private final Map<String, Set<String>> domainCosmeticExceptions;
    public final Counts counts;

    private Rules(Builder b) {
        blockHosts = Collections.unmodifiableSet(b.blockHosts);
        blockByHost = freeze(b.blockByHost);
        blockAny = Collections.unmodifiableList(new ArrayList<>(b.blockAny));
        exceptionHosts = Collections.unmodifiableSet(b.exceptionHosts);
        exceptionByHost = freeze(b.exceptionByHost);
        exceptionAny = Collections.unmodifiableList(new ArrayList<>(b.exceptionAny));
        genericCosmetic = Collections.unmodifiableList(new ArrayList<>(b.genericCosmetic));
        genericCosmeticExceptions = Collections.unmodifiableSet(new HashSet<>(b.genericCosmeticExceptions));
        Map<String, List<String>> cosmetic = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : b.domainCosmetic.entrySet()) {
            cosmetic.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        domainCosmetic = Collections.unmodifiableMap(cosmetic);
        Map<String, Set<String>> cosmeticExceptions = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : b.domainCosmeticExceptions.entrySet()) {
            cosmeticExceptions.put(e.getKey(),
                    Collections.unmodifiableSet(new HashSet<>(e.getValue())));
        }
        domainCosmeticExceptions = Collections.unmodifiableMap(cosmeticExceptions);
        int netCount = blockAny.size();
        for (List<NetRule> list : blockByHost.values()) {
            netCount += list.size();
        }
        int exceptionCount = exceptionHosts.size() + exceptionAny.size();
        for (List<NetRule> list : exceptionByHost.values()) {
            exceptionCount += list.size();
        }
        int cosmeticCount = genericCosmetic.size();
        for (List<String> list : cosmetic.values()) {
            cosmeticCount += list.size();
        }
        counts = new Counts(blockHosts.size(), netCount, exceptionCount, cosmeticCount, b.unsupported);
    }

    private static Map<String, List<NetRule>> freeze(Map<String, List<NetRule>> source) {
        Map<String, List<NetRule>> out = new HashMap<>();
        for (Map.Entry<String, List<NetRule>> e : source.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    public boolean blocks(String url, String documentHost, String type) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String host = host(url);
        if (host.isEmpty()) {
            return false;
        }
        if (matchesHost(exceptionHosts, host)) {
            return false;
        }
        for (String suffix : suffixes(host)) {
            List<NetRule> list = exceptionByHost.get(suffix);
            if (list != null) {
                for (NetRule rule : list) {
                    if (rule.matches(url, host, documentHost, type)) {
                        return false;
                    }
                }
            }
        }
        for (NetRule rule : exceptionAny) {
            if (rule.matches(url, host, documentHost, type)) {
                return false;
            }
        }
        if (matchesHost(blockHosts, host)) {
            return true;
        }
        for (String suffix : suffixes(host)) {
            List<NetRule> list = blockByHost.get(suffix);
            if (list != null) {
                for (NetRule rule : list) {
                    if (rule.matches(url, host, documentHost, type)) {
                        return true;
                    }
                }
            }
        }
        for (NetRule rule : blockAny) {
            if (rule.matches(url, host, documentHost, type)) {
                return true;
            }
        }
        return false;
    }

    public List<String> cosmeticFor(String host) {
        LinkedHashSet<String> out = new LinkedHashSet<>(genericCosmetic);
        out.removeAll(genericCosmeticExceptions);
        if (host == null || host.isEmpty()) {
            return new ArrayList<>(out);
        }
        List<String> keys = suffixes(host);
        for (String key : keys) {
            List<String> list = domainCosmetic.get(key);
            if (list != null) {
                out.addAll(list);
            }
        }
        for (String key : keys) {
            Set<String> exceptions = domainCosmeticExceptions.get(key);
            if (exceptions != null) {
                out.removeAll(exceptions);
            }
        }
        return new ArrayList<>(out);
    }

    static boolean suffixMatch(String host, String rule) {
        if (host == null || rule == null || rule.isEmpty()) {
            return false;
        }
        return host.equals(rule) || host.endsWith("." + rule);
    }

    static boolean matchesHost(Set<String> set, String host) {
        for (String suffix : suffixes(host)) {
            if (set.contains(suffix)) {
                return true;
            }
        }
        return false;
    }

    static boolean matchesAny(String host, String[] domains) {
        if (domains.length == 0 || host == null || host.isEmpty()) {
            return false;
        }
        for (String domain : domains) {
            if (suffixMatch(host, domain)) {
                return true;
            }
        }
        return false;
    }

    static List<String> suffixes(String host) {
        List<String> out = new ArrayList<>(4);
        if (host == null || host.isEmpty()) {
            return out;
        }
        int start = 0;
        while (start < host.length()) {
            out.add(host.substring(start));
            int dot = host.indexOf('.', start);
            if (dot < 0) {
                break;
            }
            start = dot + 1;
        }
        return out;
    }

    static boolean isThirdParty(String host, String docHost) {
        if (docHost == null || docHost.isEmpty() || host == null || host.isEmpty()) {
            return false;
        }
        return !(host.equals(docHost) || host.endsWith("." + docHost) || docHost.endsWith("." + host));
    }

    static String host(String url) {
        int scheme = url.indexOf("://");
        int start = scheme >= 0 ? scheme + 3 : 0;
        int end = url.length();
        for (int i = start; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String host = url.substring(start, end);
        int at = host.lastIndexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }
        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            if (close > 0) {
                host = host.substring(0, close + 1);
            }
        } else {
            int colon = host.indexOf(':');
            if (colon >= 0) {
                host = host.substring(0, colon);
            }
        }
        return host.toLowerCase(Locale.US);
    }

    public static String requestType(String url) {
        String path = url;
        int cut = path.length();
        int q = path.indexOf('?');
        if (q >= 0 && q < cut) cut = q;
        int h = path.indexOf('#');
        if (h >= 0 && h < cut) cut = h;
        path = path.substring(0, cut).toLowerCase(Locale.US);
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        int dot = path.lastIndexOf('.');
        String ext = dot >= 0 ? path.substring(dot + 1) : "";
        switch (ext) {
            case "png": case "jpg": case "jpeg": case "gif": case "webp": case "bmp":
            case "svg": case "ico": case "avif":
                return "image";
            case "js": case "mjs":
                return "script";
            case "css":
                return "stylesheet";
            case "woff": case "woff2": case "ttf": case "otf": case "eot":
                return "font";
            case "mp4": case "m4s": case "webm": case "mp3": case "m4a": case "ogg":
            case "wav": case "flac": case "aac": case "ts":
                return "media";
            default:
                return "other";
        }
    }

    public static boolean safeSelector(String selector) {
        if (selector == null) {
            return false;
        }
        String s = selector.trim();
        if (s.isEmpty() || s.length() > 300) {
            return false;
        }
        String lower = s.toLowerCase(Locale.US);
        if (lower.contains("url(") || lower.contains("expression(")) {
            return false;
        }
        int paren = 0;
        int bracket = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7f || c == '{' || c == '}' || c == ';' || c == '\\') {
                return false;
            }
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                paren++;
            } else if (c == ')') {
                if (--paren < 0) return false;
            } else if (c == '[') {
                bracket++;
            } else if (c == ']') {
                if (--bracket < 0) return false;
            }
        }
        return quote == 0 && paren == 0 && bracket == 0;
    }

    public String describe() {
        return "host " + counts.hostRules + " · net " + counts.netRules
                + " · exception " + counts.exceptions + " · cosmetic " + counts.cosmeticRules
                + " · unsupported " + counts.unsupported;
    }

    public static Rules parse(String content) {
        Builder b = new Builder();
        if (content == null) {
            return b.build();
        }
        String[] lines = content.split("\n");
        for (String raw : lines) {
            b.addLine(raw);
        }
        return b.build();
    }

    private static final class Builder {
        final Set<String> blockHosts = new HashSet<>();
        final Map<String, List<NetRule>> blockByHost = new HashMap<>();
        final List<NetRule> blockAny = new ArrayList<>();
        final Set<String> exceptionHosts = new HashSet<>();
        final Map<String, List<NetRule>> exceptionByHost = new HashMap<>();
        final List<NetRule> exceptionAny = new ArrayList<>();
        final LinkedHashSet<String> genericCosmetic = new LinkedHashSet<>();
        final Set<String> genericCosmeticExceptions = new HashSet<>();
        final Map<String, LinkedHashSet<String>> domainCosmetic = new LinkedHashMap<>();
        final Map<String, LinkedHashSet<String>> domainCosmeticExceptions = new LinkedHashMap<>();
        int unsupported;

        void addLine(String raw) {
            if (raw == null) {
                return;
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[Adblock")
                    || line.startsWith("[Adblock Plus")) {
                return;
            }
            if (line.startsWith("#?#") || line.startsWith("#$#") || line.startsWith("#%#")
                    || line.startsWith("#@?#") || line.startsWith("#@$#")) {
                unsupported++;
                return;
            }
            int marker = cosmeticMarker(line);
            if (marker >= 0) {
                addCosmetic(line, marker);
                return;
            }
            if (line.startsWith("#")) {
                return;
            }
            addNetwork(line);
        }

        private static int cosmeticMarker(String line) {
            String[] markers = {"#@#", "#?#", "#$#", "#%#", "##"};
            int best = -1;
            for (String marker : markers) {
                int index = line.indexOf(marker);
                if (index >= 0 && (best < 0 || index < best)
                        && (marker.equals("##") || index == 0 || line.charAt(index - 1) != '#')) {
                    best = index;
                }
            }
            return best;
        }

        private void addCosmetic(String line, int marker) {
            String markerText;
            if (line.startsWith("#", marker)) {
                if (line.startsWith("#@#", marker)) {
                    markerText = "#@#";
                } else if (line.startsWith("#?#", marker) || line.startsWith("#$#", marker)
                        || line.startsWith("#%#", marker)) {
                    unsupported++;
                    return;
                } else {
                    markerText = "##";
                }
            } else if (line.startsWith("#@#", marker)) {
                markerText = "#@#";
            } else if (line.startsWith("#?#", marker) || line.startsWith("#$#", marker)
                    || line.startsWith("#%#", marker)) {
                unsupported++;
                return;
            } else {
                markerText = "##";
            }
            String domainsPart = line.substring(0, marker).trim();
            String selector = line.substring(marker + markerText.length()).trim();
            if (selector.startsWith("+js(") || selector.startsWith("^")) {
                unsupported++;
                return;
            }
            if (!safeSelector(selector)) {
                unsupported++;
                return;
            }
            String[] parts = domainsPart.isEmpty() ? new String[0] : domainsPart.split(",");
            List<String> includes = new ArrayList<>();
            List<String> excludes = new ArrayList<>();
            for (String part : parts) {
                String d = part.trim().toLowerCase(Locale.US);
                if (d.isEmpty()) {
                    continue;
                }
                if (d.startsWith("~")) {
                    String value = cleanDomain(d.substring(1));
                    if (!value.isEmpty()) excludes.add(value);
                } else {
                    String value = cleanDomain(d);
                    if (!value.isEmpty()) includes.add(value);
                }
            }
            boolean exception = markerText.equals("#@#");
            if (includes.isEmpty()) {
                if (exception) {
                    genericCosmeticExceptions.add(selector);
                } else if (!excludes.isEmpty()) {
                    unsupported++;
                } else {
                    genericCosmetic.add(selector);
                }
                return;
            }
            for (String domain : includes) {
                Map<String, LinkedHashSet<String>> target =
                        exception ? domainCosmeticExceptions : domainCosmetic;
                LinkedHashSet<String> list = target.get(domain);
                if (list == null) {
                    list = new LinkedHashSet<>();
                    target.put(domain, list);
                }
                list.add(selector);
            }
            if (!exception) {
                for (String domain : excludes) {
                    LinkedHashSet<String> list = domainCosmeticExceptions.get(domain);
                    if (list == null) {
                        list = new LinkedHashSet<>();
                        domainCosmeticExceptions.put(domain, list);
                    }
                    list.add(selector);
                }
            }
        }

        private void addNetwork(String line) {
            boolean exception = line.startsWith("@@");
            String body = exception ? line.substring(2).trim() : line;
            if (body.isEmpty()) {
                return;
            }
            String options = null;
            int dollar = body.indexOf('$');
            if (dollar >= 0) {
                options = body.substring(dollar + 1);
                body = body.substring(0, dollar).trim();
            }
            if (body.startsWith("/") && body.lastIndexOf('/') > 0) {
                unsupported++;
                return;
            }
            String lower = body.toLowerCase(Locale.US);
            if (lower.startsWith("0.0.0.0 ") || lower.startsWith("127.0.0.1 ")
                    || lower.startsWith("::1 ")) {
                body = body.substring(body.indexOf(' ') + 1).trim();
                String hostPart = body.split("\\s+")[0].trim();
                String host = cleanDomain(hostPart);
                if (host.isEmpty()) {
                    unsupported++;
                } else if (exception) {
                    exceptionHosts.add(host);
                } else {
                    blockHosts.add(host);
                }
                return;
            }
            boolean thirdParty = false;
            boolean notThirdParty = false;
            List<String> includeDomains = new ArrayList<>();
            List<String> excludeDomains = new ArrayList<>();
            Set<String> allowTypes = new HashSet<>();
            Set<String> denyTypes = new HashSet<>();
            boolean skip = false;
            if (options != null && !options.isEmpty()) {
                String[] parts = options.split(",");
                for (String rawOption : parts) {
                    String option = rawOption.trim().toLowerCase(Locale.US);
                    if (option.isEmpty() || option.equals("important")) {
                        continue;
                    }
                    boolean negated = option.startsWith("~");
                    if (negated) {
                        option = option.substring(1);
                    }
                    if (option.equals("3p")) {
                        option = "third-party";
                    } else if (option.equals("1p")) {
                        option = "first-party";
                    } else if (option.equals("xhr")) {
                        option = "xmlhttprequest";
                    } else if (option.equals("css")) {
                        option = "stylesheet";
                    } else if (option.equals("frame")) {
                        option = "subdocument";
                    } else if (option.equals("doc")) {
                        option = "document";
                    }
                    if (option.equals("third-party")) {
                        if (negated) notThirdParty = true;
                        else thirdParty = true;
                    } else if (option.equals("first-party")) {
                        if (negated) thirdParty = true;
                        else notThirdParty = true;
                    } else if (option.startsWith("domain=")) {
                        String value = option.substring(7);
                        for (String domain : value.split("\\|")) {
                            String d = domain.trim();
                            if (d.isEmpty()) continue;
                            if (d.startsWith("~")) {
                                String clean = cleanDomain(d.substring(1));
                                if (!clean.isEmpty()) excludeDomains.add(clean);
                            } else {
                                String clean = cleanDomain(d);
                                if (!clean.isEmpty()) includeDomains.add(clean);
                            }
                        }
                    } else if (TYPES.contains(option)) {
                        if (negated) denyTypes.add(option);
                        else allowTypes.add(option);
                    } else {
                        unsupported++;
                        skip = true;
                        break;
                    }
                }
            }
            if (skip) {
                return;
            }
            if (allowTypes.remove("document")) {
                if (allowTypes.isEmpty()) {
                    return;
                }
            }
            Pattern pattern = null;
            String hostKey = null;
            boolean hostOnly = false;
            if (body.startsWith("||")) {
                String rest = body.substring(2);
                int cut = rest.length();
                for (int i = 0; i < rest.length(); i++) {
                    char c = rest.charAt(i);
                    if (c == '/' || c == '^' || c == '?' || c == '*' || c == '|') {
                        cut = i;
                        break;
                    }
                }
                String hostPart = rest.substring(0, cut);
                String tail = rest.substring(cut);
                if (hostPart.startsWith("*.")) {
                    hostPart = hostPart.substring(2);
                }
                hostKey = cleanDomain(hostPart);
                if (hostKey.isEmpty()) {
                    unsupported++;
                    return;
                }
                boolean endAnchor = tail.endsWith("|");
                if (endAnchor) tail = tail.substring(0, tail.length() - 1);
                if (tail.isEmpty() || tail.equals("^")) {
                    hostOnly = true;
                } else {
                    pattern = hostPattern(hostKey, tail, endAnchor);
                }
            } else if (body.startsWith("|")) {
                String rest = body.substring(1);
                boolean endAnchor = rest.endsWith("|");
                if (endAnchor) rest = rest.substring(0, rest.length() - 1);
                if (rest.isEmpty()) return;
                pattern = generalPattern(rest, true, endAnchor);
            } else if (!body.contains("/") && !body.contains("*") && !body.contains("^")
                    && !body.contains("?") && !body.contains("|")) {
                String host = cleanDomain(body);
                if (host.isEmpty()) {
                    unsupported++;
                    return;
                }
                hostKey = host;
                hostOnly = true;
            } else {
                boolean endAnchor = body.endsWith("|");
                if (endAnchor) body = body.substring(0, body.length() - 1);
                pattern = generalPattern(body, false, endAnchor);
            }
            boolean simple = hostOnly && includeDomains.isEmpty() && excludeDomains.isEmpty()
                    && !thirdParty && !notThirdParty && allowTypes.isEmpty() && denyTypes.isEmpty();
            if (exception && simple) {
                exceptionHosts.add(hostKey);
                return;
            }
            NetRule rule = new NetRule(hostKey, pattern,
                    includeDomains.toArray(new String[0]), excludeDomains.toArray(new String[0]),
                    thirdParty, notThirdParty, allowTypes, denyTypes);
            if (exception) {
                if (hostKey != null) {
                    put(exceptionByHost, hostKey, rule);
                } else {
                    exceptionAny.add(rule);
                }
            } else if (simple) {
                blockHosts.add(hostKey);
            } else if (hostKey != null) {
                put(blockByHost, hostKey, rule);
            } else {
                blockAny.add(rule);
            }
        }

        private static void put(Map<String, List<NetRule>> map, String key, NetRule rule) {
            List<NetRule> list = map.get(key);
            if (list == null) {
                list = new ArrayList<>();
                map.put(key, list);
            }
            list.add(rule);
        }

        private static String cleanDomain(String value) {
            String d = value.trim().toLowerCase(Locale.US);
            if (d.startsWith("www.")) {
                d = d.substring(4);
            }
            if (d.endsWith(".")) {
                d = d.substring(0, d.length() - 1);
            }
            if (d.isEmpty() || d.length() > 253 || d.contains(":") || d.contains("/")
                    || d.contains(" ") || !d.contains(".")) {
                return "";
            }
            for (int i = 0; i < d.length(); i++) {
                char c = d.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-')) {
                    return "";
                }
            }
            return d;
        }

        private static Pattern hostPattern(String host, String tail, boolean endAnchor) {
            StringBuilder sb = new StringBuilder("^[a-z][a-z0-9+.-]*://(?:[^/]*\\.)?");
            sb.append(Pattern.quote(host));
            sb.append("(?::\\d+)?");
            if (tail.startsWith("^")) {
                sb.append("(?:[^a-zA-Z0-9_.%-]|$)");
                tail = tail.substring(1);
            }
            appendGlob(sb, tail);
            if (endAnchor) {
                sb.append('$');
            }
            return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
        }

        private static Pattern generalPattern(String body, boolean anchorStart, boolean endAnchor) {
            StringBuilder sb = new StringBuilder();
            if (anchorStart) {
                sb.append('^');
            }
            appendGlob(sb, body);
            if (endAnchor) {
                sb.append('$');
            }
            return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
        }

        private static void appendGlob(StringBuilder sb, String body) {
            int i = 0;
            while (i < body.length()) {
                char c = body.charAt(i);
                if (c == '*') {
                    sb.append(".*");
                    i++;
                } else if (c == '^') {
                    sb.append("(?:[^a-zA-Z0-9_.%-]|$)");
                    i++;
                } else {
                    int j = i;
                    while (j < body.length() && body.charAt(j) != '*' && body.charAt(j) != '^') {
                        j++;
                    }
                    sb.append(Pattern.quote(body.substring(i, j)));
                    i = j;
                }
            }
        }

        Rules build() {
            return new Rules(this);
        }
    }
}
