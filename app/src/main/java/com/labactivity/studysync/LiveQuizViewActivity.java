package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.labactivity.studysync.adapters.LiveQuizQuestionAdapter;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiveQuizViewActivity extends AppCompatActivity {

    private ImageView backButton, moreBtn;
    private TextView liveQuizTitleTxt;
    private Spinner spinnerTimePerQuestion;
    private Button startLiveQuizBtn;
    private ToggleButton toggleHideAnswers;
    private RecyclerView recyclerView;

    private FirebaseFirestore db;
    private String roomId, quizId;

    private List<Map<String, Object>> questionList = new ArrayList<>();
    private LiveQuizQuestionAdapter adapter;

    private boolean isAnswersHidden = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_quiz_view);

        roomId = getIntent().getStringExtra("roomId");
        quizId = getIntent().getStringExtra("quizId");

        moreBtn = findViewById(R.id.more_button);
        backButton = findViewById(R.id.backButton);
        liveQuizTitleTxt = findViewById(R.id.liveQuizTitleTxt);
        spinnerTimePerQuestion = findViewById(R.id.spinnerTimePerQuestion);
        startLiveQuizBtn = findViewById(R.id.startLiveQuizBtn);
        recyclerView = findViewById(R.id.recyclerView);
        toggleHideAnswers = findViewById(R.id.toggleHideAnswers);

        backButton.setOnClickListener(v -> finish());
        moreBtn.setOnClickListener(this::showPopupMenu);

        db = FirebaseFirestore.getInstance();

        db.collection("chat_rooms")
                .document(roomId)
                .collection("live_quiz")
                .document(quizId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        liveQuizTitleTxt.setText(documentSnapshot.getString("title"));
                        questionList = (List<Map<String, Object>>) documentSnapshot.get("questions");
                        setupRecycler();
                    }
                });

        spinnerTimePerQuestion.setSelection(2);


        toggleHideAnswers.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAnswersHidden = isChecked;
            if (adapter != null) adapter.setHideAnswers(isAnswersHidden);
        });

        startLiveQuizBtn.setOnClickListener(v -> {
            startLiveQuizBtn.setEnabled(false);
            String selected = spinnerTimePerQuestion.getSelectedItem().toString();
            int duration = selected.equals("1 minute") ? 60 : Integer.parseInt(selected.replace("s", ""));

            db.collection("chat_rooms")
                    .document(roomId)
                    .collection("live_quiz")
                    .document(quizId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("isStarted"))) {
                            Toast.makeText(this, "Quiz already started.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        startLiveQuizBtn.setEnabled(true);

                        Map<String, Object> updateData = new HashMap<>();
                        updateData.put("duration", duration);
                        updateData.put("isStarted", true);
                        updateData.put("startTime", Timestamp.now());

                        db.collection("chat_rooms")
                                .document(roomId)
                                .collection("live_quiz")
                                .document(quizId)
                                .update(updateData)
                                .addOnSuccessListener(unused -> {
                                    Intent intent = new Intent(this, ChatRoomActivity.class);
                                    intent.putExtra("roomId", roomId);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to start quiz", Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to check quiz status", Toast.LENGTH_SHORT).show());
            startLiveQuizBtn.setEnabled(true);
        });
    }
    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.live_quiz_view_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_live_quiz) {
                deleteLiveQuiz();
            }
            return false;
        });
        popup.show();
    }
    private void deleteLiveQuiz() {
        if (roomId == null || quizId == null) {
            Toast.makeText(this, "Error: Quiz or Room ID missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("chat_rooms")
                .document(roomId)
                .collection("live_quiz")
                .document(quizId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Live Quiz deleted successfully.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete Live Quiz: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void setupRecycler() {
        adapter = new LiveQuizQuestionAdapter(questionList, isAnswersHidden);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }
}
