package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
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
    private TextView account, serverSummary, librarySummary, status;
    private TextView localServerSummary;
    private CheckBox showShelf;
    private Button serverButton, libraryButton, localServerButton, clearLocalServerButton, saveButton;
    private List<PlexManager.Server> availableServers = new ArrayList<>();
    private List<PlexManager.Library> availableLibraries = new ArrayList<>();
    private PlexManager.Pin pendingPin;
    private AlertDialog pinDialog;
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
        if (pinDialog != null && pinDialog.isShowing()) pinDialog.dismiss();
        super.onDestroy();
    }

    private void buildScreen() {
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(48 * d);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF000000);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, Math.round(30 * d), pad, Math.round(32 * d));
        scroll.addView(content);

        TextView title = new TextView(this);
        title.setText(R.string.plex_settings_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(34);
        title.setTypeface(getFont(), Typeface.BOLD);
        content.addView(title, matchWrap());
        TextView explanation = new TextView(this);
        explanation.setText(R.string.plex_settings_explanation);
        explanation.setTextColor(0xFFBDBDC2);
        explanation.setTextSize(17);
        explanation.setPadding(0, Math.round(12 * d), 0, Math.round(18 * d));
        content.addView(explanation, matchWrap());

        account = addLabel(content, "");
        addButton(content, R.string.plex_link_account, this::linkOrUnlink);
        serverSummary = addLabel(content, "");
        serverButton = addButton(content, R.string.plex_choose_servers, this::chooseServers);
        addSectionTitle(content, R.string.plex_local_server_title);
        localServerSummary = addLabel(content, "");
        addLabel(content, getString(R.string.plex_local_server_help));
        localServerButton = addButton(content, R.string.plex_local_server_configure, this::configureLocalServer);
        clearLocalServerButton = addButton(content, R.string.plex_local_server_clear, this::clearLocalServer);
        librarySummary = addLabel(content, "");
        libraryButton = addButton(content, R.string.plex_choose_libraries, this::chooseLibraries);

        showShelf = new CheckBox(this);
        showShelf.setText(R.string.plex_show_shelf);
        showShelf.setTextSize(19);
        showShelf.setTypeface(getFont());
        showShelf.setTextColor(0xFFFFFFFF);
        showShelf.setButtonTintList(getColorStateList(R.color.home_redirect_text));
        showShelf.setMinHeight(Math.round(60 * d));
        showShelf.setFocusable(true);
        content.addView(showShelf, matchWrap());
        showShelf.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !plex.configured()) {
                button.setChecked(false);
                message(R.string.plex_setup_first);
                return;
            }
            plex.setEnabled(checked);
            refreshLabels();
        });

        saveButton = addButton(content, R.string.plex_save_show, this::saveAndShow);
        addButton(content, R.string.plex_refresh_now, this::refreshNow);
        status = addLabel(content, "");
        addButton(content, R.string.plex_close, this::finish);
        setContentView(scroll);
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

    private Button addButton(LinearLayout parent, int label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setTypeface(getFont());
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundResource(R.drawable.pill_bg);
        button.setAllCaps(false);
        button.setFocusable(true);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(7);
        parent.addView(button, params);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView addSectionTitle(LinearLayout parent, int label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(23);
        view.setTypeface(getFont(), Typeface.BOLD);
        view.setPadding(0, dp(18), 0, dp(2));
        parent.addView(view, matchWrap());
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private Typeface getFont() { return getResources().getFont(R.font.outfit); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void refreshLabels() {
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
            new AlertDialog.Builder(this, R.style.Theme_TvHome_Dialog)
                    .setTitle(R.string.plex_unlink_title)
                    .setMessage(R.string.plex_unlink_message)
                    .setPositiveButton(R.string.plex_unlink, (dialog, which) -> {
                        plex.unlink();
                        availableServers.clear();
                        availableLibraries.clear();
                        refreshLabels();
                    })
                    .setNegativeButton(R.string.plex_cancel, null).show();
            return;
        }
        status.setText(R.string.plex_linking);
        PlexManager.execute(plex::beginPin, (pin, error) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            if (error != null) {
                status.setText(R.string.plex_link_error);
                message(error.getMessage());
            } else showPin(pin);
        }));
    }

    private void showPin(PlexManager.Pin pin) {
        pendingPin = pin;
        pinDeadline = System.currentTimeMillis() + 5 * 60 * 1000L;
        TextView instructions = new TextView(this);
        instructions.setText(getString(R.string.plex_pin_instructions, pin.code));
        instructions.setTextColor(0xFFFFFFFF);
        instructions.setTextSize(21);
        instructions.setPadding(dp(24), dp(10), dp(24), dp(16));
        pinDialog = new AlertDialog.Builder(this, R.style.Theme_TvHome_Dialog)
                .setTitle(R.string.plex_pin_title).setView(instructions)
                .setPositiveButton(R.string.plex_open_link, null)
                .setNegativeButton(R.string.plex_cancel, (dialog, which) -> cancelPin())
                .create();
        pinDialog.setOnShowListener(dialog -> pinDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://plex.tv/link/"))); }
            catch (ActivityNotFoundException e) { message(R.string.plex_open_link_error); }
        }));
        pinDialog.show();
        status.setText(R.string.plex_waiting_approval);
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
            if (completed != null && pinDialog != null && pinDialog.isShowing()) pinDialog.dismiss();
            if (error != null) {
                status.setText(R.string.plex_link_error);
                message(error.getMessage());
            } else {
                availableServers = servers;
                status.setText(getString(R.string.plex_link_success, plex.username()));
                reloadLibraries();
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
            else { availableServers = servers; reloadLibraries(); }
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
            refreshLabels();
            if (afterLoad != null) afterLoad.run();
            return;
        }
        PlexManager.execute(() -> plex.libraries(selected), (libraries, error) -> runOnUiThread(() -> {
            if (error == null) availableLibraries = libraries;
            else status.setText(getString(R.string.plex_link_error_detail, error.getMessage()));
            refreshLabels();
            if (afterLoad != null) afterLoad.run();
        }));
    }

    private void chooseServers() {
        if (availableServers.isEmpty()) { reloadServers(); return; }
        Set<String> selected = plex.selectedServers();
        boolean[] checked = new boolean[availableServers.size()];
        String[] labels = new String[availableServers.size()];
        for (int i = 0; i < availableServers.size(); i++) {
            PlexManager.Server server = availableServers.get(i);
            labels[i] = server.name;
            checked[i] = selected.isEmpty() || selected.contains(server.id);
        }
        new AlertDialog.Builder(this, R.style.Theme_TvHome_Dialog)
                .setTitle(R.string.plex_choose_servers)
                .setMultiChoiceItems(labels, checked, (dialog, which, value) -> checked[which] = value)
                .setPositiveButton(R.string.plex_save, (dialog, which) -> {
                    Set<String> ids = new HashSet<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) ids.add(availableServers.get(i).id);
                    try { plex.setSelectedServers(ids, availableServers); }
                    catch (Exception e) { message(e.getMessage()); return; }
                    availableLibraries.clear();
                    reloadLibraries();
                    refreshLabels();
                }).setNegativeButton(R.string.plex_cancel, null).show();
    }

    private void configureLocalServer() {
        if (availableServers.isEmpty()) { reloadServers(); return; }
        Set<String> selected = plex.selectedServers();
        List<PlexManager.Server> candidates = new ArrayList<>();
        for (PlexManager.Server server : availableServers) if (selected.contains(server.id)) candidates.add(server);
        if (candidates.isEmpty()) {
            message(R.string.plex_local_server_choose_first);
            return;
        }
        if (candidates.size() == 1) {
            showLocalServerEditor(candidates.get(0));
            return;
        }
        String[] labels = new String[candidates.size()];
        int checked = 0;
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = candidates.get(i).name;
            if (candidates.get(i).id.equals(plex.localRouteServerId())) checked = i;
        }
        new AlertDialog.Builder(this, R.style.Theme_TvHome_Dialog)
                .setTitle(R.string.plex_local_server_choose)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    showLocalServerEditor(candidates.get(which));
                }).setNegativeButton(R.string.plex_cancel, null).show();
    }

    private void showLocalServerEditor(PlexManager.Server server) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));
        TextView explanation = new TextView(this);
        explanation.setText(R.string.plex_local_server_secure_note);
        explanation.setTextColor(0xFFBDBDC2);
        explanation.setTextSize(16);
        form.addView(explanation, matchWrap());

        EditText address = new EditText(this);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_PHONE);
        address.setHint(R.string.plex_local_server_ip_hint);
        if (server.id.equals(plex.localRouteServerId())) address.setText(plex.localRouteAddress());
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = Math.round(8 * d);
        form.addView(address, inputParams);

        EditText port = new EditText(this);
        port.setSingleLine(true);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setHint(R.string.plex_local_server_port_hint);
        port.setText(server.id.equals(plex.localRouteServerId())
                ? Integer.toString(plex.localRoutePort()) : "32400");
        LinearLayout.LayoutParams portParams = matchWrap();
        portParams.topMargin = Math.round(4 * d);
        form.addView(port, portParams);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_TvHome_Dialog)
                .setTitle(getString(R.string.plex_local_server_editor, server.name))
                .setView(form)
                .setPositiveButton(R.string.plex_local_server_test_save, null)
                .setNegativeButton(R.string.plex_cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String ip = address.getText().toString().trim();
            int portNumber;
            try { portNumber = Integer.parseInt(port.getText().toString().trim()); }
            catch (NumberFormatException e) { message(R.string.plex_local_server_invalid); return; }
            if (!PlexLocalEndpoint.isPrivateIpv4(ip) || portNumber < 1 || portNumber > 65535) {
                message(R.string.plex_local_server_invalid);
                return;
            }
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setEnabled(false);
            status.setText(R.string.plex_local_server_testing);
            PlexManager.execute(() -> {
                plex.testLocalRoute(server, ip, portNumber);
                return Boolean.TRUE;
            }, (result, error) -> runOnUiThread(() -> {
                if (isFinishing() || !dialog.isShowing()) return;
                save.setEnabled(true);
                if (error != null) {
                    status.setText(R.string.plex_local_server_failed);
                    message(error.getMessage());
                    return;
                }
                try { plex.saveLocalRoute(server, ip, portNumber); }
                catch (Exception e) { status.setText(R.string.plex_local_server_failed); message(e.getMessage()); return; }
                dialog.dismiss();
                availableLibraries.clear();
                status.setText(R.string.plex_local_server_saved);
                reloadLibraries();
                refreshLabels();
            }));
        }));
        dialog.show();
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
        new AlertDialog.Builder(this, R.style.Theme_TvHome_Dialog)
                .setTitle(R.string.plex_choose_libraries)
                .setSingleChoiceItems(new String[]{getString(R.string.plex_all_libraries),
                        getString(R.string.plex_pick_libraries)}, plex.allLibraries() ? 0 : 1,
                        (dialog, which) -> {
                            if (which == 0) {
                                plex.setSelectedLibraries(new HashSet<>(), true);
                                refreshLabels();
                                dialog.dismiss();
                            } else {
                                dialog.dismiss();
                                if (availableLibraries.isEmpty()) reloadLibraries(this::showLibraryChecklist);
                                else showLibraryChecklist();
                            }
                        })
                .setNegativeButton(R.string.plex_cancel, null).show();
        reloadLibraries();
    }

    private void showLibraryChecklist() {
        if (availableLibraries.isEmpty()) { message(R.string.plex_no_libraries); return; }
        Set<String> selected = plex.selectedLibraries();
        boolean[] checked = new boolean[availableLibraries.size()];
        String[] labels = new String[availableLibraries.size()];
        for (int i = 0; i < availableLibraries.size(); i++) {
            PlexManager.Library library = availableLibraries.get(i);
            labels[i] = library.toString();
            checked[i] = selected.isEmpty() || selected.contains(library.key());
        }
        new AlertDialog.Builder(this, R.style.Theme_TvHome_Dialog)
                .setTitle(R.string.plex_pick_libraries)
                .setMultiChoiceItems(labels, checked, (dialog, which, value) -> checked[which] = value)
                .setPositiveButton(R.string.plex_save, (dialog, which) -> {
                    Set<String> ids = new HashSet<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) ids.add(availableLibraries.get(i).key());
                    if (ids.isEmpty()) { message(R.string.plex_choose_one_library); return; }
                    plex.setSelectedLibraries(ids, false);
                    refreshLabels();
                }).setNegativeButton(R.string.plex_cancel, null).show();
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
