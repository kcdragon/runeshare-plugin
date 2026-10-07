package app.runeshare.api;

public interface StartTaskSessionResponseHandler {
    void onSuccess(StartTaskSessionResponse startTaskSessionResponse);

    /**
     * @param reason why, worded to finish a sentence shown to the player
     */
    void onFailure(String reason);
}
