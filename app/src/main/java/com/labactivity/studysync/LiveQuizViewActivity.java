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

    private ImageView backButton;
    private TextView liveQuizTitleTxt;
    private Spinner spinnerTimePerQuestion;
    private Button startLiveQuizBtn, leaderboardsBtn;
    private ToggleButton toggleHideAnswers;
    private Button showQuestionsBtn;
    private RecyclerView recyclerView;

    private FirebaseFirestore db;
    private String roomId, quizId;

    private List<Map<String, Object>> questionList = new ArrayList<>();
    private LiveQuizQuestionAdapter adapter;

    private boolean isAnswersHidden = true; // Default to hidden

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_quiz_view);

        roomId = getIntent().getStringExtra("roomId");
        quizId = getIntent().getStringExtra("quizId");

        backButton = findViewById(R.id.backButton);
        liveQuizTitleTxt = findViewById(R.id.liveQuizTitleTxt);
        spinnerTimePerQuestion = findViewById(R.id.spinnerTimePerQuestion);
        startLiveQuizBtn = findViewById(R.id.startLiveQuizBtn);
        recyclerView = findViewById(R.id.recyclerView);
        toggleHideAnswers = findViewById(R.id.toggleHideAnswers);

        backButton.setOnClickListener(v -> finish());

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
                                    // Optional: Also write to a "start trigger" collection if needed by listener logic
                                    Intent intent = new Intent(this, ChatRoomActivity.class);
                                    intent.putExtra("roomId", roomId);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to start quiz", Toast.LENGTH_SHORT).show());
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to check quiz status", Toast.LENGTH_SHORT).show());
        });
    }

    private void setupRecycler() {
        adapter = new LiveQuizQuestionAdapter(questionList, isAnswersHidden);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }
}
