package com.aletv.app;

import java.io.Serializable;
import java.util.ArrayList;

public final class Channel implements Serializable {
    private static final long serialVersionUID = 3L;

    public final String id;
    public final String name;
    public final String group;
    public final String url;
    public final String logoUrl;
    public final ArrayList<String> backupUrls;
    public final String userAgent;
    public final String referer;
    public final String origin;

    public Channel(
            String id,
            String name,
            String group,
            String url,
            String logoUrl,
            ArrayList<String> backupUrls,
            String userAgent,
            String referer,
            String origin
    ) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "Canal" : name;
        this.group = group == null || group.isEmpty() ? "Otros" : group;
        this.url = url == null ? "" : url;
        this.logoUrl = logoUrl == null ? "" : logoUrl;
        this.backupUrls = backupUrls == null ? new ArrayList<>() : new ArrayList<>(backupUrls);
        this.userAgent = userAgent == null ? "" : userAgent;
        this.referer = referer == null ? "" : referer;
        this.origin = origin == null ? "" : origin;
    }
}
