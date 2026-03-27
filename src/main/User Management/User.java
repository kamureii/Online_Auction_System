import java.util.ArrayList;
import java.util.List;
public abstract class User {
    protected String username;
    protected String password;

    public User(String username, String password){
        this.username = username;
        this.password = password;
    }
    public String getUsername(){
        return username;
    }
    public abstract void showMenu();
}

