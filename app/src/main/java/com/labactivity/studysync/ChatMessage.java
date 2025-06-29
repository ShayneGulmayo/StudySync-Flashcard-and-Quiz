package com.labactivity.studysync;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class ChatMessage {
    private String senderId;
    private String senderName;
    private String senderPhotoUrl;
    private String text;

    @ServerTimestamp
    private Date timestamp;

    private String type; // "user" or "system"

    // Required public no-argument constructor
    public ChatMessage() {}

    public ChatMessage(String senderId, String senderName, String senderPhotoUrl, String text, Date timestamp) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderPhotoUrl = senderPhotoUrl;
        this.text = text;
        this.timestamp = timestamp;
        this.type = "user"; // default type
    }

    // Getters
    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderPhotoUrl() {
        return senderPhotoUrl;
    }

    public String getText() {
        return text;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    // Setters
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public void setSenderPhotoUrl(String senderPhotoUrl) {
        this.senderPhotoUrl = senderPhotoUrl;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public void setType(String type) {
        this.type = type;
    }
}
