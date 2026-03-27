import java.util.*;

public class UserManager {
    private List<User> users = new ArrayList<>();

    public void register(User user) {
        users.add(user);
    }

    public User login(String username, String password) {
        for (User u : users) {
            if (u.getUsername().equals(username) && u.password.equals(password)) {
                return u;
            }
        }
        return null;
    }
}