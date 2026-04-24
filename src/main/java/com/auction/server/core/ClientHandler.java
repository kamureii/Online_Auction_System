package com.auction.server.core;

import com.auction.server.dao.BidDAO;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.shared.dto.LoginDTO;
import com.auction.shared.dto.RegisterDTO;
import com.auction.shared.model.Bid;
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
                        case "GET_ITEMS":
                            response = handleGetItems();
                            break;
                        case "ADD_ITEM":
                            response = handleAddItem(request.getPayload());
                            break;
                        case "PLACE_BID":
                            response = handlePlaceBid(request.getPayload());
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

    private Response handleGetItems() {
        java.util.List<com.auction.shared.model.Item> products = ItemDAO.GetAllItems();
        String productsJson = gson.toJson(products);
        return new Response("SUCCESS", "Get products successfully!", productsJson);
    }

    private Response handleAddItem(String payload) {
        try {
            System.out.println("Received ADD_ITEM request with payload: " + payload);
            
            com.auction.shared.model.Item newProduct = gson.fromJson(payload, com.auction.shared.model.Item.class);
            
            if (newProduct == null) {
                return new Response("ERROR", "Invalid item data received!", null);
            }
            
            System.out.println("Parsed item: " + newProduct.getName() + ", Price: " + newProduct.getStartingPrice());
            
            boolean isSuccess = ItemDAO.AddItem(newProduct);

            if (isSuccess) {
                System.out.println("Successfully added item: " + newProduct.getName());
                return new Response("SUCCESS", "Added a product successfully!", null);
            } else {
                System.err.println("Failed to add item: " + newProduct.getName());
                return new Response("ERROR", "Database error: Failed to save item to database!", null);
            }
        } catch (Exception e) {
            System.err.println("Error in handleAddItem: " + e.getMessage());
            e.printStackTrace();
            return new Response("ERROR", "Server error: " + e.getMessage(), null);
        }
    }

    private Response handlePlaceBid(String payload) throws SQLException {
        Bid newBid = gson.fromJson(payload, Bid.class);

        String result = BidDAO.placeBid(newBid);

        if(result.equals("SUCCESS")) {
            return new Response("SUCCESS", "Bid placed successfully!", null);
        }
        else {
            return new Response("ERROR", result, null);
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

        // Server-side validation
        if (registerData.getUsername() == null || registerData.getUsername().trim().isEmpty()
                || registerData.getPassword() == null || registerData.getPassword().trim().isEmpty()
                || registerData.getEmail() == null || registerData.getEmail().trim().isEmpty()
                || registerData.getFullname() == null || registerData.getFullname().trim().isEmpty()) {
            return new Response("ERROR", "Vui lòng điền đầy đủ thông tin!", null);
        }

        // Kiểm tra username đã tồn tại
        if (userDAO.isUsernameExists(registerData.getUsername())) {
            return new Response("ERROR", "Tên đăng nhập đã tồn tại!", null);
        }

        // Kiểm tra email đã tồn tại
        if (userDAO.isEmailExists(registerData.getEmail())) {
            return new Response("ERROR", "Email đã được sử dụng!", null);
        }

        User newUser = new User (
                registerData.getUsername(),
                registerData.getEmail(),
                registerData.getPassword(),
                registerData.getFullname()
        );

        boolean isSuccess = userDAO.registerUser(newUser);

        if(isSuccess) {
            return new Response("SUCCESS", "Đăng ký tài khoản thành công!", newUser.getUsername());
        }
        else {
            return new Response("ERROR", "Đăng ký thất bại! Vui lòng thử lại.", null);
        }
    }
}
