package com.auction.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.rmi.ServerError;
import java.util.Scanner;

public class ClientMain
{
    private static final String SERVER_IP = "127.0.0.1";
    private static final int PORT = 8080;

    public static void main() throws IOException {
        System.out.println("Server started on port " + PORT);

        try (Socket socket = new Socket(SERVER_IP, PORT)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner scanner = new Scanner(System.in);

            System.out.println("Server: " + in.readLine());

            while (true) {
                System.out.println("Typing ('quit' for exit): ");
                String message = scanner.nextLine();

                out.println(message);

                if (message.equalsIgnoreCase("quit")) {
                    break;
                }

                String response = in.readLine();
                System.out.println("Server response: " + response);
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
