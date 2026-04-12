package com.auction.client.service;

import com.auction.shared.model.User;
import com.auction.shared.network.Response;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class ServerConnector {

    // Đường dẫn file lưu trữ (nằm ngay thư mục gốc của project)
    private static final String DATABASE_FILE = "users_db.txt";

    public Response login(String username, String password) {
        System.out.println("Đang kiểm tra đăng nhập cho: " + username);

        // Check tài khoản admin mặc định
        if (username.equals("admin") && password.equals("123")) {
            return new Response("SUCCESS", "Đăng nhập admin thành công!", null);
        }

        // Đọc file để kiểm tra tài khoản đã đăng ký
        try {
            File file = new File(DATABASE_FILE);
            if (!file.exists()) {
                return new Response("ERROR", "Tài khoản không tồn tại!", null);
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                // File bây giờ có định dạng: username:password:email:fullname
                String[] parts = line.split(":");
                if (parts.length >= 2) { // Đảm bảo có ít nhất user và pass
                    String storedUser = parts[0];
                    String storedPass = parts[1];

                    if (storedUser.equals(username) && storedPass.equals(password)) {
                        reader.close();
                        return new Response("SUCCESS", "Đăng nhập thành công!", null);
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            return new Response("ERROR", "Lỗi hệ thống khi đọc dữ liệu!", null);
        }

        return new Response("ERROR", "Sai tài khoản hoặc mật khẩu!", null);
    }

    public Response register(String username, String password, String email, String fullname) {
        System.out.println("Đang xử lý đăng ký cho: " + username);

        // Kiểm tra tất cả các trường không được để trống
        if (username == null || username.isEmpty() ||
                password == null || password.isEmpty() ||
                email == null || email.isEmpty() ||
                fullname == null || fullname.isEmpty()) {
            return new Response("ERROR", "Vui lòng nhập đầy đủ thông tin!", null);
        }

        // Kiểm tra trùng user đã có trong file
        if (username.equals("admin") || isUserExists(username)) {
            return new Response("ERROR", "Tài khoản đã tồn tại!", null);
        }

        // Ghi dữ liệu mới vào file theo định dạng username:password:email:fullname
        try (FileWriter fw = new FileWriter(DATABASE_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            out.println(username + ":" + password + ":" + email + ":" + fullname);
            return new Response("SUCCESS", "Đăng ký thành công!", null);

        } catch (IOException e) {
            e.printStackTrace();
            return new Response("ERROR", "Không thể lưu tài khoản vào hệ thống!", null);
        }
    }

    // Hàm phụ để check xem tên user đã có trong file chưa
    private boolean isUserExists(String username) {
        try {
            File file = new File(DATABASE_FILE);
            if (!file.exists()) return false;

            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                // Kiểm tra dựa trên phần đầu tiên (username) trước dấu ":"
                if (line.split(":")[0].equals(username)) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}