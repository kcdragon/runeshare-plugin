package app.runeshare.api;

import app.runeshare.RuneShareConfig;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;

@Slf4j
@Singleton
public class RuneShareApi {
    private static final String RUNESHARE_HOST = "https://osrs.runeshare.app";
    private static final String BANK_TABS_PATH = "/api/bank_tabs";

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

    public void startTaskSession(final NPC npc, final String accountType, final boolean isLeaguesWorld, final StartTaskSessionResponseHandler startTaskSessionResponseHandler) {
        final Gson runeshareGson = gson.newBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

        StartTaskSession startTaskSession = StartTaskSession
                .builder()
                .npcRunescapeId(npc.getId())
                .leagues(isLeaguesWorld)
                .accountType(accountType)
                .build();

        final Request request = new Request.Builder()
                .url(RUNESHARE_HOST + "/api/task_sessions")
                .header("Authorization", "Token token=" + getApiToken())
                .header("Accept", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), runeshareGson.toJson(startTaskSession)))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to start task session in RuneShare.", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                String responseBody = null;
                try {
                    responseBody = response.body().string();
                } catch (IOException e) {
                    log.warn("Failed to parse 'start task session' response. Response Status code is {}. Response Body is {}", response.code(), responseBody);
                }

                if (response.code() == HTTP_CREATED && responseBody != null) {
                    log.debug("Successfully started task session in RuneShare. Response Body is {}.", responseBody);

                    StartTaskSessionResponse startTaskSessionResponse = runeshareGson.fromJson(responseBody, StartTaskSessionResponse.class);
                    startTaskSessionResponseHandler.onSuccess(startTaskSessionResponse);
                } else {
                    log.warn("Failed to start task session in RuneShare. Response Status code is {}. Response Body is {}", response.code(), responseBody);
                }
                response.close();
            }
        });
    }

    public void stopTaskSession(final StopTaskSession stopTaskSession, final StopTaskSessionResponseHandler stopTaskSessionResponseHandler) {
        final Request request = new Request.Builder()
                .url(RUNESHARE_HOST + "/api/task_sessions/" + stopTaskSession.getTaskSessionId())
                .header("Authorization", "Token token=" + getApiToken())
                .header("Accept", "application/json")
                .method("PUT", RequestBody.create(null, ""))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to stop task session in RuneShare.", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.code() == HTTP_OK) {
                    log.debug("Successfully stopped task session in RuneShare.");

                    stopTaskSessionResponseHandler.onSuccess();
                } else {
                    log.warn("Failed to stop task session in RuneShare. Response Status code is {}. Response Body is {}", response.code(), response.body());
                }
                response.close();
            }
        });
    }

    public void createTaskEvent(final RuneShareTaskEvent runeShareTaskEvent) {
        final Gson runeshareGson = gson.newBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

        final Request request = new Request.Builder()
                .url(RUNESHARE_HOST + "/api/task_sessions/" + runeShareTaskEvent.getTaskSessionId() + "/task_events")
                .header("Authorization", "Token token=" + getApiToken())
                .header("Accept", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), runeshareGson.toJson(runeShareTaskEvent)))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to create task event in RuneShare.", e);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                if (response.code() == HTTP_CREATED) {
                    log.debug("Successfully created task event in RuneShare.");
                } else {
                    log.warn("Failed to create task event in RuneShare. Response Status code is {}. Response Body is {}", response.code(), response.body());
                }
                response.close();
            }
        });
    }

    private void createRuneShareBankTab(final RuneShareBankTab runeShareBankTab) {
        final Gson runeshareGson = gson.newBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

        final Request request = new Request.Builder()
                .url(RUNESHARE_HOST + BANK_TABS_PATH)
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
                    log.debug("Successfully updated bank tab in RuneShare.");
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
