package app.runeshare.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static java.net.HttpURLConnection.HTTP_CREATED;

@Slf4j
public class RuneShareApi {
    private static final String URL = "http://osrs.runeshare.test/api/bank_tabs";

    private final String apiToken;

    public RuneShareApi(final String apiToken) {
        this.apiToken = apiToken;
    }

    public void createRuneShareBankTab(final RuneShareBankTab runeShareBankTab) {
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
            log.warn("Failed to create bank tab.", e);
        }

        if (response == null) {
            log.warn("Failed to create bank tab. No response");
        } else if (response.statusCode() != HTTP_CREATED) {
            log.warn("Failed to create bank tab. Response Status code is {}. Response Body is {}", response.statusCode(), response.body());
        } else {
            log.info("Successfully created bank tab in RuneShare.");
        }
    }
}
