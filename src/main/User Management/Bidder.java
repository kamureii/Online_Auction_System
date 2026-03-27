public class Bidder extends User {

    public Bidder(String username, String password) {
        super(username, password);
    }

    public void placeBid() {
        System.out.println("Đang đặt giá...");
    }

    @Override
    public void showMenu() {
        System.out.println("Menu Bidder");
    }
}