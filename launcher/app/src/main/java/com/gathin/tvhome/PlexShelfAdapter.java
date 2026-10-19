package com.gathin.tvhome;

import android.graphics.Outline;
import android.graphics.Bitmap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Posters inside the full-width Plex shelf. */
final class PlexShelfAdapter extends RecyclerView.Adapter<PlexShelfAdapter.Holder> {
    private final PlexManager manager;
    private final AppAdapter.TileListener listener;
    private final List<PlexManager.MediaItem> items = new ArrayList<>();

    PlexShelfAdapter(PlexManager manager, AppAdapter.TileListener listener) {
        this.manager = manager;
        this.listener = listener;
    }

    void setItems(List<PlexManager.MediaItem> values) {
        RecyclerView recycler = attachedRecycler;
        boolean restoreFocus = recycler != null && recycler.hasFocus();
        String focusedKey = null;
        int focusedSlot = 0;
        if (restoreFocus) {
            View focused = recycler.findFocus();
            Object tag = focused == null ? null : focused.getTag();
            if (tag instanceof String) focusedKey = (String) tag;
            RecyclerView.ViewHolder holder = focused == null ? null
                    : recycler.findContainingViewHolder(focused);
            if (holder != null) focusedSlot = holder.getBindingAdapterPosition();
        }
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
        if (restoreFocus) {
            String key = focusedKey;
            int slot = focusedSlot;
            recycler.post(() -> focus(key == null ? slot : Math.max(0, positionOf(key))));
        }
    }

    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            PlexManager.MediaItem item = items.get(i);
            if ((item.serverId + "|" + item.ratingKey + "|" + item.type).equals(key)) return i;
        }
        return -1;
    }

    String keyAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        PlexManager.MediaItem item = items.get(position);
        return item.serverId + "|" + item.ratingKey + "|" + item.type;
    }

    void focus(int position) {
        if (items.isEmpty()) return;
        int target = Math.max(0, Math.min(position, items.size() - 1));
        RecyclerView recycler = attachedRecycler;
        if (recycler == null) return;
        recycler.post(() -> {
            RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(target);
            if (holder != null) holder.itemView.findViewById(R.id.plex_poster).requestFocus();
            else {
                recycler.scrollToPosition(target);
                recycler.post(() -> {
                    RecyclerView.ViewHolder next = recycler.findViewHolderForAdapterPosition(target);
                    if (next != null) next.itemView.findViewById(R.id.plex_poster).requestFocus();
                });
            }
        });
    }

    private RecyclerView attachedRecycler;
    @Override public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        attachedRecycler = recyclerView;
    }
    @Override public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        if (attachedRecycler == recyclerView) attachedRecycler = null;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plex_poster, parent, false);
        return new Holder(view);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        PlexManager.MediaItem item = items.get(position);
        holder.title.setText(item.title);
        holder.detail.setText(item.type.equals("show") && !item.episode.isEmpty()
                ? item.episode : item.year > 0 ? String.valueOf(item.year) : item.serverName);
        String description = item.type.equals("show") && !item.episodeTitle.isEmpty()
                ? item.title + ", " + item.episodeTitle : item.title;
        if (!item.rating.isEmpty()) description += ", " + item.rating;
        holder.card.setContentDescription(description);
        holder.card.setTag(keyAt(position));
        holder.poster.setImageDrawable(null);
        holder.poster.setBackgroundColor(0xFF29292D);
        holder.card.setOnClickListener(view -> PlexManager.open(view.getContext(), item));
        holder.card.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN
                    || (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN)) return false;
            int index = holder.getBindingAdapterPosition();
            return index != RecyclerView.NO_POSITION && listener.onPlexVerticalKey(index, keyCode);
        });
        holder.card.setOnFocusChangeListener((view, focused) -> {
            float density = view.getResources().getDisplayMetrics().density;
            view.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f)
                    .translationY(focused ? -3f * density : 0f).setDuration(220)
                    .setInterpolator(new OvershootInterpolator(1.2f)).start();
            int index = holder.getBindingAdapterPosition();
            if (focused && index != RecyclerView.NO_POSITION) listener.onPlexFocused(index, item, view);
        });
        String key = manager.username() + "|" + item.serverId + "|" + item.thumb;
        if (item.thumb != null && !item.thumb.isEmpty()) {
            holder.poster.setTag(key);
            PlexManager.execute(() -> manager.poster(item), (bitmap, error) -> holder.poster.post(() -> {
                if (error == null && bitmap != null && key.equals(holder.poster.getTag())) {
                    holder.poster.setImageBitmap(bitmap);
                }
            }));
        } else holder.poster.setTag(null);
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView poster;
        final TextView title, detail;
        Holder(View view) {
            super(view);
            card = view.findViewById(R.id.plex_poster);
            poster = view.findViewById(R.id.plex_poster_image);
            title = view.findViewById(R.id.plex_title);
            detail = view.findViewById(R.id.plex_detail);
            float radius = view.getResources().getDisplayMetrics().density * 10f;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);
        }
    }
}
