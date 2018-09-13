package com.hq.android.hserver.sdk.nano;

public enum Status {
    SWITCH_PROTOCOL(101, "Switching Protocols"),
    OK(200, "OK"),
    REDIRECT(301, "Moved Permanently"),
    BAD_REQUEST(400, "Bad Request"),
    INTERNAL_ERROR(500, "Internal Server Error");

    private final int requestStatus;
    private final String description;

    Status(int requestStatus, String description) {
        this.requestStatus = requestStatus;
        this.description = description;
    }

    public String getDescription() {
        return "" + this.requestStatus + " " + this.description;
    }

    public int getRequestStatus() {
        return this.requestStatus;
    }
}
