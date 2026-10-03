package app.runeshare;

import net.runelite.api.Item;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns RuneLite item containers into the item id lists the RuneShare API expects.
 * <p>
 * RuneLite reports an empty slot as -1. It is sent as null rather than as a number
 * so that the API can tell "the player left this slot empty" apart from an item it
 * failed to recognise, and so that the items after it keep their position. Trailing
 * empties are dropped, because a tab that ends early looks the same either way.
 * <p>
 * Noted items are sent under their noted item id, which the API will not recognise.
 * Unnoting them would mean an ItemComposition lookup per slot, and a combat loadout
 * rarely carries notes, so they are left alone for now.
 */
final class Loadout
{
	private static final int MAX_BACKPACK_SLOTS = 28;

	private Loadout()
	{
	}

	@Nullable
	static List<Integer> backpackItemIds(@Nullable final Item[] items)
	{
		if (items == null)
		{
			return null;
		}

		final int readableSlots = Math.min(items.length, MAX_BACKPACK_SLOTS);

		int lastFilledSlot = -1;
		for (int slot = 0; slot < readableSlots; slot++)
		{
			if (isFilled(items[slot]))
			{
				lastFilledSlot = slot;
			}
		}

		final List<Integer> itemIds = new ArrayList<>(lastFilledSlot + 1);
		for (int slot = 0; slot <= lastFilledSlot; slot++)
		{
			final Item item = items[slot];
			itemIds.add(isFilled(item) ? item.getId() : null);
		}

		return Collections.unmodifiableList(itemIds);
	}

	@Nullable
	static List<Integer> equipmentItemIds(@Nullable final Item[] items)
	{
		if (items == null)
		{
			return null;
		}

		final List<Integer> itemIds = new ArrayList<>();
		for (final Item item : items)
		{
			if (isFilled(item))
			{
				itemIds.add(item.getId());
			}
		}

		return Collections.unmodifiableList(itemIds);
	}

	/**
	 * An empty slot is -1, but 0 is treated as empty too: the API drops a 0 before it
	 * assigns positions, which would shift every later item along by one.
	 */
	private static boolean isFilled(@Nullable final Item item)
	{
		return item != null && item.getId() > 0;
	}
}
