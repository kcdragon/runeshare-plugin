package app.runeshare;

import lombok.Value;
import net.runelite.api.WorldType;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * The account type and Leagues flag RuneShare shows next to a player's bank tabs and
 * task sessions.
 * <p>
 * Both come from the client (the account type varbit and the world type), which may
 * only be read on the client thread. Build one there and hand it to the EDT, rather
 * than reading the client from the code that sends it.
 */
@Value
public class PlayerAccount
{
	@Nullable
	String accountType;

	boolean leagues;

	static PlayerAccount from(final int accountTypeVarbitValue, @Nullable final EnumSet<WorldType> worldTypes)
	{
		return new PlayerAccount(accountType(accountTypeVarbitValue), isLeagues(worldTypes));
	}

	/**
	 * Returns null for a value RuneShare doesn't know, so it is left out of the request
	 * and the account type already stored on RuneShare is kept.
	 */
	@Nullable
	static String accountType(final int accountTypeVarbitValue)
	{
		switch (accountTypeVarbitValue)
		{
			case 0:
				return "normal";
			case 1:
				return "ironman";
			case 2:
				return "ultimate_ironman";
			case 3:
				return "hardcore_ironman";
			case 4:
				return "group_ironman";
			case 5:
				return "hardcore_group_ironman";
			case 6:
				return "unranked_group_ironman";
			default:
				return null;
		}
	}

	/**
	 * Deadman seasonal worlds are flagged SEASONAL too, but they are not Leagues.
	 */
	static boolean isLeagues(@Nullable final EnumSet<WorldType> worldTypes)
	{
		if (worldTypes == null)
		{
			return false;
		}

		return worldTypes.contains(WorldType.SEASONAL) && !worldTypes.contains(WorldType.DEADMAN);
	}
}
