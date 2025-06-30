package com.labactivity.studysync.adapters;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.R;

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

        if (flashcard.getPhotoUrl() != null && !flashcard.getPhotoUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(flashcard.getPhotoUrl()).into(holder.flashcardImage);
            holder.flashcardImage.setVisibility(View.VISIBLE);
        } else {
            holder.flashcardImage.setVisibility(View.GONE);
        }

        holder.cardTerm.setVisibility(View.VISIBLE);
        holder.cardDefinition.setVisibility(View.GONE);
        holder.definitionCard.setVisibility(View.GONE);

        float scale = holder.itemView.getContext().getResources().getDisplayMetrics().density;
        holder.cardTerm.setCameraDistance(8000 * scale);
        holder.cardDefinition.setCameraDistance(8000 * scale);

        holder.itemView.setOnClickListener(v -> {
            if (holder.cardTerm.getVisibility() == View.VISIBLE) {
                holder.cardTerm.animate()
                        .rotationY(90f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            holder.cardTerm.setVisibility(View.GONE);
                            holder.cardTerm.setRotationY(0f);

                            holder.cardDefinition.setRotationY(-90f);
                            holder.cardDefinition.setVisibility(View.VISIBLE);

                            holder.definitionCard.setVisibility(View.VISIBLE);
                            if (flashcard.getPhotoUrl() != null && !flashcard.getPhotoUrl().isEmpty()) {
                                holder.flashcardImage.setVisibility(View.VISIBLE);
                            } else {
                                holder.flashcardImage.setVisibility(View.GONE);
                            }

                            holder.cardDefinition.animate()
                                    .rotationY(0f)
                                    .setDuration(150)
                                    .start();
                        }).start();
            } else {
                holder.cardDefinition.animate()
                        .rotationY(90f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            holder.cardDefinition.setVisibility(View.GONE);
                            holder.cardDefinition.setRotationY(0f);

                            // Hide definition contents
                            holder.definitionCard.setVisibility(View.GONE);
                            holder.flashcardImage.setVisibility(View.GONE);

                            holder.cardTerm.setRotationY(-90f);
                            holder.cardTerm.setVisibility(View.VISIBLE);
                            holder.cardTerm.animate()
                                    .rotationY(0f)
                                    .setDuration(150)
                                    .start();
                        }).start();
            }
        });

        if (flashcard.getPhotoUrl() != null && !flashcard.getPhotoUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(flashcard.getPhotoUrl())
                    .transform(new RoundedCorners(dpToPx(20, holder)))
                    .into(holder.flashcardImage);
            holder.flashcardImage.setVisibility(View.VISIBLE);
            holder.definitionCard.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            holder.definitionCard.setPadding(400, 0, 32, 0);

        } else {
            holder.flashcardImage.setVisibility(View.GONE);
            holder.definitionCard.setGravity(Gravity.CENTER);
        }


    }

    private int dpToPx(int dp, CarouselViewHolder holder) {
        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public int getItemCount() {
        return flashcards.size();
    }

    public static class CarouselViewHolder extends RecyclerView.ViewHolder {
        CardView cardTerm, cardDefinition;
        TextView termCard, definitionCard;
        ImageView flashcardImage;

        public CarouselViewHolder(@NonNull View itemView) {
            super(itemView);
            cardTerm = itemView.findViewById(R.id.card_term);
            cardDefinition = itemView.findViewById(R.id.card_definition);
            termCard = itemView.findViewById(R.id.term_card);
            definitionCard = itemView.findViewById(R.id.definition_card);
            flashcardImage = itemView.findViewById(R.id.flashcard_image);
        }
    }
}
