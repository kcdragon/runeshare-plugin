package app.runeshare.api;

public enum ConnectionStatus {
    UNKNOWN,
    CONNECTED,
    INVALID_TOKEN,
    API_DISABLED,
    UNAVAILABLE;

    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;

    /**
     * The status implied by a response from any endpoint. Responses that say
     * nothing about the connection itself, such as a 404 or 422, leave it as is.
     */
    public ConnectionStatus after(final ApiResponse response) {
        if (response.isSuccessful()) {
            return CONNECTED;
        }

        switch (response.getStatusCode()) {
            case HTTP_UNAUTHORIZED:
                return INVALID_TOKEN;
            case HTTP_FORBIDDEN:
                return API_DISABLED;
            default:
                return response.isTransientFailure() ? UNAVAILABLE : this;
        }
    }
}
