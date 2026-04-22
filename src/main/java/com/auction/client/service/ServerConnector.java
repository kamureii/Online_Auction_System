package com.auction.client.service;

import com.auction.shared.dto.LoginDTO;
import com.auction.shared.dto.RegisterDTO;
import com.auction.shared.network.Request;
import com.auction.shared.network.Response;
import com.auction.shared.model.Item;
import com.auction.shared.model.Bid;
import com.auction.shared.model.User;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

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

    public static User currentUser;

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
        Response res = sendRequest(req);
        if (res != null && "SUCCESS".equals(res.getStatus())) {
            currentUser = gson.fromJson(res.getPayload(), User.class);
        }
        return res;
    }

    public Response register(String username, String email, String password, String fullName) {
        RegisterDTO regData = new RegisterDTO(username, email, password, fullName);
        Request req = new Request("REGISTER", gson.toJson(regData));
        return sendRequest(req); // Trả về Response để controller xử lý thông báo
    }

    public List<Item> getProducts() {
        Request req = new Request("GET_ITEMS", "");
        Response res = sendRequest(req);
        if (res != null && "SUCCESS".equals(res.getStatus())) {
            Type listType = new TypeToken<List<Item>>(){}.getType();
            return gson.fromJson(res.getPayload(), listType);
        }
        return new ArrayList<>();
    }

    public Response addProduct(Item item) {
        Request req = new Request("ADD_ITEM", gson.toJson(item));
        return sendRequest(req);
    }

    public Response placeBid(Bid bid) {
        Request req = new Request("PLACE_BID", gson.toJson(bid));
        return sendRequest(req);
    }
}