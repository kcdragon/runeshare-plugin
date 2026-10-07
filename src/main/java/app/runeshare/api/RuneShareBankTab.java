package app.runeshare.api;

import app.runeshare.PlayerAccount;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.TagTab;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RuneShareBankTab {
    private String tag;
    private String iconRunescapeItemId;
    private List<RuneShareBankTabItem> items;
    private String accountType;
    private boolean leagues;

    public static RuneShareBankTab from(final TagTab tagTab, final List<Integer> itemIds, final Layout layout, final PlayerAccount playerAccount) {
        RuneShareBankTab runeShareBankTab = new RuneShareBankTab();
        runeShareBankTab.setTag(tagTab.getTag());
        runeShareBankTab.setIconRunescapeItemId(Integer.toString(tagTab.getIconItemId()));
        runeShareBankTab.setAccountType(playerAccount.getAccountType());
        runeShareBankTab.setLeagues(playerAccount.isLeagues());

        List<RuneShareBankTabItem> runeShareBankTabItems = new ArrayList<>();
        runeShareBankTab.setItems(runeShareBankTabItems);

        final int[] runescapeItemIds;
        if (layout != null) {
            runescapeItemIds = layout.getLayout();
        } else {
            runescapeItemIds = itemIds.stream().mapToInt(i->i).toArray();
        }

        for (int position = 0; position < runescapeItemIds.length; position++) {
            int runescapeItemId = runescapeItemIds[position];
            if (runescapeItemId >= 0) {
                RuneShareBankTabItem runeShareBankTabItem = new RuneShareBankTabItem();
                runeShareBankTabItem.setPosition(position);
                runeShareBankTabItem.setRunescapeItemId(Integer.toString(runescapeItemId));
                runeShareBankTabItems.add(runeShareBankTabItem);
            }
        }

        return runeShareBankTab;
    }
}
