package com.labactivity.studysync;

public class FlashcardSet {
    private String id;
    private String title;
    private int numberOfItems;
    private String ownerUsername;
    private int progress;
    private String photoUrl; // ✅ New field for profile photo URL
    private String type;


    public FlashcardSet() {
        // Empty constructor for Firestore
    }

    public FlashcardSet(String id, String title, int numberOfItems, String ownerUsername, int progress, String photoUrl) {
        this.id = id;
        this.title = title;
        this.numberOfItems = numberOfItems;
        this.ownerUsername = ownerUsername;
        this.progress = progress;
        this.photoUrl = photoUrl;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getNumberOfItems() {
        return numberOfItems;
    }

    public void setNumberOfItems(int numberOfItems) {
        this.numberOfItems = numberOfItems;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
