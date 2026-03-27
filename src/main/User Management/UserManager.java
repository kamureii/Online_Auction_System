import java.io.*;
import java.util.*;

public class UserManager {
    private List<User> users = new ArrayList<>();
    private final String FILE_NAME = "users.txt";

    public UserManager() {
        loadFromFile();
    }

    public void register(User user) {
        users.add(user);
        saveToFile();
    }

    // đăng nhập
    public User login(String username, String password) {
        for (User u : users) {
            if (u.getUsername().equals(username) && u.password.equals(password)) {
                return u;
            }
        }
        return null;
    }

    private void saveToFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_NAME))) {
            for (User u : users) {
                String role = "";

                if (u instanceof Bidder) role = "BIDDER";
                else if (u instanceof Seller) role = "SELLER";
                else if (u instanceof Admin) role = "ADMIN";

                bw.write(u.getUsername() + "," + u.password + "," + role);
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Lỗi lưu file!");
        }
    }

    private void loadFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(FILE_NAME))) {
            String line;

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                String username = parts[0];
                String password = parts[1];
                String role = parts[2];

                User user = null;

                if (role.equals("BIDDER")) user = new Bidder(username, password);
                else if (role.equals("SELLER")) user = new Seller(username, password);
                else if (role.equals("ADMIN")) user = new Admin(username, password);

                if (user != null) users.add(user);
            }
        } catch (IOException e) {

        }
    }
}