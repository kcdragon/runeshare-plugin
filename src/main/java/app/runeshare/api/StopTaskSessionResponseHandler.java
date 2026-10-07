package app.runeshare.api;

public interface StopTaskSessionResponseHandler {
    void onSuccess();

    /**
     * Called once every retry has failed.
     *
     * @param reason why, worded to finish a sentence shown to the player
     */
    void onFailure(String reason);
}
