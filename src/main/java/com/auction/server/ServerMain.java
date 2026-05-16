package com.auction.server;

import com.auction.server.core.ClientHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.sql.SQLOutput;

public class ServerMain {
    private static final int PORT = 8080;

    public static void main(String[] args) {
        System.out.println("=====Starting Server======");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Connected to " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted from" + clientSocket.getInetAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        }
    }
}
