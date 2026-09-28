package com.aletv.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LogoLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(24 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private LogoLoader() {}

    public static void load(ImageView imageView, TextView initialsView, Channel channel) {
        String url = channel.logoUrl == null ? "" : channel.logoUrl.trim();
        String initials = initials(channel.name);

        imageView.setTag(url);
        imageView.setImageDrawable(null);
        initialsView.setText(initials);
        initialsView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);

        if (url.isEmpty()) return;

        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            showIfCurrent(imageView, initialsView, url, cached);
            return;
        }

        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(url);
            if (bitmap != null) CACHE.put(url, bitmap);

            imageView.post(() -> {
                if (bitmap != null) {
                    showIfCurrent(imageView, initialsView, url, bitmap);
                }
            });
        });
    }

    private static void showIfCurrent(
            ImageView imageView,
            TextView initialsView,
            String expectedUrl,
            Bitmap bitmap
    ) {
        Object tag = imageView.getTag();
        if (tag == null || !expectedUrl.equals(tag.toString())) return;

        imageView.setImageBitmap(bitmap);
        imageView.setVisibility(View.VISIBLE);
        initialsView.setVisibility(View.GONE);
    }

    private static Bitmap download(String urlString) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(9000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "AleTV/1.2 FireTV");
            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) return null;

            String type = conn.getContentType();
            if (type != null && type.toLowerCase(Locale.US).contains("svg")) {
                return null;
            }

            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "TV";

        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (sb.length() >= 2) break;
            }
        }

        return sb.length() == 0 ? "TV" : sb.toString();
    }
}
