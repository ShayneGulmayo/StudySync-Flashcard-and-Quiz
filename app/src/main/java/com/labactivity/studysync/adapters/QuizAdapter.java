package com.labactivity.studysync.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.labactivity.studysync.QuizProgressActivity;
import com.labactivity.studysync.R;

import java.util.List;

public class QuizAdapter extends RecyclerView.Adapter<QuizAdapter.QuizViewHolder> {
    private final List<DocumentSnapshot> quizList;
    private final Context context;

    public QuizAdapter(List<DocumentSnapshot> quizList, Context context) {
        this.quizList = quizList;
        this.context = context;
    }

    @NonNull
    @Override
    public QuizViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quiz_set, parent, false);
        return new QuizViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuizViewHolder holder, int position) {
        DocumentSnapshot quiz = quizList.get(position);

        String title = quiz.getString("quizName");
        String owner = quiz.getString("ownerUsername");
        long numItems = quiz.getLong("numberOfItems") != null ? quiz.getLong("numberOfItems") : 0;
        long progress = quiz.getLong("progress") != null ? quiz.getLong("progress") : 0;

        holder.quizTitle.setText(title);
        holder.ownerUsername.setText(owner);
        holder.itemCount.setText(numItems + " items");
        holder.progressPercentage.setText(progress + "%");
        holder.foregroundProgress.setProgress((int) progress);

        holder.cardView.setOnClickListener(v -> {
            Intent intent = new Intent(context, QuizProgressActivity.class);
            intent.putExtra("quizId", quiz.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return quizList.size();
    }

    public static class QuizViewHolder extends RecyclerView.ViewHolder {
        TextView quizTitle, itemCount, ownerUsername, progressPercentage;
        ProgressBar foregroundProgress;
        CardView cardView;

        public QuizViewHolder(@NonNull View itemView) {
            super(itemView);
            quizTitle = itemView.findViewById(R.id.set_name_text);
            itemCount = itemView.findViewById(R.id.set_item_text);
            ownerUsername = itemView.findViewById(R.id.quiz_owner);
            progressPercentage = itemView.findViewById(R.id.progress_percentage2);
            foregroundProgress = itemView.findViewById(R.id.stats_progressbar);
            cardView = itemView.findViewById(R.id.cardView);
        }
    }
}
