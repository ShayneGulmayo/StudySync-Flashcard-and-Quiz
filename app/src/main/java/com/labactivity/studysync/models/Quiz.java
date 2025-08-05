package com.labactivity.studysync.models;

import java.util.Date;
import java.util.List;

public class Quiz {
    private String quizId;
    private String title;
    private String owner_uid;
    private String owner_username;
    private String privacy;
    private String reminder;
    private Date created_at;
    private int number_of_items;
    private int progress;
    private long lastAccessed;
    private List<Question> questions;

    public Quiz() {}

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

    public String getOwnerUsername() {
        return owner_username;
    }
    public void setOwnerUsername(String ownerUsername) {
        this.owner_username = ownerUsername;
    }

    public String getReminder() { return reminder; }
    public void setReminder(String reminder) {
        this.reminder = reminder;
    }

    public int getNumber_of_items() {
        return number_of_items;
    }
    public void setNumber_of_items(int number_of_items) {
        this.number_of_items = number_of_items;
    }

    public int getQuestionCount() {
        return number_of_items;
    }
    public void setQuestionCount(int count) {
        this.number_of_items = count;
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

    public long getLastAccessed() {
        return lastAccessed;
    }
    public void setLastAccessed(long lastAccessed) {
        this.lastAccessed = lastAccessed;
    }

    public List<Question> getQuestions() {
        return questions;
    }
    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }

    public boolean isPublic() { return "public".equalsIgnoreCase(privacy); }

    public static class Question {
        private String question;
        private String quizType;
        private String type;
        private String reminder;
        private String photoUrl;
        private String localPhotoPath;
        private Object correctAnswer;
        private List<String> choices;

        public Question() {}

        public String getQuestion() {
            return question;
        }
        public void setQuestion(String question) {
            this.question = question;
        }

        public String getQuizType() {
            return quizType;
        }
        public void setQuizType(String quizType) { this.quizType = quizType; }

        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }

        public String getCorrectAnswerAsString() { return String.join(", ", getCorrectAnswerAsList()); }
        public void setCorrectAnswer(Object correctAnswer) {
            this.correctAnswer = correctAnswer;
        }

        public String getPhotoUrl() {return photoUrl;}
        public void setPhotoUrl(String photoUrl) {this.photoUrl = photoUrl;}

        public String getLocalPhotoPath() {
            return localPhotoPath;
        }
        public void setLocalPhotoPath(String localPhotoPath) { this.localPhotoPath = localPhotoPath; }

        public String getReminder() {
            return reminder;
        }
        public void setReminder(String reminder) {
            this.reminder = reminder;
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
        public List<String> getCorrectAnswerAsList() {
            if (correctAnswer instanceof List) {
                return (List<String>) correctAnswer;
            } else if (correctAnswer instanceof String) {
                return List.of((String) correctAnswer);
            } else {
                return List.of();
            }
        }

        public boolean isMultipleChoice() {
            return "multiple choice".equalsIgnoreCase(type);
        }

        public boolean isEnumeration() {
            return "enumeration".equalsIgnoreCase(type);
        }
    }

    public static class AnsweredQuestion {
        private int totalAnswered;
        private int totalCorrect;
        private int questionIndex;
        private long lastUpdated;
        private boolean isCorrect;
        private List<String> userAnswer;
        private List<AnsweredQuestion> answeredQuestions;

        public AnsweredQuestion() {}

        public AnsweredQuestion(int questionIndex, List<String> userAnswer, boolean isCorrect) {
            this.questionIndex = questionIndex;
            this.userAnswer = userAnswer;
            this.isCorrect = isCorrect;
        }

        public int getQuestionIndex() { return questionIndex; }
        public void setQuestionIndex(int questionIndex) { this.questionIndex = questionIndex; }

        public int getTotalAnswered() { return totalAnswered; }
        public void setTotalAnswered(int totalAnswered) { this.totalAnswered = totalAnswered; }

        public int getTotalCorrect() { return totalCorrect; }
        public void setTotalCorrect(int totalCorrect) { this.totalCorrect = totalCorrect; }

        public long getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

        public boolean isCorrect() { return isCorrect; }
        public void setCorrect(boolean correct) { isCorrect = correct; }

        public List<String> getUserAnswer() { return userAnswer; }
        public void setUserAnswer(List<String> userAnswer) { this.userAnswer = userAnswer; }

        public List<AnsweredQuestion> getAnsweredQuestions() { return answeredQuestions; }
        public void setAnsweredQuestions(List<AnsweredQuestion> answeredQuestions) { this.answeredQuestions = answeredQuestions; }
    }
}