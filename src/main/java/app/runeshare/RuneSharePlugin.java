package app.runeshare;

import app.runeshare.api.RuneShareApi;
import app.runeshare.api.RuneShareTaskEvent;
import app.runeshare.ui.RuneSharePluginPanel;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.plugins.xptracker.XpTrackerService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

@Slf4j
@PluginDescriptor(
	name = "RuneShare",
	description = "Share bank tabs with other players",
	tags = { "gear", "inventory", "setups" }
)
@PluginDependency(BankTagsPlugin.class)
@PluginDependency(XpTrackerPlugin.class)
public class RuneSharePlugin extends Plugin
{
	private static final int NAVIGATION_PRIORITY = 100;
	private static final String PLUGIN_NAME = "RuneShare";
	private static final long TIME_BETWEEN_TASK_EVENTS_MS = 30 * 1000;

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private RuneShareConfig runeShareConfig;

	@Inject
	private TabManager tabManager;

	@Inject
	private TagManager tagManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private RuneShareApi runeShareApi;

	@Inject
	private XpTrackerService xpTrackerService;

	private RuneSharePluginPanel panel;

	private NavigationButton navigationButton;

	private String activeTag = null;

	private Layout activeLayout = null;

	private List<Integer> activeItemIds = null;

	private RuneShareSessionTracker runeShareSessionTracker = null;

	private Long lastTaskEventSentAtMs = null;

	@Override
	protected void startUp() throws Exception
	{
		this.runeShareSessionTracker = new RuneShareSessionTracker(runeShareApi);
		this.panel = new RuneSharePluginPanel(runeShareConfig, runeShareApi, runeShareSessionTracker);

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
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(RuneShareConfig.CONFIG_GROUP)) {
			SwingUtilities.invokeLater(() -> {
				this.panel.redraw();
			});
		}
	}

	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		if (runeShareSessionTracker != null) {
			runeShareSessionTracker.setAccountType(getAccountType());
			runeShareSessionTracker.setWorldTypes(client.getWorldType());
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied hitsplatApplied)
	{
		Actor actor = hitsplatApplied.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}

		final Hitsplat hitsplat = hitsplatApplied.getHitsplat();
		final NPC npc = (NPC) actor;

		boolean weAreAttacking = hitsplat.isMine();
		if (weAreAttacking)
		{
			log.debug("You are attacking {}", npc.getName());

			this.panel.updateNpc(npc);

			long currentTimeInMs = System.currentTimeMillis();
			if (runeShareSessionTracker.isRunning() && (lastTaskEventSentAtMs == null || lastTaskEventSentAtMs + TIME_BETWEEN_TASK_EVENTS_MS < currentTimeInMs)) {
				final int attackXp = client.getSkillExperience(Skill.ATTACK);
				final int strengthXp = client.getSkillExperience(Skill.STRENGTH);
				final int defenceXp = client.getSkillExperience(Skill.DEFENCE);
				final int rangedXp = client.getSkillExperience(Skill.RANGED);
				final int magicXp = client.getSkillExperience(Skill.MAGIC);
				final int hitpointsXp = client.getSkillExperience(Skill.HITPOINTS);
				final int slayerXp = client.getSkillExperience(Skill.SLAYER);

				SwingUtilities.invokeLater(() -> {
					runeShareSessionTracker.updateXp(attackXp, strengthXp, defenceXp, rangedXp, magicXp, hitpointsXp, slayerXp);
				});

				lastTaskEventSentAtMs = currentTimeInMs;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		String tag = bankTagsService.getActiveTag();
		List<Integer> itemIds = tagManager.getItemsForTag(tag);
		Layout layout = bankTagsService.getActiveLayout();

		boolean hasTagChanged = tag != null && !tag.equals(this.activeTag);
		boolean hasItemIdsChanged = activeItemIds != null && !itemIds.isEmpty() && !itemIds.equals(activeItemIds);
		boolean hasLayoutChanged = (layout != null && activeLayout == null) || (layout == null && activeLayout != null) || (layout != null && activeLayout != null && !Arrays.equals(layout.getLayout(), activeLayout.getLayout()));

		if (hasTagChanged || hasItemIdsChanged || hasLayoutChanged) {
			this.activeTag = tag;

			if (itemIds != null) {
				this.activeItemIds = new ArrayList<>(itemIds);
			} else {
				this.activeItemIds = null;
			}

			if (layout != null) {
				this.activeLayout = new Layout(layout);
			} else {
				this.activeLayout = null;
			}

			log.debug("Active tag has changed to \"{}\"", this.activeTag);

			TagTab activeTagTab = tabManager.find(this.activeTag);

			SwingUtilities.invokeLater(() -> {
				final String apiToken = runeShareConfig.apiToken();
				if (apiToken != null && !apiToken.isEmpty() && runeShareConfig.autoSave()) {
					log.info("Automatically saving bank tab to RuneShare.");
					runeShareApi.createRuneShareBankTab(activeTagTab, activeItemIds, activeLayout);
				}

				this.panel.updateActiveTag(activeTagTab, activeItemIds, activeLayout);
			});
		} else if (tag == null && this.activeTag != null) {
			this.activeTag = null;
			this.activeItemIds = null;
			this.activeLayout = null;

			log.debug("There is no longer an active tag");

			SwingUtilities.invokeLater(() -> {
				this.panel.updateActiveTag(null, null, null);
			});
		}
	}

	@Provides
	RuneShareConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneShareConfig.class);
	}

	private String getAccountType() {
		int accountTypeId = client.getVarbitValue(Varbits.ACCOUNT_TYPE);
		String accountType = null;
		if (accountTypeId == 0) {
			accountType = "normal";
		} else if (accountTypeId == 1) {
			accountType = "ironman";
		} else if (accountTypeId == 2) {
			accountType = "ultimate_ironman";
		} else if (accountTypeId == 3) {
			accountType = "hardcore_ironman";
		} else if (accountTypeId == 4) {
			accountType = "group_ironman";
		} else if (accountTypeId == 5) {
			accountType = "hardcore_group_ironman";
		} else if (accountTypeId == 6) {
			accountType = "unranked_group_ironman";
		}
		return accountType;
	}
}
