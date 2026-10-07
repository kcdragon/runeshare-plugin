package app.runeshare.ui;

import app.runeshare.PlayerAccount;
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
import net.runelite.client.util.LinkBrowser;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Slf4j
public class RuneSharePluginPanel extends PluginPanel {
    private static final String MAIN_TITLE = "RuneShare";

    private static final String BANK_TABS_TITLE = "Bank Tabs";

    private static final String TASK_SESSIONS_TITLE = "Task Sessions";

    private static final DateTimeFormatter SYNCED_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @NonNull
    private final RuneShareConfig runeShareConfig;

    @NonNull
    private final RuneShareApi runeShareApi;

    @NonNull
    private final RuneShareConnection runeShareConnection;

    @NonNull
    private final RuneShareSessionTracker runeShareSessionTracker;

    @NonNull
    private final BankTabSync bankTabSync;

    private TagTab activeTagTab = null;

    private List<Integer> activeItemIds = null;

    private Layout activeLayout = null;

    private PlayerAccount activePlayerAccount = null;

    private Integer activeNpcId = null;

    private Integer activeWorldMapXCoordinate = null;

    private Integer activeWorldMapYCoordinate = null;

    private Integer activeWorldMapPlane = null;

    private Integer activeTaskSessionId = null;

    private boolean startingTaskSession = false;

    @Nullable
    private String taskSessionError = null;

    public RuneSharePluginPanel(@NonNull RuneShareConfig runeShareConfig, @NonNull RuneShareApi runeShareApi, @NonNull RuneShareConnection runeShareConnection, @NonNull RuneShareSessionTracker runeShareSessionTracker, @NonNull BankTabSync bankTabSync) {
        super(true);

        this.runeShareConfig = runeShareConfig;
        this.runeShareApi = runeShareApi;
        this.runeShareConnection = runeShareConnection;
        this.runeShareSessionTracker = runeShareSessionTracker;
        this.bankTabSync = bankTabSync;

        runeShareConnection.setListener(this::onConnectionChanged);
        bankTabSync.setListener(this::redraw);

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout());

