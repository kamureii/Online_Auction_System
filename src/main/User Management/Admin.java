public class Admin extends User {

    public Admin(String username, String password) {
        super(username, password);
    }

    public void manageUsers() {
        System.out.println("Quản lý user...");
    }

    @Override
    public void showMenu() {
        System.out.println("Menu Admin");
    }
}