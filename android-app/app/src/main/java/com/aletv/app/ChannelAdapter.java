package com.aletv.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
    public interface Listener {
        void onChannelSelected(Channel channel, int position);
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

    public ArrayList<Channel> snapshot() {
        return new ArrayList<>(items);
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
                .inflate(R.layout.item_channel, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Channel channel = items.get(position);

        holder.name.setText(channel.name);
        holder.group.setText(MainActivity.friendlyGroup(channel.group));
        LogoLoader.load(holder.logo, holder.initials, channel);

        holder.itemView.setContentDescription(
                channel.name + ", " + MainActivity.friendlyGroup(channel.group)
        );

        holder.itemView.setOnClickListener(
                v -> listener.onChannelSelected(channel, holder.getBindingAdapterPosition())
        );

        holder.itemView.setOnFocusChangeListener((view, focused) -> {
            float scale = focused ? 1.055f : 1.0f;
            view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(120)
                    .start();
            view.setElevation(focused ? 18f : 2f);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView logo;
        final TextView initials;
        final TextView name;
        final TextView group;

        Holder(@NonNull View itemView) {
            super(itemView);
            logo = itemView.findViewById(R.id.channelLogo);
            initials = itemView.findViewById(R.id.channelInitials);
            name = itemView.findViewById(R.id.channelName);
            group = itemView.findViewById(R.id.channelGroup);
        }
    }
}
