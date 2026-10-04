package app.runeshare;

import app.runeshare.api.RuneShareBankTab;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.api.WorldType;
import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlayerAccountTest
{
	@Test
	public void accountTypeMapsEveryKnownVarbitValue()
	{
		assertEquals("normal", PlayerAccount.accountType(0));
		assertEquals("ironman", PlayerAccount.accountType(1));
		assertEquals("ultimate_ironman", PlayerAccount.accountType(2));
		assertEquals("hardcore_ironman", PlayerAccount.accountType(3));
		assertEquals("group_ironman", PlayerAccount.accountType(4));
		assertEquals("hardcore_group_ironman", PlayerAccount.accountType(5));
		assertEquals("unranked_group_ironman", PlayerAccount.accountType(6));
	}

	@Test
	public void accountTypeIsNullForAnUnknownVarbitValue()
	{
		assertNull(PlayerAccount.accountType(7));
	}

	@Test
	public void seasonalWorldIsLeagues()
	{
		assertTrue(PlayerAccount.isLeagues(EnumSet.of(WorldType.SEASONAL)));
	}

	@Test
	public void seasonalDeadmanWorldIsNotLeagues()
	{
		assertFalse(PlayerAccount.isLeagues(EnumSet.of(WorldType.SEASONAL, WorldType.DEADMAN)));
	}

	@Test
	public void ordinaryWorldIsNotLeagues()
	{
		assertFalse(PlayerAccount.isLeagues(EnumSet.noneOf(WorldType.class)));
	}

	@Test
	public void unknownWorldIsNotLeagues()
	{
		assertFalse(PlayerAccount.isLeagues(null));
	}

	@Test
	public void bankTabSendsAccountTypeInSnakeCaseAndOmitsItWhenUnknown()
	{
		final Gson runeshareGson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
		final RuneShareBankTab bankTab = new RuneShareBankTab();

		bankTab.setAccountType("ironman");
		assertTrue(runeshareGson.toJson(bankTab).contains("\"account_type\":\"ironman\""));

		bankTab.setAccountType(null);
		assertFalse(runeshareGson.toJson(bankTab).contains("account_type"));
	}
}
