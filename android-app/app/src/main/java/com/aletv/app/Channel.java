package com.aletv.app;

import java.io.Serializable;

public final class Channel implements Serializable {
    private static final long serialVersionUID = 2L;

    public final String id;
    public final String name;
    public final String group;
    public final String url;
    public final String logoUrl;
    public final String userAgent;
    public final String referer;
    public final String origin;

    public Channel(
            String id,
            String name,
            String group,
            String url,
            String logoUrl,
            String userAgent,
            String referer,
            String origin
    ) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "Canal" : name;
        this.group = group == null || group.isEmpty() ? "Otros" : group;
        this.url = url == null ? "" : url;
        this.logoUrl = logoUrl == null ? "" : logoUrl;
        this.userAgent = userAgent == null ? "" : userAgent;
        this.referer = referer == null ? "" : referer;
        this.origin = origin == null ? "" : origin;
    }
}
