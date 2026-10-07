package app.runeshare.api;

import lombok.Value;

import javax.annotation.Nullable;

@Value
public class CurrentUser {
    String username;

    String game;

    ApiToken apiToken;

    /**
     * Added to the API after {@code username}, so treat it as optional.
     */
    @Nullable
    String profileUrl;

    @Value
    public static class ApiToken {
        String name;
    }
}
