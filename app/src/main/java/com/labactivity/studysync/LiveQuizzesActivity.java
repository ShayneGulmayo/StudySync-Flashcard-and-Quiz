package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.Query;
import com.labactivity.studysync.adapters.LiveQuizAdapter;
import com.labactivity.studysync.models.LiveQuiz;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class LiveQuizzesActivity extends AppCompatActivity {
    private String roomId;
    private ImageView backBtn;
    private RecyclerView recyclerView;
    private LiveQuizAdapter adapter;
    private ArrayList<LiveQuiz> quizList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_live_quizzes);

        roomId = getIntent().getStringExtra("roomId");
        backBtn = findViewById(R.id.backButton);
        recyclerView = findViewById(R.id.recyclerView);

        quizList = new ArrayList<>();
        adapter = new LiveQuizAdapter(this, quizList, roomId);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        backBtn.setOnClickListener(v -> finish());

        loadLiveQuizzes();
    }

    private void loadLiveQuizzes() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference quizRef = db.collection("chat_rooms")
                .document(roomId)
                .collection("live_quiz");

        quizRef.orderBy("created_at", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    quizList.clear();
                    querySnapshot.forEach(doc -> {
                        String id = doc.getId();
                        String title = doc.getString("title");
                        ArrayList<?> questions = (ArrayList<?>) doc.get("questions");
                        int questionCount = (questions != null) ? questions.size() : 0;

                        quizList.add(new LiveQuiz(id, title, questionCount));
                    });
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load quizzes", Toast.LENGTH_SHORT).show();
                });
    }

}
