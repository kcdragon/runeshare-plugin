package app.runeshare.api;

import app.runeshare.PlayerAccount;
import app.runeshare.RuneShareConfig;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
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
    private static final String ME_PATH = "/api/me";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;

    private final Gson runeshareGson;

    private final RuneShareConfig runeShareConfig;

    private final RuneShareConnection runeShareConnection;

    @Inject
    public RuneShareApi(OkHttpClient okHttpClient, Gson gson, RuneShareConfig runeShareConfig, RuneShareConnection runeShareConnection) {
        this.okHttpClient = okHttpClient;
        this.runeshareGson = gson.newBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        this.runeShareConfig = runeShareConfig;
        this.runeShareConnection = runeShareConnection;
    }

    public void fetchCurrentUser() {
        final String apiToken = runeShareConfig.apiToken();
        final Request request = newRequest(ME_PATH, apiToken)
                .get()
                .build();

        send(request, apiToken, "fetch the current user", response -> {
            if (response.isSuccessful()) {
                runeShareConnection.reportCurrentUser(apiToken, response, parseCurrentUser(response.getBody()));
            }
        });
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
        final String apiToken = runeShareConfig.apiToken();
        final Request request = newRequest("/api/task_sessions", apiToken)
                .post(jsonBody(startTaskSession))
                .build();

        send(request, apiToken, "start task session", response -> {
            if (response.isSuccessful() && response.getBody() != null) {
                startTaskSessionResponseHandler.onSuccess(runeshareGson.fromJson(response.getBody(), StartTaskSessionResponse.class));
            }
        });
    }

    public void stopTaskSession(final StopTaskSession stopTaskSession, final StopTaskSessionResponseHandler stopTaskSessionResponseHandler) {
        final String apiToken = runeShareConfig.apiToken();
        final Request request = newRequest("/api/task_sessions/" + stopTaskSession.getTaskSessionId(), apiToken)
                .put(RequestBody.create(null, ""))
                .build();

        send(request, apiToken, "stop task session", response -> {
            if (response.isSuccessful()) {
                stopTaskSessionResponseHandler.onSuccess();
            }
        });
    }

    public void createTaskEvent(final RuneShareTaskEvent runeShareTaskEvent) {
        final String apiToken = runeShareConfig.apiToken();
        final Request request = newRequest("/api/task_sessions/" + runeShareTaskEvent.getTaskSessionId() + "/task_events", apiToken)
                .post(jsonBody(runeShareTaskEvent))
                .build();

        send(request, apiToken, "create task event", response -> {});
    }

    private void createRuneShareBankTab(final RuneShareBankTab runeShareBankTab) {
        final String apiToken = runeShareConfig.apiToken();
        final Request request = newRequest(BANK_TABS_PATH, apiToken)
                .post(jsonBody(runeShareBankTab))
                .build();

        send(request, apiToken, "update bank tab", response -> {});
    }

    private Request.Builder newRequest(final String path, final String apiToken) {
        return new Request.Builder()
                .url(RUNESHARE_HOST + path)
                .header("Authorization", "Token token=" + apiToken)
                .header("Accept", "application/json");
    }

    private RequestBody jsonBody(final Object payload) {
        return RequestBody.create(JSON, runeshareGson.toJson(payload));
    }

    /**
     * @param apiToken the token the request was built with, so that a response
     *                 arriving after the player changes it can be recognised as stale
     */
    private void send(final Request request, final String apiToken, final String action, final ApiResponseHandler apiResponseHandler) {
        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to {} in RuneShare.", action, e);
                handle(request, apiToken, ApiResponse.noResponse(), apiResponseHandler);
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
                handle(request, apiToken, apiResponse, apiResponseHandler);
            }
        });
    }

    private void handle(final Request request, final String apiToken, final ApiResponse apiResponse, final ApiResponseHandler apiResponseHandler) {
        runeShareConnection.report(apiToken, apiResponse);

        // Any accepted request proves the token works, but only /me says whose
        // it is, so fetch it if the startup /me call didn't get through.
        final boolean connectedWithoutKnowingWho = apiResponse.isSuccessful() && runeShareConnection.getCurrentUser() == null;
        final boolean isMeRequest = request.url().encodedPath().equals(ME_PATH);
        if (connectedWithoutKnowingWho && !isMeRequest) {
            fetchCurrentUser();
        }

        apiResponseHandler.onResponse(apiResponse);
    }

    @Nullable
    private CurrentUser parseCurrentUser(@Nullable final String body) {
        if (body == null) {
            return null;
        }

        try {
            return runeshareGson.fromJson(body, CurrentUser.class);
        } catch (JsonParseException e) {
            log.warn("Failed to parse the current user from RuneShare: {}", body, e);
            return null;
        }
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
