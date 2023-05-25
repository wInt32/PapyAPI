package me.papyapi;

public class PapyResponse {
    public final boolean success;
    public final String message;
    public final boolean close_connection;
    public PapyResponse(boolean success, String message, boolean close_connection) {
        this.success = success;
        this.message = message;
        this.close_connection = close_connection;
    }
    public PapyResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.close_connection = false;
    }

    public PapyResponse(boolean success) {
        this.success = success;
        this.message = "";
        this.close_connection = false;

    }

}
