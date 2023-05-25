package me.papyapi;


import org.jetbrains.annotations.NotNull;

import java.util.Scanner;

public class SetBlockRequest extends PapyRequest{

    public String world = "";

    public int x = 0;
    public int y = 0;
    public int z = 0;
    public String block = "";

    public SetBlockRequest(@NotNull PapyRequest request) {
        super(request.getBody(), request.getType());
        Scanner scanner = new Scanner(request.getBody());
        scanner.next();
        try {
            this.world = scanner.next();
            this.x = Integer.parseInt(scanner.next());
            this.y = Integer.parseInt(scanner.next());
            this.z = Integer.parseInt(scanner.next());
            this.block = scanner.next();
            scanner.nextLine();
        } catch (Exception e) {
            this.setInvalid(true);
        }

    }
}
