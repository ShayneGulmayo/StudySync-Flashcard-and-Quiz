package com.labactivity.studysync;

import java.util.Date;
import java.util.List;

public class ChatRoom {

    private String id;
    private String chatRoomName;
    private String photoUrl;
    private List<String> members;

    private String lastMessage;
    private String lastMessageSender;
    private String lastMessageSenderId;
    private Date lastMessageTimestamp;

    // 🆕 Unread flag
    private boolean unread;

    // Required empty constructor for Firestore
    public ChatRoom() {}

    // Full constructor
    public ChatRoom(String id, String chatRoomName, String photoUrl, List<String> members,
                    String lastMessage, String lastMessageSender, String lastMessageSenderId,
                    Date lastMessageTimestamp, boolean unread) {
        this.id = id;
        this.chatRoomName = chatRoomName;
        this.photoUrl = photoUrl;
        this.members = members;
        this.lastMessage = lastMessage;
        this.lastMessageSender = lastMessageSender;
        this.lastMessageSenderId = lastMessageSenderId;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.unread = unread;
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getChatRoomName() {
        return chatRoomName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public List<String> getMembers() {
        return members;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String getLastMessageSender() {
        return lastMessageSender;
    }

    public String getLastMessageSenderId() {
        return lastMessageSenderId;
    }

    public Date getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }

    public boolean isUnread() {
        return unread;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setChatRoomName(String chatRoomName) {
        this.chatRoomName = chatRoomName;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public void setLastMessageSender(String lastMessageSender) {
        this.lastMessageSender = lastMessageSender;
    }

    public void setLastMessageSenderId(String lastMessageSenderId) {
        this.lastMessageSenderId = lastMessageSenderId;
    }

    public void setLastMessageTimestamp(Date lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public void setUnread(boolean unread) {
        this.unread = unread;
    }
}
