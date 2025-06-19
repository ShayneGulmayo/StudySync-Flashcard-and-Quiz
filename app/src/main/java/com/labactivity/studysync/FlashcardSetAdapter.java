package com.labactivity.studysync;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

public class FlashcardSetAdapter extends RecyclerView.Adapter<FlashcardSetAdapter.ViewHolder> implements Filterable {

    public interface OnFlashcardSetClickListener {
        void onFlashcardSetClick(FlashcardSet set);
    }

    private Context context;
    private ArrayList<FlashcardSet> flashcardSets;
    private ArrayList<FlashcardSet> flashcardSetsFull; // backup for filtering
    private OnFlashcardSetClickListener listener;

    public FlashcardSetAdapter(Context context, ArrayList<FlashcardSet> flashcardSets, OnFlashcardSetClickListener listener) {
        this.context = context;
        this.flashcardSets = flashcardSets;
        this.flashcardSetsFull = new ArrayList<>(flashcardSets); // full backup list for search
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

        // Limit title to 20 characters
        String title = set.getTitle();
        if (title.length() > 20) {
            title = title.substring(0, 17) + "...";
        }
        holder.setNameText.setText(title);

        holder.setItemText.setText(set.getNumberOfItems() + " items");
        holder.flashcardOwner.setText(set.getOwnerUsername());

        // Progress bar
        int progressValue = set.getProgress();
        holder.statsProgressBar.setProgress(progressValue);
        holder.progressPercentageText.setText(progressValue + "%");

        // Load user profile photo
        if (set.getPhotoUrl() != null) {
            Glide.with(context)
                    .load(set.getPhotoUrl())
                    .apply(new RequestOptions()
                            .placeholder(R.drawable.user_profile)
                            .error(R.drawable.user_profile)
                            .circleCrop())
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.userProfileImage);
        } else {
            holder.userProfileImage.setImageResource(R.drawable.user_profile);
        }

        // Click listener
        holder.itemView.setOnClickListener(v -> listener.onFlashcardSetClick(set));
    }


    @Override
    public int getItemCount() {
        return flashcardSets.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView setNameText, setItemText, flashcardOwner, progressPercentageText;
        ProgressBar backgroundProgressBar, statsProgressBar;
        ImageView userProfileImage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            setNameText = itemView.findViewById(R.id.set_name_text);
            setItemText = itemView.findViewById(R.id.set_item_text);
            flashcardOwner = itemView.findViewById(R.id.flashcard_owner);
            progressPercentageText = itemView.findViewById(R.id.progress_percentage2);
            backgroundProgressBar = itemView.findViewById(R.id.background_progressbar);
            statsProgressBar = itemView.findViewById(R.id.stats_progressbar);
            userProfileImage = itemView.findViewById(R.id.user_profile);
        }
    }

    @Override
    public Filter getFilter() {
        return flashcardSetFilter;
    }

    private final Filter flashcardSetFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<FlashcardSet> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(flashcardSetsFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (FlashcardSet set : flashcardSetsFull) {
                    if (set.getTitle().toLowerCase().contains(filterPattern)
                            || set.getOwnerUsername().toLowerCase().contains(filterPattern)) {
                        filteredList.add(set);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            flashcardSets.clear();
            flashcardSets.addAll((List) results.values);
            notifyDataSetChanged();
        }
    };

    // Optionally call this if you reload or refresh your list externally
    public void updateData(ArrayList<FlashcardSet> newList) {
        flashcardSets.clear();
        flashcardSets.addAll(newList);
        flashcardSetsFull.clear();
        flashcardSetsFull.addAll(newList);
        notifyDataSetChanged();
    }
}
