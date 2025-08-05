package com.labactivity.studysync;

import android.content.Intent;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.*;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.storage.FirebaseStorage;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

public class CreateQuizActivity extends AppCompatActivity {

    private TextView roleTxt, privacyTxt;
    private EditText quizTitleInput;
    private ImageView backButton, checkButton, privacyIcon;
    private FloatingActionButton addQuizButton;

    private ConstraintLayout privacyContainer;
    private LinearLayout quizContainer;
    private View currentQuizItemView;

    private String ownerUid, currentUid, ownerUsername, currentUsersName;
    private String quizId = null;
    private int questionCount = 0;
    private final int MAX_QUESTIONS = 50;
    private boolean isPublic = true;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private ActivityResultLauncher<Intent> pickQuizImageLauncher;
    private Map<View, Uri> pendingUploads = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_quiz);

        quizId = getIntent().getStringExtra("quizId");
        Log.d("CreateQuizActivity", "onCreate called, quizId: " + quizId);

        TextView headerText = findViewById(R.id.txtView_add_quiz);
        quizContainer = findViewById(R.id.container_add_quiz);
        addQuizButton = findViewById(R.id.floating_add_btn);
        backButton = findViewById(R.id.back_button);
        checkButton = findViewById(R.id.save_button);
        quizTitleInput = findViewById(R.id.quiz_name);
        privacyContainer = findViewById(R.id.privacy_container);
        privacyTxt = findViewById(R.id.privacy_text);
        privacyIcon = findViewById(R.id.icon_privacy);
        roleTxt = findViewById(R.id.role_text);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUid = auth.getCurrentUser().getUid();

        if (quizId != null) {
            headerText.setText("Edit Quiz");
        } else {
            headerText.setText("Add Quiz");
        }

        db.collection("users")
                .document(currentUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUsersName = documentSnapshot.getString("username");
                    }
                });

        if (quizId != null) {
            loadQuizData(quizId);
            db.collection("quiz").document(quizId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Boolean publicStatus = documentSnapshot.getBoolean("isPublic");
                            isPublic = (publicStatus != null) ? publicStatus : false;

                            if (isPublic) {
                                privacyTxt.setText("Public");
                                Glide.with(this).load(R.drawable.public_icon).into(privacyIcon);
                            } else {
                                privacyTxt.setText("Private");
                                Glide.with(this).load(R.drawable.lock).into(privacyIcon);
                            }

                            if (isPublic) {
                                String privacyRole = documentSnapshot.getString("privacyRole");

                                if (privacyRole != null && !privacyRole.trim().isEmpty()) {
                                    roleTxt.setText(privacyRole);
                                } else {
                                    roleTxt.setText("");
                                }
                            } else {
                                roleTxt.setText("");
                            }

                            ownerUid = documentSnapshot.getString("owner_uid");
                            if (!currentUid.equals(ownerUid)) {
                                privacyContainer.setVisibility(View.GONE);
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e("CreateQuizActivity", "Failed to fetch privacy/role info", e));
        } else {
            ownerUid = currentUid;
            ownerUsername = currentUsersName;
            addQuizView();
        }

        addQuizButton.setOnClickListener(v -> {
            if (questionCount >= MAX_QUESTIONS) {
                Toast.makeText(this, "Maximum of 50 questions reached", Toast.LENGTH_SHORT).show();
            } else {
                addQuizView();
            }
        });

        backButton.setOnClickListener(v -> {
            if (questionCount > 0) {
                showExitConfirmation();
            } else {
                finish();
            }
        });

        checkButton.setOnClickListener(v -> {
            if (quizTitleInput.getText().toString().trim().isEmpty()) {
                quizTitleInput.setError("Quiz title is required");
                quizTitleInput.requestFocus();
                return;
            }

            if (validateAllQuestions()) {
                Toast.makeText(this, "Quiz saved successfully!", Toast.LENGTH_SHORT).show();
                saveQuizToFirebase();
                finish();
            }
        });

        roleTxt.setOnClickListener(v -> {
            if (isPublic) {
                androidx.appcompat.widget.PopupMenu roleMenu = new androidx.appcompat.widget.PopupMenu(this, roleTxt);
                roleMenu.getMenu().add("View");
                roleMenu.getMenu().add("Edit");

                roleMenu.setOnMenuItemClickListener(item -> {
                    roleTxt.setText(item.getTitle());
                    return true;
                });

                roleMenu.show();
            }
        });

        View.OnClickListener privacyClickListener = v -> {
            androidx.appcompat.widget.PopupMenu privacyMenu = new androidx.appcompat.widget.PopupMenu(this, privacyTxt);

            if (isPublic) {
                privacyMenu.getMenu().add("Private");
            } else {
                privacyMenu.getMenu().add("Public");
            }

            privacyMenu.setOnMenuItemClickListener(item -> {
                String selected = item.getTitle().toString();

                if (selected.equals("Private")) {
                    isPublic = false;
                    privacyTxt.setText("Private");
                    roleTxt.setText("");
                    Glide.with(this).load(R.drawable.lock).into(privacyIcon);
                } else {
                    isPublic = true;
                    privacyTxt.setText("Public");
                    if (roleTxt.getText().toString().trim().isEmpty()) {
                        roleTxt.setText("View");
                    }
                    Glide.with(this).load(R.drawable.public_icon).into(privacyIcon);
                }
                return true;
            });
            privacyMenu.show();
        };

        privacyTxt.setOnClickListener(privacyClickListener);
        privacyIcon.setOnClickListener(privacyClickListener);

        pickQuizImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri sourceUri = result.getData().getData();

                        CropImageOptions cropOptions = new CropImageOptions();
                        cropOptions.aspectRatioX = 1;
                        cropOptions.aspectRatioY = 1;
                        cropOptions.fixAspectRatio = true;
                        cropOptions.maxCropResultWidth = 512;
                        cropOptions.maxCropResultHeight = 512;

                        CropImageContractOptions options = new CropImageContractOptions(sourceUri, cropOptions);
                        cropImageLauncher.launch(options);
                    }
                }
        );
    }

    private void saveQuizToFirebase() {
        List<Map<String, Object>> questionList = new ArrayList<>();

        for (int i = 0; i < quizContainer.getChildCount(); i++) {
            View quizItem = quizContainer.getChildAt(i);
            Spinner spinner = quizItem.findViewById(R.id.quiz_type_spinner);
            LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);
            EditText questionInput = quizItem.findViewById(R.id.quiz_question_input);
            String quizType = spinner.getSelectedItem().toString().toLowerCase();

            Map<String, Object> questionData = new HashMap<>();
            questionData.put("question", questionInput.getText().toString().trim());
            questionData.put("quizType", quizType);

            ImageView imageView = quizItem.findViewById(R.id.add_image_button);
            String photoUrl = null;
            String photoPath = null;

            Object tagObj = imageView.getTag();
            Object descObj = imageView.getContentDescription();

            if (tagObj != null && tagObj.toString().startsWith("http")) {
                photoUrl = tagObj.toString();
            }
            if (descObj != null && !descObj.toString().equals("pending")) {
                photoPath = descObj.toString();
            }

            if (photoUrl != null) {
                questionData.put("photoUrl", photoUrl);
            }
            if (photoPath != null) {
                questionData.put("photoPath", photoPath);
            }

            if (quizType.equals("multiple choice")) {
                List<String> choices = new ArrayList<>();
                String correctAnswer = "";
                for (int j = 0; j < optionsContainer.getChildCount(); j++) {
                    View option = optionsContainer.getChildAt(j);
                    RadioButton rb = option.findViewById(R.id.radioOption);
                    EditText et = option.findViewById(R.id.edit_option_text);
                    String text = et.getText().toString().trim();
                    choices.add(text);
                    if (rb != null && rb.isChecked()) {
                        correctAnswer = text;
                    }
                }
                questionData.put("choices", choices);
                questionData.put("correctAnswer", correctAnswer);

            } else if (quizType.equals("enumeration")) {
                List<String> answers = new ArrayList<>();
                for (int j = 0; j < optionsContainer.getChildCount(); j++) {
                    View answer = optionsContainer.getChildAt(j);
                    EditText answerInput = answer.findViewById(R.id.edit_option_text);
                    answers.add(answerInput.getText().toString().trim());
                }
                questionData.put("choices", answers);
                questionData.put("correctAnswer", answers);
            }

            questionList.add(questionData);
        }

        Map<String, Object> quizData = new HashMap<>();

        if (quizId == null) {
            ownerUid = auth.getCurrentUser().getUid();
            quizData.put("owner_uid", ownerUid);
            quizData.put("owner_username", ownerUsername);
            quizData.put("created_at", Timestamp.now());
        }

        quizData.put("title", quizTitleInput.getText().toString().trim());
        quizData.put("number_of_items", questionCount);
        quizData.put("questions", questionList);
        Map<String, Object> accessUsers = new HashMap<>();
        accessUsers.put(auth.getCurrentUser().getUid(), "Owner");
        quizData.put("accessUsers", accessUsers);

        if (isPublic) {
            quizData.put("privacy", "Public");
            String role = roleTxt.getText().toString().trim().toLowerCase();
            quizData.put("privacyRole", role.isEmpty() ? "view" : role);
        } else {
            quizData.put("privacy", "Private");
            quizData.put("privacyRole", null);
        }

        DocumentReference quizRef;
        if (quizId == null) {
            quizRef = db.collection("quiz").document();
            quizId = quizRef.getId();
        } else {
            quizRef = db.collection("quiz").document(quizId);
        }

        quizRef.get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                if (quizId != null) {
                    quizData.put("owner_uid", doc.getString("owner_uid"));
                    quizData.put("owner_username", doc.getString("owner_username"));
                }

                Map<String, Object> existingAccess = (Map<String, Object>) doc.get("accessUsers");
                if (existingAccess != null) {
                    quizData.put("accessUsers", existingAccess);
                }

                quizRef.set(quizData, SetOptions.merge())
                        .addOnSuccessListener(unused -> {
                            resetQuizAttemptsPercentage(quizId);
                            resetOwnedSetProgressForUsers(quizId);
                            Toast.makeText(this, "Quiz updated successfully!", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to update quiz", Toast.LENGTH_SHORT).show());
            }
            else {
                quizRef.set(quizData)
                        .addOnSuccessListener(unused -> {
                            for (Map.Entry<View, Uri> entry : pendingUploads.entrySet()) {
                                uploadQuizImage(entry.getValue(), ownerUid, quizId, entry.getKey());
                            }
                            pendingUploads.clear();

                            Map<String, Object> ownedSet = new HashMap<>();
                            ownedSet.put("id", quizId);
                            ownedSet.put("type", "quiz");

                            DocumentReference userRef = db.collection("users").document(ownerUid);
                            userRef.update("owned_sets", FieldValue.arrayUnion(ownedSet))
                                    .addOnSuccessListener(userUpdate -> {
                                        Toast.makeText(this, "Quiz saved successfully!", Toast.LENGTH_SHORT).show();
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Map<String, Object> initData = new HashMap<>();
                                        List<Map<String, Object>> newList = new ArrayList<>();
                                        newList.add(ownedSet);
                                        initData.put("owned_sets", newList);

                                        userRef.set(initData, SetOptions.merge())
                                                .addOnSuccessListener(mergeSuccess -> {
                                                    Toast.makeText(this, "Quiz saved and owned set initialized!", Toast.LENGTH_SHORT).show();
                                                    finish();
                                                })
                                                .addOnFailureListener(err -> {
                                                    Toast.makeText(this, "Quiz saved but failed to register as owned", Toast.LENGTH_LONG).show();
                                                    finish();
                                                });
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Failed to save quiz", Toast.LENGTH_SHORT).show();
                        });
            }
        });
    }

    private void resetQuizAttemptsPercentage(String quizId) {
        CollectionReference attemptsRef = db.collection("quiz").document(quizId).collection("quiz_attempt");

        attemptsRef.get().addOnSuccessListener(querySnapshot -> {
            WriteBatch batch = db.batch();

            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                DocumentReference attemptRef = doc.getReference();
                batch.update(attemptRef, "percentage", 0);
            }

            batch.commit().addOnSuccessListener(aVoid ->
                    Log.d("ProgressReset", "Quiz attempt percentages set to 0%")
            ).addOnFailureListener(e ->
                    Log.e("ProgressReset", "Failed to update quiz attempts", e)
            );
        });
    }

    private void resetOwnedSetProgressForUsers(String quizId) {
        db.collection("users").get().addOnSuccessListener(usersSnapshot -> {
            WriteBatch batch = db.batch();

            for (DocumentSnapshot userDoc : usersSnapshot.getDocuments()) {
                List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) userDoc.get("owned_sets");

                if (ownedSets == null) continue;

                boolean updated = false;

                for (Map<String, Object> set : ownedSets) {
                    if (quizId.equals(set.get("id"))) {
                        set.put("progress", 0);
                        updated = true;
                    }
                }

                if (updated) {
                    batch.update(userDoc.getReference(), "owned_sets", ownedSets);
                }
            }

            batch.commit().addOnSuccessListener(aVoid ->
                    Log.d("ProgressReset", "Owned sets progress set to 0% for all users")
            ).addOnFailureListener(e ->
                    Log.e("ProgressReset", "Failed to update owned_sets", e)
            );
        });
    }

    private void showExitConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Leave without saving?")
                .setMessage("Are you sure you want to leave? Any unsaved changes will be lost.")
                .setPositiveButton("Yes", (dialog, which) -> finish())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void addQuizView() {
        View quizItem = LayoutInflater.from(this).inflate(R.layout.item_add_quiz, null);
        questionCount++;

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 24);
        quizItem.setLayoutParams(params);

        Spinner quizTypeSpinner = quizItem.findViewById(R.id.quiz_type_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.quiz_types,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        quizTypeSpinner.setAdapter(adapter);

        TextView addOptionText = quizItem.findViewById(R.id.add_option_text);
        LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);
        EditText questionInput = quizItem.findViewById(R.id.quiz_question_input);

        setupAddOptionListener(addOptionText, quizTypeSpinner, optionsContainer);

        ImageButton deleteBtn = quizItem.findViewById(R.id.delete_question_button);
        deleteBtn.setOnClickListener(v -> {
            quizContainer.removeView(quizItem);
            questionCount--;
        });

        ImageView addImageButton = quizItem.findViewById(R.id.add_image_button);
        addImageButton.setOnClickListener(v -> {
            currentQuizItemView = quizItem;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Image"), 1010);

        });

        quizTypeSpinner.setOnItemSelectedListener(getQuizTypeChangeListener(addOptionText, optionsContainer));

        quizTypeSpinner.post(() -> {
            int defaultPosition = 0;
            quizTypeSpinner.setSelection(defaultPosition);
            getQuizTypeChangeListener(addOptionText, optionsContainer)
                    .onItemSelected(quizTypeSpinner, null, defaultPosition, 0);
        });

        quizContainer.addView(quizItem);
    }

    private void addOptionView(LinearLayout container) {
        View optionView = LayoutInflater.from(this).inflate(R.layout.item_add_quiz_options, null);
        RadioButton radioButton = optionView.findViewById(R.id.radioOption);
        ImageButton deleteOption = optionView.findViewById(R.id.delete_option);

        radioButton.setOnClickListener(v -> {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                RadioButton rb = child.findViewById(R.id.radioOption);
                if (rb != null && rb != radioButton) {
                    rb.setChecked(false);
                }
            }
        });

        deleteOption.setOnClickListener(v -> {
            container.removeView(optionView);
            if (container.getChildCount() < 2) {
                Toast.makeText(this, "A question must have at least 2 options", Toast.LENGTH_SHORT).show();
            }
        });

        container.addView(optionView);
    }

    private boolean validateAllQuestions() {
        int quizCount = quizContainer.getChildCount();

        if (quizCount == 0) {
            Toast.makeText(this, "You need at least one question", Toast.LENGTH_SHORT).show();
            return false;
        }

        for (int i = 0; i < quizCount; i++) {
            View quizItem = quizContainer.getChildAt(i);
            Spinner spinner = quizItem.findViewById(R.id.quiz_type_spinner);
            LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);
            EditText questionInput = quizItem.findViewById(R.id.quiz_question_input);
            String quizType = spinner.getSelectedItem().toString().toLowerCase();

            if (questionInput.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Each question must have text", Toast.LENGTH_SHORT).show();
                return false;
            }

            if (quizType.equals("multiple choice")) {
                if (optionsContainer.getChildCount() < 2) {
                    Toast.makeText(this, "Each multiple choice question must have at least 2 options", Toast.LENGTH_SHORT).show();
                    return false;
                }

                boolean hasSelected = false;

                for (int j = 0; j < optionsContainer.getChildCount(); j++) {
                    View option = optionsContainer.getChildAt(j);
                    RadioButton rb = option.findViewById(R.id.radioOption);
                    EditText et = option.findViewById(R.id.edit_option_text);
                    if (et == null || et.getText().toString().trim().isEmpty()) {
                        Toast.makeText(this, "All multiple choice options must be filled", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    if (rb != null && rb.isChecked()) {
                        hasSelected = true;
                    }
                }

                if (!hasSelected) {
                    Toast.makeText(this, "All questions must have a selected correct answer", Toast.LENGTH_SHORT).show();
                    return false;
                }
            } else if (quizType.equals("enumeration")) {
                Set<String> uniqueAnswers = new HashSet<>();
                boolean hasInput = false;

                for (int j = 0; j < optionsContainer.getChildCount(); j++) {
                    View answer = optionsContainer.getChildAt(j);
                    EditText answerInput = answer.findViewById(R.id.edit_option_text);
                    if (answerInput == null) continue;

                    String input = answerInput.getText().toString().trim();

                    if (input.isEmpty()) {
                        Toast.makeText(this, "All enumeration answers must be filled", Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    if (!uniqueAnswers.add(input.toLowerCase())) {
                        Toast.makeText(this, "Duplicate enumeration answers are not allowed", Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    hasInput = true;
                }

                if (!hasInput) {
                    Toast.makeText(this, "Enumeration questions must have at least one answer with input", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        }
        return true;
    }

    private void renumberEnumerationInputs(LinearLayout container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            TextView numberLabel = child.findViewById(R.id.enumeration_number);
            if (numberLabel != null) {
                numberLabel.setText(String.valueOf(i + 1));
            }
        }
    }

    private void loadQuizData(String quizId) {
        db.collection("quiz").document(quizId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        quizTitleInput.setText(documentSnapshot.getString("title"));

                        ownerUid = documentSnapshot.getString("owner_uid");
                        ownerUsername = documentSnapshot.getString("owner_username");

                        List<Map<String, Object>> questions = (List<Map<String, Object>>) documentSnapshot.get("questions");
                        if (questions != null) {
                            for (Map<String, Object> question : questions) {
                                addQuizViewFromData(question);
                            }
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load quiz data", Toast.LENGTH_SHORT).show()
                );
    }

    private void addQuizViewFromData(Map<String, Object> questionData) {
        View quizItem = LayoutInflater.from(this).inflate(R.layout.item_add_quiz, null);
        questionCount++;

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 24);
        quizItem.setLayoutParams(params);

        Spinner quizTypeSpinner = quizItem.findViewById(R.id.quiz_type_spinner);
        EditText questionInput = quizItem.findViewById(R.id.quiz_question_input);
        LinearLayout optionsContainer = quizItem.findViewById(R.id.answer_choices_container);
        TextView addOptionText = quizItem.findViewById(R.id.add_option_text);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.quiz_types,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        quizTypeSpinner.setAdapter(adapter);

        String questionText = (String) questionData.get("question");
        questionInput.setText(questionText);

        List<String> choices = new ArrayList<>();
        Object rawChoices = questionData.get("choices");
        if (rawChoices instanceof List<?>) {
            for (Object obj : (List<?>) rawChoices) {
                if (obj instanceof String) {
                    choices.add((String) obj);
                }
            }
        }

        Object quizTypeObj = questionData.get("quizType");
        String quizType = (quizTypeObj != null) ? quizTypeObj.toString().toLowerCase() : "multiple choice";

        Object correctAnswerObj = questionData.get("correctAnswer");

        final String correctAnswerString;
        final List<String> correctAnswerList;

        if (correctAnswerObj instanceof String) {
            correctAnswerString = (String) correctAnswerObj;
            correctAnswerList = null;
        } else if (correctAnswerObj instanceof List) {
            correctAnswerList = (List<String>) correctAnswerObj;
            correctAnswerString = null;
        } else {
            correctAnswerString = null;
            correctAnswerList = null;
        }

        final AdapterView.OnItemSelectedListener[] typeChangeListenerHolder = new AdapterView.OnItemSelectedListener[1];

        setupAddOptionListener(addOptionText, quizTypeSpinner, optionsContainer);

        AdapterView.OnItemSelectedListener typeChangeListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                String selectedQuizType = adapterView.getItemAtPosition(pos).toString().toLowerCase();
                addOptionText.setText(selectedQuizType.equals("enumeration") ? "Add answer" : "Add option");
                optionsContainer.removeAllViews();

                if (selectedQuizType.equals("multiple choice")) {
                    for (String choice : choices) {
                        View optionView = LayoutInflater.from(CreateQuizActivity.this).inflate(R.layout.item_add_quiz_options, null);
                        RadioButton rb = optionView.findViewById(R.id.radioOption);
                        EditText et = optionView.findViewById(R.id.edit_option_text);
                        ImageButton deleteOption = optionView.findViewById(R.id.delete_option);

                        et.setText(choice);
                        rb.setChecked(choice.equals(correctAnswerString));

                        rb.setOnClickListener(v -> {
                            for (int i = 0; i < optionsContainer.getChildCount(); i++) {
                                View child = optionsContainer.getChildAt(i);
                                RadioButton other = child.findViewById(R.id.radioOption);
                                if (other != rb) other.setChecked(false);
                            }
                        });

                        deleteOption.setOnClickListener(v -> optionsContainer.removeView(optionView));

                        optionsContainer.addView(optionView);
                    }
                } else {
                    for (String choice : choices) {
                        View answerView = LayoutInflater.from(CreateQuizActivity.this).inflate(R.layout.item_add_quiz_enumerations, null);
                        EditText et = answerView.findViewById(R.id.edit_option_text);
                        TextView numberLabel = answerView.findViewById(R.id.enumeration_number);
                        ImageButton deleteAnswer = answerView.findViewById(R.id.delete_option);

                        et.setText(choice);
                        numberLabel.setText(String.valueOf(optionsContainer.getChildCount() + 1));
                        deleteAnswer.setOnClickListener(v -> {
                            optionsContainer.removeView(answerView);
                            renumberEnumerationInputs(optionsContainer);
                        });

                        optionsContainer.addView(answerView);
                    }
                    renumberEnumerationInputs(optionsContainer);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };

        typeChangeListenerHolder[0] = typeChangeListener;
        quizTypeSpinner.setOnItemSelectedListener(typeChangeListener);

        String displayType;
        switch (quizType) {
            case "multiple choice":
                displayType = "Multiple Choice";
                break;
            case "enumeration":
                displayType = "Enumeration";
                break;
            default:
                Toast.makeText(this, "Unknown quiz type: " + quizType, Toast.LENGTH_SHORT).show();
                displayType = "Multiple Choice";
                break;
        }

        int spinnerPosition = adapter.getPosition(displayType);
        quizTypeSpinner.setSelection(spinnerPosition);

        ImageButton deleteBtn = quizItem.findViewById(R.id.delete_question_button);
        deleteBtn.setOnClickListener(v -> {
            quizContainer.removeView(quizItem);
            questionCount--;
        });

        ImageView addImageButton = quizItem.findViewById(R.id.add_image_button);
        addImageButton.setOnClickListener(v -> {
            currentQuizItemView = quizItem;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Image"), 1010);
        });

        Object photoUrlObj = questionData.get("photoUrl");
        if (photoUrlObj instanceof String) {
            String photoUrl = (String) photoUrlObj;
            Glide.with(this).load(photoUrl).into(addImageButton);
            addImageButton.setTag(photoUrl);
        }

        quizContainer.addView(quizItem);
    }

    private void setupAddOptionListener(TextView addOptionText, Spinner quizTypeSpinner, LinearLayout optionsContainer) {
        addOptionText.setOnClickListener(v -> {
            String quizType = quizTypeSpinner.getSelectedItem().toString().toLowerCase();
            int currentCount = optionsContainer.getChildCount();

            if (quizType.equals("multiple choice")) {
                if (currentCount >= 4) {
                    Toast.makeText(CreateQuizActivity.this, "Maximum of 4 options allowed", Toast.LENGTH_SHORT).show();
                    return;
                }

                View optionView = LayoutInflater.from(CreateQuizActivity.this).inflate(R.layout.item_add_quiz_options, null);
                RadioButton rb = optionView.findViewById(R.id.radioOption);
                EditText et = optionView.findViewById(R.id.edit_option_text);
                ImageButton deleteOption = optionView.findViewById(R.id.delete_option);

                rb.setOnClickListener(btn -> {
                    for (int i = 0; i < optionsContainer.getChildCount(); i++) {
                        View child = optionsContainer.getChildAt(i);
                        RadioButton other = child.findViewById(R.id.radioOption);
                        if (other != rb) other.setChecked(false);
                    }
                });

                deleteOption.setOnClickListener(btn -> optionsContainer.removeView(optionView));
                optionsContainer.addView(optionView);

            } else if (quizType.equals("enumeration")) {
                if (currentCount >= 15) {
                    Toast.makeText(CreateQuizActivity.this, "Maximum of 15 answers allowed", Toast.LENGTH_SHORT).show();
                    return;
                }

                View answerView = LayoutInflater.from(CreateQuizActivity.this).inflate(R.layout.item_add_quiz_enumerations, null);
                EditText et = answerView.findViewById(R.id.edit_option_text);
                TextView numberLabel = answerView.findViewById(R.id.enumeration_number);
                ImageButton deleteAnswer = answerView.findViewById(R.id.delete_option);

                numberLabel.setText(String.valueOf(currentCount + 1));
                deleteAnswer.setOnClickListener(btn -> {
                    optionsContainer.removeView(answerView);
                    renumberEnumerationInputs(optionsContainer);
                });

                optionsContainer.addView(answerView);
            }
        });
    }

    private AdapterView.OnItemSelectedListener getQuizTypeChangeListener(
            TextView addOptionText,
            LinearLayout optionsContainer
    ) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                Object item = adapterView.getItemAtPosition(pos);
                if (item == null) return;

                String quizType = item.toString().toLowerCase();
                addOptionText.setText(quizType.equals("enumeration") ? "Add answer" : "Add option");
                optionsContainer.removeAllViews();

                if (quizType.equals("multiple choice")) {
                    for (int i = 0; i < 2; i++) {
                        addOptionView(optionsContainer);
                    }
                } else {
                    View answerView = LayoutInflater.from(CreateQuizActivity.this)
                            .inflate(R.layout.item_add_quiz_enumerations, null);
                    optionsContainer.addView(answerView);
                    renumberEnumerationInputs(optionsContainer);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        };
    }

    private void uploadQuizImage(Uri imageUri, String ownerUid, String quizId, View quizItemView) {
        if (imageUri == null || quizItemView == null) return;

        String fileName = "quiz_" + UUID.randomUUID().toString() + ".jpg";
        String storagePath = "quiz_images/" + ownerUid + "/" + quizId + "/" + fileName;

        FirebaseStorage.getInstance()
                .getReference(storagePath)
                .putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    taskSnapshot.getStorage().getDownloadUrl().addOnSuccessListener(downloadUrl -> {
                        ImageView imageView = quizItemView.findViewById(R.id.add_image_button);
                        if (imageView != null && !isFinishing() && !isDestroyed()) {
                            Glide.with(CreateQuizActivity.this).load(downloadUrl).into(imageView);
                            imageView.setTag(downloadUrl.toString());
                            imageView.setContentDescription(fileName);
                        }

                        updateQuestionImageInFirestore(quizItemView, downloadUrl.toString(), fileName);
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to get image URL", Toast.LENGTH_SHORT).show();
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateQuestionImageInFirestore(View quizItemView, String photoUrl, String photoPath) {
        EditText questionInput = quizItemView.findViewById(R.id.quiz_question_input);
        String questionText = questionInput.getText().toString().trim();

        db.collection("quiz").document(quizId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        List<Map<String, Object>> questions = (List<Map<String, Object>>) doc.get("questions");
                        if (questions != null) {
                            for (Map<String, Object> question : questions) {
                                if (questionText.equals(question.get("question"))) {
                                    question.put("photoUrl", photoUrl);
                                    question.put("photoPath", photoPath);
                                    break;
                                }
                            }

                            db.collection("quiz").document(quizId)
                                    .update("questions", questions)
                                    .addOnSuccessListener(aVoid -> Log.d("FIRESTORE", "Photo info saved"))
                                    .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to update photo info"));
                        }
                    }
                });
    }

    public static File getFileFromUri(Context context, Uri uri) throws Exception {
        ContentResolver contentResolver = context.getContentResolver();
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri));
        if (extension == null) extension = "jpg";

        File tempFile = File.createTempFile("upload", "." + extension, context.getCacheDir());

        try (InputStream inputStream = contentResolver.openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        return tempFile;
    }

    private final ActivityResultLauncher<CropImageContractOptions> cropImageLauncher =
            registerForActivityResult(new CropImageContract(), result -> {
                if (result.isSuccessful() && result.getUriContent() != null && currentQuizItemView != null) {
                    Uri croppedUri = result.getUriContent();

                    if (quizId == null) {
                        pendingUploads.put(currentQuizItemView, croppedUri);
                        Toast.makeText(this, "Image ready to upload after quiz is saved", Toast.LENGTH_SHORT).show();
                    } else {
                        uploadQuizImage(croppedUri, ownerUid, quizId, currentQuizItemView);
                    }
                }

            });

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1010 && resultCode == RESULT_OK && data != null) {
            Uri sourceUri = data.getData();
            Uri destinationUri = Uri.fromFile(new File(getCacheDir(), "cropped_" + System.currentTimeMillis() + ".jpg"));

            Uri safeUri = copyToCacheAndGetUri(sourceUri);

            UCrop.Options options = new UCrop.Options();
            options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
            options.setCompressionQuality(90);
            options.withAspectRatio(1, 1);

            UCrop.of(safeUri, destinationUri)
                    .withOptions(options)
                    .withMaxResultSize(512, 512)
                    .start(this);
        }

        if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK && data != null) {
            Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                if (currentQuizItemView == null) {
                    Toast.makeText(this, "Could not link image to question view", Toast.LENGTH_SHORT).show();
                    return;
                }
                ImageView imageView = currentQuizItemView.findViewById(R.id.add_image_button);
                Glide.with(this).load(resultUri).into(imageView);
                imageView.setTag(resultUri.toString());
                imageView.setContentDescription("pending");

                if (quizId == null) {
                    pendingUploads.put(currentQuizItemView, resultUri);
                    Toast.makeText(this, "Image ready to upload after quiz is saved", Toast.LENGTH_SHORT).show();
                } else {
                    uploadQuizImage(resultUri, ownerUid, quizId, currentQuizItemView);
                }
            }
        }
    }

    private Uri copyToCacheAndGetUri(Uri sourceUri) {
        try {
            File cacheDir = getCacheDir();
            File tempFile = new File(cacheDir, "temp_crop_" + System.currentTimeMillis() + ".jpg");
            InputStream in = getContentResolver().openInputStream(sourceUri);
            OutputStream out = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
            return FileProvider.getUriForFile(this, getPackageName() + ".provider", tempFile);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (questionCount > 0) {
            showExitConfirmation();
        } else {
            super.onBackPressed();
        }
    }
}