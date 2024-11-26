package app.runeshare;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("RuneShare")
public interface RuneShareConfig extends Config
{
	@ConfigItem(
			keyName = "apiToken",
			name = "API Token",
			description = "API token for your RuneShare account."
	)
	String apiToken();

	@ConfigItem(
			keyName = "autoSave",
			name = "Auto Save?",
			description = "If checked, changes to Bank Tag Tabs will automatically be saved in RuneShare."
	)
	boolean autoSave();
}
