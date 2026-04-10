package com.auction.client.service;

import com.auction.shared.network.Response;
import java.util.ArrayList;
import java.util.List;

// ĐÂY LÀ CLASS MÀ ĐỘI UI SẼ GỌI ĐỂ LẤY DỮ LIỆU (ĐANG DÙNG MOCK DATA)
public class ServerConnector {
    public Response login(String username, String password) {
        System.out.println("[Mock API] Đang gọi login với user: " + username);

        if (username.equals("admin") && password.equals("123")) {
            return new Response("SUCCESS", "Đăng nhập thành công!", "{\"id\":1, \"username\":\"admin\", \"role\":\"BIDDER\"}");
        } else {
            return new Response("ERROR", "Sai tài khoản hoặc mật khẩu!", null);
        }
    }

    public Response register(String username, String email, String password, String fullName) {
        System.out.println("[Mock API] Đang gọi register cho user: " + username);

        if (username.equals("admin") || email.equals("admin@gmail.com")) {
            return new Response("ERROR", "Tên đăng nhập hoặc Email đã tồn tại!", null);
        }
        return new Response("SUCCESS", "Đăng ký thành công! Vui lòng đăng nhập lại.", null);
    }

    // (Lưu ý: Tạm thời dùng List<String> cho dễ hiểu.
    // Khi Thành viên 2 tạo xong class Product, hãy đổi List<String> thành List<Product>)
    public List<String> getProducts() {
        System.out.println("[Mock API] Đang lấy danh sách sản phẩm...");

        List<String> mockProducts = new ArrayList<>();
        mockProducts.add("1 | Laptop Dell XPS 15 | Giá hiện tại: 1500$ | Thời gian còn: 2h");
        mockProducts.add("2 | iPhone 15 Pro Max  | Giá hiện tại: 999$  | Thời gian còn: 5h");
        mockProducts.add("3 | Đồng hồ Rolex      | Giá hiện tại: 5000$ | Thời gian còn: 1h");

        return mockProducts;
    }

    public Response addProduct(String name, double startingPrice, String description) {
        System.out.println("[Mock API] Đang thêm sản phẩm: " + name);

        if (startingPrice <= 0) {
            return new Response("ERROR", "Giá khởi điểm phải lớn hơn 0!", null);
        }
        return new Response("SUCCESS", "Đăng bán sản phẩm thành công!", null);
    }

    public Response placeBid(int productId, double bidAmount) {
        System.out.println("[Mock API] Đang trả giá " + bidAmount + " cho SP có ID: " + productId);

        // Giả sử giá hiện tại của sản phẩm đang là 1500
        double currentHighestPrice = 1500.0;

        if (bidAmount <= currentHighestPrice) {
            return new Response("ERROR", "Giá trả phải cao hơn giá hiện tại (" + currentHighestPrice + "$)", null);
        }

        return new Response("SUCCESS", "Trả giá thành công! Bạn đang dẫn đầu.", null);
    }
}