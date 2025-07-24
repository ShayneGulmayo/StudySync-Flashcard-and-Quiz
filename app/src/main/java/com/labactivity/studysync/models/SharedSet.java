package com.labactivity.studysync.models;

public class SharedSet {
    private String id;
    private String title;
    private String setType;
    private int itemCount;
    private String ownerName;
    private String senderName;
    private String roomId;

    public SharedSet() {} // Required for Firestore deserialization

    public SharedSet(String id, String title, String setType, int itemCount, String ownerName, String senderName, String roomId) {
        this.id = id;
        this.title = title;
        this.setType = setType;
        this.itemCount = itemCount;
        this.ownerName = ownerName;
        this.senderName = senderName;
        this.roomId = roomId;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSetType() { return setType; }
    public int getItemCount() { return itemCount; }
    public String getOwnerName() { return ownerName; }
    public String getSenderName() { return senderName; }
    public String getRoomId() { return roomId; }
}
