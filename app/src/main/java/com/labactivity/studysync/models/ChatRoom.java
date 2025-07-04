package com.labactivity.studysync.models;

import java.util.Date;
import java.util.List;

public class ChatRoom {

    private String id;
    private String chatRoomName;
    private String photoUrl;
    private String photoPath;
    private List<String> members;
    private List<String> admins;
    private String ownerId;

    private String lastMessage;
    private String lastMessageSender;
    private String lastMessageSenderId;
    private Date lastMessageTimestamp;

    private boolean unread;

    // Required no-arg constructor for Firestore
    public ChatRoom() {}

    public ChatRoom(String id, String chatRoomName, String photoUrl, String photoPath,
                    List<String> members, List<String> admins, String ownerId,
                    String lastMessage, String lastMessageSender, String lastMessageSenderId,
                    Date lastMessageTimestamp, boolean unread) {
        this.id = id;
        this.chatRoomName = chatRoomName;
        this.photoUrl = photoUrl;
        this.photoPath = photoPath;
        this.members = members;
        this.admins = admins;
        this.ownerId = ownerId;
        this.lastMessage = lastMessage;
        this.lastMessageSender = lastMessageSender;
        this.lastMessageSenderId = lastMessageSenderId;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.unread = unread;
    }

    // Getters
    public String getId() { return id; }

    public String getChatRoomName() { return chatRoomName; }

    public String getPhotoUrl() { return photoUrl; }

    public String getPhotoPath() { return photoPath; }

    public List<String> getMembers() { return members; }

    public List<String> getAdmins() { return admins; }

    public String getOwnerId() { return ownerId; }

    public String getLastMessage() { return lastMessage; }

    public String getLastMessageSender() { return lastMessageSender; }

    public String getLastMessageSenderId() { return lastMessageSenderId; }

    public Date getLastMessageTimestamp() { return lastMessageTimestamp; }

    public boolean isUnread() { return unread; }

    // Setters
    public void setId(String id) { this.id = id; }

    public void setChatRoomName(String chatRoomName) { this.chatRoomName = chatRoomName; }

    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public void setMembers(List<String> members) { this.members = members; }

    public void setAdmins(List<String> admins) { this.admins = admins; }

    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public void setLastMessageSender(String lastMessageSender) { this.lastMessageSender = lastMessageSender; }

    public void setLastMessageSenderId(String lastMessageSenderId) { this.lastMessageSenderId = lastMessageSenderId; }

    public void setLastMessageTimestamp(Date lastMessageTimestamp) { this.lastMessageTimestamp = lastMessageTimestamp; }

    public void setUnread(boolean unread) { this.unread = unread; }
}
