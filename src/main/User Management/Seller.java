public class Seller extends User {

    public Seller(String username, String password) {
        super(username, password);
    }

    public void createAuction() {
        System.out.println("Đăng sản phẩm...");
    }

    @Override
    public void showMenu() {
        System.out.println("Menu Seller");
    }
}