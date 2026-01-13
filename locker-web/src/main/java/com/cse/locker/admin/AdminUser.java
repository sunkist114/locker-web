package com.cse.locker.admin;

import jakarta.persistence.*;

@Entity
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;
    private String role;

    protected AdminUser() {}

    public AdminUser(String username, String password) {
        this.username = username;
        this.password = password;
        this.role = "ROLE_ADMIN";
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
