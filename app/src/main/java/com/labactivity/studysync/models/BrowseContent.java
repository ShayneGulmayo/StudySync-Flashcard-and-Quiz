package com.labactivity.studysync.models;

import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.models.Quiz;
import com.labactivity.studysync.models.User;

public class BrowseContent {
    private String type;
    private User user;
    private Flashcard flashcard;
    private Quiz quiz;

    public static BrowseContent fromUser(User user) {
        BrowseContent content = new BrowseContent();
        content.type = "user";
        content.user = user;
        return content;
    }

    public static BrowseContent fromFlashcard(Flashcard flashcard) {
        BrowseContent content = new BrowseContent();
        content.type = "flashcard";
        content.flashcard = flashcard;
        return content;
    }

    public static BrowseContent fromQuiz(Quiz quiz) {
        BrowseContent content = new BrowseContent();
        content.type = "quiz";
        content.quiz = quiz;
        return content;
    }

    public String getType() { return type; }
    public User getUser() { return user; }
    public Flashcard getFlashcard() { return flashcard; }
    public Quiz getQuiz() { return quiz; }
}
