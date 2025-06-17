package com.labactivity.studysync;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;

public class FlashcardSetAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnFlashcardClickListener {
        void onClick(FlashcardSet set);
    }

    private List<FlashcardSet> flashcardSets;
    private final OnFlashcardClickListener listener;
    private Context context;
    private FirebaseFirestore db;

    private HashMap<String, String> usernameCache;

    private String currentUserId;
    private String currentUsername;

    private static final int VIEW_TYPE_EMPTY = 0;
    private static final int VIEW_TYPE_CONTENT = 1;

    public FlashcardSetAdapter(Context context, List<FlashcardSet> flashcardSets, OnFlashcardClickListener listener) {
        this.context = context;
        this.flashcardSets = flashcardSets;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
        this.usernameCache = new HashMap<>();
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // fetch current user's username once
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUsername = documentSnapshot.getString("username");
                    } else {
                        currentUsername = "Unknown";
                    }
                })
                .addOnFailureListener(e -> currentUsername = "Unknown");
    }

    @Override
    public int getItemViewType(int position) {
        if (flashcardSets == null || flashcardSets.isEmpty()) {
            return VIEW_TYPE_EMPTY;
        }
        return VIEW_TYPE_CONTENT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_EMPTY) {
            View view = inflater.inflate(R.layout.no_item_flashcard_set, parent, false);
            return new EmptyViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_flashcard_set, parent, false);
            return new ContentViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ContentViewHolder) {
            FlashcardSet set = flashcardSets.get(position);
            ContentViewHolder contentHolder = (ContentViewHolder) holder;

            contentHolder.setName.setText(set.getName());

            // Set number of items (flashcards) in this set
            int itemCount = (set.getFlashcards() != null) ? set.getFlashcards().size() : 0;
            contentHolder.setItems.setText(itemCount + " items");

            // Owner username fetching logic
            if (set.getOwnerId().equals(currentUserId)) {
                contentHolder.ownerName.setText(currentUsername);
            } else if (usernameCache.containsKey(set.getOwnerId())) {
                contentHolder.ownerName.setText(usernameCache.get(set.getOwnerId()));
            } else {
                db.collection("users").document(set.getOwnerId())
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String username = documentSnapshot.getString("username");
                                if (username != null) {
                                    usernameCache.put(set.getOwnerId(), username);
                                    contentHolder.ownerName.setText(username);
                                } else {
                                    contentHolder.ownerName.setText("Unknown");
                                }
                            } else {
                                contentHolder.ownerName.setText("Unknown");
                            }
                        })
                        .addOnFailureListener(e -> contentHolder.ownerName.setText("Unknown"));
            }

            contentHolder.itemView.setOnClickListener(v -> listener.onClick(set));
        }
    }

    @Override
    public int getItemCount() {
        if (flashcardSets == null || flashcardSets.isEmpty()) {
            return 1; // show 1 empty view
        }
        return flashcardSets.size();
    }

    static class EmptyViewHolder extends RecyclerView.ViewHolder {
        EmptyViewHolder(View itemView) {
            super(itemView);
        }
    }

    static class ContentViewHolder extends RecyclerView.ViewHolder {
        TextView setName;
        TextView setItems;
        TextView ownerName;

        ContentViewHolder(View itemView) {
            super(itemView);
            setName = itemView.findViewById(R.id.set_name_text);
            setItems = itemView.findViewById(R.id.set_item_text);
            ownerName = itemView.findViewById(R.id.flashcard_owner);
        }
    }

    public void updateData(List<FlashcardSet> newFlashcardSets) {
        this.flashcardSets = newFlashcardSets;
        notifyDataSetChanged();
    }
}
