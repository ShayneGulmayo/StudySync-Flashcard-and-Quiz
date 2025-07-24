package com.labactivity.studysync.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.labactivity.studysync.LiveQuizViewActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.LiveQuiz;

import java.util.List;

public class LiveQuizAdapter extends RecyclerView.Adapter<LiveQuizAdapter.ViewHolder> {
    private final Context context;
    private final List<LiveQuiz> quizList;
    private final String roomId;

    public LiveQuizAdapter(Context context, List<LiveQuiz> quizList, String roomId) {
        this.context = context;
        this.quizList = quizList;
        this.roomId = roomId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_live_quiz, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LiveQuiz quiz = quizList.get(position);
        holder.title.setText(quiz.getTitle());
        holder.count.setText(quiz.getQuestionCount() + " questions");

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, LiveQuizViewActivity.class);
            intent.putExtra("roomId", roomId);
            intent.putExtra("quizId", quiz.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return quizList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, count;

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.quiz_title);
            count = itemView.findViewById(R.id.quiz_count);
        }
    }
}
