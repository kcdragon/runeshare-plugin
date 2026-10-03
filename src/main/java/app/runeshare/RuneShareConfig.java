package app.runeshare;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import static app.runeshare.RuneShareConfig.CONFIG_GROUP;

@ConfigGroup(CONFIG_GROUP)
public interface RuneShareConfig extends Config
{
	String CONFIG_GROUP = "RuneShare";
	String API_TOKEN_CONFIG_KEY = "apiToken";

	@ConfigItem(
			keyName = API_TOKEN_CONFIG_KEY,
			name = "API Token",
			description = "Your unique API token for your RuneShare account. Go to https://osrs.runeshare.app/api_tokens to create one."
	)
	default String apiToken() { return null; }

	@ConfigItem(
			keyName = "autoSave",
			name = "Auto Save?",
			description = "If checked, changes to Bank Tag Tabs will automatically be saved in RuneShare."
	)
	default boolean autoSave() { return false; }

	@ConfigItem(
			keyName = "shareLocation",
			name = "Share Location?",
			description = "If checked, the world map location where a task session started will be shared with RuneShare."
	)
	default boolean shareLocation() { return true; }

	@ConfigItem(
			keyName = "shareLoadout",
			name = "Share Loadout?",
			description = "If checked, the inventory and equipment you are wearing when a task session starts will be shared with RuneShare."
	)
	default boolean shareLoadout() { return true; }
}
