package app.runeshare.ui;

import app.runeshare.RuneShareConfig;
import app.runeshare.api.RuneShareApi;
import com.google.gson.Gson;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.BorderLayout;
import java.util.List;

@Slf4j
public class RuneSharePluginPanel extends PluginPanel {
    private static final String MAIN_TITLE = "RuneShare";

    @NonNull
    private final RuneShareConfig runeShareConfig;

    @NonNull
    private final Gson gson;

    private TagTab activeTagTab = null;

    private List<Integer> activeItemIds = null;

    private Layout activeLayout = null;

    public RuneSharePluginPanel(@NonNull RuneShareConfig runeShareConfig, @NonNull Gson gson) {
        super(false);

        this.runeShareConfig = runeShareConfig;
        this.gson = gson;

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        drawPanel();
    }

    public void updateActiveTag(@Nullable TagTab activeTagTab, @Nullable List<Integer> activeItemIds, @Nullable Layout activeLayout) {
        this.activeTagTab = activeTagTab;
        this.activeLayout = activeLayout;
        this.activeItemIds = activeItemIds;

        if (activeTagTab != null) {
            log.debug("Redrawing panel with \"{}\" tag", activeTagTab.getTag());
        } else {
            log.debug("Redrawing panel without a tag");
        }

        drawPanel();
    }

    public void redraw() {
        log.debug("Redrawing panel");
        drawPanel();
    }

    private void drawPanel() {
        final JPanel containerPanel = new JPanel();
        containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.Y_AXIS));
        containerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        containerPanel.setVisible(true);

        final JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BorderLayout());
        final JLabel title = new JLabel();
        title.setText(MAIN_TITLE);
        title.setForeground(Color.WHITE);
        titlePanel.add(title, BorderLayout.WEST);
        containerPanel.add(titlePanel);

        final String apiToken = runeShareConfig.apiToken();
        final boolean noApiTokenConfigured = apiToken == null || apiToken.isEmpty();
        if (noApiTokenConfigured) {
            final JTextArea noApiTokenConfiguredTextArea = new JTextArea(1, 20);
            noApiTokenConfiguredTextArea.setText("There is no API token configured. Please add this to the RuneShare plugin settings.");
            noApiTokenConfiguredTextArea.setWrapStyleWord(true);
            noApiTokenConfiguredTextArea.setLineWrap(true);
            noApiTokenConfiguredTextArea.setOpaque(false);
            noApiTokenConfiguredTextArea.setEditable(false);
            noApiTokenConfiguredTextArea.setFocusable(false);
            containerPanel.add(noApiTokenConfiguredTextArea);

        } else if (this.activeTagTab == null) {
            final JTextArea noActiveTagTextArea = new JTextArea(1, 20);
            noActiveTagTextArea.setText("There is no active tag. Please select an tag in your bank.");
            noActiveTagTextArea.setWrapStyleWord(true);
            noActiveTagTextArea.setLineWrap(true);
            noActiveTagTextArea.setOpaque(false);
            noActiveTagTextArea.setEditable(false);
            noActiveTagTextArea.setFocusable(false);
            containerPanel.add(noActiveTagTextArea);
        } else {
            final JTextArea activeTagTextArea = new JTextArea(1, 20);
            activeTagTextArea.setText("Active Tag: " + this.activeTagTab.getTag());
            activeTagTextArea.setWrapStyleWord(true);
            activeTagTextArea.setLineWrap(true);
            activeTagTextArea.setOpaque(false);
            activeTagTextArea.setEditable(false);
            activeTagTextArea.setFocusable(false);
            containerPanel.add(activeTagTextArea);

            if (runeShareConfig.autoSave()) {
                final JTextArea noSyncNeededTextArea = new JTextArea(1, 20);
                noSyncNeededTextArea.setText("Active tags are being saved automatically to RuneShare.");
                noSyncNeededTextArea.setWrapStyleWord(true);
                noSyncNeededTextArea.setLineWrap(true);
                noSyncNeededTextArea.setOpaque(false);
                noSyncNeededTextArea.setEditable(false);
                noSyncNeededTextArea.setFocusable(false);
                containerPanel.add(noSyncNeededTextArea);
            } else {
                final JButton syncButton = new JButton();
                syncButton.setText("Sync to RuneShare");
                syncButton.addActionListener((event) -> {
                    RuneShareApi runeShareApi = new RuneShareApi(apiToken, gson);
                    runeShareApi.createRuneShareBankTab(activeTagTab, activeItemIds, activeLayout);
                });
                containerPanel.add(syncButton);
            }
        }

        removeAll();
        add(containerPanel, BorderLayout.NORTH);
        revalidate();
    }
}
