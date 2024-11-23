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
}
