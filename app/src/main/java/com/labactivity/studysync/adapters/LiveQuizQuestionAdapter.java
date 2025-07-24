package com.labactivity.studysync.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.labactivity.studysync.R;

import java.util.List;
import java.util.Map;

public class LiveQuizQuestionAdapter extends RecyclerView.Adapter<LiveQuizQuestionAdapter.QuestionViewHolder> {
    private List<Map<String, Object>> questions;
    private boolean hideAnswers;

    public LiveQuizQuestionAdapter(List<Map<String, Object>> questions, boolean hideAnswers) {
        this.questions = questions;
        this.hideAnswers = hideAnswers;
    }

    public void setHideAnswers(boolean hideAnswers) {
        this.hideAnswers = hideAnswers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QuestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_live_quiz_question, parent, false);
        return new QuestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestionViewHolder holder, int position) {
        Map<String, Object> q = questions.get(position);
        holder.questionTxt.setText((position + 1) + ". " + q.get("question").toString());
        holder.answerTxt.setText("Answer: " + q.get("correctAnswer").toString());
        holder.answerTxt.setVisibility(hideAnswers ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return questions.size();
    }

    public static class QuestionViewHolder extends RecyclerView.ViewHolder {
        TextView questionTxt, answerTxt;

        public QuestionViewHolder(@NonNull View itemView) {
            super(itemView);
            questionTxt = itemView.findViewById(R.id.questionText);
            answerTxt = itemView.findViewById(R.id.answerText);
        }
    }
}
