package app.runeshare.api;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RuneShareBankTab {
    private String tag;
    private String iconRunescapeItemId;
    private List<RuneShareBankTabItem> items;
}
