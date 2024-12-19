package app.runeshare.api;

import app.runeshare.RuneShareConfig;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.net.HttpURLConnection.HTTP_CREATED;

@Slf4j
@Singleton
public class RuneShareApi {
    private static final String URL = "https://osrs.runeshare.app/api/bank_tabs";

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    @Inject
    private RuneShareConfig runeShareConfig;

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
        final Gson runeshareGson = gson.newBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

        final Request request = new Request.Builder()
                .url(URL)
                .header("Authorization", "Token token=" + getApiToken())
                .header("Accept", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), runeshareGson.toJson(runeShareBankTab)))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to update bank tab.", e);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                if (response.code() == HTTP_CREATED) {
                    log.info("Successfully updated bank tab in RuneShare.");
                } else {
                    log.warn("Failed to update bank tab. Response Status code is {}. Response Body is {}", response.code(), response.body());
                }
                response.close();
            }
        });
    }

    private String getApiToken() {
        return runeShareConfig.apiToken();
    }
}
