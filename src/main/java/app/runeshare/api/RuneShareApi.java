package app.runeshare.api;

import app.runeshare.RuneShareConfig;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    /**
     * Shared by every plugin, so retries are cancelled individually and the
     * executor itself is never shut down.
     */
    private final ScheduledExecutorService scheduledExecutorService;

    private final Set<Future<?>> scheduledRetries = ConcurrentHashMap.newKeySet();

    @Inject
    public RuneShareApi(OkHttpClient okHttpClient, Gson gson, RuneShareConfig runeShareConfig, RuneShareConnection runeShareConnection, ScheduledExecutorService scheduledExecutorService) {
        this.okHttpClient = okHttpClient;
        this.runeshareGson = gson.newBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        this.runeShareConfig = runeShareConfig;
        this.runeShareConnection = runeShareConnection;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public void cancelRetries() {
        scheduledRetries.forEach(retry -> retry.cancel(false));
        scheduledRetries.clear();
    }

    public void fetchCurrentUser() {
        final String apiToken = runeShareConfig.apiToken();
        final Request request = newRequest(ME_PATH, apiToken)
                .get()
                .build();

        send(request, apiToken, "fetch the current user", response -> {
            if (response.isSuccessful()) {
                runeShareConnection.reportCurrentUser(apiToken, response, parse(response.getBody(), CurrentUser.class));
            }
        });
    }

    public void startTaskSession(final StartTaskSession startTaskSession, final StartTaskSessionResponseHandler startTaskSessionResponseHandler) {
        final String apiToken = runeShareConfig.apiToken();
        final Request request = newRequest("/api/task_sessions", apiToken)
                .post(jsonBody(startTaskSession))
                .build();

        send(request, apiToken, "start task session", response -> {
            final StartTaskSessionResponse startTaskSessionResponse = response.isSuccessful() ? parse(response.getBody(), StartTaskSessionResponse.class) : null;
            if (startTaskSessionResponse != null) {
                startTaskSessionResponseHandler.onSuccess(startTaskSessionResponse);
            } else if (response.isSuccessful()) {
                startTaskSessionResponseHandler.onFailure("RuneShare sent a response the plugin didn't understand");
            } else {
                startTaskSessionResponseHandler.onFailure(response.describeFailure());
            }
        });
    }

    public void stopTaskSession(final StopTaskSession stopTaskSession, final StopTaskSessionResponseHandler stopTaskSessionResponseHandler) {
        final String apiToken = runeShareConfig.apiToken();
        final Request request = newRequest("/api/task_sessions/" + stopTaskSession.getTaskSessionId(), apiToken)
                .put(RequestBody.create(null, ""))
                .build();

        sendWithRetries(request, apiToken, "stop task session", RetryPolicy.STOP_TASK_SESSION, 1, response -> {
            if (response.isSuccessful()) {
                stopTaskSessionResponseHandler.onSuccess();
            } else {
                stopTaskSessionResponseHandler.onFailure(response.describeFailure());
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

    /**
     * Makes a single attempt. {@link BankTabSync} owns retrying, because a newer
     * tab change has to be able to replace a pending retry.
     */
    public void sendBankTab(final RuneShareBankTab runeShareBankTab, final Consumer<ApiResponse> onResponse) {
        final String apiToken = runeShareConfig.apiToken();
        final Request request = newRequest(BANK_TABS_PATH, apiToken)
                .post(jsonBody(runeShareBankTab))
                .build();

        send(request, apiToken, "update bank tab", onResponse::accept);
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

    private void sendWithRetries(final Request request, final String apiToken, final String action, final RetryPolicy retryPolicy, final int attemptNumber, final ApiResponseHandler apiResponseHandler) {
        send(request, apiToken, action, response -> {
            final Long retryDelayMs = response.isSuccessful() ? null : retryPolicy.delayBeforeRetryMs(response, attemptNumber);
            if (retryDelayMs == null) {
                apiResponseHandler.onResponse(response);
                return;
            }

            log.debug("Retrying {} in {}ms.", action, retryDelayMs);
            scheduledRetries.removeIf(Future::isDone);
            scheduledRetries.add(scheduledExecutorService.schedule(
                    () -> sendWithRetries(request, apiToken, action, retryPolicy, attemptNumber + 1, apiResponseHandler),
                    retryDelayMs,
                    TimeUnit.MILLISECONDS));
        });
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
                    final String body = readBody(response);
                    final String serverError = response.isSuccessful() ? null : firstServerError(body);
                    apiResponse = new ApiResponse(response.code(), body, serverError);
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
    private <T> T parse(@Nullable final String body, final Class<T> responseClass) {
        if (body == null) {
            return null;
        }

        try {
            return runeshareGson.fromJson(body, responseClass);
        } catch (JsonParseException e) {
            log.warn("Failed to parse {} from RuneShare: {}", responseClass.getSimpleName(), body, e);
            return null;
        }
    }

    @Nullable
    private String firstServerError(@Nullable final String body) {
        final ErrorsResponse errorsResponse = parse(body, ErrorsResponse.class);
        if (errorsResponse == null || errorsResponse.errors == null || errorsResponse.errors.isEmpty()) {
            return null;
        }
        return errorsResponse.errors.get(0);
    }

    private static class ErrorsResponse {
        List<String> errors;
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
