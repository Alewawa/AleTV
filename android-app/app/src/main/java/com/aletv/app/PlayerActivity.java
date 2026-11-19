package com.aletv.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class PlayerActivity extends Activity {
    private PlayerView playerView;
    private ExoPlayer player;
    private View errorPanel;
    private TextView errorText;
    private View channelOverlay;
    private TextView channelTitle;
    private ImageView playerLogo;
    private TextView playerInitials;

    private ArrayList<Channel> channels = new ArrayList<>();
    private int currentIndex = 0;
    private Channel currentChannel;

    private int automaticRetries = 0;
    private int sourceIndex = 0;
    private String activeUrl = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable hideOverlayRunnable = () -> {
        if (channelOverlay != null) {
            channelOverlay.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction(() -> channelOverlay.setVisibility(View.INVISIBLE))
                    .start();
        }
    };

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_player);
        hideSystemUi();

        Object serialized = getIntent().getSerializableExtra("channels");
        if (serialized instanceof ArrayList) {
            channels = (ArrayList<Channel>) serialized;
        }

        currentIndex = getIntent().getIntExtra("position", 0);
        if (currentIndex < 0) currentIndex = 0;
        if (currentIndex >= channels.size()) currentIndex = Math.max(0, channels.size() - 1);

        playerView = findViewById(R.id.playerView);
        errorPanel = findViewById(R.id.errorPanel);
        errorText = findViewById(R.id.playbackErrorText);
        channelOverlay = findViewById(R.id.channelOverlay);
        channelTitle = findViewById(R.id.channelTitle);
        playerLogo = findViewById(R.id.playerLogo);
        playerInitials = findViewById(R.id.playerInitials);

        Button retry = findViewById(R.id.playerRetryButton);
        Button external = findViewById(R.id.openExternalButton);
        Button back = findViewById(R.id.backButton);

        retry.setOnClickListener(v -> {
            automaticRetries = 0;
            sourceIndex = 0;
            errorPanel.setVisibility(View.GONE);
            startPlayback();
        });

        external.setOnClickListener(v -> openExternalPlayer());
        back.setOnClickListener(v -> finish());

        if (channels.isEmpty()) {
            showError("No se recibio la lista de canales.");
        } else {
            loadCurrentChannel();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            int key = event.getKeyCode();

            // Flecha arriba / Channel+ = canal siguiente.
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_CHANNEL_UP) {
                switchChannel(+1);
                return true;
            }

            // Flecha abajo / Channel- = canal anterior.
            if (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                switchChannel(-1);
                return true;
            }
            // OK cambia a otra señal del mismo canal si existe.
            if ((key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)
                    && errorPanel != null && errorPanel.getVisibility() != View.VISIBLE
                    && currentChannel != null && !currentChannel.backupUrls.isEmpty()) {
                sourceIndex = (sourceIndex + 1) % (currentChannel.backupUrls.size() + 1);
                automaticRetries = 0;
                showSourceOverlay();
                startPlayback();
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void switchChannel(int delta) {
        if (channels == null || channels.isEmpty()) return;

        int size = channels.size();
        currentIndex = (currentIndex + delta + size) % size;

        automaticRetries = 0;
        sourceIndex = 0;
        errorPanel.setVisibility(View.GONE);
        loadCurrentChannel();
    }

    private void loadCurrentChannel() {
        if (channels == null || channels.isEmpty()) return;

        currentChannel = channels.get(currentIndex);
        sourceIndex = 0;
        LastChannelStore.save(this, currentChannel);
        updateChannelOverlay();
        startPlayback();
    }

    private void updateChannelOverlay() {
        if (currentChannel == null) return;

        channelTitle.setText(
                (currentIndex + 1) + "  " + currentChannel.name
        );
        TextView hint = findViewById(R.id.channelHint);
        if (hint != null) {
            hint.setText(currentChannel.backupUrls.isEmpty()
                    ? "↑ siguiente    ↓ anterior"
                    : "↑ siguiente    ↓ anterior    OK: otra señal");
        }

        LogoLoader.load(playerLogo, playerInitials, currentChannel);
        showChannelOverlay();
    }

    private void showChannelOverlay() {
        handler.removeCallbacks(hideOverlayRunnable);
        channelOverlay.setAlpha(1f);
        channelOverlay.setVisibility(View.VISIBLE);
        handler.postDelayed(hideOverlayRunnable, 4200);
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

    private void showSourceOverlay() {
        if (currentChannel == null) return;
        channelTitle.setText((currentIndex + 1) + " " + currentChannel.name
                + "  (señal " + (sourceIndex + 1) + "/"
                + (currentChannel.backupUrls.size() + 1) + ")");
        showChannelOverlay();
    }

    private String sourceAt(int index) {
        if (currentChannel == null) return "";
        if (index == 0) return currentChannel.url;
        int alt = index - 1;
        if (alt >= 0 && alt < currentChannel.backupUrls.size()) {
            return currentChannel.backupUrls.get(alt);
        }
        return "";
    }

    private boolean tryNextSource() {
        String next = sourceAt(sourceIndex + 1);
        if (next.isEmpty()) return false;
        sourceIndex++;
        automaticRetries = 0;
        errorPanel.setVisibility(View.GONE);
        showSourceOverlay();
        startPlayback();
        return true;
    }

    private void startPlayback() {
        releasePlayer();

        activeUrl = sourceAt(sourceIndex);
        if (activeUrl.isEmpty()) {
            showError("Este canal no tiene una URL valida.");
            return;
        }

        DefaultHttpDataSource.Factory httpFactory =
                new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(12000)
                        .setReadTimeoutMs(22000)
                        .setUserAgent(
                                currentChannel.userAgent.isEmpty()
                                        ? "Mozilla/5.0 (Android TV; AleTV/1.2)"
                                        : currentChannel.userAgent
                        );

        Map<String, String> headers = new HashMap<>();
        if (!currentChannel.referer.isEmpty()) {
            headers.put("Referer", currentChannel.referer);
        }
        if (!currentChannel.origin.isEmpty()) {
            headers.put("Origin", currentChannel.origin);
        }
        if (!headers.isEmpty()) {
            httpFactory.setDefaultRequestProperties(headers);
        }

        DefaultDataSource.Factory dataSourceFactory =
                new DefaultDataSource.Factory(this, httpFactory);

        DefaultMediaSourceFactory mediaSourceFactory =
                new DefaultMediaSourceFactory(dataSourceFactory);

        player = new ExoPlayer.Builder(
                    this,
                    new DefaultRenderersFactory(this).setEnableDecoderFallback(true)
                )
                .setMediaSourceFactory(mediaSourceFactory)
                .build();

        playerView.setPlayer(player);
        final ExoPlayer playing = player;

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    errorPanel.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (player != playing) return;
                if (automaticRetries < 1) {
                    automaticRetries++;
                    handler.postDelayed(() -> {
                        if (player == playing) startPlayback();
                    }, 1200);
                } else if (!tryNextSource()) {
                    showError("No se pudo reproducir " + currentChannel.name
                            + ".\n\nSe probaron las fuentes disponibles.\n"
                            + "↑ o ↓ para cambiar de canal o abrir con VLC.");
                }
            }
        });

        player.setMediaItem(MediaItem.fromUri(activeUrl));
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
        if (currentChannel == null || currentChannel.url.isEmpty()) return;

        Uri uri = Uri.parse(activeUrl.isEmpty() ? currentChannel.url : activeUrl);

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
