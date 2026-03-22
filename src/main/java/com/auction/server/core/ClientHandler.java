package com.auction.server.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }
    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            out.println("Welcome to the Auction Server");

            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                System.out.println("[Client " + clientSocket.getPort() + "]: " + clientMessage);

                out.println("Server recieved: " + clientMessage);

                if(clientMessage.equalsIgnoreCase("quit")){
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to connect to the client" + clientSocket.getPort());
        } finally {
            try {
                System.out.println("Closing connection to " + clientSocket.getPort());
                if(in != null) in.close();
                if(out != null) out.close();
                if(clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
