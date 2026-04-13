package com.auction.client.service;

import com.auction.shared.dto.LoginDTO;
import com.auction.shared.dto.RegisterDTO;
import com.auction.shared.network.Request;
import com.auction.shared.network.Response;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ServerConnector {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8080;
    private Gson gson = new Gson();

    private Response sendRequest(Request request) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Gửi chuỗi JSON lên Server
            out.println(gson.toJson(request));

            // Nhận kết quả từ Server
            String responseJson = in.readLine();
            return gson.fromJson(responseJson, Response.class);

        } catch (Exception e) {
            e.printStackTrace();
            return new Response("ERROR", "Không thể kết nối tới Server. Hãy kiểm tra xem Server đã bật chưa!", null);
        }
    }

    public Response login(String username, String password) {
        LoginDTO loginData = new LoginDTO(username, password);
        Request req = new Request("LOGIN", gson.toJson(loginData));
        return sendRequest(req); // Đẩy qua động cơ mạng
    }

    public Response register(String username, String email, String password, String fullName) {
        RegisterDTO regData = new RegisterDTO(username, email, password, fullName);
        Request req = new Request("REGISTER", gson.toJson(regData));
        return sendRequest(req); // Đẩy qua động cơ mạng
    }

    // MOCK DATA

    public List<String> getProducts() {
        List<String> mockProducts = new ArrayList<>();
        mockProducts.add("1 | Laptop Dell XPS 15 | Giá: 1500$ | Còn: 2h");
        mockProducts.add("2 | iPhone 15 Pro Max  | Giá: 999$  | Còn: 5h");
        return mockProducts;
    }

    public Response placeBid(int productId, double bidAmount) {
        return new Response("SUCCESS", "Trả giá thành công (Mock)!", null);
    }
}