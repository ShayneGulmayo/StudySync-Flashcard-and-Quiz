package com.labactivity.studysync.models;

import com.google.firebase.firestore.PropertyName;

public class Flashcard {
    private String term;
    private String definition;
    private String photoUrl;
    private String photoPath;
    private String id;
    private String title;
    private int numberOfItems;
    private String ownerUsername;
    private int progress;
    private String type;
    private String privacy;
    private String reminder;
    private String ownerUid;
    private long lastAccessed;


    public Flashcard() {}

    public Flashcard(String term, String definition, String photoUrl, String photoPath) {
        this.term = term;
        this.definition = definition;
        this.photoUrl = photoUrl;
        this.photoPath = photoPath;
    }

    public Flashcard(String id, String title, int numberOfItems, String ownerUsername, int progress, String photoUrl) {
        this.id = id;
        this.title = title;
        this.numberOfItems = numberOfItems;
        this.ownerUsername = ownerUsername;
        this.progress = progress;
        this.photoUrl = photoUrl;
    }

    public String getTerm() { return term; }
    public String getDefinition() { return definition; }
    public String getPhotoPath() { return photoPath; }

    @PropertyName("owner_uid")
    public String getOwnerUid() { return ownerUid; }

    @PropertyName("owner_uid")
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getNumberOfItems() { return numberOfItems; }
    public void setNumberOfItems(int numberOfItems) { this.numberOfItems = numberOfItems; }

    @PropertyName("owner_username")
    public String getOwnerUsername() { return ownerUsername; }

    @PropertyName("owner_username")
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPrivacy() { return privacy; }
    public void setPrivacy(String privacy) { this.privacy = privacy; }

    public String getReminder() { return reminder; }
    public boolean isPublic() {
        return "public".equalsIgnoreCase(privacy);
    }

    public void setReminder(String reminder) { this.reminder = reminder; }
    public long getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(long lastAccessed) {
        this.lastAccessed = lastAccessed;
    }
}

