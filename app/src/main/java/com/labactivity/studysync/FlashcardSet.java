package com.labactivity.studysync;

import java.util.ArrayList;
import java.util.Map;

public class FlashcardSet {
    private String id;
    private String name;
    private String ownerId;
    private long timestamp;
    private ArrayList<Map<String, String>> flashcards;

    public FlashcardSet() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public String getOwnerId() { return ownerId; }
    public long getTimestamp() { return timestamp; }
    public ArrayList<Map<String, String>> getFlashcards() { return flashcards; }
}
