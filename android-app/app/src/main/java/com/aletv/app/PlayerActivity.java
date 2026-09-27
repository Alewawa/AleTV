package com.aletv.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

public final class PlayerActivity extends Activity {
    private PlayerView playerView;
    private ExoPlayer player;
    private View errorPanel;
    private TextView errorText;

    private String channelName;
    private String streamUrl;
    private String userAgent;
    private String referer;
    private String origin;

    private int automaticRetries = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_player);
        hideSystemUi();

        channelName = safe(getIntent().getStringExtra("name"));
        streamUrl = safe(getIntent().getStringExtra("url"));
        userAgent = safe(getIntent().getStringExtra("userAgent"));
        referer = safe(getIntent().getStringExtra("referer"));
        origin = safe(getIntent().getStringExtra("origin"));

        playerView = findViewById(R.id.playerView);
        errorPanel = findViewById(R.id.errorPanel);
        errorText = findViewById(R.id.playbackErrorText);
        TextView title = findViewById(R.id.channelTitle);
        Button retry = findViewById(R.id.playerRetryButton);
        Button external = findViewById(R.id.openExternalButton);
        Button back = findViewById(R.id.backButton);

        title.setText(channelName.isEmpty() ? "AleTV" : channelName);

        retry.setOnClickListener(v -> {
            automaticRetries = 0;
            errorPanel.setVisibility(View.GONE);
            startPlayback();
        });

        external.setOnClickListener(v -> openExternalPlayer());
        back.setOnClickListener(v -> finish());

        startPlayback();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void startPlayback() {
        releasePlayer();

        if (streamUrl.isEmpty()) {
            showError("Este canal no tiene una URL valida.");
            return;
        }

        DefaultHttpDataSource.Factory httpFactory =
                new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(12000)
                        .setReadTimeoutMs(22000)
                        .setUserAgent(
                                userAgent.isEmpty()
                                        ? "Mozilla/5.0 (Android TV; AleTV/1.0)"
                                        : userAgent
                        );

        Map<String, String> headers = new HashMap<>();
        if (!referer.isEmpty()) headers.put("Referer", referer);
        if (!origin.isEmpty()) headers.put("Origin", origin);
        if (!headers.isEmpty()) {
            httpFactory.setDefaultRequestProperties(headers);
        }

        DefaultDataSource.Factory dataSourceFactory =
                new DefaultDataSource.Factory(this, httpFactory);

        DefaultMediaSourceFactory mediaSourceFactory =
                new DefaultMediaSourceFactory(dataSourceFactory);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();

        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    automaticRetries = 0;
                    errorPanel.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (automaticRetries < 1) {
                    automaticRetries++;
                    handler.postDelayed(PlayerActivity.this::startPlayback, 1600);
                } else {
                    showError(
                            "No se pudo reproducir " + channelName
                                    + ".\n\nPuedes reintentar o abrirlo con VLC."
                    );
                }
            }
        });

        player.setMediaItem(MediaItem.fromUri(streamUrl));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
        Button retry = findViewById(R.id.playerRetryButton);
        retry.requestFocus();
    }

    private void openExternalPlayer() {
        Uri uri = Uri.parse(streamUrl);

        Intent vlc = new Intent(Intent.ACTION_VIEW);
        vlc.setDataAndType(uri, "application/x-mpegURL");
        vlc.setPackage("org.videolan.vlc");
        vlc.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(vlc);
            return;
        } catch (ActivityNotFoundException ignored) {
        }

        Intent generic = new Intent(Intent.ACTION_VIEW);
        generic.setDataAndType(uri, "application/x-mpegURL");

        try {
            startActivity(generic);
        } catch (ActivityNotFoundException e) {
            showError(
                    "No encontre un reproductor externo.\n"
                            + "Instala VLC en el Fire TV y vuelve a intentar."
            );
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }
}
