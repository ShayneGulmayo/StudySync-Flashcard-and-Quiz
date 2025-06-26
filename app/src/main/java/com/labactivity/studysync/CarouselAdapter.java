package com.labactivity.studysync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CarouselAdapter extends RecyclerView.Adapter<CarouselAdapter.CarouselViewHolder> {

    private final List<Flashcard> flashcards;

    public CarouselAdapter(List<Flashcard> flashcards) {
        this.flashcards = flashcards;
    }

    @NonNull
    @Override
    public CarouselViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_carousel, parent, false);
        return new CarouselViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CarouselViewHolder holder, int position) {
        Flashcard flashcard = flashcards.get(position);
        holder.termCard.setText(flashcard.getTerm());
        holder.definitionCard.setText(flashcard.getDefinition());

        // Initially show term, hide definition
        holder.termCard.setVisibility(View.VISIBLE);
        holder.definitionCard.setVisibility(View.GONE);

        // Set camera distance for 3D effect
        float scale = holder.itemView.getContext().getResources().getDisplayMetrics().density;
        holder.termCard.setCameraDistance(8000 * scale);
        holder.definitionCard.setCameraDistance(8000 * scale);

        // Toggle with flip animation
        holder.itemView.setOnClickListener(v -> {
            View visibleView, hiddenView;

            if (holder.termCard.getVisibility() == View.VISIBLE) {
                visibleView = holder.termCard;
                hiddenView = holder.definitionCard;
            } else {
                visibleView = holder.definitionCard;
                hiddenView = holder.termCard;
            }

            // Flip out the visible side
            visibleView.animate()
                    .rotationY(90f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        visibleView.setVisibility(View.GONE);
                        visibleView.setRotationY(0f);

                        // Flip in the hidden side
                        hiddenView.setRotationY(-90f);
                        hiddenView.setVisibility(View.VISIBLE);
                        hiddenView.animate()
                                .rotationY(0f)
                                .setDuration(150)
                                .start();
                    }).start();
        });
    }

    @Override
    public int getItemCount() {
        return flashcards.size();
    }

    public static class CarouselViewHolder extends RecyclerView.ViewHolder {
        TextView termCard, definitionCard;

        public CarouselViewHolder(@NonNull View itemView) {
            super(itemView);
            termCard = itemView.findViewById(R.id.term_card);
            definitionCard = itemView.findViewById(R.id.definition_card);
        }
    }
}
