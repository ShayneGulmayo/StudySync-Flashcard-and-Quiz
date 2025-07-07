package com.labactivity.studysync.adapters;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.FlashcardPreviewActivity;
import com.labactivity.studysync.QuizPreviewActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.models.Quiz;
import com.labactivity.studysync.models.User;

import java.util.List;

public class SetCardAdapter extends RecyclerView.Adapter<SetCardAdapter.SetViewHolder> {

    private final Context context;
    private final List<Object> sets;
    private final FirebaseFirestore db;

    public SetCardAdapter(Context context, List<Object> sets) {
        this.context = context;
        this.sets = sets;
        this.db = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public SetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_set, parent, false);
        return new SetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SetViewHolder holder, int position) {

        Object item = sets.get(position);

        if (item instanceof Flashcard) {
            Flashcard flashcard = (Flashcard) item;
            holder.setTitle.setText(flashcard.getTitle());

            String base = "Flashcard set · " + flashcard.getNumberOfItems() + " terms";
            holder.setDescription.setText(base);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, FlashcardPreviewActivity.class);
                intent.putExtra("setId", flashcard.getId());
                context.startActivity(intent);
            });

            loadUsernameAndAppend(ownerUid(flashcard), base, holder);

        } else if (item instanceof Quiz) {
            Quiz quiz = (Quiz) item;
            holder.setTitle.setText(quiz.getTitle());

            String base = "Quiz set · " + quiz.getNumber_of_items() + " items";
            holder.setDescription.setText(base);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, QuizPreviewActivity.class);
                intent.putExtra("quizId", quiz.getQuizId());
                context.startActivity(intent);
            });

            loadUsernameAndAppend(ownerUid(quiz), base, holder);
        }
    }

    private String ownerUid(Object obj) {
        if (obj instanceof Flashcard) {
            return ((Flashcard) obj).getOwnerUid();
        } else if (obj instanceof Quiz) {
            return ((Quiz) obj).getOwner_uid();
        }
        return null;
    }

    private void loadUsernameAndAppend(String uid, String base, SetViewHolder holder) {
        if (uid == null || uid.isEmpty()) return;
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    User user = snapshot.toObject(User.class);
                    if (user != null && user.getUsername() != null) {
                        holder.setDescription.setText(base + " · by " + user.getUsername());
                    }
                });
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
            cardView = itemView.findViewById(R.id.card_view);
        }
    }
}
