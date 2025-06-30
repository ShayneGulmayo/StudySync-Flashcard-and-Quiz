package com.labactivity.studysync.models;

public class Flashcard {
    private String term;
    private String definition;
    private String photoUrl;
    private String photoPath;

    public Flashcard(String term, String definition, String photoUrl, String photoPath) {
        this.term = term;
        this.definition = definition;
        this.photoUrl = photoUrl;
        this.photoPath = photoPath;
    }

    public String getTerm() { return term; }
    public String getDefinition() { return definition; }
    public String getPhotoUrl() { return photoUrl; }
    public String getPhotoPath() { return photoPath; }

}
