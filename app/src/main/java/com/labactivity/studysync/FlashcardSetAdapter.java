package com.labactivity.studysync;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class FlashcardSetAdapter extends RecyclerView.Adapter<FlashcardSetAdapter.ViewHolder> {

    public interface OnFlashcardSetClickListener {
        void onFlashcardSetClick(FlashcardSet set);
    }

    private Context context;
    private ArrayList<FlashcardSet> flashcardSets;
    private OnFlashcardSetClickListener listener;

    public FlashcardSetAdapter(Context context, ArrayList<FlashcardSet> flashcardSets, OnFlashcardSetClickListener listener) {
        this.context = context;
        this.flashcardSets = flashcardSets;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_flashcard_set, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FlashcardSet set = flashcardSets.get(position);
        holder.setNameText.setText(set.getTitle());
        holder.setItemText.setText(set.getNumberOfItems() + " items");
        holder.flashcardOwner.setText(set.getOwnerUsername());

        // Set progress value (0-100)
        int progressValue = set.getProgress();
        holder.statsProgressBar.setProgress(progressValue);
        holder.progressPercentageText.setText(progressValue + "%");

        holder.itemView.setOnClickListener(v -> listener.onFlashcardSetClick(set));
    }

    @Override
    public int getItemCount() {
        return flashcardSets.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView setNameText, setItemText, flashcardOwner, progressPercentageText;
        ProgressBar backgroundProgressBar, statsProgressBar;
        ImageView imageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            setNameText = itemView.findViewById(R.id.set_name_text);
            setItemText = itemView.findViewById(R.id.set_item_text);
            flashcardOwner = itemView.findViewById(R.id.flashcard_owner);
            progressPercentageText = itemView.findViewById(R.id.progress_percentage2);

            backgroundProgressBar = itemView.findViewById(R.id.background_progressbar);
            statsProgressBar = itemView.findViewById(R.id.stats_progressbar);

            imageView = itemView.findViewById(R.id.imageView);
        }
    }
}
