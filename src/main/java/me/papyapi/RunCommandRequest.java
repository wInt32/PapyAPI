package me.papyapi;

import java.util.Scanner;

public class RunCommandRequest extends PapyRequest {

    public final String cmdline;

    public RunCommandRequest(PapyRequest request) {
        super(request.getBody(), request.getType());

        Scanner scanner = new Scanner(request.getBody());
        scanner.next();
        this.cmdline = scanner.nextLine();
    }
}
