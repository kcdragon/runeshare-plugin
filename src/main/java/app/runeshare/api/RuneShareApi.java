package app.runeshare.api;

import app.runeshare.PlayerAccount;
import app.runeshare.RuneShareConfig;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import okhttp3.*;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
public class RuneShareApi {
    private static final String RUNESHARE_HOST = "https://osrs.runeshare.app";
    private static final String BANK_TABS_PATH = "/api/bank_tabs";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;

    private final Gson runeshareGson;

    private final RuneShareConfig runeShareConfig;

    @Inject
    public RuneShareApi(OkHttpClient okHttpClient, Gson gson, RuneShareConfig runeShareConfig) {
        this.okHttpClient = okHttpClient;
        this.runeshareGson = gson.newBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        this.runeShareConfig = runeShareConfig;
    }

    public void createRuneShareBankTab(final TagTab tagTab, final List<Integer> itemIds, final Layout layout, final PlayerAccount playerAccount) {
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

        createRuneShareBankTab(runeShareBankTab);
    }

    public void startTaskSession(final StartTaskSession startTaskSession, final StartTaskSessionResponseHandler startTaskSessionResponseHandler) {
        final Request request = newRequest("/api/task_sessions")
                .post(jsonBody(startTaskSession))
                .build();

        send(request, "start task session", response -> {
            if (response.isSuccessful() && response.getBody() != null) {
                startTaskSessionResponseHandler.onSuccess(runeshareGson.fromJson(response.getBody(), StartTaskSessionResponse.class));
            }
        });
    }

    public void stopTaskSession(final StopTaskSession stopTaskSession, final StopTaskSessionResponseHandler stopTaskSessionResponseHandler) {
        final Request request = newRequest("/api/task_sessions/" + stopTaskSession.getTaskSessionId())
                .put(RequestBody.create(null, ""))
                .build();

        send(request, "stop task session", response -> {
            if (response.isSuccessful()) {
                stopTaskSessionResponseHandler.onSuccess();
            }
        });
    }

    public void createTaskEvent(final RuneShareTaskEvent runeShareTaskEvent) {
        final Request request = newRequest("/api/task_sessions/" + runeShareTaskEvent.getTaskSessionId() + "/task_events")
                .post(jsonBody(runeShareTaskEvent))
                .build();

        send(request, "create task event", response -> {});
    }

    private void createRuneShareBankTab(final RuneShareBankTab runeShareBankTab) {
        final Request request = newRequest(BANK_TABS_PATH)
                .post(jsonBody(runeShareBankTab))
                .build();

        send(request, "update bank tab", response -> {});
    }

    private Request.Builder newRequest(final String path) {
        return new Request.Builder()
                .url(RUNESHARE_HOST + path)
                .header("Authorization", "Token token=" + runeShareConfig.apiToken())
                .header("Accept", "application/json");
    }

    private RequestBody jsonBody(final Object payload) {
        return RequestBody.create(JSON, runeshareGson.toJson(payload));
    }

    private void send(final Request request, final String action, final ApiResponseHandler apiResponseHandler) {
        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to {} in RuneShare.", action, e);
                apiResponseHandler.onResponse(ApiResponse.noResponse());
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                final ApiResponse apiResponse;
                try (response) {
                    apiResponse = new ApiResponse(response.code(), readBody(response));
                }

                if (apiResponse.isSuccessful()) {
                    log.debug("Did {} in RuneShare.", action);
                } else {
                    log.warn("Failed to {} in RuneShare. Response status code is {}. Response body is {}", action, apiResponse.getStatusCode(), apiResponse.getBody());
                }
                apiResponseHandler.onResponse(apiResponse);
            }
        });
    }

    @Nullable
    private static String readBody(final Response response) {
        final ResponseBody body = response.body();
        if (body == null) {
            return null;
        }

        try {
            return body.string();
        } catch (IOException e) {
            log.warn("Failed to read RuneShare response body.", e);
            return null;
        }
    }

    private interface ApiResponseHandler {
        void onResponse(ApiResponse apiResponse);
    }
}
