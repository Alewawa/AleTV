package com.aletv.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
    public interface Listener {
        void onChannelSelected(Channel channel);
    }

    private final List<Channel> items = new ArrayList<>();
    private final Listener listener;

    public ChannelAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<Channel> channels) {
        items.clear();
        items.addAll(channels);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        Channel channel = items.get(position);
        String key = !channel.id.isEmpty() ? channel.id : channel.url;
        return key.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(com.aletv.app.R.layout.item_channel, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Channel channel = items.get(position);

        holder.number.setText(String.format(Locale.US, "%02d", position + 1));
        holder.name.setText(channel.name);
        holder.group.setText(MainActivity.friendlyGroup(channel.group));

        holder.itemView.setContentDescription(
                channel.name + ", " + MainActivity.friendlyGroup(channel.group)
        );

        holder.itemView.setOnClickListener(v -> listener.onChannelSelected(channel));

        holder.itemView.setOnFocusChangeListener((view, focused) -> {
            float scale = focused ? 1.045f : 1.0f;
            view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(120)
                    .start();
            view.setElevation(focused ? 16f : 2f);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView name;
        final TextView group;

        Holder(@NonNull View itemView) {
            super(itemView);
            number = itemView.findViewById(R.id.channelNumber);
            name = itemView.findViewById(R.id.channelName);
            group = itemView.findViewById(R.id.channelGroup);
        }
    }
}
