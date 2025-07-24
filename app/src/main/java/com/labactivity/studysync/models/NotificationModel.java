package com.labactivity.studysync.models;

import com.google.firebase.Timestamp;

public class NotificationModel {
    private String notificationId;
    private String senderId;
    private String receiverId;
    private String text;
    private String type;
    private String setId;
    private String setType;
    private String requestedRole;
    private String status; // "pending", "accepted", "denied", "read"
    private Timestamp timestamp;

    public NotificationModel() {}

    // Getters and setters...

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSetId() { return setId; }
    public void setSetId(String setId) { this.setId = setId; }

    public String getSetType() { return setType; }
    public void setSetType(String setType) { this.setType = setType; }

    public String getRequestedRole() { return requestedRole; }
    public void setRequestedRole(String requestedRole) { this.requestedRole = requestedRole; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
