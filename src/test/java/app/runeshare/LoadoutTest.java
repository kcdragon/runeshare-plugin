package app.runeshare;

import net.runelite.api.Item;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LoadoutTest
{
	private static final int EMPTY = -1;

	private static final int AIR_RUNE = 556;
	private static final int EARTH_RUNE = 557;
	private static final int ABYSSAL_WHIP = 4151;

	@Test
	public void backpackDropsTrailingEmptySlots()
	{
		assertEquals(
				Arrays.asList(AIR_RUNE, EARTH_RUNE),
				Loadout.backpackItemIds(items(AIR_RUNE, EARTH_RUNE, EMPTY, EMPTY)));
	}

	@Test
	public void backpackKeepsAnEmptySlotBetweenItems()
	{
		assertEquals(
				Arrays.asList(AIR_RUNE, null, EARTH_RUNE),
				Loadout.backpackItemIds(items(AIR_RUNE, EMPTY, EARTH_RUNE)));
	}

	@Test
	public void backpackTreatsZeroAsAnEmptySlot()
	{
		// A zero would be dropped by the API before it assigns positions, which would
		// shift the earth rune into the air rune's slot.
		assertEquals(
				Arrays.asList(AIR_RUNE, null, EARTH_RUNE),
				Loadout.backpackItemIds(items(AIR_RUNE, 0, EARTH_RUNE)));
	}

	@Test
	public void backpackIsEmptyWhenEverySlotIs()
	{
		assertEquals(Collections.emptyList(), Loadout.backpackItemIds(items(EMPTY, EMPTY, EMPTY)));
	}

	@Test
	public void backpackStopsAtTwentyEightSlots()
	{
		final Item[] items = new Item[30];
		Arrays.fill(items, new Item(AIR_RUNE, 1));

		assertEquals(28, Loadout.backpackItemIds(items).size());
	}

	@Test
	public void backpackIsNullWithoutAContainer()
	{
		assertNull(Loadout.backpackItemIds(null));
	}

	@Test
	public void equipmentDropsEmptySlotsRatherThanKeepingTheirPlace()
	{
		// The API works out each item's slot for itself, so the gaps carry nothing.
		assertEquals(
				Arrays.asList(ABYSSAL_WHIP, AIR_RUNE),
				Loadout.equipmentItemIds(items(EMPTY, ABYSSAL_WHIP, EMPTY, AIR_RUNE)));
	}

	@Test
	public void equipmentIsEmptyWhenNothingIsWorn()
	{
		assertEquals(Collections.emptyList(), Loadout.equipmentItemIds(items(EMPTY, EMPTY)));
	}

	@Test
	public void equipmentIsNullWithoutAContainer()
	{
		assertNull(Loadout.equipmentItemIds(null));
	}

	private static Item[] items(final int... itemIds)
	{
		final Item[] items = new Item[itemIds.length];
		for (int slot = 0; slot < itemIds.length; slot++)
		{
			items[slot] = new Item(itemIds[slot], 1);
		}
		return items;
	}
}
