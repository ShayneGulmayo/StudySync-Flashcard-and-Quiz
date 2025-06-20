package com.labactivity.studysync;

public class Flashcard {
    private String term;
    private String definition;

    public Flashcard(String term, String definition) {
        this.term = term;
        this.definition = definition;
    }

    public String getTerm() { return term; }

    public String getDefinition() { return definition; }
}
