package app.runeshare.ui;

import app.runeshare.RuneShareConfig;
import app.runeshare.api.RuneShareApi;
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
import java.awt.GridLayout;
import java.util.List;

@Slf4j
public class RuneSharePluginPanel extends PluginPanel {
    private static final String MAIN_TITLE = "RuneShare";

    @NonNull
    private final RuneShareConfig runeShareConfig;

    private TagTab activeTagTab = null;

    private List<Integer> activeItemIds = null;

    private Layout activeLayout = null;

    public RuneSharePluginPanel(@NonNull RuneShareConfig runeShareConfig) {
        super(false);

        this.runeShareConfig = runeShareConfig;

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

        if (this.activeTagTab == null) {
            final JTextPane noActiveTagPane = new JTextPane();
            noActiveTagPane.setEditable(false);
            noActiveTagPane.setFocusable(false);
            noActiveTagPane.setOpaque(false);
            noActiveTagPane.setLayout(new BorderLayout());
            noActiveTagPane.setAlignmentX(SwingConstants.LEFT);
            noActiveTagPane.setAlignmentY(SwingConstants.TOP);
            noActiveTagPane.setText("There is no active tag. Please select an tag in your bank.");
            noActiveTagPane.setForeground(Color.WHITE);
            containerPanel.add(noActiveTagPane);
        } else {
            final JPanel activeTagPanel = new JPanel();
            activeTagPanel.setLayout(new GridLayout(1, 2));

            final JLabel activeTagLabel = new JLabel();
            activeTagLabel.setText("Active Tag");
            activeTagLabel.setForeground(Color.WHITE);
            activeTagLabel.setHorizontalAlignment(SwingConstants.LEFT);
            activeTagPanel.add(activeTagLabel);

            final JLabel activeTagText = new JLabel();
            activeTagText.setText(this.activeTagTab.getTag());
            activeTagText.setForeground(Color.WHITE);
            activeTagText.setHorizontalAlignment(SwingConstants.RIGHT);
            activeTagPanel.add(activeTagText);

            containerPanel.add(activeTagPanel);

            if (runeShareConfig.autoSave()) {
                final JTextPane noSyncNeededPane = new JTextPane();
                noSyncNeededPane.setEditable(false);
                noSyncNeededPane.setFocusable(false);
                noSyncNeededPane.setOpaque(false);
                noSyncNeededPane.setLayout(new BorderLayout());
                noSyncNeededPane.setAlignmentX(SwingConstants.LEFT);
                noSyncNeededPane.setAlignmentY(SwingConstants.TOP);
                noSyncNeededPane.setText("Active tags are being saved automatically to RuneShare.");
                noSyncNeededPane.setForeground(Color.WHITE);
                containerPanel.add(noSyncNeededPane);
            } else {
                final JButton syncButton = new JButton();
                syncButton.setText("Sync to RuneShare");
                syncButton.addActionListener((event) -> {
                    RuneShareApi runeShareApi = new RuneShareApi(runeShareConfig.apiToken());
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
