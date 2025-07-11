package com.labactivity.studysync;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
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
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LoadingSetActivity extends AppCompatActivity {
    private static final String TAG = "LoadingSetActivity";
    private final Executor executor = Executors.newSingleThreadExecutor();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading_set);


        Uri pdfUri = getIntent().getData();
        if (pdfUri == null) {
            Toast.makeText(this, "Invalid file", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try (InputStream inputStream = getContentResolver().openInputStream(pdfUri)) {
            if (inputStream != null) {
                byte[] pdfBytes = readBytes(inputStream);
                runGeminiModel(pdfBytes);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read the PDF", e);
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void runGeminiModel(byte[] pdfBytes) {
        GenerativeModel ai = FirebaseAI.getInstance(
                GenerativeBackend.vertexAI("global")
        ).generativeModel("gemini-2.5-flash");

        GenerativeModelFutures model = GenerativeModelFutures.from(ai);

        Content prompt = new Content.Builder()
                .addInlineData(pdfBytes, "application/pdf")
                .addText(
                        "From the contents of this PDF, generate a JSON object **only** in the following structure:\n" +
                                "{\n" +
                                "  \"title\": \"<A descriptive title>\",\n" +
                                "  \"terms\": [\n" +
                                "    { \"term\": \"<Term 1>\", \"definition\": \"<Definition 1>\" },\n" +
                                "    { \"term\": \"<Term 2>\", \"definition\": \"<Definition 2>\" },\n" +
                                "    ...\n" +
                                "  ]\n" +
                                "}\n" +
                                "Return only this JSON object — do not include any extra explanation, markdown, or preamble."
                )
                .build();

        Futures.addCallback(model.generateContent(prompt), new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String aiText = result.getText();
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
                    JSONArray termsArray = json.getJSONArray("terms");

                    Map<String, Map<String, String>> termsMap = new HashMap<>();
                    for (int i = 0; i < termsArray.length(); i++) {
                        JSONObject item = termsArray.getJSONObject(i);
                        Map<String, String> entry = new HashMap<>();
                        entry.put("term", item.optString("term", ""));
                        entry.put("definition", item.optString("definition", ""));
                        termsMap.put(String.valueOf(i), entry);
                    }

                    saveToFirestore(title, termsMap);
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse AI response", e);
                    showError("AI returned invalid format");
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini generation failed", t);
                showError("Failed to generate flashcards");
            }
        }, executor);
    }

    private void saveToFirestore(String title, Map<String, Map<String, String>> termsMap) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showError("User not authenticated");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, String> accessUsers = new HashMap<>();
        accessUsers.put(user.getUid(), "Owner");

        String createdAt = new SimpleDateFormat("MM/dd/yyyy | hh:mm a", Locale.getDefault()).format(new Date());

        Map<String, Object> doc = new HashMap<>();
        doc.put("accessUsers", accessUsers);
        doc.put("createdAt", createdAt);
        doc.put("owner_uid", user.getUid());
        doc.put("privacy", "private");
        doc.put("privacyRole", "edit");
        doc.put("number_of_items", termsMap.size());
        doc.put("title", title);
        doc.put("terms", termsMap);

        db.collection("flashcards")
                .add(doc)
                .addOnSuccessListener(ref -> {
                    Intent intent = new Intent(this, FlashcardPreviewActivity.class);
                    intent.putExtra("setId", ref.getId());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore save failed", e);
                    showError("Failed to save flashcards");
                });
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
}
