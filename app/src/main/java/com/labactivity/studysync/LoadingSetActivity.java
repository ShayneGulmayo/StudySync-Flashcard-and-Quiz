package com.labactivity.studysync;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LoadingSetActivity extends AppCompatActivity {
    private static final String TAG = "LoadingSetActivity";
    private final Executor executor = Executors.newSingleThreadExecutor();

    private String setType;
    private boolean flashToQuizQuestionIsDefinition;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading_set);

        setType = getIntent().getStringExtra("setType");
        String fileType = getIntent().getStringExtra("type");
        flashToQuizQuestionIsDefinition = getIntent().getBooleanExtra("flashToQuizQuestionIsDefinition", false);


        String convertFromId = getIntent().getStringExtra("convertFromId");
        String originalType = getIntent().getStringExtra("originalType");

        if (convertFromId != null && originalType != null) {
            setType = "quiz".equals(originalType) ? "flashcard" : "quiz";
            convertExistingSet(originalType, convertFromId);
            return;
        }

        if ("text".equals(fileType)) {
            String userPrompt = getIntent().getStringExtra("textPrompt");
            if (userPrompt == null || userPrompt.trim().isEmpty()) {
                showError("Text prompt is empty.");
                return;
            }
            runGeminiTextPrompt(userPrompt);
        } else {
            Uri fileUri = getIntent().getData();
            if (fileUri == null) {
                showError("Invalid file");
                return;
            }

            try (InputStream inputStream = getContentResolver().openInputStream(fileUri)) {
                if (inputStream != null) {
                    byte[] fileBytes = readBytes(inputStream);
                    String mimeType = (fileType != null) ?
                            (fileType.equals("image") ? "image/*" : "application/pdf") :
                            getContentResolver().getType(fileUri);

                    runGeminiModel(fileBytes, mimeType);
                } else {
                    showError("Failed to read file");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to open file", e);
                showError("Failed to read file");
            }
        }
    }
    public void convertExistingSet(String originalType, String setId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String collection = "flashcard".equals(originalType) ? "flashcards" : "quiz";

        db.collection(collection).document(setId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        showError("Original set not found.");
                        return;
                    }

                    StringBuilder inputBuilder = new StringBuilder();
                    inputBuilder.append("Title: ").append(doc.getString("title")).append("\n");

                    if ("quiz".equals(originalType)) {
                        List<Map<String, Object>> questions = (List<Map<String, Object>>) doc.get("questions");
                        if (questions != null) {
                            for (Map<String, Object> q : questions) {
                                inputBuilder.append("Q: ").append(q.get("question")).append("\n");
                                Object correct = q.get("correctAnswer");
                                if (correct instanceof List) {
                                    List<String> correctAnswers = (List<String>) correct;
                                    if (!correctAnswers.isEmpty()) {
                                        inputBuilder.append("A: ").append(correctAnswers.get(0)).append("\n");
                                    }
                                } else if (correct instanceof String) {
                                    inputBuilder.append("A: ").append(correct).append("\n");
                                }
                            }
                        }
                    } else {
                        Map<String, Map<String, String>> terms = (Map<String, Map<String, String>>) doc.get("terms");
                        if (terms != null) {
                            for (Map<String, String> entry : terms.values()) {
                                String term = entry.get("term");
                                String definition = entry.get("definition");
                                if (flashToQuizQuestionIsDefinition) {
                                    inputBuilder.append("Q: ").append(definition).append("\n");
                                    inputBuilder.append("A: ").append(term).append("\n");
                                } else {
                                    inputBuilder.append("Q: ").append(term).append("\n");
                                    inputBuilder.append("A: ").append(definition).append("\n");
                                }
                            }
                            inputBuilder.append("\nConvert the above flashcards into multiple choice quiz questions. ");
                            inputBuilder.append("Use the ").append(flashToQuizQuestionIsDefinition ? "definition" : "term").append(" as the question. ");
                            inputBuilder.append("Each question must have exactly 4 options: the correct answer and 3 contextually related but incorrect distractors. ");
                            inputBuilder.append("Use type: \"multiple choice\" only. ");
                            inputBuilder.append("Return only JSON in this format:\n").append(getConvertQuizPrompt("flashcard"));
                        }
                    }

                    runGeminiTextPrompt(inputBuilder.toString());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching original set", e);
                    showError("Failed to fetch original set.");
                });
    }



    private void runGeminiModel(byte[] data, String mimeType) {
        GenerativeModel ai = FirebaseAI.getInstance(
                GenerativeBackend.vertexAI("global")
        ).generativeModel("gemini-2.5-flash");

        GenerativeModelFutures model = GenerativeModelFutures.from(ai);

        String mediaDescription = mimeType != null && mimeType.startsWith("image") ? "image" : "PDF";
        String promptText = setType != null && setType.equals("quiz")
                ? getQuizPrompt(mediaDescription)
                : getFlashcardPrompt(mediaDescription);

        Content prompt = new Content.Builder()
                .addInlineData(data, mimeType)
                .addText(promptText)
                .build();

        Futures.addCallback(model.generateContent(prompt), new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                handleAIResponse(result.getText());
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini generation failed", t);
                showError("Failed to generate content");
            }
        }, executor);
    }

    private void runGeminiTextPrompt(String inputText) {
        GenerativeModel ai = FirebaseAI.getInstance(
                GenerativeBackend.vertexAI("global")
        ).generativeModel("gemini-2.5-flash");

        GenerativeModelFutures model = GenerativeModelFutures.from(ai);

        String promptText = setType != null && setType.equals("quiz")
                ? getQuizPrompt("text") + "\nInput:\n" + inputText
                : getFlashcardPrompt("text") + "\nInput:\n" + inputText;

        Content prompt = new Content.Builder()
                .addText(promptText)
                .build();

        Futures.addCallback(model.generateContent(prompt), new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                handleAIResponse(result.getText());
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini text generation failed", t);
                showError("Failed to generate from text prompt");
            }
        }, executor);
    }

    private void handleAIResponse(String aiText) {
        if (aiText == null || aiText.trim().isEmpty()) {
            showError("AI returned empty content");
            return;
        }

        if (aiText.startsWith("```")) {
            aiText = aiText.replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();
        }

        try {
            JSONObject json = new JSONObject(aiText);
            String title = json.optString("title", "Untitled Set");

            if ("quiz".equals(setType)) {
                JSONArray questionsArray = json.getJSONArray("questions");
                List<Map<String, Object>> questions = new ArrayList<>();
                for (int i = 0; i < questionsArray.length(); i++) {
                    JSONObject q = questionsArray.getJSONObject(i);
                    Map<String, Object> item = new HashMap<>();
                    item.put("question", q.optString("question", ""));
                    item.put("type", q.optString("type", "multiple choice"));

                    JSONArray choicesJson = q.optJSONArray("choices");
                    if (choicesJson != null) {
                        List<String> choices = new ArrayList<>();
                        for (int j = 0; j < choicesJson.length(); j++) {
                            choices.add(choicesJson.getString(j));
                        }
                        item.put("choices", choices);
                    }
                    Object correct = q.get("correctAnswer");
                    if (correct instanceof JSONArray) {
                        List<String> correctAnswers = new ArrayList<>();
                        JSONArray correctJson = (JSONArray) correct;
                        for (int j = 0; j < correctJson.length(); j++) {
                            correctAnswers.add(correctJson.getString(j));
                        }
                        item.put("correctAnswer", correctAnswers);
                    } else {
                        item.put("correctAnswer", correct.toString());
                    }

                    questions.add(item);
                }

                saveQuizToFirestore(title, questions);
            } else {
                JSONArray termsArray = json.getJSONArray("terms");
                Map<String, Map<String, String>> termsMap = new HashMap<>();
                for (int i = 0; i < termsArray.length(); i++) {
                    JSONObject item = termsArray.getJSONObject(i);
                    Map<String, String> entry = new HashMap<>();
                    entry.put("term", item.optString("term", ""));
                    entry.put("definition", item.optString("definition", ""));
                    termsMap.put(String.valueOf(i), entry);
                }

                saveFlashcardsToFirestore(title, termsMap);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse AI response", e);
            showError("AI returned invalid format");
        }
    }

    private void saveFlashcardsToFirestore(String title, Map<String, Map<String, String>> termsMap) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showError("User not authenticated");
            return;
        }

        Map<String, String> accessUsers = new HashMap<>();
        accessUsers.put(user.getUid(), "Owner");

        Map<String, Object> doc = new HashMap<>();
        doc.put("accessUsers", accessUsers);
        doc.put("owner_uid", user.getUid());
        doc.put("createdAt", getCurrentTime());
        doc.put("privacy", "private");
        doc.put("privacyRole", "edit");
        doc.put("title", title);
        doc.put("terms", termsMap);
        doc.put("number_of_items", termsMap.size());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("flashcards")
                .add(doc)
                .addOnSuccessListener(ref -> {
                    Map<String, Object> ownedSetEntry = new HashMap<>();
                    ownedSetEntry.put("id", ref.getId());
                    ownedSetEntry.put("type", "flashcard");
                    ownedSetEntry.put("lastAccessed", System.currentTimeMillis());

                    db.collection("users").document(user.getUid())
                            .update("owned_sets", FieldValue.arrayUnion(ownedSetEntry))
                            .addOnSuccessListener(aVoid -> Log.d("SaveSet", "Flashcard added to owned_sets"))
                            .addOnFailureListener(e -> Log.w("SaveSet", "Failed to update owned_sets", e));

                    Intent intent = new Intent(this, FlashcardPreviewActivity.class);
                    intent.putExtra("setId", ref.getId());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> showError("Failed to save flashcards"));
    }


    private void saveQuizToFirestore(String title, List<Map<String, Object>> questions) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showError("User not authenticated");
            return;
        }

        Map<String, String> accessUsers = new HashMap<>();
        accessUsers.put(user.getUid(), "Owner");

        Map<String, Object> doc = new HashMap<>();
        doc.put("accessUsers", accessUsers);
        doc.put("owner_uid", user.getUid());
        doc.put("created_at", new Date());
        doc.put("privacy", "private");
        doc.put("privacyRole", "edit");
        doc.put("title", title);
        doc.put("questions", questions);
        doc.put("number_of_items", questions.size());
        doc.put("progress", 0);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("quiz")
                .add(doc)
                .addOnSuccessListener(ref -> {
                    Map<String, Object> ownedSetEntry = new HashMap<>();
                    ownedSetEntry.put("id", ref.getId());
                    ownedSetEntry.put("type", "quiz");
                    ownedSetEntry.put("lastAccessed", System.currentTimeMillis());

                    db.collection("users").document(user.getUid())
                            .update("owned_sets", FieldValue.arrayUnion(ownedSetEntry))
                            .addOnSuccessListener(aVoid -> Log.d("SaveSet", "Quiz added to owned_sets"))
                            .addOnFailureListener(e -> Log.w("SaveSet", "Failed to update owned_sets", e));

                    Intent intent = new Intent(this, QuizPreviewActivity.class);
                    intent.putExtra("quizId", ref.getId());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> showError("Failed to save quiz"));
    }


    private String getCurrentTime() {
        return new SimpleDateFormat("MM/dd/yyyy | hh:mm a", Locale.getDefault()).format(new Date());
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private byte[] readBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
    private String getConvertQuizPrompt(String source) {
        return "From the contents of this " + source + ", generate a Firestore-compatible JSON object ONLY in the following structure:\n" +
                "{\n" +
                "  \"title\": \"<Descriptive title>\",\n" +
                "  \"questions\": [\n" +
                "    {\n" +
                "      \"question\": \"<Question text>\",\n" +
                "      \"type\": \"multiple choice\",\n" +
                "      \"choices\": [\"Option 1\", \"Option 2\", \"Option 3\", \"Option 4\"],\n" +
                "      \"correctAnswer\": \"Correct Answer\",\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "Important rules:\n" +
                "- Use ONLY \"multiple choice\" questions. DO NOT use enumeration.\n" +
                "- The `choices` array must contain 4 plausible but incorrect answers, except for one correct one.\n" +
                "- The `correctAnswer` string must exactly match one of the values in the `choices` array.\n" +
                "- `photoPath` must always be set to \"Add Image\".\n" +
                "- If there's no image, set `photoUrl` to an empty string.\n" +
                "- Return only the JSON object with no explanation or extra commentary.";
    }



    private String getFlashcardPrompt(String source) {
        return "From the contents of this " + source + ", generate a JSON object ONLY in the following structure:\n" +
                "{\n" +
                "  \"title\": \"<A descriptive title>\",\n" +
                "  \"terms\": [\n" +
                "    { \"term\": \"<Term 1>\", \"definition\": \"<Definition 1>\" },\n" +
                "    { \"term\": \"<Term 2>\", \"definition\": \"<Definition 2>\" }\n" +
                "  ]\n" +
                "}\nReturn only this JSON object. No extra text.";
    }

    private String getQuizPrompt(String source) {
        return "From the contents of this " + source + ", generate a JSON object ONLY in the following structure:\n" +
                "{\n" +
                "  \"title\": \"<Descriptive title>\",\n" +
                "  \"questions\": [\n" +
                "    {\n" +
                "      \"question\": \"<Question text>\",\n" +
                "      \"type\": \"multiple choice\" or \"enumeration\",\n" +
                "      \"choices\": [\"Option 1\", \"Option 2\", ...],\n" +
                "      \"correctAnswer\": \"Correct Answer\" or [\"Answer1\", \"Answer2\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}\nReturn only this JSON object. No explanation or markdown.";
    }
}
