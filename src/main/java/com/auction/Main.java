package com.auction;

import com.auction.client.Launcher;
import com.auction.server.ServerMain;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String mode = args[0].toLowerCase();
        String[] appArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (mode) {
            case "server":
                ServerMain.main(appArgs);
                break;
            case "client":
                Launcher.main(appArgs);
                break;
            default:
                System.out.println("Unknown mode: " + args[0]);
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar target/online-auction.jar server");
        System.out.println("  java -jar target/online-auction.jar client");
    }
}