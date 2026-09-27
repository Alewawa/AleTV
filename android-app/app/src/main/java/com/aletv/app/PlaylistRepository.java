package com.aletv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlaylistRepository {
    private static final String CACHE_FILE = "playlist_cache.m3u";

    private static final String[] SOURCES = new String[] {
            "https://alewawa.github.io/AleTV/playlist.m3u",
            "https://raw.githubusercontent.com/Alewawa/AleTV/main/playlist.m3u",
            "https://cdn.jsdelivr.net/gh/Alewawa/AleTV@main/playlist.m3u"
    };

    public interface Callback {
        void onSuccess(List<Channel> channels, String source, boolean fromCache);
        void onError(String message);
    }

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PlaylistRepository(Context context) {
        appContext = context.getApplicationContext();
    }

    public void load(boolean forceRefresh, Callback callback) {
        executor.execute(() -> {
            Exception lastError = null;

            for (String source : SOURCES) {
                try {
                    String url = source;
                    if (forceRefresh && !source.contains("cdn.jsdelivr.net")) {
                        url += "?v=" + System.currentTimeMillis();
                    }

                    String text = download(url);
                    List<Channel> channels = PlaylistParser.parse(text);
                    if (!channels.isEmpty()) {
                        saveCache(text);
                        postSuccess(callback, channels, source, false);
                        return;
                    }
                } catch (Exception e) {
                    lastError = e;
                }
            }

            try {
                String cached = readCache();
                List<Channel> channels = PlaylistParser.parse(cached);
                if (!channels.isEmpty()) {
                    postSuccess(callback, channels, "Copia guardada", true);
                    return;
                }
            } catch (Exception ignored) {
            }

            String msg = "No se pudo descargar la lista.";
            if (lastError != null && lastError.getMessage() != null) {
                msg += " " + lastError.getMessage();
            }
            final String finalMsg = msg;
            mainHandler.post(() -> callback.onError(finalMsg));
        });
    }

    private void postSuccess(
            Callback callback,
            List<Channel> channels,
            String source,
            boolean fromCache
    ) {
        mainHandler.post(() -> callback.onSuccess(channels, source, fromCache));
    }

    private String download(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(18000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "AleTV/1.0 FireTV");
        connection.setRequestProperty("Accept", "*/*");

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new Exception("HTTP " + code);
        }

        try (InputStream in = connection.getInputStream()) {
            return readAll(in);
        } finally {
            connection.disconnect();
        }
    }

    private String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private void saveCache(String text) throws Exception {
        try (FileOutputStream out = appContext.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readCache() throws Exception {
        File file = new File(appContext.getFilesDir(), CACHE_FILE);
        if (!file.exists()) return "";
        try (FileInputStream in = new FileInputStream(file)) {
            return readAll(in);
        }
    }
}
