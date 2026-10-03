package app.runeshare.ui;

import app.runeshare.RuneShareConfig;
import app.runeshare.RuneShareSessionTracker;
import app.runeshare.api.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.Objects;

@Slf4j
public class RuneSharePluginPanel extends PluginPanel {
    private static final String MAIN_TITLE = "RuneShare";

    private static final String BANK_TABS_TITLE = "Bank Tabs";

    private static final String TASK_SESSIONS_TITLE = "Task Sessions";

    @NonNull
    private final RuneShareConfig runeShareConfig;

    @NonNull
    private final RuneShareApi runeShareApi;

    @NonNull
    private final RuneShareSessionTracker runeShareSessionTracker;

    private TagTab activeTagTab = null;

    private List<Integer> activeItemIds = null;

    private Layout activeLayout = null;

    private Integer activeNpcId = null;

    private Integer activeWorldMapXCoordinate = null;

    private Integer activeWorldMapYCoordinate = null;

    private Integer activeTaskSessionId = null;

    public RuneSharePluginPanel(@NonNull RuneShareConfig runeShareConfig, @NonNull RuneShareApi runeShareApi, @NonNull RuneShareSessionTracker runeShareSessionTracker) {
        super(true);

        this.runeShareConfig = runeShareConfig;
        this.runeShareApi = runeShareApi;
        this.runeShareSessionTracker = runeShareSessionTracker;

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        drawPanel();
    }

    public void updateActiveTag(@Nullable TagTab activeTagTab, @Nullable List<Integer> activeItemIds, @Nullable Layout activeLayout) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> updateActiveTag(activeTagTab, activeItemIds, activeLayout));
            return;
        }

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

    public void updateNpc(int npcId, @Nullable Integer x, @Nullable Integer y) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> updateNpc(npcId, x, y));
            return;
        }

        this.activeWorldMapXCoordinate = x;
        this.activeWorldMapYCoordinate = y;

        // The panel doesn't show coordinates, so only a new NPC needs a redraw.
        if (!Objects.equals(this.activeNpcId, npcId)) {
            this.activeNpcId = npcId;

            drawPanel();
        }
    }

    /**
     * Forgets any location captured before the player opted out of sharing it.
     * The panel never renders the coordinates, so there is nothing to redraw.
     */
    public void clearLocation() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clearLocation);
            return;
        }

        this.activeWorldMapXCoordinate = null;
        this.activeWorldMapYCoordinate = null;
    }

    public void redraw() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::redraw);
            return;
        }

        log.debug("Redrawing panel");
        drawPanel();
    }

    /**
     * A section heading, styled like the ones RuneLite uses for config sections:
     * bold orange text above a divider line.
     */
    private JPanel createSectionHeader(String text) {
        final JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.BRAND_ORANGE);
        label.setFont(FontManager.getRunescapeBoldFont());

        final JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(0, 0, 3, 0)));
        header.add(label, BorderLayout.WEST);
        fullWidth(header);

        return header;
    }

    /**
     * The panel's read-only body text. JTextArea rather than JLabel so that long
     * messages wrap to the panel width instead of being clipped.
     * <p>
     * Deliberately not passed through {@link #fullWidth}: a wrapping text area's
     * preferred height depends on the width it ends up with, so pinning its
     * maximum height to the value known at build time would cut off the later
     * lines of a message that wraps. It stretches to the panel width on its own,
     * so it only needs the alignment.
     */
    private JTextArea createBodyText(String text) {
        final JTextArea textArea = new JTextArea(text);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setOpaque(false);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        return textArea;
    }

    /**
     * Stretch a component across the panel.
     * <p>
     * BoxLayout lays children out at their preferred width and then aligns them
     * against each other by alignmentX, so a narrow child sits inset from the
     * panel edge rather than flush against it, no matter what alignment it is
     * given. Letting each child grow to the full width removes the difference
     * the alignment would otherwise be resolving, which left-aligns the text and
     * lets a button's label use the whole width instead of truncating.
     */
    private void fullWidth(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
    }

    private void drawPanel() {
        final JPanel containerPanel = new JPanel();
        containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.Y_AXIS));
        containerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        containerPanel.setVisible(true);

        final JLabel title = new JLabel(MAIN_TITLE);
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());
        fullWidth(title);
        containerPanel.add(title);

        final String apiToken = runeShareConfig.apiToken();
        final boolean noApiTokenConfigured = apiToken == null || apiToken.isEmpty();
        if (noApiTokenConfigured) {
            containerPanel.add(Box.createVerticalStrut(10));
            containerPanel.add(createBodyText("There is no API token configured. Please add this to the RuneShare plugin settings."));

            finishPanel(containerPanel);
            return;
        }

        containerPanel.add(Box.createVerticalStrut(12));
        containerPanel.add(createSectionHeader(BANK_TABS_TITLE));
        containerPanel.add(Box.createVerticalStrut(6));
        addBankTabsSection(containerPanel);

        containerPanel.add(Box.createVerticalStrut(16));
        containerPanel.add(createSectionHeader(TASK_SESSIONS_TITLE));
        containerPanel.add(Box.createVerticalStrut(6));
        addTaskSessionsSection(containerPanel);

        finishPanel(containerPanel);
    }

    private void addBankTabsSection(JPanel containerPanel) {
        if (this.activeTagTab == null) {
            containerPanel.add(createBodyText("There is no active tag. Please select an tag in your bank."));
            return;
        }

        containerPanel.add(createBodyText("Active Tag: " + this.activeTagTab.getTag()));

        if (runeShareConfig.autoSave()) {
            containerPanel.add(Box.createVerticalStrut(4));
            containerPanel.add(createBodyText("Active tags are being saved automatically to RuneShare."));
            return;
        }

        final JButton syncButton = new JButton("Sync to RuneShare");
        syncButton.addActionListener((event) -> {
            runeShareApi.createRuneShareBankTab(activeTagTab, activeItemIds, activeLayout);
        });
        fullWidth(syncButton);

        containerPanel.add(Box.createVerticalStrut(6));
        containerPanel.add(syncButton);
    }

    private void addTaskSessionsSection(JPanel containerPanel) {
        if (activeNpcId == null) {
            containerPanel.add(createBodyText("Start fighting an NPC to start tracking."));
            return;
        }

        if (activeTaskSessionId == null) {
            final JButton startSessionButton = new JButton("Start Session");
            startSessionButton.addActionListener((event) -> {
                final StartTaskSession startTaskSession = StartTaskSession
                        .builder()
                        .npcRunescapeId(activeNpcId)
                        .worldMapXCoordinate(activeWorldMapXCoordinate)
                        .worldMapYCoordinate(activeWorldMapYCoordinate)
                        .build();

                runeShareSessionTracker.start(startTaskSession, startTaskSessionResponse -> {
                    this.activeTaskSessionId = startTaskSessionResponse.getTaskSessionId();
                    this.redraw();
                });
            });
            fullWidth(startSessionButton);
            containerPanel.add(startSessionButton);
        } else {
            final JButton stopSessionButton = new JButton("Stop Session");
            stopSessionButton.addActionListener((event) -> {
                runeShareSessionTracker.stop(() -> {
                    this.activeTaskSessionId = null;
                    this.redraw();
                });
            });
            fullWidth(stopSessionButton);
            containerPanel.add(stopSessionButton);
        }
    }

    private void finishPanel(JPanel containerPanel) {
        removeAll();
        add(containerPanel, BorderLayout.NORTH);
        revalidate();
        repaint();
    }
}
