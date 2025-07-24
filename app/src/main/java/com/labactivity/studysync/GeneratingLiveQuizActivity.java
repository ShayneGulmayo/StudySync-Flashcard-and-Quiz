package com.labactivity.studysync;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.firebase.Timestamp;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeneratingLiveQuizActivity extends AppCompatActivity {
    private static final String TAG = "GeneratingLiveQuiz";
    private final Executor executor = Executors.newSingleThreadExecutor();

    private FirebaseFirestore db;
    private FirebaseUser user;
    private String roomId;
    private String durationPerQuestion;
    private int duration = 30;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_generating_live_quiz);

        db = FirebaseFirestore.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showError("User not logged in.");
            return;
        }

        roomId = getIntent().getStringExtra("roomId");
        durationPerQuestion = getIntent().getStringExtra("secondsPerQuestion");
        String durationPerQuestion = getIntent().getStringExtra("secondsPerQuestion");

        if (durationPerQuestion != null) {
            try {
                duration = Integer.parseInt(durationPerQuestion);
            } catch (NumberFormatException e) {
                Log.e("GeneratingLiveQuiz", "Invalid duration format: " + durationPerQuestion, e);
            }
        }


        if (getIntent().hasExtra("prompt")) {
            String prompt = getIntent().getStringExtra("prompt");
            runGeminiTextPrompt(prompt);
        } else if (getIntent().hasExtra("setId") && getIntent().hasExtra("type")) {
            String setId = getIntent().getStringExtra("setId");
            String setType = getIntent().getStringExtra("type");
            loadSetData(setId, setType);
        } else {
            showError("Missing input data.");
        }
    }

    private void loadSetData(String setId, String setType) {
        String collection = setType.equals("flashcard") ? "flashcards" : "quiz";

        db.collection(collection)
                .document(setId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        showError("Set not found.");
                        return;
                    }

                    StringBuilder input = new StringBuilder();
                    input.append("Title: ").append(doc.getString("title")).append("\n");

                    if (setType.equals("quiz")) {
                        List<Map<String, Object>> questions = (List<Map<String, Object>>) doc.get("questions");
                        if (questions != null) {
                            for (Map<String, Object> q : questions) {
                                input.append("Q: ").append(q.get("question")).append("\n");
                                Object correct = q.get("correctAnswer");
                                if (correct instanceof List) {
                                    input.append("A: ").append(((List<?>) correct).get(0)).append("\n");
                                } else {
                                    input.append("A: ").append(correct).append("\n");
                                }
                            }
                        }
                    } else {
                        Map<String, Map<String, String>> terms = (Map<String, Map<String, String>>) doc.get("terms");
                        if (terms != null) {
                            for (Map<String, String> entry : terms.values()) {
                                input.append("Q: ").append(entry.get("term")).append("\n");
                                input.append("A: ").append(entry.get("definition")).append("\n");
                            }
                        }
                    }

                    runGeminiTextPrompt(input.toString());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading set", e);
                    showError("Failed to load set.");
                });
    }

    private void runGeminiTextPrompt(String inputText) {
        GenerativeModel ai = FirebaseAI.getInstance(
                GenerativeBackend.vertexAI("global")
        ).generativeModel("gemini-2.5-flash");

        GenerativeModelFutures model = GenerativeModelFutures.from(ai);

        String prompt = "From the following source content, generate a Firestore-compatible JSON object strictly in the format:\n" +
                "{\n" +
                "  \"title\": \"<Descriptive title>\",\n" +
                "  \"questions\": [\n" +
                "    {\n" +
                "      \"question\": \"<Short and clear quiz question>\",\n" +
                "      \"correctAnswer\": \"<One-line accurate answer>\",\n" +
                "      \"type\": \"text\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "Requirements:\n" +
                "- The number of questions generated must exactly match the number of entries (e.g., terms or facts) from the source.\n" +
                "- Ensure each question is meaningful and derived from its corresponding source entry.\n" +
                "- The correctAnswer must be accurate and match the intent of the source.\n" +
                "- Do not combine multiple facts into one question.\n" +
                "- Only include the JSON object as the output — no explanations, markdown, or commentary.\n" +
                "- The JSON must be Firestore-compatible and ready for direct insertion.";


        Content content = new Content.Builder()
                .addText(prompt + "\nSource:\n" + inputText)
                .build();

        Futures.addCallback(model.generateContent(content), new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                handleAIResponse(result.getText());
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini failed", t);
                showError("AI failed to generate quiz.");
            }
        }, executor);
    }

    private void handleAIResponse(String response) {
        try {
            if (response.startsWith("```")) {
                response = response.replaceAll("```json|```", "").trim();
            }

            JSONObject json = new JSONObject(response);
            String title = json.getString("title");
            JSONArray questionsArray = json.getJSONArray("questions");

            List<Map<String, Object>> questions = new ArrayList<>();
            for (int i = 0; i < questionsArray.length(); i++) {
                JSONObject q = questionsArray.getJSONObject(i);
                Map<String, Object> item = new HashMap<>();
                item.put("question", q.getString("question"));
                item.put("correctAnswer", q.getString("correctAnswer"));
                item.put("type", "text");
                questions.add(item);
            }

            saveToFirestore(title, questions);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse AI response", e);
            showError("AI returned invalid format");
        }
    }

    private void saveToFirestore(String title, List<Map<String, Object>> questions) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("questions", questions);
        data.put("created_by", user.getUid());
        data.put("created_at", Timestamp.now());
        data.put("duration", duration);

        db.collection("chat_rooms")
                .document(roomId)
                .collection("live_quiz")
                .add(data)
                .addOnSuccessListener(ref -> {
                    Intent intent = new Intent(this, ChatRoomActivity.class);
                    intent.putExtra("roomId", roomId);
                    intent.putExtra("startLiveQuizId", ref.getId());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> showError("Failed to save live quiz."));
    }

    private void showError(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        finish();
    }
}
