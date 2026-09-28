package com.aletv.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaylistParser {
    private static final Pattern ATTR_PATTERN =
            Pattern.compile("([\\w-]+)=\"([^\"]*)\"");

    private PlaylistParser() {}

    public static List<Channel> parse(String text) {
        List<Channel> channels = new ArrayList<>();
        if (text == null) return channels;

        String[] lines = text.replace("\r", "").split("\n");
        String extinf = null;
        String userAgent = "";
        String referer = "";
        String origin = "";

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF:")) {
                extinf = line;
                userAgent = "";
                referer = "";
                origin = "";
                continue;
            }

            if (extinf != null && line.startsWith("#EXTVLCOPT:")) {
                String opt = line.substring("#EXTVLCOPT:".length());
                int eq = opt.indexOf('=');
                if (eq > 0) {
                    String key = opt.substring(0, eq).trim().toLowerCase(Locale.US);
                    String value = opt.substring(eq + 1).trim();
                    if (key.equals("http-user-agent")) userAgent = value;
                    if (key.equals("http-referrer") || key.equals("http-referer")) referer = value;
                    if (key.equals("http-origin")) origin = value;
                }
                continue;
            }

            if (extinf != null && line.startsWith("#EXTHTTP:")) {
                String json = line.substring("#EXTHTTP:".length()).trim();
                userAgent = extractJsonValue(json, "User-Agent", userAgent);
                referer = extractJsonValue(json, "Referer", referer);
                referer = extractJsonValue(json, "Referrer", referer);
                origin = extractJsonValue(json, "Origin", origin);
                continue;
            }

            if (extinf != null && line.startsWith("#")) {
                continue;
            }

            if (extinf != null && !line.startsWith("#")) {
                Channel c = parseEntry(extinf, line, userAgent, referer, origin);
                if (c != null && !c.url.isEmpty()) {
                    channels.add(c);
                }
                extinf = null;
                userAgent = "";
                referer = "";
                origin = "";
            }
        }

        return channels;
    }

    private static Channel parseEntry(
            String extinf,
            String url,
            String userAgent,
            String referer,
            String origin
    ) {
        int comma = extinf.indexOf(',');
        String name = comma >= 0 ? extinf.substring(comma + 1).trim() : "Canal";
        String header = comma >= 0 ? extinf.substring(0, comma) : extinf;

        String id = attr(header, "tvg-id");
        String group = attr(header, "group-title");
        String logo = attr(header, "tvg-logo");
        name = cleanName(name);

        return new Channel(id, name, group, url.trim(), logo, userAgent, referer, origin);
    }

    private static String attr(String header, String key) {
        Matcher matcher = ATTR_PATTERN.matcher(header);
        while (matcher.find()) {
            if (key.equalsIgnoreCase(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return "";
    }

    private static String cleanName(String value) {
        String v = value == null ? "" : value;
        v = v.replaceAll(
                "(?i)\\s*\\((2160p|1440p|1080p|1080i|720p|576p|576i|540p|480p|480i|360p|240p|4k|uhd|fhd|hd|sd)\\)\\s*",
                " "
        );
        return v.replaceAll("\\s{2,}", " ").trim();
    }

    private static String extractJsonValue(String json, String key, String fallback) {
        Pattern p = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : fallback;
    }
}
