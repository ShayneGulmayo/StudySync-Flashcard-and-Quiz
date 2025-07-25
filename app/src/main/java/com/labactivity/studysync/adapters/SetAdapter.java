package com.labactivity.studysync.adapters;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.QuizPreviewActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SetAdapter extends RecyclerView.Adapter<SetAdapter.ViewHolder> implements Filterable {

    public interface OnFlashcardSetClickListener {
        void onFlashcardSetClick(Flashcard set);
    }

    private Context context;
    private ArrayList<Flashcard> flashcard;
    private ArrayList<Flashcard> flashcardSetsFull;
    private OnFlashcardSetClickListener listener;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    private static final int TYPE_FLASHCARD = 0;
    private static final int TYPE_QUIZ = 1;

    private final HashMap<String, User> cachedUsers = new HashMap<>();

    @Override
    public int getItemViewType(int position) {
        Flashcard set = flashcard.get(position);
        return set.getType().equalsIgnoreCase("quiz") ? TYPE_QUIZ : TYPE_FLASHCARD;
    }

    public SetAdapter(Context context, ArrayList<Flashcard> flashcardSets, OnFlashcardSetClickListener listener) {
        this.context = context;
        this.flashcard = flashcardSets;
        this.flashcardSetsFull = new ArrayList<>(flashcardSets);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(
                viewType == TYPE_QUIZ ? R.layout.item_quiz_set : R.layout.item_flashcard_set,
                parent,
                false
        );
        return new ViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Flashcard set = flashcard.get(position);

        String title = set.getTitle();
        if (title.length() > 30) title = title.substring(0, 27) + "...";
        holder.setNameText.setText(title);
        holder.setItemText.setText(set.getNumber_Of_Items() + " items");

        String ownerUid = set.getOwnerUid();
        holder.flashcardOwner.setText("Loading...");

        if (cachedUsers.containsKey(ownerUid)) {
            User owner = cachedUsers.get(ownerUid);
            holder.flashcardOwner.setText(owner != null ? owner.getUsername() : "Unknown");
            loadPhoto(holder.userProfileImage, owner != null ? owner.getPhotoUrl() : null);
        } else {
            db.collection("users").document(ownerUid).get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    User user = documentSnapshot.toObject(User.class);
                    cachedUsers.put(ownerUid, user);
                    holder.flashcardOwner.setText(user != null ? user.getUsername() : "Unknown");
                    loadPhoto(holder.userProfileImage, user != null ? user.getPhotoUrl() : null);
                } else {
                    holder.flashcardOwner.setText("Unknown");
                }
            });
        }

        if (getItemViewType(position) == TYPE_QUIZ) {
            holder.statsProgressBar.setProgress(0);
            holder.progressPercentageText.setText("...");

            int progressValue = set.getProgress();
            holder.statsProgressBar.setProgress(progressValue);
            holder.progressPercentageText.setText(progressValue + "%");


            if (set.getReminder() != null && !set.getReminder().isEmpty()) {
                holder.setReminderTextView.setVisibility(View.VISIBLE);
                holder.setReminderTextView.setText("Reminder: " + set.getReminder());
            } else {
                holder.setReminderTextView.setVisibility(View.GONE);
            }

        } else {
            int progressValue = set.getProgress();
            holder.statsProgressBar.setProgress(progressValue);
            holder.progressPercentageText.setText(progressValue + "%");

            if (set.getReminder() != null && !set.getReminder().isEmpty()) {
                holder.setReminderTextView.setVisibility(View.VISIBLE);
                holder.setReminderTextView.setText("Reminder: " + set.getReminder());
            } else {
                holder.setReminderTextView.setVisibility(View.GONE);
            }
        }

        holder.privacyIcon.setImageResource("Private".equalsIgnoreCase(set.getPrivacy()) ? R.drawable.lock : R.drawable.public_icon);

        holder.itemView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
            }
            return false; // allows click to still work
        });

        holder.itemView.setOnClickListener(v -> listener.onFlashcardSetClick(set));

    }

    private void loadPhoto(ImageView view, String url) {
        if (url != null && !url.isEmpty()) {
            Glide.with(context)
                    .load(url)
                    .apply(new RequestOptions()
                            .placeholder(R.drawable.user_profile)
                            .error(R.drawable.user_profile)
                            .circleCrop())
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(view);
        } else {
            view.setImageResource(R.drawable.user_profile);
        }
    }

    @Override
    public int getItemCount() {
        return flashcard.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView setNameText, setItemText, flashcardOwner, progressPercentageText, setReminderTextView;
        ProgressBar statsProgressBar;
        ImageView userProfileImage, privacyIcon;
        int viewType;

        public ViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;

            setNameText = itemView.findViewById(R.id.set_name_text);
            setItemText = itemView.findViewById(R.id.set_item_text);
            progressPercentageText = itemView.findViewById(R.id.progress_percentage2);
            statsProgressBar = itemView.findViewById(R.id.stats_progressbar);
            privacyIcon = itemView.findViewById(R.id.privacy_icon);
            setReminderTextView = itemView.findViewById(R.id.set_reminder);

            if (viewType == TYPE_QUIZ) {
                flashcardOwner = itemView.findViewById(R.id.quiz_owner);
                userProfileImage = itemView.findViewById(R.id.quiz_user_profile);
            } else {
                flashcardOwner = itemView.findViewById(R.id.flashcard_owner);
                userProfileImage = itemView.findViewById(R.id.user_profile);
            }
        }
    }

    @Override
    public Filter getFilter() {
        return flashcardSetFilter;
    }

    private final Filter flashcardSetFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Flashcard> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(flashcardSetsFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (Flashcard set : flashcardSetsFull) {
                    boolean matchesTitle = set.getTitle().toLowerCase().contains(filterPattern);
                    boolean matchesOwner = false;
                    User owner = cachedUsers.get(set.getOwnerUid());
                    if (owner != null && owner.getUsername() != null) {
                        matchesOwner = owner.getUsername().toLowerCase().contains(filterPattern);
                    }
                    if (matchesTitle || matchesOwner) {
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
            flashcard.clear();
            flashcard.addAll((List<Flashcard>) results.values);
            notifyDataSetChanged();
        }
    };

    public void cleanupListeners() {
        cachedUsers.clear();
    }
}
