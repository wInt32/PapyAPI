package me.papyapi;

import java.net.InetAddress;
import java.util.Scanner;

public class PapyRequest {

    private final String requestBody;
    private final PapyRequestType requestType;
    private boolean invalid = false;
    public int number = 0;

    public PapyRequest(String requestBody) {
        this.requestBody = requestBody;
        this.requestType = parseType(requestBody);
    }

    public PapyRequest(String requestBody, PapyRequestType requestType) {
        this.requestBody = requestBody;
        this.requestType = requestType;
    }

    public static PapyRequestType parseType(String requestBody) {
        Scanner scanner = new Scanner(requestBody);
        switch (scanner.next()) {
            case "runcmd" -> {
                return PapyRequestType.RUN_COMMAND;
            }
            case "ping" -> {
                return PapyRequestType.PING;
            }
            case "disconnect" -> {
                return PapyRequestType.CLOSE_CONNECTION;
            }
            case "setblock" -> {
                return PapyRequestType.SET_BLOCK;
            }
            case "async_setblock" -> {
                return PapyRequestType.ASYNC_SET_BLOCK;
            }
            case "sync" -> {
                return PapyRequestType.SYNC;
            }
            default -> {
                return PapyRequestType.UNIMPLEMENTED;
            }
        }
    }

    public String getBody() {
        return requestBody;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    public PapyRequestType getType() {
        return requestType;
    }

}
