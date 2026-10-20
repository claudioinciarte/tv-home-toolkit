package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Remote-friendly account, server and library settings for the Plex shelf. */
public final class PlexSettingsActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PlexManager plex;
    private TextView account, serverSummary, librarySummary, status, detailTitle, detailBody;
    private TextView localServerSummary;
    private LinearLayout editor;
    private LinearLayout overview;
    private ScrollView editorScroll;
    private EditText localAddressInput, localPortInput;
    private TextView activeMenuRow;
    private CheckBox showShelf;
    private TextView accountButton, serverButton, libraryButton, localServerButton,
            clearLocalServerButton, saveButton;
    private List<PlexManager.Server> availableServers = new ArrayList<>();
    private List<PlexManager.Library> availableLibraries = new ArrayList<>();
    private PlexManager.Pin pendingPin;
    private long pinDeadline;
    private final AtomicBoolean polling = new AtomicBoolean();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        plex = new PlexManager(this);
        buildScreen();
        refreshLabels();
        if (plex.linked()) reloadServers();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.HORIZONTAL);
        screen.setBackgroundColor(0xFF606970);

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(android.view.Gravity.CENTER_VERTICAL);
        identity.setPadding(dp(44), dp(32), dp(30), dp(32));
        identity.setBackgroundColor(0xFF89949B);
        screen.addView(identity, column(0.32f));
        TextView brand = new TextView(this);
        brand.setText("PLEX");
        brand.setTextColor(0xFFE5A00D);
        brand.setTextSize(18);
        brand.setLetterSpacing(0.18f);
        brand.setTypeface(getFont(), Typeface.BOLD);
        identity.addView(brand, matchWrap());
        TextView title = new TextView(this);
        title.setText(R.string.plex_settings_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(34);
        title.setTypeface(getFont(), Typeface.BOLD);
        title.setPadding(0, dp(14), 0, dp(14));
        identity.addView(title, matchWrap());
        TextView explanation = new TextView(this);
        explanation.setText(R.string.plex_settings_side_note);
        explanation.setTextColor(0xFFE6E9EB);
        explanation.setTextSize(17);
        identity.addView(explanation, matchWrap());

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(22), dp(28), dp(18), dp(20));
        menu.setBackgroundColor(0xFF515A61);
        screen.addView(menu, column(0.31f));
        TextView menuTitle = new TextView(this);
        menuTitle.setText(R.string.plex_settings_menu);
        menuTitle.setTextColor(0xFFD5DBDE);
        menuTitle.setTextSize(14);
        menuTitle.setLetterSpacing(0.12f);
        menuTitle.setTypeface(getFont(), Typeface.BOLD);
        menuTitle.setPadding(dp(14), dp(8), dp(14), dp(14));
        menu.addView(menuTitle, matchWrap());
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        menu.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(options);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(38), dp(42), dp(36), dp(28));
        details.setBackgroundColor(0xFF68737A);
        screen.addView(details, column(0.37f));
        TextView detailsLabel = new TextView(this);
        detailsLabel.setText(R.string.plex_settings_details);
        detailsLabel.setTextColor(0xFFE5E9EB);
        detailsLabel.setTextSize(14);
        detailsLabel.setLetterSpacing(0.12f);
        detailsLabel.setTypeface(getFont(), Typeface.BOLD);
        details.addView(detailsLabel, matchWrap());
        detailTitle = new TextView(this);
        detailTitle.setTextColor(0xFFFFFFFF);
        detailTitle.setTextSize(25);
        detailTitle.setTypeface(getFont(), Typeface.BOLD);
        detailTitle.setPadding(0, dp(30), 0, dp(12));
        details.addView(detailTitle, matchWrap());
        detailBody = new TextView(this);
        detailBody.setTextColor(0xFFE7EAEC);
        detailBody.setTextSize(17);
        details.addView(detailBody, matchWrap());
        overview = new LinearLayout(this);
        overview.setOrientation(LinearLayout.VERTICAL);
        details.addView(overview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        View divider = new View(this);
        divider.setBackgroundColor(0x55FFFFFF);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(24);
        dividerParams.bottomMargin = dp(14);
        overview.addView(divider, dividerParams);
        account = addReadout(overview, R.string.plex_link_account);
        serverSummary = addReadout(overview, R.string.plex_choose_servers);
        librarySummary = addReadout(overview, R.string.plex_choose_libraries);
        localServerSummary = addReadout(overview, R.string.plex_local_server_title);
        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(15);
        status.setTypeface(getFont());
        status.setPadding(0, dp(22), 0, 0);
        overview.addView(status, matchWrap());
        editorScroll = new ScrollView(this);
        editorScroll.setFillViewport(false);
        editorScroll.setVisibility(View.GONE);
        details.addView(editorScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(0, dp(18), 0, 0);
        editor.setVisibility(View.GONE);
        editorScroll.addView(editor);

        accountButton = addOption(options, R.string.plex_link_account,
                R.string.plex_settings_account_detail, this::linkOrUnlink);
        serverButton = addOption(options, R.string.plex_choose_servers,
                R.string.plex_settings_servers_detail, this::chooseServers);
        libraryButton = addOption(options, R.string.plex_choose_libraries,
                R.string.plex_settings_libraries_detail, this::chooseLibraries);
        localServerButton = addOption(options, R.string.plex_local_server_configure,
                R.string.plex_settings_local_detail, this::configureLocalServer);
        clearLocalServerButton = addOption(options, R.string.plex_local_server_clear,
                R.string.plex_settings_clear_local_detail, this::clearLocalServer);
        showShelf = new CheckBox(this);
        showShelf.setText(R.string.plex_show_shelf);
        showShelf.setTextSize(17);
        showShelf.setTypeface(getFont());
        showShelf.setTextColor(0xFFE6E9EB);
        showShelf.setButtonTintList(getColorStateList(R.color.home_redirect_text));
        showShelf.setGravity(android.view.Gravity.CENTER_VERTICAL);
        showShelf.setPadding(dp(14), 0, dp(10), 0);
        showShelf.setFocusable(true);
        addOptionView(options, showShelf);
        showShelf.setOnFocusChangeListener((view, focused) -> styleOption(showShelf, focused,
                R.string.plex_show_shelf, R.string.plex_settings_shelf_detail));
        showShelf.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !plex.configured()) {
                button.setChecked(false);
                message(R.string.plex_setup_first);
                return;
            }
            plex.setEnabled(checked);
            refreshLabels();
        });
        addOption(options, R.string.plex_refresh_now,
                R.string.plex_settings_refresh_detail, this::refreshNow);
        saveButton = addOption(options, R.string.plex_save_show,
                R.string.plex_settings_save_detail, this::saveAndShow);
        addOption(options, R.string.plex_close,
                R.string.plex_settings_close_detail, this::finish);
        setContentView(screen);
        accountButton.requestFocus();
    }

    private LinearLayout.LayoutParams column(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
    }

    private TextView addReadout(LinearLayout parent, int label) {
        TextView heading = new TextView(this);
        heading.setText(label);
        heading.setTextColor(0xFFCAD1D5);
        heading.setTextSize(13);
        heading.setTypeface(getFont(), Typeface.BOLD);
        parent.addView(heading, matchWrap());
        TextView view = new TextView(this);
        view.setTextColor(0xFFE3E7E9);
        view.setTextSize(16);
        view.setTypeface(getFont());
        view.setPadding(0, dp(2), 0, dp(8));
        parent.addView(view, matchWrap());
        return view;
    }

    private TextView addOption(LinearLayout parent, int label, int description, Runnable action) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextSize(17);
        row.setTypeface(getFont());
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(10), 0);
        row.setFocusable(true);
        row.setClickable(true);
        addOptionView(parent, row);
        row.setOnFocusChangeListener((view, focused) -> styleOption(row, focused, label, description));
        row.setOnClickListener(view -> { activeMenuRow = row; action.run(); });
        return row;
    }

    private void addOptionView(LinearLayout parent, TextView row) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        params.bottomMargin = dp(2);
        parent.addView(row, params);
    }

    private void styleOption(TextView row, boolean focused, int label, int description) {
        row.setBackground(new ColorDrawable(focused ? 0xFFE6EAEC : 0x00515A61));
        row.setTextColor(focused ? 0xFF20272B : 0xFFE6E9EB);
        if (focused && detailTitle != null) {
            if (editorScroll != null && editorScroll.getVisibility() == View.VISIBLE) return;
            detailTitle.setText(label);
            detailBody.setText(description);
        }
    }

    private void showEditor(int title, int description) {
        detailTitle.setText(title);
        detailBody.setText(description);
        overview.setVisibility(View.GONE);
        editor.setVisibility(View.VISIBLE);
        editorScroll.setVisibility(View.VISIBLE);
        editor.removeAllViews();
    }

    private void showOverview() {
        editor.setVisibility(View.GONE);
        editorScroll.setVisibility(View.GONE);
        overview.setVisibility(View.VISIBLE);
        if (activeMenuRow != null) activeMenuRow.requestFocus();
    }

    private TextView editorText(String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(0xFFE7EAEC);
        text.setTextSize(16);
        text.setPadding(0, dp(6), 0, dp(10));
        editor.addView(text, matchWrap());
        return text;
    }

    private TextView editorButton(String label, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(0xFFE6E9EB);
        button.setTextSize(17);
        button.setGravity(android.view.Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), 0, dp(10), 0);
        button.setFocusable(true);
        button.setClickable(true);
        button.setBackground(new ColorDrawable(0x33515A61));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        params.bottomMargin = dp(6);
        editor.addView(button, params);
        button.setOnFocusChangeListener((v, focused) -> {
            button.setBackground(new ColorDrawable(focused ? 0xFFE6EAEC : 0x33515A61));
            button.setTextColor(focused ? 0xFF20272B : 0xFFE6E9EB);
        });
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private CheckBox editorCheck(String label, boolean checked) {
        CheckBox check = new CheckBox(this);
        check.setText(label);
        check.setChecked(checked);
        check.setTextColor(0xFFE6E9EB);
        check.setTextSize(16);
        check.setButtonTintList(getColorStateList(R.color.home_redirect_text));
        check.setPadding(dp(4), dp(4), 0, dp(4));
        editor.addView(check, matchWrap());
        return check;
    }

    private TextView addLabel(LinearLayout parent, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xFFBDBDC2);
        view.setTextSize(16);
        view.setTypeface(getFont());
        view.setPadding(0, dp(10), 0, dp(2));
        parent.addView(view, matchWrap());
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private Typeface getFont() { return getResources().getFont(R.font.outfit); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void refreshLabels() {
        accountButton.setText(plex.linked() ? R.string.plex_unlink : R.string.plex_link_account);
        account.setText(plex.linked()
                ? getString(R.string.plex_account_connected, plex.username())
                : getString(R.string.plex_account_unlinked));
        Set<String> selected = plex.selectedServers();
        serverSummary.setText(getString(R.string.plex_servers_summary, selected.size(), availableServers.size()));
        librarySummary.setText(plex.allLibraries()
                ? getString(R.string.plex_all_libraries)
                : getString(R.string.plex_selected_libraries, plex.selectedLibraries().size()));
        localServerSummary.setText(plex.hasLocalRoute()
                ? getString(R.string.plex_local_server_set, plex.localRouteSummary(availableServers))
                : getString(R.string.plex_local_server_not_set));
        showShelf.setOnCheckedChangeListener(null);
        showShelf.setChecked(plex.enabled());
        showShelf.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !plex.configured()) {
                button.setChecked(false);
                message(R.string.plex_setup_first);
                return;
            }
            plex.setEnabled(checked);
        });
        serverButton.setEnabled(plex.linked());
        libraryButton.setEnabled(plex.linked());
        localServerButton.setEnabled(plex.linked() && !selected.isEmpty());
        clearLocalServerButton.setEnabled(plex.hasLocalRoute());
        saveButton.setEnabled(plex.linked());
    }

    private void linkOrUnlink() {
        if (plex.linked()) {
            showEditor(R.string.plex_unlink_title, R.string.plex_unlink_message);
            editorButton(getString(R.string.plex_unlink), () -> {
                plex.unlink();
                availableServers.clear();
                availableLibraries.clear();
                refreshLabels();
                showOverview();
            });
            editorButton(getString(R.string.plex_cancel), this::showOverview);
            return;
        }
        showEditor(R.string.plex_pin_title, R.string.plex_pin_instructions);
        status.setText(R.string.plex_linking);
        PlexManager.execute(plex::beginPin, (pin, error) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            if (error != null) {
                status.setText(R.string.plex_link_error);
                editorText(error.getMessage());
            } else showPin(pin);
        }));
    }

    private void showPin(PlexManager.Pin pin) {
        pendingPin = pin;
        pinDeadline = System.currentTimeMillis() + 5 * 60 * 1000L;
        showEditor(R.string.plex_pin_title, R.string.plex_settings_account_detail);
        editorText(getString(R.string.plex_pin_instructions, pin.code));
        editorButton(getString(R.string.plex_open_link), () -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://plex.tv/link/"))); }
            catch (ActivityNotFoundException e) { message(R.string.plex_open_link_error); }
        });
        editorButton(getString(R.string.plex_cancel), () -> { cancelPin(); showOverview(); });
        status.setText(R.string.plex_waiting_approval);
        editorText(getString(R.string.plex_waiting_approval));
        pollPin();
    }

    private void pollPin() {
        if (pendingPin == null || !polling.compareAndSet(false, true)) return;
        if (System.currentTimeMillis() > pinDeadline) {
            polling.set(false);
            status.setText(R.string.plex_pin_expired);
            cancelPin();
            return;
        }
        PlexManager.Pin pin = pendingPin;
        PlexManager.execute(() -> plex.pollPin(pin), (token, error) -> runOnUiThread(() -> {
            polling.set(false);
            if (pin != pendingPin || isFinishing()) return;
            if (error != null) {
                status.setText(getString(R.string.plex_link_error_detail, error.getMessage()));
                handler.postDelayed(this::pollPin, 4000);
            } else if (token == null) handler.postDelayed(this::pollPin, 3000);
            else finishLink(token);
        }));
    }

    private void finishLink(String token) {
        status.setText(R.string.plex_completing_link);
        PlexManager.Pin completed = pendingPin;
        pendingPin = null;
        PlexManager.execute(() -> plex.completeLink(token), (servers, error) -> runOnUiThread(() -> {
            if (error != null) {
                status.setText(R.string.plex_link_error);
                editorText(error.getMessage());
            } else {
                availableServers = servers;
                status.setText(getString(R.string.plex_link_success, plex.username()));
                reloadLibraries();
                showOverview();
            }
            refreshLabels();
        }));
    }

    private void cancelPin() {
        pendingPin = null;
        polling.set(false);
        handler.removeCallbacksAndMessages(null);
    }

    private void reloadServers() {
        status.setText(R.string.plex_loading_servers);
        PlexManager.execute(plex::discoverServers, (servers, error) -> runOnUiThread(() -> {
            if (error != null) status.setText(getString(R.string.plex_link_error_detail, error.getMessage()));
            else { availableServers = servers; status.setText(""); reloadLibraries(); }
            refreshLabels();
        }));
    }

    private void reloadLibraries() {
        reloadLibraries(null);
    }

    private void reloadLibraries(Runnable afterLoad) {
        List<PlexManager.Server> selected = new ArrayList<>();
        Set<String> ids = plex.selectedServers();
        for (PlexManager.Server server : availableServers) if (ids.contains(server.id)) selected.add(server);
        if (selected.isEmpty()) {
            availableLibraries.clear();
            status.setText("");
            refreshLabels();
            if (afterLoad != null) afterLoad.run();
            return;
        }
        status.setText(R.string.plex_loading_libraries);
        PlexManager.execute(() -> plex.libraries(selected), (libraries, error) -> runOnUiThread(() -> {
            if (error == null) {
                availableLibraries = libraries;
                status.setText("");
            }
            else status.setText(getString(R.string.plex_link_error_detail, error.getMessage()));
            refreshLabels();
            if (afterLoad != null) afterLoad.run();
        }));
    }

    private void chooseServers() {
        if (availableServers.isEmpty()) { reloadServers(); return; }
        Set<String> selected = plex.selectedServers();
        showEditor(R.string.plex_choose_servers, R.string.plex_settings_servers_detail);
        editorText(getString(R.string.plex_servers_summary, selected.size(), availableServers.size()));
        List<CheckBox> checks = new ArrayList<>();
        for (PlexManager.Server server : availableServers)
            checks.add(editorCheck(server.name, selected.isEmpty() || selected.contains(server.id)));
        editorButton(getString(R.string.plex_save), () -> {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < checks.size(); i++) if (checks.get(i).isChecked()) ids.add(availableServers.get(i).id);
            try { plex.setSelectedServers(ids, availableServers); }
            catch (Exception e) { editorText(e.getMessage()); return; }
            availableLibraries.clear();
            reloadLibraries();
            refreshLabels();
            showOverview();
        });
        editorButton(getString(R.string.plex_cancel), this::showOverview);
    }

    private void configureLocalServer() {
        if (availableServers.isEmpty()) { reloadServers(); return; }
        Set<String> selected = plex.selectedServers();
        List<PlexManager.Server> candidates = new ArrayList<>();
        for (PlexManager.Server server : availableServers) if (selected.contains(server.id)) candidates.add(server);
        if (candidates.isEmpty()) {
            status.setText(R.string.plex_local_server_choose_first);
            return;
        }
        PlexManager.Server preferred = candidates.get(0);
        for (PlexManager.Server candidate : candidates)
            if (candidate.id.equals(plex.localRouteServerId())) preferred = candidate;
        showLocalServerEditor(preferred);
    }

    private void showLocalServerEditor(PlexManager.Server server) {
        showEditor(R.string.plex_local_server_title, R.string.plex_local_server_secure_note);
        editorText(getString(R.string.plex_local_server_editor, server.name));
        List<PlexManager.Server> candidates = new ArrayList<>();
        Set<String> selected = plex.selectedServers();
        for (PlexManager.Server item : availableServers) if (selected.contains(item.id)) candidates.add(item);
        for (PlexManager.Server item : candidates)
            editorButton((item.id.equals(server.id) ? "✓  " : "") + item.name,
                    () -> showLocalServerEditor(item));
        localAddressInput = new EditText(this);
        localAddressInput.setSingleLine(true);
        localAddressInput.setInputType(InputType.TYPE_CLASS_PHONE);
        localAddressInput.setHint(R.string.plex_local_server_ip_hint);
        if (server.id.equals(plex.localRouteServerId())) localAddressInput.setText(plex.localRouteAddress());
        editor.addView(localAddressInput, matchWrap());
        localPortInput = new EditText(this);
        localPortInput.setSingleLine(true);
        localPortInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        localPortInput.setHint(R.string.plex_local_server_port_hint);
        localPortInput.setText(server.id.equals(plex.localRouteServerId())
                ? Integer.toString(plex.localRoutePort()) : "32400");
        editor.addView(localPortInput, matchWrap());
        editorButton(getString(R.string.plex_local_server_test_save), () -> {
            String ip = localAddressInput.getText().toString().trim();
            int portNumber;
            try { portNumber = Integer.parseInt(localPortInput.getText().toString().trim()); }
            catch (NumberFormatException e) { editorText(getString(R.string.plex_local_server_invalid)); return; }
            if (!PlexLocalEndpoint.isPrivateIpv4(ip) || portNumber < 1 || portNumber > 65535) {
                editorText(getString(R.string.plex_local_server_invalid));
                return;
            }
            status.setText(R.string.plex_local_server_testing);
            PlexManager.execute(() -> {
                plex.testLocalRoute(server, ip, portNumber);
                return Boolean.TRUE;
            }, (result, error) -> runOnUiThread(() -> {
                if (isFinishing()) return;
                if (error != null) {
                    String detail = getString(R.string.plex_link_error_detail, error.getMessage());
                    status.setText(detail);
                    editorText(detail);
                    return;
                }
                try { plex.saveLocalRoute(server, ip, portNumber); }
                catch (Exception e) { status.setText(R.string.plex_local_server_failed); editorText(e.getMessage()); return; }
                availableLibraries.clear();
                status.setText(R.string.plex_local_server_saved);
                reloadLibraries();
                refreshLabels();
                showOverview();
            }));
        });
        editorButton(getString(R.string.plex_cancel), this::showOverview);
    }

    private void clearLocalServer() {
        plex.clearLocalRoute();
        availableLibraries.clear();
        refreshLabels();
        status.setText(R.string.plex_local_server_cleared);
        reloadLibraries();
    }

    private void chooseLibraries() {
        if (availableServers.isEmpty()) { reloadServers(); return; }
        showEditor(R.string.plex_choose_libraries, R.string.plex_settings_libraries_detail);
        if (availableLibraries.isEmpty()) reloadLibraries(this::showLibraryEditor);
        else showLibraryEditor();
    }

    private void showLibraryEditor() {
        if (availableLibraries.isEmpty()) {
            editorText(getString(R.string.plex_no_libraries));
            editorButton(getString(R.string.plex_cancel), this::showOverview);
            return;
        }
        showEditor(R.string.plex_choose_libraries, R.string.plex_settings_libraries_detail);
        CheckBox all = editorCheck(getString(R.string.plex_all_libraries), plex.allLibraries());
        editorText(getString(R.string.plex_pick_libraries));
        Set<String> selected = plex.selectedLibraries();
        List<CheckBox> checks = new ArrayList<>();
        for (int i = 0; i < availableLibraries.size(); i++) {
            PlexManager.Library library = availableLibraries.get(i);
            checks.add(editorCheck(library.toString(), selected.isEmpty() || selected.contains(library.key())));
        }
        editorButton(getString(R.string.plex_save), () -> {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < checks.size(); i++) if (checks.get(i).isChecked()) ids.add(availableLibraries.get(i).key());
            if (all.isChecked()) plex.setSelectedLibraries(new HashSet<>(), true);
            else if (ids.isEmpty()) { editorText(getString(R.string.plex_choose_one_library)); return; }
            else plex.setSelectedLibraries(ids, false);
            refreshLabels();
            showOverview();
        });
        editorButton(getString(R.string.plex_cancel), this::showOverview);
    }

    private void saveAndShow() {
        Set<String> selected = plex.selectedServers();
        boolean eligible = false;
        for (PlexManager.Library library : availableLibraries) {
            if (selected.contains(library.serverId)
                    && (plex.allLibraries() || plex.selectedLibraries().contains(library.key()))) {
                eligible = true;
                break;
            }
        }
        if (!plex.configured() || (!eligible && plex.loadCache().items.isEmpty())) {
            message(R.string.plex_setup_first);
            return;
        }
        plex.setEnabled(true);
        Toast.makeText(this, R.string.plex_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void refreshNow() {
        if (!plex.configured()) { message(R.string.plex_setup_first); return; }
        status.setText(R.string.plex_refreshing);
        PlexManager.execute(plex::refreshRecent, (result, error) -> runOnUiThread(() -> {
            if (error != null) status.setText(getString(R.string.plex_link_error_detail, error.getMessage()));
            else status.setText(getString(R.string.plex_refresh_done, result.items.size(), result.offlineServers.size()));
        }));
    }

    private void message(int text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private void message(String text) {
        Toast.makeText(this, text == null ? getString(R.string.plex_link_error) : text, Toast.LENGTH_LONG).show();
    }
}
