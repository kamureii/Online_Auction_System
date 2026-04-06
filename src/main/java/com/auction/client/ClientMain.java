package com.auction.client;

import com.auction.shared.dto.LoginDTO;
import com.auction.shared.dto.RegisterDTO;
import com.auction.shared.network.Request;
import com.auction.shared.network.Response;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.rmi.ServerError;
import java.sql.SQLOutput;
import java.util.Scanner;

public class ClientMain
{
    public static void main() throws IOException {

        final String SERVER_IP = "localhost";
        final int PORT = 8080;

        Gson gson = new Gson();

        try (Socket socket = new Socket(SERVER_IP, PORT)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner scanner = new Scanner(System.in);

            System.out.println("Welcome to ABC AUCTION SERVER");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("Choose your choice (1 or 2): ");
            String choice = scanner.nextLine();

            String requestJson = "";

            if (choice.equals("1")) {
                System.out.println("Please enter your Username or Email: ");
                String username = scanner.next();
                System.out.println("Please enter your Password: ");
                String password = scanner.next();

                LoginDTO loginData = new LoginDTO(username, password);
                String payloadJson = gson.toJson(loginData);
            }
            else {
                System.out.println("Please enter your Username: ");
                String username = scanner.nextLine();
                System.out.println("Please enter your Email: ");
                String email = scanner.nextLine();
                System.out.println("please enter your Password: ");
                String password = scanner.nextLine();
                System.out.println("Please enter your Full Name: ");
                String fullName = scanner.nextLine();

                RegisterDTO registerData = new RegisterDTO(username, email, password, fullName);
                requestJson = gson.toJson(new Request("REGISTER", gson.toJson(registerData)));
            }

            out.println(requestJson);

            String responseJson = in.readLine();
            Response response = gson.fromJson(responseJson, Response.class);

            System.out.println("===========================================================");

            if ("SUCCESS".equals(response.getStatus())) {
                System.out.println("Congratulations: " + response.getMessage());
                System.out.println("User data: " + response.getPayload());
            }
            else {
                System.out.println("Server error: " + response.getMessage());
            }
            System.out.println("===========================================================");

        } catch (IOException e) {
            System.err.println("Can't connect to server: " + e.getMessage());
        }
    }
}
