package app.runeshare.api;

import lombok.Value;

import javax.annotation.Nullable;

@Value
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

    public static ApiResponse noResponse() {
        return new ApiResponse(NO_RESPONSE, null);
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Whether trying the same request again later could succeed, as opposed to
     * a response that would come back the same way every time.
     */
    public boolean isTransientFailure() {
        final boolean serverError = statusCode >= 500 && statusCode < 600;
        return statusCode == NO_RESPONSE || serverError || statusCode == HTTP_TOO_MANY_REQUESTS;
    }
}