        drawPanel();
    }

    public void updateActiveTag(@Nullable TagTab activeTagTab, @Nullable List<Integer> activeItemIds, @Nullable Layout activeLayout, @Nullable PlayerAccount activePlayerAccount) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> updateActiveTag(activeTagTab, activeItemIds, activeLayout, activePlayerAccount));
            return;
        }

        this.activeTagTab = activeTagTab;
        this.activeLayout = activeLayout;
        this.activeItemIds = activeItemIds;
        this.activePlayerAccount = activePlayerAccount;

        if (activeTagTab != null) {
            log.debug("Redrawing panel with \"{}\" tag", activeTagTab.getTag());
        } else {
            log.debug("Redrawing panel without a tag");
        }

        drawPanel();
    }

    public void updateNpc(int npcId, @Nullable Integer x, @Nullable Integer y, @Nullable Integer plane) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> updateNpc(npcId, x, y, plane));
            return;
        }

        this.activeWorldMapXCoordinate = x;
        this.activeWorldMapYCoordinate = y;
        this.activeWorldMapPlane = plane;

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
        this.activeWorldMapPlane = null;
    }

    @Override
    public void onActivate() {
        final ConnectionStatus status = runeShareConnection.getStatus();
        final boolean worthRetrying = status == ConnectionStatus.UNAVAILABLE || status == ConnectionStatus.API_DISABLED;
        if (worthRetrying) {
            runeShareApi.fetchCurrentUser();
        }
    }

    private void onConnectionChanged() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::onConnectionChanged);
            return;
        }

        // A rejected token can't stop the session on RuneShare either, so the
        // player would otherwise be stuck behind a Stop button that never works.
        if (runeShareConnection.getStatus() == ConnectionStatus.INVALID_TOKEN && activeTaskSessionId != null) {
            runeShareSessionTracker.abandon();
            activeTaskSessionId = null;
        }

        drawPanel();
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

    private JLabel createLink(String text, String url) {
        final String escapedText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        final JLabel link = new JLabel("<html><u>" + escapedText + "</u></html>");
        link.setForeground(ColorScheme.BRAND_ORANGE);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                LinkBrowser.browse(url);
            }
        });
        fullWidth(link);

        return link;
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

        final ConnectionStatus connectionStatus = runeShareConnection.getStatus();
        addConnectionStatus(containerPanel, connectionStatus);
        if (connectionStatus == ConnectionStatus.INVALID_TOKEN) {
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

    private void addConnectionStatus(JPanel containerPanel, ConnectionStatus connectionStatus) {
        switch (connectionStatus) {
            case CONNECTED:
                final CurrentUser currentUser = runeShareConnection.getCurrentUser();
                if (currentUser == null) {
                    return;
                }

                final String connectedAs = "Connected as " + currentUser.getUsername();
                containerPanel.add(Box.createVerticalStrut(6));
                if (currentUser.getProfileUrl() != null) {
                    containerPanel.add(createLink(connectedAs, currentUser.getProfileUrl()));
                } else {
                    containerPanel.add(createBodyText(connectedAs));
                }

                if (currentUser.getApiToken() != null) {
                    final JTextArea tokenName = createBodyText("Using the \"" + currentUser.getApiToken().getName() + "\" API token");
                    tokenName.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                    tokenName.setFont(FontManager.getRunescapeSmallFont());
                    containerPanel.add(tokenName);
                }
                return;
            case INVALID_TOKEN:
                containerPanel.add(Box.createVerticalStrut(10));
                containerPanel.add(createBodyText("Your API token wasn't accepted. Create a new one at osrs.runeshare.app/api_tokens and paste it into the plugin settings."));
                return;
            case API_DISABLED:
                containerPanel.add(Box.createVerticalStrut(6));
                containerPanel.add(createBodyText("RuneShare isn't accepting plugin data right now."));
                return;
            case UNAVAILABLE:
                containerPanel.add(Box.createVerticalStrut(6));
                containerPanel.add(createBodyText("Can't reach RuneShare right now."));
                return;
        }
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
        } else {
            final JButton syncButton = new JButton("Sync to RuneShare");
            syncButton.addActionListener((event) -> {
                bankTabSync.sync(RuneShareBankTab.from(activeTagTab, activeItemIds, activeLayout, activePlayerAccount));
            });
            fullWidth(syncButton);

            containerPanel.add(Box.createVerticalStrut(6));
            containerPanel.add(syncButton);
        }

        final BankTabSync.Status syncStatus = bankTabSync.getStatus();
        if (syncStatus != null) {
            containerPanel.add(Box.createVerticalStrut(6));
            containerPanel.add(createBodyText(describe(syncStatus)));
        }
    }

    private static String describe(BankTabSync.Status syncStatus) {
        final String quotedTag = "'" + syncStatus.getTag() + "'";
        switch (syncStatus.getState()) {
            case SYNCING:
                return "Syncing " + quotedTag + "...";
            case SYNCED:
                return "Synced " + quotedTag + " at " + SYNCED_AT_FORMAT.format(syncStatus.getSyncedAt()) + ".";
            case RETRYING:
                return "Couldn't sync " + quotedTag + " (" + syncStatus.getFailure() + "), retrying.";
            case FAILED:
            default:
                return "Couldn't sync " + quotedTag + ": " + syncStatus.getFailure() + ".";
        }
    }

    private void addTaskSessionsSection(JPanel containerPanel) {
        if (activeNpcId == null) {
            containerPanel.add(createBodyText("Start fighting an NPC to start tracking."));
            return;
        }

        if (activeTaskSessionId == null) {
            final JButton startSessionButton = new JButton(startingTaskSession ? "Starting Session..." : "Start Session");
            // A second click before RuneShare answers would start a second session.
            startSessionButton.setEnabled(!startingTaskSession);
            startSessionButton.addActionListener((event) -> {
                startingTaskSession = true;
                taskSessionError = null;

                final StartTaskSession startTaskSession = StartTaskSession
                        .builder()
                        .npcRunescapeId(activeNpcId)
                        .worldMapXCoordinate(activeWorldMapXCoordinate)
                        .worldMapYCoordinate(activeWorldMapYCoordinate)
                        .worldMapPlane(activeWorldMapPlane)
                        .build();

                runeShareSessionTracker.start(startTaskSession, new StartTaskSessionResponseHandler() {
                    @Override
                    public void onSuccess(StartTaskSessionResponse startTaskSessionResponse) {
                        startingTaskSession = false;
                        activeTaskSessionId = startTaskSessionResponse.getTaskSessionId();
                        drawPanel();
                    }

                    @Override
                    public void onFailure(String reason) {
                        startingTaskSession = false;
                        taskSessionError = "Couldn't start the session: " + reason + ".";
                        drawPanel();
                    }
                });
                drawPanel();
            });
            fullWidth(startSessionButton);
            containerPanel.add(startSessionButton);
        } else {
            final JButton stopSessionButton = new JButton("Stop Session");
            stopSessionButton.addActionListener((event) -> {
                // The tracker forgets the session straight away, so the panel does
                // too, rather than leaving Stop up until RuneShare answers.
                activeTaskSessionId = null;
                taskSessionError = null;
                runeShareSessionTracker.stop(new StopTaskSessionResponseHandler() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onFailure(String reason) {
                        taskSessionError = "Couldn't stop the session on RuneShare: " + reason + ".";
                        drawPanel();
                    }
                });
                drawPanel();
            });
            fullWidth(stopSessionButton);
            containerPanel.add(stopSessionButton);
        }

        if (taskSessionError != null) {
            containerPanel.add(Box.createVerticalStrut(6));
            containerPanel.add(createBodyText(taskSessionError));
        }
    }

    private void finishPanel(JPanel containerPanel) {
        removeAll();
        add(containerPanel, BorderLayout.NORTH);
        revalidate();
        repaint();
    }
}
