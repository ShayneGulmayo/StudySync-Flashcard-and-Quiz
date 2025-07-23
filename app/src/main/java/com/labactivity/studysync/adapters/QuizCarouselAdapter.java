package com.labactivity.studysync.adapters;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.Quiz;

import java.io.File;
import java.util.List;

public class QuizCarouselAdapter extends RecyclerView.Adapter<QuizCarouselAdapter.ViewHolder> {
    private final List<Quiz.Question> questions;

    public QuizCarouselAdapter(List<Quiz.Question> questions) {
        this.questions = questions;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quiz_carousel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Quiz.Question question = questions.get(position);
        holder.questionText.setText(question.getQuestion());

        String correctAnswer = question.getCorrectAnswerAsString();
        holder.answerText.setText(correctAnswer.isEmpty() ? "N/A" : correctAnswer);

        String localImagePath = question.getLocalPhotoPath(); // new field for offline
        String imageUrl = question.getPhotoUrl(); // online fallback

        if (localImagePath != null && !localImagePath.isEmpty()) {
            File imageFile = new File(localImagePath);
            if (imageFile.exists()) {
                holder.questionImage.setVisibility(View.VISIBLE);
                Glide.with(holder.itemView.getContext())
                        .load(Uri.fromFile(imageFile))
                        .fitCenter()
                        .into(holder.questionImage);
            } else {
                holder.questionImage.setVisibility(View.GONE);
            }
        } else if (imageUrl != null && !imageUrl.isEmpty()) {
            holder.questionImage.setVisibility(View.VISIBLE);
            Glide.with(holder.itemView.getContext())
                    .load(imageUrl)
                    .fitCenter()
                    .into(holder.questionImage);
        } else {
            holder.questionImage.setVisibility(View.GONE);
        }

        float scale = holder.itemView.getContext().getResources().getDisplayMetrics().density;
        holder.cardFront.setCameraDistance(8000 * scale);
        holder.cardBack.setCameraDistance(8000 * scale);

        holder.cardFront.setVisibility(View.VISIBLE);
        holder.cardBack.setVisibility(View.GONE);

        holder.cardContainer.setOnClickListener(v -> {
            if (holder.cardFront.getVisibility() == View.VISIBLE) {
                holder.cardFront.animate()
                        .rotationY(90f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            holder.cardFront.setVisibility(View.GONE);
                            holder.cardFront.setRotationY(0f);
                            holder.cardBack.setRotationY(-90f);
                            holder.cardBack.setVisibility(View.VISIBLE);
                            holder.cardBack.animate().rotationY(0f).setDuration(150).start();
                        }).start();
            } else {
                holder.cardBack.animate()
                        .rotationY(90f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            holder.cardBack.setVisibility(View.GONE);
                            holder.cardBack.setRotationY(0f);
                            holder.cardFront.setRotationY(-90f);
                            holder.cardFront.setVisibility(View.VISIBLE);
                            holder.cardFront.animate().rotationY(0f).setDuration(150).start();
                        }).start();
            }
        });
    }


    @Override
    public int getItemCount() {
        return questions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardFront, cardBack;
        TextView questionText, answerText;
        FrameLayout cardContainer;
        ImageView questionImage;
        //ImageView answerImage;

        ViewHolder(View itemView) {
            super(itemView);
            cardFront = itemView.findViewById(R.id.card_front);
            cardBack = itemView.findViewById(R.id.card_back);
            questionText = itemView.findViewById(R.id.question_text);
            answerText = itemView.findViewById(R.id.answer_text);
            cardContainer = itemView.findViewById(R.id.card_container);
            questionImage = itemView.findViewById(R.id.question_image);
            //answerImage = itemView.findViewById(R.id.answer_image);
        }
    }
}
