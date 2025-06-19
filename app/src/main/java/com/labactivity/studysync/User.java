package com.labactivity.studysync;
public class User {
    private String uid;
    private String firstName;
    private String lastName;
    private String username;
    private String photoUrl;

    // Required empty constructor for Firestore
    public User() {}

    public User(String uid, String firstName, String lastName, String username, String photoUrl) {
        this.uid = uid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.photoUrl = photoUrl;
    }

    // Getters and Setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    // Convenience method
    public String getFullName() {
        return (firstName + " " + lastName).trim();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof User) {
            return uid != null && uid.equals(((User) obj).uid);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return uid != null ? uid.hashCode() : 0;
    }
}


