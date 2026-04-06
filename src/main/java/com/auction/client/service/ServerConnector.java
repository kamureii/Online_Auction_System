package com.auction.client.service;
import com.auction.shared.model.User;
import com.auction.shared.network.Response;

public class ServerConnector {
    public Response login(String username, String password) {
        System.out.println("UI đang gọi hàm login với user: " + username);

        // --- DỮ LIỆU GIẢ (MOCK) ĐỂ UI TEST ---
        if (username.equals("admin") && password.equals("123")) {
            // Giả vờ đăng nhập thành công, tạo một User giả trả về
            User fakeUser = new User(1, "admin", "admin@gmail.com", "123", "Admin", "ADMIN");
            // Trong thực tế, payload sẽ là chuỗi JSON, ở đây ta cứ để chuỗi thông báo
            return new Response("SUCCESS", "Đăng nhập thành công!", "Dữ liệu JSON của user ở đây");
        } else {
            return new Response("ERROR", "Sai tài khoản hoặc mật khẩu!", null);
        }
    }
}
