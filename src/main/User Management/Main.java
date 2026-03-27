import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        UserManager userManager = new UserManager();
        Scanner sc = new Scanner(System.in);

        for (int i = 0; i < 2; i++) {

            System.out.println("\n===== HỆ THỐNG ĐẤU GIÁ =====");
            System.out.println("1. Đăng ký");
            System.out.println("2. Đăng nhập");
            System.out.println("0. Thoát");

            int choice = sc.nextInt();
            sc.nextLine();

            switch (choice) {

                case 1:
                    System.out.print("Username: ");
                    String u = sc.nextLine();

                    System.out.print("Password: ");
                    String p = sc.nextLine();

                    System.out.println("Chọn vai trò:");
                    System.out.println("1. Bidder");
                    System.out.println("2. Seller");
                    System.out.println("3. Admin");

                    int role = sc.nextInt();
                    sc.nextLine();

                    User newUser = null;

                    if (role == 1) newUser = new Bidder(u, p);
                    else if (role == 2) newUser = new Seller(u, p);
                    else if (role == 3) newUser = new Admin(u, p);

                    userManager.register(newUser);
                    System.out.println("Đăng ký thành công!");
                    break;

                case 2:
                    System.out.print("Username: ");
                    String username = sc.nextLine();

                    System.out.print("Password: ");
                    String password = sc.nextLine();

                    User user = userManager.login(username, password);

                    if (user == null) {
                        System.out.println("Sai tài khoản!");
                    } else {
                        System.out.println("Đăng nhập thành công!");

                        user.showMenu();

                        if (user instanceof Bidder) {
                            ((Bidder) user).placeBid();
                        } else if (user instanceof Seller) {
                            ((Seller) user).createAuction();
                        } else if (user instanceof Admin) {
                            ((Admin) user).manageUsers();
                        }
                    }
                    break;

                case 0:
                    System.out.println("Thoát...");
                    return;

                default:
                    System.out.println("Sai lựa chọn!");
            }
        }
    }
}