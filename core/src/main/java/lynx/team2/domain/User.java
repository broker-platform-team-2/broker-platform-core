package lynx.team2.domain;

public class User extends Entity<Long>{
    private String username;
    private String email;
    private String password;
    private Long platform_user_id;

    public User(String username, String email, String password, Long platform_user_id) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.platform_user_id = platform_user_id;
    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getPlatform_user_id() {
        return platform_user_id;
    }

    public void setPlatform_user_id(Long platform_user_id) {
        this.platform_user_id = platform_user_id;
    }
}
