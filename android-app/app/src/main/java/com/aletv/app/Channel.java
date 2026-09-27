package com.aletv.app;

public final class Channel {
    public final String id;
    public final String name;
    public final String group;
    public final String url;
    public final String userAgent;
    public final String referer;
    public final String origin;

    public Channel(
            String id,
            String name,
            String group,
            String url,
            String userAgent,
            String referer,
            String origin
    ) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "Canal" : name;
        this.group = group == null || group.isEmpty() ? "Otros" : group;
        this.url = url == null ? "" : url;
        this.userAgent = userAgent == null ? "" : userAgent;
        this.referer = referer == null ? "" : referer;
        this.origin = origin == null ? "" : origin;
    }
}
