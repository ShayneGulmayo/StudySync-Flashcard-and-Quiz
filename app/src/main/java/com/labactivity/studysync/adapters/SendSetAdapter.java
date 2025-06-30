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

import java.util.List;

public class SendSetAdapter extends RecyclerView.Adapter<SendSetAdapter.SetViewHolder> {

    public interface OnSetClickListener {
        void onSetClick(Object item);
    }

    private final List<Object> sets;
    private final OnSetClickListener listener;
    private final FirebaseFirestore db;

    public SendSetAdapter(List<Object> sets, OnSetClickListener listener) {
        this.sets = sets;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public SetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_set, parent, false);
        return new SetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SetViewHolder holder, int position) {
        Object set = sets.get(position);

        if (set instanceof Flashcard) {
            Flashcard flashcard = (Flashcard) set;
            holder.setTitle.setText(flashcard.getTitle());

            db.collection("users").document(flashcard.getOwnerUid())
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        User user = snapshot.toObject(User.class);
                        if (user != null) {
                            String desc = "Flashcard set · " + flashcard.getNumberOfItems() + " terms · by " + user.getUsername();
                            holder.setDescription.setText(desc);
                            holder.cardView.setOnClickListener(v -> listener.onSetClick(flashcard));
                        }
                    });

        } else if (set instanceof Quiz) {
            Quiz quiz = (Quiz) set;
            holder.setTitle.setText(quiz.getTitle());

            db.collection("users").document(quiz.getOwner_uid())
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        User user = snapshot.toObject(User.class);
                        if (user != null) {
                            String desc = "Quiz set · " + quiz.getNumber_of_items() + " items · by " + user.getUsername();
                            holder.setDescription.setText(desc);
                            holder.cardView.setOnClickListener(v -> listener.onSetClick(quiz));
                        }
                    });
        }
    }

    @Override
    public int getItemCount() {
        return sets.size();
    }

    public static class SetViewHolder extends RecyclerView.ViewHolder {
        TextView setTitle, setDescription;
        CardView cardView;

        public SetViewHolder(@NonNull View itemView) {
            super(itemView);
            setTitle = itemView.findViewById(R.id.setTitle);
            setDescription = itemView.findViewById(R.id.description);
            cardView = (CardView) itemView;
        }
    }
}
