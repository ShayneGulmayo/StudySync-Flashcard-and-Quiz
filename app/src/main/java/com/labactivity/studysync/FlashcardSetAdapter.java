package com.labactivity.studysync;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FlashcardSetAdapter extends RecyclerView.Adapter<FlashcardSetAdapter.ViewHolder> implements Filterable {

    public interface OnFlashcardSetClickListener {
        void onFlashcardSetClick(FlashcardSet set);
    }

    private Context context;
    private ArrayList<FlashcardSet> flashcardSets;
    private ArrayList<FlashcardSet> flashcardSetsFull;
    private OnFlashcardSetClickListener listener;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    private static final int TYPE_FLASHCARD = 0;
    private static final int TYPE_QUIZ = 1;

    private final HashMap<String, String> cachedUsernames = new HashMap<>();
    private final HashMap<String, com.google.firebase.firestore.ListenerRegistration> userListeners = new HashMap<>();

    @Override
    public int getItemViewType(int position) {
        FlashcardSet set = flashcardSets.get(position);
        return set.getType().equalsIgnoreCase("quiz") ? TYPE_QUIZ : TYPE_FLASHCARD;
    }

    public FlashcardSetAdapter(Context context, ArrayList<FlashcardSet> flashcardSets, OnFlashcardSetClickListener listener) {
        this.context = context;
        this.flashcardSets = flashcardSets;
        this.flashcardSetsFull = new ArrayList<>(flashcardSets);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == TYPE_QUIZ) {
            view = LayoutInflater.from(context).inflate(R.layout.item_quiz_set, parent, false);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.item_flashcard_set, parent, false);
        }
        return new ViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FlashcardSet set = flashcardSets.get(position);

        String title = set.getTitle();
        if (title.length() > 20) {
            title = title.substring(0, 17) + "...";
        }
        holder.setNameText.setText(title);
        holder.setItemText.setText(set.getNumberOfItems() + " items");

        String ownerUid = set.getOwnerUid();
        holder.flashcardOwner.setText("Loading...");
        if (cachedUsernames.containsKey(ownerUid)) {
            holder.flashcardOwner.setText(cachedUsernames.get(ownerUid));
        } else {
            if (!userListeners.containsKey(ownerUid)) {
                com.google.firebase.firestore.ListenerRegistration registration =
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(ownerUid)
                                .addSnapshotListener((documentSnapshot, e) -> {
                                    if (e == null && documentSnapshot != null && documentSnapshot.exists()) {
                                        String username = documentSnapshot.getString("username");
                                        if (username != null) {
                                            cachedUsernames.put(ownerUid, username);
                                            notifyDataSetChanged();
                                        }
                                    }
                                });
                userListeners.put(ownerUid, registration);
            }
        }

        if (getItemViewType(position) == TYPE_QUIZ) {
            holder.statsProgressBar.setProgress(0);
            holder.progressPercentageText.setText("...");

            db.collection("quiz_attempts")
                    .document(set.getId())
                    .collection("users")
                    .document(currentUserId)
                    .get()
                    .addOnSuccessListener(attempt -> {
                        if (attempt.exists()) {
                            Long scoreL = attempt.getLong("score");
                            Long totalL = attempt.getLong("total");

                            if (scoreL != null && totalL != null && totalL > 0) {
                                int correct = scoreL.intValue();
                                int totalItems = totalL.intValue();
                                int percent = Math.round((correct / (float) totalItems) * 100);
                                holder.statsProgressBar.setProgress(percent);
                                holder.progressPercentageText.setText(percent + "%");
                            } else {
                                holder.statsProgressBar.setProgress(0);
                                holder.progressPercentageText.setText("0%");
                            }
                        } else {
                            holder.statsProgressBar.setProgress(0);
                            holder.progressPercentageText.setText("0%");
                        }
                    })
                    .addOnFailureListener(e -> {
                        holder.statsProgressBar.setProgress(0);
                        holder.progressPercentageText.setText("0%");
                    });

        } else {
            int progressValue = set.getProgress();
            holder.statsProgressBar.setProgress(progressValue);
            holder.progressPercentageText.setText(progressValue + "%");
        }

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

        if ("Private".equalsIgnoreCase(set.getPrivacy())) {
            holder.privacyIcon.setImageResource(R.drawable.lock);
        } else {
            holder.privacyIcon.setImageResource(R.drawable.public_icon);
        }

        if (holder.viewType == TYPE_FLASHCARD) {
            if (set.getReminder() != null && !set.getReminder().isEmpty()) {
                holder.setReminderTextView.setVisibility(View.VISIBLE);
                holder.setReminderTextView.setText("Reminder: " + set.getReminder());
            } else {
                holder.setReminderTextView.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (set.getType().equals("quiz")) {
                Intent intent = new Intent(context, QuizPreviewActivity.class);
                intent.putExtra("quizId", set.getId());
                intent.putExtra("quizName", set.getTitle());
                intent.putExtra("photoUrl", set.getPhotoUrl());
                context.startActivity(intent);
            } else {
                listener.onFlashcardSetClick(set);
            }
        });
    }

    @Override
    public int getItemCount() {
        return flashcardSets.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView setNameText, setItemText, flashcardOwner, progressPercentageText, setReminderTextView;
        ProgressBar statsProgressBar;
        ImageView userProfileImage, privacyIcon;
        CardView cardView;
        int viewType;

        public ViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;

            setNameText = itemView.findViewById(R.id.set_name_text);
            setItemText = itemView.findViewById(R.id.set_item_text);
            progressPercentageText = itemView.findViewById(R.id.progress_percentage2);
            statsProgressBar = itemView.findViewById(R.id.stats_progressbar);
            privacyIcon = itemView.findViewById(R.id.privacy_icon);

            if (viewType == TYPE_QUIZ) {
                flashcardOwner = itemView.findViewById(R.id.quiz_owner);
                cardView = itemView.findViewById(R.id.cardView);
                userProfileImage = itemView.findViewById(R.id.quiz_user_profile);
            } else {
                flashcardOwner = itemView.findViewById(R.id.flashcard_owner);
                userProfileImage = itemView.findViewById(R.id.user_profile);
                privacyIcon = itemView.findViewById(R.id.privacy_icon);
                setReminderTextView = itemView.findViewById(R.id.set_reminder);
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
            List<FlashcardSet> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(flashcardSetsFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (FlashcardSet set : flashcardSetsFull) {
                    if (set.getTitle().toLowerCase().contains(filterPattern)
                            || (cachedUsernames.containsKey(set.getOwnerUid()) && cachedUsernames.get(set.getOwnerUid()).toLowerCase().contains(filterPattern))) {
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

    public void cleanupListeners() {
        for (com.google.firebase.firestore.ListenerRegistration registration : userListeners.values()) {
            registration.remove();
        }
        userListeners.clear();
        cachedUsernames.clear();
    }
}
