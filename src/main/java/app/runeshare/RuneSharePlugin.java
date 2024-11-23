package app.runeshare;

import app.runeshare.ui.RuneSharePluginPanel;
import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.util.Arrays;

@Slf4j
@PluginDescriptor(
	name = "RuneShare",
	description = "Share bank tabs with other players",
	tags = { "gear", "inventory", "setups" }
)
@PluginDependency(BankTagsPlugin.class)
public class RuneSharePlugin extends Plugin
{
	private static final int NAVIGATION_PRIORITY = 100;
	private static final String PLUGIN_NAME = "RuneShare";

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RuneShareConfig config;

	@Inject
	private TabManager tabManager;

	@Inject
	private BankTagsService bankTagsService;

	private RuneSharePluginPanel panel;

	private NavigationButton navigationButton;

	private String activeTag = null;

	private Layout activeLayout = null;

	@Override
	protected void startUp() throws Exception
	{
		this.panel = new RuneSharePluginPanel(this.config);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

		this.navigationButton = NavigationButton.builder()
				.tooltip(PLUGIN_NAME)
				.icon(icon)
				.priority(NAVIGATION_PRIORITY)
				.panel(this.panel)
				.build();

		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navigationButton);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		String tag = bankTagsService.getActiveTag();
		Layout layout = bankTagsService.getActiveLayout();

		boolean hasTagChanged = tag != null && !tag.equals(this.activeTag);
		boolean hasLayoutChanged = layout != null && activeLayout != null && !Arrays.equals(layout.getLayout(), activeLayout.getLayout());

		if (hasTagChanged || hasLayoutChanged) {
			this.activeTag = tag;
			this.activeLayout = layout;

			log.info("Active tag has changed to \"{}\"", this.activeTag);

			TagTab activeTagTab = tabManager.find(this.activeTag);

			clientThread.invokeLater(() -> {
				this.panel.updateActiveTag(activeTagTab, activeLayout);
			});
		} else if (tag == null && this.activeTag != null) {
			this.activeTag = null;
			this.activeLayout = null;

			log.info("There is no longer an active tag");

			clientThread.invokeLater(() -> {
				this.panel.updateActiveTag(null, null);
			});
		}
	}

	@Provides
	RuneShareConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneShareConfig.class);
	}
}
