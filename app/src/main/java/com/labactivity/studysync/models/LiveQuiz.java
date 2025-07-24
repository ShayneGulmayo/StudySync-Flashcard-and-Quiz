package com.labactivity.studysync.models;

public class LiveQuiz {
    private String id;
    private String title;
    private int questionCount;

    public LiveQuiz(String id, String title, int questionCount) {
        this.id = id;
        this.title = title;
        this.questionCount = questionCount;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getQuestionCount() {
        return questionCount;
    }
}
