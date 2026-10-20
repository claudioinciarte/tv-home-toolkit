package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;

    private AppAdapter adapter;
    private PlexManager plex;
    private final AtomicBoolean plexRefreshing = new AtomicBoolean();
    private boolean homeActive;
    private boolean plexHomeVisible;
    private RecyclerView grid;
    private FrameLayout root;
    private FrameLayout plexCarousel;
    private RecyclerView plexList;
    private PlexShelfAdapter plexPosters;
    private ImageView plexBackdrop, plexBackdropAlt, plexBackdropFront, plexBackdropBack;
    private AnimatorSet plexBackdropTransition;
    private View plexHeroDetails;
    private TextView plexShelfTitle, plexConnectionStatus, plexHeroTitle, plexHeroSubtitle, plexHeroRating, plexStatus;
    private View plexRefresh;
    private String plexBackdropKey;
    private int plexDetailsGeneration;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    private TextView greeting;
    private TextView btnWifi;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private static String lastPlexFocusedKey;
    private static int lastPlexFocusedSlot;
    private float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(() -> {
                updateWifiLabel();
                refreshPlexIfStale();
            });
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            refreshPlexIfStale();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.32f, 0.42f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.62f));
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);
        plexCarousel = findViewById(R.id.plex_carousel);
        plexBackdrop = findViewById(R.id.plex_backdrop);
        plexBackdropAlt = findViewById(R.id.plex_backdrop_alt);
        plexBackdropFront = plexBackdrop;
        plexBackdropBack = plexBackdropAlt;
        plexShelfTitle = findViewById(R.id.plex_shelf_title);
        plexConnectionStatus = findViewById(R.id.plex_connection_status);
        plexHeroTitle = findViewById(R.id.plex_hero_title);
        plexHeroSubtitle = findViewById(R.id.plex_hero_subtitle);
        plexHeroRating = findViewById(R.id.plex_hero_rating);
        plexHeroDetails = findViewById(R.id.plex_hero_details);
        plexStatus = findViewById(R.id.plex_shelf_status);
        plexRefresh = findViewById(R.id.plex_refresh);
        plexList = findViewById(R.id.plex_list);
        plexList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        plexList.setHasFixedSize(true);
        plexPosters = new PlexShelfAdapter(new PlexManager(this), this);
        plexList.setAdapter(plexPosters);
        plexRefresh.setOnClickListener(v -> onPlexRefreshRequested());
        plexRefresh.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN
                    || (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN)) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && plexPosters.getItemCount() > 0) {
                focusPlexItem(0);
                return true;
            }
            return onPlexVerticalKey(0, keyCode);
        });

        grid = findViewById(R.id.grid);
        GridLayoutManager layout = new GridLayoutManager(this, COLUMNS);
        grid.setLayoutManager(layout);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row to the bottom of the screen; the grid hides below until you press Down.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int dockRow = (int) ((145 + 6 + 12) * density);   // item height plus DockDecoration offsets
            int want = Math.max(0, h - dockRow - (int) (30 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
            updatePlexCarouselBounds(want);
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (plexHomeVisible) plexCarousel.setTranslationY(-rv.computeVerticalScrollOffset());
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        View btnSettings = findViewById(R.id.btnSettings);
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> HomeSettings.show(this));

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnWifi) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null
                    && (newF.getId() == R.id.card || newF.getId() == R.id.plex_poster);
            if (!onTile) hideGlass();
        });

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        homeActive = true;
        HomeRedirect.markHomeVisible(this);
        root.postDelayed(HomeRedirectService::onHomeVisible, 120);
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        ambient.setAlpha(0f);
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final String returnPlexKey = lastPlexFocusedKey;
        final int returnPlexSlot = lastPlexFocusedSlot;
        adapter.reload();
        plex = new PlexManager(this);
        plexHomeVisible = plex.enabled() && plex.configured();
        plexCarousel.setVisibility(plexHomeVisible ? View.VISIBLE : View.GONE);
        plexCarousel.setTranslationY(plexHomeVisible ? -grid.computeVerticalScrollOffset() : 0f);
        plexBackdropKey = null;
        showPlexContent(plex.loadCache(), false);
        grid.scheduleLayoutAnimation();
        updateGreeting();
        updateWifiLabel();
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Return to the app we left from, or the same slot if that app is gone. Always refocus: after
        // reload() the view that holds focus may no longer show the app it had.
        grid.post(() -> {
            if (returnKey == null && returnPlexKey != null && plexHomeVisible) {
                int index = plexPosters.positionOf(returnPlexKey);
                focusPlexItem(index >= 0 ? index : returnPlexSlot);
                refreshPlexIfStale();
                return;
            }
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
            refreshPlexIfStale();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        HomeRedirect.markHomeVisible(this);
        root.postDelayed(HomeRedirectService::onHomeVisible, 120);
        refreshPlexIfStale();
    }

    @Override
    protected void onPause() {
        super.onPause();
        homeActive = false;
        handler.removeCallbacks(minuteTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(16f * density * AppAdapter.FOCUS_SCALE + pad);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        if (adapter.isDockPosition(position)) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
            if (plexHomeVisible) hideHero();
            else showHero(position, r.top - dy);
        } else {
            hideHero();
            // Scroll just enough so the whole glass (not only the tile) is visible.
            Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        String label = adapter.labelAt(position);
        int color = adapter.ambientColorAt(position);
        float y = dockTopInRoot - 14 * density - 26 * density - heroName.getHeight();
        boolean changed = !label.contentEquals(heroName.getText());

        if (!heroShown) {
            heroName.setText(label);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            heroName.animate().alpha(1f).setDuration(260).start();
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(600).start();
            heroShown = true;
        } else if (changed) {
            heroName.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                heroName.setText(label);
                heroName.setY(y);
                heroName.setTranslationX(12 * density);
                heroName.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            if (color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                    setAmbient(color);
                    ambient.animate().alpha(1f).setDuration(500).start();
                }).start();
            }
        } else {
            heroName.setY(y);
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x5C000000 | c, 0x26000000 | c, 0x00000000 | c});
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(160).start();
        ambient.animate().alpha(0f).setDuration(300).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int appIndex = adapter.appIndexAt(position);
        if (appIndex < 0) return true;
        int appCount = adapter.getAppCount();
        int dock = adapter.dockCount();
        int targetIndex;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (appIndex < dock) {
                if (!plexHomeVisible) return false;
                focusPlexItem(appIndex);
                return true;
            }
            targetIndex = appIndex - COLUMNS;
        } else {
            int lastRowStart = appCount <= 0 ? 0 : ((appCount - 1) / COLUMNS) * COLUMNS;
            if (appIndex >= lastRowStart) return true;
            targetIndex = Math.min(appIndex + COLUMNS, appCount - 1);
        }
        int target = adapter.positionForAppIndex(targetIndex);
        if (target >= 0) focusTile(target);
        return true;
    }

    @Override
    public void onPlexFocused(int index, PlexManager.MediaItem item, View card) {
        int direction = index < lastPlexFocusedSlot ? -1 : 1;
        lastPlexFocusedKey = item.serverId + "|" + item.ratingKey + "|" + item.type;
        lastPlexFocusedSlot = index;
        lastFocusedKey = null;
        hideHero();
        hideGlass();
        showPlexDetails(item, true, direction);
    }

    @Override
    public boolean onPlexVerticalKey(int index, int keyCode) {
        int dock = adapter.dockCount();
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            findViewById(R.id.btnSettings).requestFocus();
            return true;
        }
        if (dock > 0) focusTile(adapter.positionForAppIndex(Math.min(index, dock - 1)));
        return true;
    }

    @Override
    public void onPlexRefreshRequested() {
        refreshPlex();
    }

    private void focusPlexItem(int index) {
        if (!plexHomeVisible) return;
        if (plexPosters.getItemCount() == 0) plexRefresh.requestFocus();
        else plexPosters.focus(index);
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    private void refreshPlexIfStale() {
        if (homeActive && plexHomeVisible && plex != null
                && (plex.cacheNeedsMetadataRefresh() || plex.cacheAgeMs() >= 5 * 60 * 1000L)) refreshPlex();
    }

    private void refreshPlex() {
        if (!homeActive || !plexHomeVisible || plex == null || !plex.configured()
                || !plexRefreshing.compareAndSet(false, true)) return;
        plex.markMetadataRefreshAttempt();
        showPlexContent(plex.loadCache(), true);
        PlexManager current = plex;
        PlexManager.execute(current::refreshRecent, (result, error) -> runOnUiThread(() -> {
            plexRefreshing.set(false);
            if (!homeActive || isFinishing()) return;
            if (error != null) {
                Set<String> offline = new HashSet<>(current.selectedServers());
                showPlexContent(new PlexManager.FetchResult(current.loadCache().items, offline), false);
            } else showPlexContent(result, false);
        }));
    }

    private void updatePlexCarouselBounds(int paddingTop) {
        if (plexCarousel == null) return;
        int height = Math.max(0, paddingTop - (int) (8 * density));
        ViewGroup.LayoutParams params = plexCarousel.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            plexCarousel.setLayoutParams(params);
        }
    }

    private void showPlexContent(PlexManager.FetchResult result, boolean loading) {
        List<PlexManager.MediaItem> items = result == null ? Collections.emptyList() : result.items;
        plexPosters.setItems(items);
        boolean hasItems = !items.isEmpty();
        String state = loading ? getString(R.string.plex_shelf_loading)
                : result != null && !result.offlineServers.isEmpty()
                ? getString(R.string.plex_shelf_offline, result.offlineServers.size())
                : hasItems ? "" : getString(R.string.plex_shelf_empty);
        plexShelfTitle.setText(R.string.plex_shelf_title);
        plexConnectionStatus.setText(state);
        plexConnectionStatus.setVisibility(hasItems && !state.isEmpty() ? View.VISIBLE : View.GONE);
        plexStatus.setText(state.isEmpty() ? getString(R.string.plex_shelf_empty) : state);
        plexStatus.setVisibility(hasItems || state.isEmpty() ? View.GONE : View.VISIBLE);
        plexList.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        if (hasItems) {
            int selected = plexPosters.positionOf(lastPlexFocusedKey);
            showPlexDetails(items.get(selected >= 0 ? selected : 0), false, 1);
        } else {
            plexDetailsGeneration++;
            plexHeroDetails.animate().cancel();
            plexHeroTitle.setText("");
            plexHeroSubtitle.setText("");
            plexHeroRating.setVisibility(View.GONE);
            plexHeroDetails.setAlpha(1f);
            plexHeroDetails.setTranslationX(0f);
            plexBackdropKey = null;
            settlePlexBackdropTransition();
            ImageView backdrop = plexBackdropFront;
            backdrop.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                if (plexBackdropKey != null) return;
                backdrop.setImageDrawable(null);
                plexBackdropBack.setImageDrawable(null);
            }).start();
        }
    }

    private void showPlexDetails(PlexManager.MediaItem item, boolean animate, int direction) {
        if (item == null) return;
        String subtitle;
        if (item.type.equals("show")) {
            subtitle = item.episode.isEmpty() ? item.episodeTitle : item.episode;
            if (!item.episode.isEmpty() && !item.episodeTitle.isEmpty()) subtitle += " · " + item.episodeTitle;
        } else {
            subtitle = item.year > 0 ? String.valueOf(item.year) : getString(R.string.plex_type_movie);
        }
        final String detailText = subtitle;
        final int generation = ++plexDetailsGeneration;
        plexHeroDetails.animate().cancel();
        Runnable update = () -> {
            if (generation != plexDetailsGeneration) return;
            plexHeroTitle.setText(item.title);
            plexHeroSubtitle.setText(detailText);
            plexHeroRating.setText(item.rating);
            plexHeroRating.setVisibility(item.rating.isEmpty() ? View.GONE : View.VISIBLE);
            if (!animate) {
                plexHeroDetails.setAlpha(1f);
                plexHeroDetails.setTranslationX(0f);
                return;
            }
            float enterX = direction * 8 * density;
            plexHeroDetails.setAlpha(0f);
            plexHeroDetails.setTranslationX(enterX);
            plexHeroDetails.animate().alpha(1f).translationX(0f).setDuration(200).start();
        };
        if (animate) {
            float exitX = -direction * 8 * density;
            plexHeroDetails.animate().alpha(0f).translationX(exitX).setDuration(120).withEndAction(update).start();
        } else update.run();
        showPlexBackdrop(item);
    }

    private void showPlexBackdrop(PlexManager.MediaItem item) {
        String key = item.serverId + "|" + item.art + "|" + item.thumb;
        if (key.equals(plexBackdropKey)) return;
        plexBackdropKey = key;
        settlePlexBackdropTransition();
        PlexManager current = plex;
        PlexManager.execute(() -> current == null ? null : current.backdrop(item), (bitmap, error) ->
                plexBackdrop.post(() -> {
                    if (!key.equals(plexBackdropKey) || error != null || bitmap == null) return;
                    ImageView from = plexBackdropFront;
                    if (from.getDrawable() == null) {
                        from.setImageBitmap(bitmap);
                        from.setAlpha(0f);
                        from.animate().alpha(0.40f).setDuration(320).start();
                        return;
                    }
                    ImageView to = plexBackdropBack;
                    to.setImageBitmap(bitmap);
                    to.setAlpha(0f);
                    AnimatorSet transition = new AnimatorSet();
                    transition.playTogether(
                            ObjectAnimator.ofFloat(from, View.ALPHA, from.getAlpha(), 0f),
                            ObjectAnimator.ofFloat(to, View.ALPHA, 0f, 0.40f));
                    transition.setDuration(320);
                    transition.addListener(new AnimatorListenerAdapter() {
                        private boolean cancelled;
                        @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
                        @Override public void onAnimationEnd(Animator animation) {
                            if (cancelled || !key.equals(plexBackdropKey)
                                    || plexBackdropTransition != transition) return;
                            plexBackdropTransition = null;
                            from.setImageDrawable(null);
                            from.setAlpha(0f);
                            plexBackdropFront = to;
                            plexBackdropBack = from;
                        }
                    });
                    plexBackdropTransition = transition;
                    transition.start();
                }));
    }

    private void settlePlexBackdropTransition() {
        AnimatorSet transition = plexBackdropTransition;
        plexBackdropTransition = null;
        if (transition != null) transition.cancel();
        plexBackdropFront.animate().cancel();
        plexBackdropBack.animate().cancel();
        if (plexBackdropBack.getDrawable() != null
                && plexBackdropBack.getAlpha() > plexBackdropFront.getAlpha()) {
            ImageView visible = plexBackdropFront;
            plexBackdropFront = plexBackdropBack;
            plexBackdropBack = visible;
        }
        plexBackdropFront.setAlpha(plexBackdropFront.getDrawable() == null ? 0f : 0.40f);
        plexBackdropBack.setAlpha(0f);
        plexBackdropBack.setImageDrawable(null);
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

}
