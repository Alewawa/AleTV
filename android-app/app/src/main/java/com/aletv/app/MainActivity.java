package com.aletv.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity implements ChannelAdapter.Listener {
    private final List<Channel> allChannels = new ArrayList<>();
    private final List<Button> categoryButtons = new ArrayList<>();

    private PlaylistRepository repository;
    private ChannelAdapter adapter;

    private RecyclerView channelGrid;
    private LinearLayout categoryContainer;
    private TextView statusText;
    private TextView sectionTitle;
    private View loadingPanel;
    private View errorPanel;
    private TextView errorText;
    private Button refreshButton;
    private Button retryButton;

    private String selectedGroup = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        hideSystemUi();

        repository = new PlaylistRepository(this);

        channelGrid = findViewById(R.id.channelGrid);
        categoryContainer = findViewById(R.id.categoryContainer);
        statusText = findViewById(R.id.statusText);
        sectionTitle = findViewById(R.id.sectionTitle);
        loadingPanel = findViewById(R.id.loadingPanel);
        errorPanel = findViewById(R.id.errorPanel);
        errorText = findViewById(R.id.errorText);
        refreshButton = findViewById(R.id.refreshButton);
        retryButton = findViewById(R.id.retryButton);

        adapter = new ChannelAdapter(this);
        channelGrid.setLayoutManager(new GridLayoutManager(this, 4));
        channelGrid.setAdapter(adapter);
        channelGrid.setItemAnimator(null);
        channelGrid.setHasFixedSize(false);

        refreshButton.setOnClickListener(v -> loadChannels(true));
        retryButton.setOnClickListener(v -> loadChannels(true));

        loadChannels(false);
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

    private void loadChannels(boolean forceRefresh) {
        loadingPanel.setVisibility(View.VISIBLE);
        errorPanel.setVisibility(View.GONE);
        statusText.setText("Cargando canales...");

        repository.load(forceRefresh, new PlaylistRepository.Callback() {
            @Override
            public void onSuccess(List<Channel> channels, String source, boolean fromCache) {
                allChannels.clear();
                allChannels.addAll(channels);

                buildCategories();
                selectGroup("");

                String suffix = fromCache ? " - copia guardada" : "";
                statusText.setText(channels.size() + " canales disponibles" + suffix);
                loadingPanel.setVisibility(View.GONE);
                errorPanel.setVisibility(View.GONE);
            }

            @Override
            public void onError(String message) {
                loadingPanel.setVisibility(View.GONE);
                errorPanel.setVisibility(View.VISIBLE);
                errorText.setText(
                        "No se pudo cargar la lista.\n\n"
                                + "Comprueba tu conexión y pulsa Reintentar."
                );
                statusText.setText("Sin conexión");
                retryButton.requestFocus();
            }
        });
    }

    private void buildCategories() {
        categoryContainer.removeAllViews();
        categoryButtons.clear();

        addCategoryButton("Inicio", "");

        List<String> preferred = Arrays.asList(
                "TV Peru",
                "Deportes Peru",
                "TV Arequipa",
                "Deportes Latinoamerica",
                "Deportes Espana"
        );

        Set<String> existing = new LinkedHashSet<>();
        for (Channel c : allChannels) existing.add(c.group);

        for (String group : preferred) {
            if (existing.remove(group)) addCategoryButton(friendlyGroup(group), group);
        }

        for (String group : existing) {
            addCategoryButton(friendlyGroup(group), group);
        }
    }

    private void addCategoryButton(String title, String group) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setBackgroundResource(R.drawable.pill_button_selector);

        ColorStateList colors = getResources().getColorStateList(R.color.card_text_selector);
        button.setTextColor(colors);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(58)
        );
        params.setMargins(0, 0, dp(12), 0);
        button.setLayoutParams(params);

        button.setTag(group);
        button.setOnClickListener(v -> selectGroup((String) v.getTag()));

        categoryButtons.add(button);
        categoryContainer.addView(button);
    }

    private void selectGroup(String group) {
        selectedGroup = group == null ? "" : group;

        List<Channel> visible = new ArrayList<>();
        for (Channel channel : allChannels) {
            if (selectedGroup.isEmpty() || selectedGroup.equals(channel.group)) {
                visible.add(channel);
            }
        }

        adapter.submit(visible);
        sectionTitle.setText(selectedGroup.isEmpty() ? "Inicio" : friendlyGroup(selectedGroup));

        channelGrid.scrollToPosition(0);
        channelGrid.postDelayed(() -> {
            RecyclerView.ViewHolder holder =
                    channelGrid.findViewHolderForAdapterPosition(0);
            if (holder != null) {
                holder.itemView.requestFocus();
            } else {
                channelGrid.requestFocus();
            }
        }, 120);
    }

    @Override
    public void onChannelSelected(Channel channel) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("name", channel.name);
        intent.putExtra("group", channel.group);
        intent.putExtra("url", channel.url);
        intent.putExtra("userAgent", channel.userAgent);
        intent.putExtra("referer", channel.referer);
        intent.putExtra("origin", channel.origin);
        startActivity(intent);
    }

    public static String friendlyGroup(String group) {
        if (group == null) return "";
        switch (group) {
            case "TV Peru": return "TV Peru";
            case "Deportes Peru": return "Deportes Peru";
            case "TV Arequipa": return "Arequipa";
            case "Deportes Latinoamerica": return "Deportes Latam";
            case "Deportes Espana": return "Deportes Espana";
            default: return group;
        }
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
