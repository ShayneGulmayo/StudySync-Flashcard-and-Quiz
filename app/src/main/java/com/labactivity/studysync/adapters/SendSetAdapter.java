package com.labactivity.studysync.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.models.Quiz;
import com.labactivity.studysync.models.User;

import java.util.ArrayList;
import java.util.List;

public class SendSetAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnSetClickListener {
        void onSetClick(Object item);
    }

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_EMPTY = 1;

    private final List<Object> originalList;
    private final List<Object> filteredList;
    private final OnSetClickListener listener;
    private final FirebaseFirestore db;

    public SendSetAdapter(List<Object> sets, OnSetClickListener listener) {
        this.originalList = new ArrayList<>(sets);
        this.filteredList = new ArrayList<>(sets);
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    @Override
    public int getItemCount() {
        return filteredList.isEmpty() ? 1 : filteredList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return filteredList.isEmpty() ? VIEW_TYPE_EMPTY : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_EMPTY) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_no_sets, parent, false);
            return new EmptyViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_set, parent, false);
            return new SetViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SetViewHolder) {
            Object item = filteredList.get(position);
            SetViewHolder setHolder = (SetViewHolder) holder;

            if (item instanceof Flashcard) {
                Flashcard flashcard = (Flashcard) item;
                setHolder.setTitle.setText(flashcard.getTitle());

                String desc = "Flashcard set · " + flashcard.getNumber_Of_Items() + " terms";
                setHolder.setDescription.setText(desc);
                setHolder.cardView.setOnClickListener(v -> listener.onSetClick(flashcard));

                String ownerUid = flashcard.getOwnerUid();
                if (ownerUid != null && !ownerUid.isEmpty()) {
                    db.collection("users").document(ownerUid)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                User user = snapshot.toObject(User.class);
                                if (user != null && user.getUsername() != null) {
                                    String updated = desc + " · by " + user.getUsername();
                                    setHolder.setDescription.setText(updated);
                                }
                            });
                }

            } else if (item instanceof Quiz) {
                Quiz quiz = (Quiz) item;
                setHolder.setTitle.setText(quiz.getTitle());

                String desc = "Quiz set · " + quiz.getNumber_of_items() + " items";
                setHolder.setDescription.setText(desc);
                setHolder.cardView.setOnClickListener(v -> listener.onSetClick(quiz));

                String ownerUid = quiz.getOwner_uid();
                if (ownerUid != null && !ownerUid.isEmpty()) {
                    db.collection("users").document(ownerUid)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                User user = snapshot.toObject(User.class);
                                if (user != null && user.getUsername() != null) {
                                    String updated = desc + " · by " + user.getUsername();
                                    setHolder.setDescription.setText(updated);
                                }
                            });
                }
            }
        }
    }

    public void filter(String query) {
        String lowerQuery = query.toLowerCase().trim();
        filteredList.clear();

        if (lowerQuery.isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            for (Object item : originalList) {
                String title = "";
                if (item instanceof Flashcard) {
                    title = ((Flashcard) item).getTitle();
                } else if (item instanceof Quiz) {
                    title = ((Quiz) item).getTitle();
                }

                if (title.toLowerCase().contains(lowerQuery)) {
                    filteredList.add(item);
                }
            }
        }

        notifyDataSetChanged();
    }

    public static class SetViewHolder extends RecyclerView.ViewHolder {
        TextView setTitle, setDescription;
        CardView cardView;

        public SetViewHolder(@NonNull View itemView) {
            super(itemView);
            setTitle = itemView.findViewById(R.id.setTitle);
            setDescription = itemView.findViewById(R.id.description);
            cardView = itemView.findViewById(R.id.card_view);
        }
    }

    public static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
