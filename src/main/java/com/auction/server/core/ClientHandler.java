package com.auction.server.core;

import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.shared.dto.LoginDTO;
import com.auction.shared.dto.RegisterDTO;
import com.auction.shared.model.User;
import com.auction.shared.network.Request;
import com.auction.shared.network.Response;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    private Gson gson;
    private UserDAO userDAO;
    private ItemDAO itemDAO;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.userDAO = new UserDAO();
        this.gson = new Gson();
        this.itemDAO = new ItemDAO();
    }
    @Override
    public void run() {
        try {
            //take input and output streams from the socket
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            //out.println("Welcome to the Auction Server");

            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                System.out.println("[Client " + clientSocket.getPort() + "]: " + clientMessage);

                //make request and trans from json to gson
                Request request = gson.fromJson(clientMessage, Request.class);
                Response response = null;

                //take request from ClientMain
                if(request != null && request.getAction() != null) {
                    switch (request.getAction()) {
                        case "LOGIN":
                            response = handleLogin(request.getPayload());
                            break;
                        case "REGISTER":
                            response = handleRegister(request.getPayload());
                            break;
                        default:
                            response = new Response("ERROR", "Invalid Action!", null);
                            break;
                    }
                }

                if(response != null) {
                    String jsonResponse = gson.toJson(response);
                    out.println(jsonResponse);
                }
            }
        } catch (IOException | SQLException e) {
            System.err.println("Failed to connect to the client " + clientSocket.getPort());
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

    private Response handleLogin(String payload) {
        LoginDTO loginData = gson.fromJson(payload, LoginDTO.class);

        User loggedInUser = userDAO.loginUser(loginData.getLoginIdentifier(), loginData.getPassword());

        if(loggedInUser != null) {
            String userJson = gson.toJson(loggedInUser);
            return new Response("SUCCESS", "Successfully logged in", userJson);
        }
        else {
            return new Response("ERROR", "Invalid Credentials", null);
        }
    }

    private Response handleRegister(String payload) throws SQLException {
        RegisterDTO registerData = gson.fromJson(payload, RegisterDTO.class);

        User newUser = new User (
                registerData.getUsername(),
                registerData.getEmail(),
                registerData.getPassword(),
                registerData.getFullname(),
                "BIDDER"
        );

        boolean isSuccess = userDAO.registerUser(newUser);

        if(isSuccess) {
            return new Response("SUCCESS", "Successfully registered user!", newUser.getUsername());
        }
        else {
            return new Response("ERROR", "Username or email existed!", null);
        }
    }
}
