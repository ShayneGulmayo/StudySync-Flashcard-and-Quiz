package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class QuizzesActivity extends AppCompatActivity {

    private ImageView addQuizButton;
    private ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quizzes);

        addQuizButton = findViewById(R.id.add_button);
        backButton = findViewById(R.id.back_button);

        // ✅ SETUP FIRESTORE QUIZ LIST DISPLAY
        RecyclerView recyclerView = findViewById(R.id.recycler_quizzes);
        List<DocumentSnapshot> quizList = new ArrayList<>();
        QuizAdapter adapter = new QuizAdapter(quizList, this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        FirebaseFirestore.getInstance().collection("quiz")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    quizList.addAll(queryDocumentSnapshots.getDocuments());
                    adapter.notifyDataSetChanged();
                });

        // ➕ Go to Create Quiz Activity
        addQuizButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(QuizzesActivity.this, CreateQuizActivity.class);
                startActivity(intent);
            }
        });

        // 🔙 Go back to Home
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Replace with an Intent to HomeActivity if needed
            }
        });
    }
}
