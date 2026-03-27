import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        UserManager userManager = new UserManager();
        Scanner sc = new Scanner(System.in);

        System.out.println("===== HỆ THỐNG ĐẤU GIÁ =====");
        System.out.println("1. Đăng ký");
        System.out.println("2. Đăng nhập");

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


                System.out.println("\n=== ĐĂNG NHẬP ===");

                System.out.print("Username: ");
                String username2 = sc.nextLine();

                System.out.print("Password: ");
                String password2 = sc.nextLine();

                User user2 = userManager.login(username2, password2);

                if (user2 == null) {
                    System.out.println("Sai tài khoản!");
                } else {
                    System.out.println("Đăng nhập thành công!");
                    user2.showMenu();
                }

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
                }

                break;

            default:
                System.out.println("Sai lựa chọn!");
        }
    }
}