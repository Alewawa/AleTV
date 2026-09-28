package com.aletv.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

public final class LastChannelStore {
    private static final String PREFS = "aletv_prefs";
    private static final String KEY_ID = "last_channel_id";
    private static final String KEY_NAME = "last_channel_name";
    private static final String KEY_URL = "last_channel_url";

    private LastChannelStore() {}

    public static void save(Context context, Channel channel) {
        if (channel == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_ID, channel.id == null ? "" : channel.id)
                .putString(KEY_NAME, channel.name == null ? "" : channel.name)
                .putString(KEY_URL, channel.url == null ? "" : channel.url)
                .apply();
    }

    public static int findIndex(Context context, List<Channel> channels) {
        if (channels == null || channels.isEmpty()) return -1;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_ID, "");
        String name = prefs.getString(KEY_NAME, "");
        String url = prefs.getString(KEY_URL, "");

        if (id != null && !id.isEmpty()) {
            for (int i = 0; i < channels.size(); i++) {
                if (id.equalsIgnoreCase(channels.get(i).id)) return i;
            }
        }

        if (name != null && !name.isEmpty()) {
            for (int i = 0; i < channels.size(); i++) {
                if (name.equalsIgnoreCase(channels.get(i).name)) return i;
            }
        }

        if (url != null && !url.isEmpty()) {
            for (int i = 0; i < channels.size(); i++) {
                if (url.equals(channels.get(i).url)) return i;
            }
        }

        return -1;
    }

    public static boolean hasLastChannel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_ID, "");
        String name = prefs.getString(KEY_NAME, "");
        String url = prefs.getString(KEY_URL, "");
        return (id != null && !id.isEmpty())
                || (name != null && !name.isEmpty())
                || (url != null && !url.isEmpty());
    }
}
