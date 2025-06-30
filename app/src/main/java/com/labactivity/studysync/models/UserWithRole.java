package com.labactivity.studysync.models;

public class UserWithRole {
    private User user;
    private String role;

    public UserWithRole(User user, String role) {
        this.user = user;
        this.role = role;
    }

    public User getUser() {
        return user;
    }

    public String getRole() {
        return role;
    }
}

