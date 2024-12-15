package app.runeshare.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.TagTab;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static java.net.HttpURLConnection.HTTP_CREATED;

@Slf4j
public class RuneShareApi {
    private static final String URL = "https://osrs.runeshare.app/api/bank_tabs";

    private final String apiToken;

    public RuneShareApi(@NonNull final String apiToken) {
        this.apiToken = apiToken;
    }

    public void createRuneShareBankTab(final TagTab tagTab, final List<Integer> itemIds, final Layout layout) {
        RuneShareBankTab runeShareBankTab = new RuneShareBankTab();
        runeShareBankTab.setTag(tagTab.getTag());
        runeShareBankTab.setIconRunescapeItemId(Integer.toString(tagTab.getIconItemId()));

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

        createRuneShareBankTab(runeShareBankTab);
    }

    private void createRuneShareBankTab(final RuneShareBankTab runeShareBankTab) {
        final Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("Authorization", "Token token=" + this.apiToken)
                .header("Content-type", "application/json")
                .header("Accepts", "application/json")
                .method("POST", HttpRequest.BodyPublishers.ofString(gson.toJson(runeShareBankTab)))
                .build();

        HttpResponse<String> response = null;

        try {
            final HttpClient client = HttpClient.newHttpClient();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to update bank tab.", e);
        }

        if (response == null) {
            log.warn("Failed to update bank tab. No response");
        } else if (response.statusCode() != HTTP_CREATED) {
            log.warn("Failed to update bank tab. Response Status code is {}. Response Body is {}", response.statusCode(), response.body());
        } else {
            log.info("Successfully updated bank tab in RuneShare.");
        }
    }
}
