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
    private String photoUrl;
    private String reminder;
    private long lastAccessed;



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

    public void setOwnerUsername(String ownerUsername) {
        this.owner_username = ownerUsername;
    }

    public String getOwnerUsername() {
        return owner_username;
    }

    public void setQuestionCount(int count) {
        this.number_of_items = count;
    }

    public int getQuestionCount() {
        return number_of_items;
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
    public boolean isPublic() {
        return "public".equalsIgnoreCase(privacy);
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

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getReminder() {
        return reminder;
    }

    public void setReminder(String reminder) {
        this.reminder = reminder;
    }

    public long getLastAccessed() {
        return lastAccessed;
    }

    public void setLastAccessed(long lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    // Inner class for Question
    public static class Question {
        private String question;
        private String type;
        private List<String> choices;
        private Object correctAnswer;
        private String reminder;

        public Question() {}

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

        public Object getCorrectAnswerRaw() {
            return correctAnswer;
        }

        public void setCorrectAnswer(Object correctAnswer) {
            this.correctAnswer = correctAnswer;
        }

        public List<String> getCorrectAnswerAsList() {
            if (correctAnswer instanceof List) {
                return (List<String>) correctAnswer;
            } else if (correctAnswer instanceof String) {
                return List.of((String) correctAnswer);
            } else {
                return List.of();
            }
        }

        public String getCorrectAnswerAsString() {
            return String.join(", ", getCorrectAnswerAsList());
        }

        public String getReminder() {
            return reminder;
        }

        public void setReminder(String reminder) {
            this.reminder = reminder;
        }

        public boolean isMultipleChoice() {
            return "multiple choice".equalsIgnoreCase(type);
        }

        public boolean isEnumeration() {
            return "enumeration".equalsIgnoreCase(type);
        }
    }
}
