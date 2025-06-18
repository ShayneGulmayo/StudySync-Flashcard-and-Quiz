package com.labactivity.studysync;

public class Flashcard {
    private String term;
    private String definition;

    public Flashcard() {
        // Required for Firestore
    }

    public Flashcard(String term, String definition) {
        this.term = term;
        this.definition = definition;
    }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
}
