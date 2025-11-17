package com.labactivity.studysync;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class InputLiveQuizActivity extends AppCompatActivity {
    private ImageView backBtn, paste;
    private Button generateBtn;
    private EditText inputPrompt;
    private String roomId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_input_live_quiz);
        backBtn = findViewById(R.id.back_button);
        paste = findViewById(R.id.pasteBtn);
        inputPrompt = findViewById(R.id.promptEditTxt);
        generateBtn = findViewById(R.id.generateBtn);
        roomId = getIntent().getStringExtra("roomId");
        backBtn.setOnClickListener(v -> finish());

        generateBtn.setOnClickListener(view -> {
            String userInput = inputPrompt.getText().toString().trim();
            if (!userInput.isEmpty()) {
                Intent intent = new Intent(this, GeneratingLiveQuizActivity.class);
                intent.putExtra("prompt", userInput);
                intent.putExtra("roomId", roomId);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Input cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        paste.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) {
                ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    CharSequence pastedText = clipData.getItemAt(0).getText();
                    if (pastedText != null) {
                        inputPrompt.setText(pastedText.toString());
                        inputPrompt.setSelection(inputPrompt.getText().length());
                    } else {
                        Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "Nothing to paste", Toast.LENGTH_SHORT).show();
            }
        });

    }
}