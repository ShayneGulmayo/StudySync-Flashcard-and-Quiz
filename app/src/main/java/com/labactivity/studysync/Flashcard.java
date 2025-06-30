package com.labactivity.studysync;

public class Flashcard {
    private String term;
    private String definition;
    private String photoUrl;

    public Flashcard(String term, String definition, String photoUrl) {
        this.term = term;
        this.definition = definition;
        this.photoUrl = photoUrl;
    }

    public String getTerm() { return term; }
    public String getDefinition() { return definition; }
    public String getPhotoUrl() { return photoUrl; }
}
