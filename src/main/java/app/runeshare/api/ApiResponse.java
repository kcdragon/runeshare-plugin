package app.runeshare.api;

import lombok.AllArgsConstructor;
import lombok.Value;

import javax.annotation.Nullable;

@Value
@AllArgsConstructor
public class ApiResponse {
    public static final int NO_RESPONSE = -1;

    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /**
     * The HTTP status, or {@link #NO_RESPONSE} when the request never got one
     * (no network, DNS failure, timeout).
     */
    int statusCode;

    @Nullable
    String body;

    /**
     * The first entry of the {@code errors} array RuneShare sends with a 422,
     * such as "Unknown NPC".
     */
    @Nullable
    String serverError;

    public ApiResponse(int statusCode, @Nullable String body) {
        this(statusCode, body, null);
    }

    public static ApiResponse noResponse() {
        return new ApiResponse(NO_RESPONSE, null);
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isTooManyRequests() {
        return statusCode == HTTP_TOO_MANY_REQUESTS;
    }

    /**
     * Whether trying the same request again later could succeed, as opposed to
     * a response that would come back the same way every time.
     */
    public boolean isTransientFailure() {
        final boolean serverError = statusCode >= 500 && statusCode < 600;
        return statusCode == NO_RESPONSE || serverError || isTooManyRequests();
    }

    /**
     * Why the request failed, worded to finish a sentence shown to the player.
     */
    public String describeFailure() {
        if (statusCode == NO_RESPONSE) {
            return "RuneShare couldn't be reached";
        }
        if (serverError != null) {
            return serverError;
        }
        return "RuneShare responded with " + statusCode;
    }
}
