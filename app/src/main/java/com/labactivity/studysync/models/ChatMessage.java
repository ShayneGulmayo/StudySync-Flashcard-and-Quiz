package com.labactivity.studysync.models;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class ChatMessage {
    private String senderId;
    private String senderName;
    private String senderPhotoUrl;
    private String text;
    private String type;
    private String imageUrl;
    private String videoUrl;
    private String status;
    private String fileUrl;
    private String fileName;
    private long fileSize;
    private String fileType;
    private String filePath;
    private String action;
    private String setId;    // ID of the shared Flashcard or Quiz set
    private String setType;  // "flashcard" or "quiz"
    private String requestedRole;
    @ServerTimestamp
    private Date timestamp;
    private String messageId;

    // Required no-arg constructor
    public ChatMessage() {}

    public ChatMessage(String senderId, String senderName, String senderPhotoUrl, String text, Date timestamp) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderPhotoUrl = senderPhotoUrl;
        this.text = text;
        this.timestamp = timestamp;
        this.type = "user";
    }

    // Getters
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getSenderPhotoUrl() { return senderPhotoUrl; }
    public String getText() { return text; }
    public String getType() { return type; }
    public String getImageUrl() { return imageUrl; }
    public String getFileUrl() { return fileUrl; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getFileType() { return fileType; }
    public String getFilePath() { return filePath; }
    public Date getTimestamp() { return timestamp; }
    public String getSetId() { return setId; }
    public String getSetType() { return setType; }

    // Setters
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public void setSenderPhotoUrl(String senderPhotoUrl) { this.senderPhotoUrl = senderPhotoUrl; }
    public void setText(String text) { this.text = text; }
    public void setType(String type) { this.type = type; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public void setSetId(String setId) { this.setId = setId; }
    public void setSetType(String setType) { this.setType = setType; }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getAction() { return action;
    }

    public String getRequestedRole() { return requestedRole;
    }

    public String getStatus() { return status;
    }

    public String getMessageId() { return messageId;
    }

    public void setRequestedRole(String requestedRole) { this.requestedRole = requestedRole;
    }

    public void setStatus(String status) { this.status = status;
    }
}
