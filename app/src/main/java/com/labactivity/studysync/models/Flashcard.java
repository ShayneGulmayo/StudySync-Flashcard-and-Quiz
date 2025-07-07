package com.labactivity.studysync.models;

import com.google.firebase.firestore.PropertyName;

public class Flashcard {
    private String term;
    private String definition;
    private String photoUrl;
    private String photoPath;
    private String id;
    private String title;
    private int number_Of_Items;
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
        this.number_Of_Items = numberOfItems;
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

    @PropertyName("number_of_items")
    public int getNumber_Of_Items() {
        return number_Of_Items;
    }

    @PropertyName("number_of_items")
    public void set_Number_Of_Items(int number_Of_Items) {
        this.number_Of_Items = number_Of_Items;
    }


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

