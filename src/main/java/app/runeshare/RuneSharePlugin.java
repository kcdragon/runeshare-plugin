package app.runeshare;

import app.runeshare.api.RuneShareApi;
import app.runeshare.ui.RuneSharePluginPanel;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
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
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
	private static final long TIME_BETWEEN_TASK_EVENTS_MS = 30 * 1000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

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
		runeShareSessionTracker = new RuneShareSessionTracker(runeShareApi);

		// startUp runs on the EDT when the plugin is toggled on, but reading the
		// account type varbit asserts it is on the client thread. Returning false
		// re-queues this for the next tick, so it retries until we are logged in
		// and the varbit and world type are actually meaningful.
		clientThread.invokeLater(() -> {
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return false;
			}

			updateAccountAndWorld();
			readLoadoutFromClient();
			return true;
		});

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
		// shutDown still runs if startUp threw before the button was built.
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(RuneShareConfig.CONFIG_GROUP)) {
			// ConfigChanged is posted on the thread that changed the setting, the EDT for
			// the settings panel, so reading containers has to hop to the client thread.
			if (runeShareSessionTracker != null) {
				if (runeShareConfig.shareLoadout()) {
					clientThread.invokeLater(this::readLoadoutFromClient);
				} else {
					clearLoadout();
				}
			}

			final boolean shareLocation = runeShareConfig.shareLocation();
			SwingUtilities.invokeLater(() -> {
				// Coordinates are only refreshed on the next hit, so drop any that
				// were cached before the player opted out.
				if (!shareLocation) {
					this.panel.clearLocation();
				}
				this.panel.redraw();
			});
		}
	}

	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		if (runeShareSessionTracker != null) {
			updateAccountAndWorld();
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

			// The ID is read here, on the client thread, because NPC.getId() turns
			// to -1 once the NPC dies or despawns, long before Start Session is clicked.
			final int npcId = npc.getId();
			final boolean npcHasNoDefinition = npcId == -1;

			// The location is best effort: the player, or their location, can be
			// absent, and the session is still worth tracking without coordinates.
			// Players can also opt out of sharing it entirely, in which case we
			// never read it in the first place.
			Integer x = null;
			Integer y = null;
			Integer plane = null;
			if (runeShareConfig.shareLocation())
			{
				final Player localPlayer = client.getLocalPlayer();
				// getWorldLocation() is in instance space inside an instance, which
				// doesn't correspond to anywhere on the real map.
				final WorldPoint playerLocation = localPlayer == null ? null : WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation());
				final WorldPoint localWorld = playerLocation == null ? null : WorldPoint.getMirrorPoint(playerLocation, true);
				x = localWorld == null ? null : localWorld.getX();
				y = localWorld == null ? null : localWorld.getY();
				plane = localWorld == null ? null : localWorld.getPlane();
			}

			if (!npcHasNoDefinition)
			{
				this.panel.updateNpc(npcId, x, y, plane);
			}

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
	public void onItemContainerChanged(ItemContainerChanged itemContainerChanged)
	{
		if (runeShareSessionTracker == null || !runeShareConfig.shareLoadout())
		{
			return;
		}

		final int containerId = itemContainerChanged.getContainerId();
		final Item[] items = itemContainerChanged.getItemContainer().getItems();

		if (containerId == InventoryID.INV)
		{
			runeShareSessionTracker.setBackpackRunescapeItemIds(Loadout.backpackItemIds(items));
		}
		else if (containerId == InventoryID.WORN)
		{
			runeShareSessionTracker.setEquipmentRunescapeItemIds(Loadout.equipmentItemIds(items));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (runeShareSessionTracker == null)
		{
			return;
		}

		final GameState gameState = gameStateChanged.getGameState();
		final boolean leavingCharacter = gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING;
		if (leavingCharacter)
		{
			clearLoadout();
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		String tag = bankTagsService.getActiveTag();

		if (tag == null) {
			if (this.activeTag != null) {
				this.activeTag = null;
				this.activeItemIds = null;
				this.activeLayout = null;

				log.debug("There is no longer an active tag");

				SwingUtilities.invokeLater(() -> {
					this.panel.updateActiveTag(null, null, null, null);
				});
			}
			return;
		}

		List<Integer> itemIds = tagManager.getItemsForTag(tag);
		Layout layout = bankTagsService.getActiveLayout();

		boolean hasTagChanged = !tag.equals(this.activeTag);
		boolean hasItemIdsChanged = activeItemIds != null && !itemIds.isEmpty() && !itemIds.equals(activeItemIds);
		boolean hasLayoutChanged = (layout != null && activeLayout == null) || (layout == null && activeLayout != null) || (layout != null && activeLayout != null && !Arrays.equals(layout.getLayout(), activeLayout.getLayout()));

		if (hasTagChanged || hasItemIdsChanged || hasLayoutChanged) {
			this.activeTag = tag;
			this.activeItemIds = new ArrayList<>(itemIds);

			if (layout != null) {
				this.activeLayout = new Layout(layout);
			} else {
				this.activeLayout = null;
			}

			log.debug("Active tag has changed to \"{}\"", this.activeTag);

			// The bank is open whenever a tag is active, so the player is logged in and
			// the account type varbit has been loaded.
			final PlayerAccount playerAccount = readPlayerAccountFromClient();

			TagTab activeTagTab = tabManager.find(this.activeTag);
			final List<Integer> itemIdsCopy = this.activeItemIds;
			final Layout layoutCopy = this.activeLayout;

			SwingUtilities.invokeLater(() -> {
				final String apiToken = runeShareConfig.apiToken();
				if (activeTagTab != null && apiToken != null && !apiToken.isEmpty() && runeShareConfig.autoSave()) {
					log.info("Automatically saving bank tab to RuneShare.");
					runeShareApi.createRuneShareBankTab(activeTagTab, itemIdsCopy, layoutCopy, playerAccount);
				}

				this.panel.updateActiveTag(activeTagTab, itemIdsCopy, layoutCopy, playerAccount);
			});
		}
	}

	@Provides
	RuneShareConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneShareConfig.class);
	}

	/**
	 * Must run on the client thread.
	 */
	private void readLoadoutFromClient() {
		if (!runeShareConfig.shareLoadout()) {
			clearLoadout();
			return;
		}

		final ItemContainer backpack = client.getItemContainer(InventoryID.INV);
		final ItemContainer equipment = client.getItemContainer(InventoryID.WORN);

		runeShareSessionTracker.setBackpackRunescapeItemIds(backpack == null ? null : Loadout.backpackItemIds(backpack.getItems()));
		runeShareSessionTracker.setEquipmentRunescapeItemIds(equipment == null ? null : Loadout.equipmentItemIds(equipment.getItems()));
	}

	private void clearLoadout() {
		runeShareSessionTracker.setBackpackRunescapeItemIds(null);
		runeShareSessionTracker.setEquipmentRunescapeItemIds(null);
	}

	private void updateAccountAndWorld() {
		runeShareSessionTracker.setAccountType(getAccountType());
		runeShareSessionTracker.setWorldTypes(client.getWorldType());
	}

	private String getAccountType() {
		return PlayerAccount.accountType(client.getVarbitValue(Varbits.ACCOUNT_TYPE));
	}

	/**
	 * Must run on the client thread.
	 */
	private PlayerAccount readPlayerAccountFromClient() {
		return PlayerAccount.from(client.getVarbitValue(Varbits.ACCOUNT_TYPE), client.getWorldType());
	}
}
