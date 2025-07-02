package com.labactivity.studysync.models;

import java.util.Date;
import java.util.List;

public class Quiz {
    private String quizId;
    private String title;
    private String owner_uid;
    private String owner_username;
    private String privacy;
    private int number_of_items;
    private int progress;
    private Date created_at;
    private List<Question> questions;

    public Quiz() {
        // Required for Firestore deserialization
    }

    // Getters and Setters
    public String getQuizId() {
        return quizId;
    }

    public void setQuizId(String quizId) {
        this.quizId = quizId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOwner_uid() {
        return owner_uid;
    }

    public void setOwner_uid(String owner_uid) {
        this.owner_uid = owner_uid;
    }


    public String getPrivacy() {
        return privacy;
    }

    public void setPrivacy(String privacy) {
        this.privacy = privacy;
    }

    public int getNumber_of_items() {
        return number_of_items;
    }

    public void setNumber_of_items(int number_of_items) {
        this.number_of_items = number_of_items;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public Date getCreated_at() {
        return created_at;
    }

    public void setCreated_at(Date created_at) {
        this.created_at = created_at;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }

    // Inner class for Question
    public static class Question {
        private String question;
        private String type;
        private List<String> choices;
        private String correctAnswer;  // will store comma-separated values if it's a list

        public Question() {}

        public Question(String question, List<String> choices) {
            this.question = question;
            this.choices = choices;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getChoices() {
            return choices;
        }

        public void setChoices(List<String> choices) {
            this.choices = choices;
        }

        public String getCorrectAnswer() {
            return correctAnswer;
        }

        public void setCorrectAnswer(String correctAnswer) {
            this.correctAnswer = correctAnswer;
        }

        private String reminder;

        public String getReminder() {
            return reminder;
        }

        public void setReminder(String reminder) {
            this.reminder = reminder;
        }


        // Convenience method
        public boolean isMultipleChoice() {
            return "multiple choice".equalsIgnoreCase(type);
        }

        public boolean isEnumeration() {
            return "enumeration".equalsIgnoreCase(type);
        }
    }
}
