package com.labactivity.studysync.adapters;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStructure;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import com.labactivity.studysync.FlashcardPreviewActivity;
import com.labactivity.studysync.ImageViewerActivity;
import com.labactivity.studysync.QuizPreviewActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.UserProfileActivity;
import com.labactivity.studysync.models.ChatMessage;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.models.Quiz;
import com.labactivity.studysync.models.User;

import java.text.DecimalFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;






public class ChatMessageAdapter extends FirestoreRecyclerAdapter<ChatMessage, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_CURRENT_USER = 1;
    private static final int VIEW_TYPE_OTHER_USER = 2;
    private static final int VIEW_TYPE_SYSTEM_MESSAGE = 3;
    private static final int VIEW_TYPE_SHARED_SET = 4;
    private static final int VIEW_TYPE_FILE_CURRENT_USER = 5;
    private static final int VIEW_TYPE_FILE_OTHER_USER = 6;
    private static final int VIEW_TYPE_SHARED_SET_CURRENT_USER = 7;


    private final String currentUserId;

    public ChatMessageAdapter(@NonNull FirestoreRecyclerOptions<ChatMessage> options, String currentUserId) {
        super(options);
        this.currentUserId = currentUserId;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = getItem(position);
        if ("system".equals(message.getType())) return VIEW_TYPE_SYSTEM_MESSAGE;
        if ("set".equals(message.getType())) {
            return message.getSenderId().equals(currentUserId) ? VIEW_TYPE_SHARED_SET_CURRENT_USER : VIEW_TYPE_SHARED_SET;
        }
        if ("file".equals(message.getType())) {
            return message.getSenderId().equals(currentUserId) ? VIEW_TYPE_FILE_CURRENT_USER : VIEW_TYPE_FILE_OTHER_USER;
        }
        return message.getSenderId().equals(currentUserId) ? VIEW_TYPE_CURRENT_USER : VIEW_TYPE_OTHER_USER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_CURRENT_USER:
                return new CurrentUserViewHolder(inflater.inflate(R.layout.item_chat_message_current_user, parent, false));
            case VIEW_TYPE_OTHER_USER:
                return new OtherUserViewHolder(inflater.inflate(R.layout.item_chat_message_other_user, parent, false));
            case VIEW_TYPE_SYSTEM_MESSAGE:
                return new SystemMessageViewHolder(inflater.inflate(R.layout.item_system_message, parent, false));
            case VIEW_TYPE_SHARED_SET:
                return new SharedSetViewHolder(inflater.inflate(R.layout.item_shared_set_other_user, parent, false));
            case VIEW_TYPE_SHARED_SET_CURRENT_USER:
                return new SharedSetCurrentUserViewHolder(inflater.inflate(R.layout.item_shared_set_current_user, parent, false));
            case VIEW_TYPE_FILE_CURRENT_USER:
                return new FileCurrentUserViewHolder(inflater.inflate(R.layout.item_file_message_current_user, parent, false));
            case VIEW_TYPE_FILE_OTHER_USER:
                return new FileOtherUserViewHolder(inflater.inflate(R.layout.item_file_message_other_user, parent, false));
            default:
                throw new IllegalArgumentException("Invalid view type");
        }
    }

    @Override
    protected void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull ChatMessage message) {

        Date previousTimestamp = null;
        if (position > 0) {
            previousTimestamp = getItem(position - 1).getTimestamp();
        }
        Context context = holder.itemView.getContext();
        String separatorText = formatTimeSeparator(context, message.getTimestamp(), previousTimestamp);

        TextView timeSeparatorView = null;

        if (holder instanceof CurrentUserViewHolder) {
            ((CurrentUserViewHolder) holder).bind(message);
            timeSeparatorView = ((CurrentUserViewHolder) holder).timeSeparator;
        } else if (holder instanceof OtherUserViewHolder) {
            boolean isSameSender = position > 0 && getItem(position - 1).getSenderId().equals(message.getSenderId());
            ((OtherUserViewHolder) holder).bind(message, isSameSender);
            timeSeparatorView = ((OtherUserViewHolder) holder).timeSeparator;
        } else if (holder instanceof SystemMessageViewHolder) {
            ((SystemMessageViewHolder) holder).bind(message);
        } else if (holder instanceof SharedSetViewHolder) {
            ((SharedSetViewHolder) holder).bind(message);
            timeSeparatorView = ((SharedSetViewHolder) holder).timeSeparator;
        } else if (holder instanceof SharedSetCurrentUserViewHolder) {
            ((SharedSetCurrentUserViewHolder) holder).bind(message);
            timeSeparatorView = ((SharedSetCurrentUserViewHolder) holder).timeSeparator;
        } else if (holder instanceof FileCurrentUserViewHolder) {
            ((FileCurrentUserViewHolder) holder).bind(message);
            timeSeparatorView = ((FileCurrentUserViewHolder) holder).timeSeparator;
        } else if (holder instanceof FileOtherUserViewHolder) {
            ((FileOtherUserViewHolder) holder).bind(message);
            timeSeparatorView = ((FileOtherUserViewHolder) holder).timeSeparator;
        }
        if (timeSeparatorView != null) {
            if (separatorText != null) {
                timeSeparatorView.setText(separatorText);
                timeSeparatorView.setVisibility(View.VISIBLE);
            } else {
                timeSeparatorView.setVisibility(View.GONE);
            }
        }
    }

    public class SharedSetViewHolder extends RecyclerView.ViewHolder {
        TextView senderName, sharedTitle, sharedType, sharedDescription, timestampText, timeSeparator;
        ImageView senderImage, saveSetBtn, savedIndicator;
        Button btnViewSet;

        public SharedSetViewHolder(@NonNull View itemView) {
            super(itemView);
            senderName = itemView.findViewById(R.id.senderName);
            sharedTitle = itemView.findViewById(R.id.sharedTitle);
            sharedType = itemView.findViewById(R.id.sharedType);
            sharedDescription = itemView.findViewById(R.id.sharedDescription);
            timestampText = itemView.findViewById(R.id.timestampText);
            senderImage = itemView.findViewById(R.id.senderImage);
            saveSetBtn = itemView.findViewById(R.id.saveQuizBtn);
            savedIndicator = itemView.findViewById(R.id.savedIndicator);
            btnViewSet = itemView.findViewById(R.id.btnViewSet);
            timeSeparator = itemView.findViewById(R.id.timeSeparatorText);

            itemView.setOnClickListener(v -> {
                timestampText.setVisibility(timestampText.getVisibility() == VISIBLE ? GONE : VISIBLE);
            });
        }

        public void bind(ChatMessage message) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            FirebaseAuth auth = FirebaseAuth.getInstance();
            String currentUserId = auth.getCurrentUser().getUid();

            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));

            final String setId = message.getSetId();
            final String setType = message.getSetType();

            if (setId == null || setId.trim().isEmpty() || setType == null || setType.trim().isEmpty()) {
                sharedTitle.setText("Shared Set Unavailable");
                sharedType.setText("Error: Missing Data");
                sharedDescription.setText("This set link is corrupted and cannot be loaded.");
                btnViewSet.setVisibility(View.GONE);
                saveSetBtn.setVisibility(View.GONE);
                savedIndicator.setVisibility(View.GONE);
                return;
            }

            db.collection("users").document(message.getSenderId()).get().addOnSuccessListener(userSnap -> {
                User user = userSnap.toObject(User.class);
                if (user != null) {
                    senderName.setText(user.getFullName());
                    Glide.with(itemView.getContext())
                            .load(user.getPhotoUrl())
                            .placeholder(R.drawable.user_profile)
                            .circleCrop()
                            .into(senderImage);

                    senderImage.setOnClickListener(view ->{
                        Intent intent = new Intent(itemView.getContext(), UserProfileActivity.class);
                        intent.putExtra("userId", message.getSenderId());
                        itemView.getContext().startActivity(intent);
                    });
                }
            });

            if ("flashcard".equals(message.getSetType())) {
                db.collection("flashcards").document(message.getSetId()).get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Flashcard flashcard = snapshot.toObject(Flashcard.class);
                        sharedTitle.setText(flashcard.getTitle());
                        sharedType.setText("Flashcard Set");

                        db.collection("users").document(flashcard.getOwnerUid()).get().addOnSuccessListener(ownerDoc -> {
                            User owner = ownerDoc.toObject(User.class);
                            String desc = flashcard.getNumber_Of_Items() + " terms" +
                                    (owner != null ? " · by " + owner.getUsername() : "");
                            sharedDescription.setText(desc);
                        }).addOnFailureListener(e -> {
                            sharedDescription.setText(flashcard.getNumber_Of_Items() + " terms");
                        });

                    } else {
                        sharedTitle.setText("Flashcard Set Unavailable");
                        sharedType.setText("Flashcard Set");
                        sharedDescription.setText("This flashcard set has been deleted.");
                        btnViewSet.setVisibility(View.GONE);
                        saveSetBtn.setVisibility(View.GONE);
                        savedIndicator.setVisibility(View.GONE);
                    }
                });
            } else if ("quiz".equals(message.getSetType())) {
                db.collection("quiz").document(message.getSetId()).get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Quiz quiz = snapshot.toObject(Quiz.class);
                        sharedTitle.setText(quiz.getTitle());
                        sharedType.setText("Quiz Set");

                        db.collection("users").document(quiz.getOwner_uid()).get().addOnSuccessListener(ownerDoc -> {
                            User owner = ownerDoc.toObject(User.class);
                            String desc = quiz.getNumber_of_items() + " items" +
                                    (owner != null ? " · by " + owner.getUsername() : "");
                            sharedDescription.setText(desc);
                        }).addOnFailureListener(e -> {
                            sharedDescription.setText(quiz.getNumber_of_items() + " items");
                        });

                    } else {
                        sharedTitle.setText("Quiz Set Unavailable");
                        sharedType.setText("Quiz Set");
                        sharedDescription.setText("This quiz set has been deleted.");
                        btnViewSet.setVisibility(View.GONE);
                        saveSetBtn.setVisibility(View.GONE);
                        savedIndicator.setVisibility(View.GONE);
                    }
                });
            }

            db.collection("users").document(currentUserId).get().addOnSuccessListener(userDoc -> {
                AtomicBoolean isSaved = new AtomicBoolean(false);
                boolean isOwned = false;

                List<Map<String, Object>> savedSets = (List<Map<String, Object>>) userDoc.get("saved_sets");
                List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) userDoc.get("owned_sets");

                if (savedSets != null) {
                    for (Map<String, Object> set : savedSets) {
                        if (message.getSetId().equals(set.get("id")) && message.getSetType().equals(set.get("type"))) {
                            isSaved.set(true);
                            break;
                        }
                    }
                }

                if (ownedSets != null) {
                    for (Map<String, Object> set : ownedSets) {
                        if (message.getSetId().equals(set.get("id")) && message.getSetType().equals(set.get("type"))) {
                            isOwned = true;
                            break;
                        }
                    }
                }

                if (isOwned) {
                    updateBookmarkIcon(true);
                    saveSetBtn.setEnabled(false);
                    saveSetBtn.setClickable(false);
                    saveSetBtn.setVisibility(GONE);
                    savedIndicator.setVisibility(VISIBLE);
                } else {
                    updateBookmarkIcon(isSaved.get());
                    savedIndicator.setVisibility(isSaved.get() ? VISIBLE : GONE);

                    saveSetBtn.setOnClickListener(v -> {
                        Map<String, Object> setData = new HashMap<>();
                        setData.put("id", message.getSetId());
                        setData.put("type", message.getSetType());

                        if (isSaved.get()) {
                            db.collection("users").document(currentUserId)
                                    .update("saved_sets", FieldValue.arrayRemove(setData))
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(itemView.getContext(), "Set unsaved", Toast.LENGTH_SHORT).show();
                                        isSaved.set(false);
                                        updateBookmarkIcon(false);
                                        savedIndicator.setVisibility(GONE);
                                    });
                        } else {
                            db.collection("users").document(currentUserId)
                                    .update("saved_sets", FieldValue.arrayUnion(setData))
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(itemView.getContext(), "Set saved!", Toast.LENGTH_SHORT).show();
                                        isSaved.set(true);
                                        updateBookmarkIcon(true);
                                        savedIndicator.setVisibility(VISIBLE);
                                    });
                        }
                    });
                }
            });

            btnViewSet.setOnClickListener(v -> {
                Intent intent;
                if ("flashcard".equals(message.getSetType())) {
                    intent = new Intent(itemView.getContext(), FlashcardPreviewActivity.class);
                    intent.putExtra("setId", message.getSetId());
                } else {
                    intent = new Intent(itemView.getContext(), QuizPreviewActivity.class);
                    intent.putExtra("quizId", message.getSetId());
                }
                itemView.getContext().startActivity(intent);
            });
        }

        private void updateBookmarkIcon(boolean isSaved) {
            if (isSaved) {
                saveSetBtn.setImageResource(R.drawable.bookmark_filled);
                saveSetBtn.setColorFilter(itemView.getContext().getResources().getColor(R.color.primary));
            } else {
                saveSetBtn.setImageResource(R.drawable.bookmark);
                saveSetBtn.setColorFilter(itemView.getContext().getResources().getColor(R.color.primary));
            }
        }
    }

    static class FileCurrentUserViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, fileDetails, timestampText, timeSeparator;
        ImageView saveFileButton;

        public FileCurrentUserViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            fileDetails = itemView.findViewById(R.id.fileDetails);
            timestampText = itemView.findViewById(R.id.timestampText);
            saveFileButton = itemView.findViewById(R.id.saveFileButton);
            timeSeparator = itemView.findViewById(R.id.timeSeparatorText);
        }

        public void bind(ChatMessage message) {
            final String fileUrl = message.getFileUrl();
            final String fileType = message.getFileType();

            if (fileUrl == null || fileUrl.trim().isEmpty() || fileType == null || message.getFileName() == null) {
                fileName.setText("File Data Missing");
                fileDetails.setText("Error: Cannot load file information.");
                saveFileButton.setVisibility(View.GONE);
                itemView.setOnClickListener(null); // Disable click
                return; // Stop execution
            }
            fileName.setText(message.getFileName());
            fileDetails.setText(message.getFileType().toUpperCase() + " · " + readableFileSize(message.getFileSize()));
            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));

            itemView.setOnClickListener(view -> {
                timestampText.setVisibility(timestampText.getVisibility() == VISIBLE ? GONE : VISIBLE);
            });
            saveFileButton.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(message.getFileUrl()));
                itemView.getContext().startActivity(intent);
            });
        }
    }

    static class FileOtherUserViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, fileDetails, timestampText, senderName, timeSeparator;
        ImageView saveFileButton, senderImage;

        public FileOtherUserViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            fileDetails = itemView.findViewById(R.id.fileDetails);
            timestampText = itemView.findViewById(R.id.timestampText);
            saveFileButton = itemView.findViewById(R.id.saveFileButton);
            senderName = itemView.findViewById(R.id.senderName);
            senderImage = itemView.findViewById(R.id.senderImage);
            timeSeparator = itemView.findViewById(R.id.timeSeparatorText);
        }

        public void bind(ChatMessage message) {

            final String fileUrl = message.getFileUrl();
            String fileType = message.getFileType();
            if (fileType == null){
                fileType = "Unknown File Type";
            }

            if (fileUrl == null || fileUrl.trim().isEmpty() || fileType == null || message.getFileName() == null) {
                fileName.setText("File Data Missing");
                fileDetails.setText("Error: Cannot load file information.");
                saveFileButton.setVisibility(View.GONE);
                itemView.setOnClickListener(null);
                return;
            }
            fileName.setText(message.getFileName());
            fileDetails.setText(message.getFileType().toUpperCase() + " · " + readableFileSize(message.getFileSize()));
            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));



            senderName.setText(message.getSenderName());
            Glide.with(itemView.getContext())
                    .load(message.getSenderPhotoUrl())
                    .placeholder(R.drawable.user_profile)
                    .circleCrop()
                    .into(senderImage);
            itemView.setOnClickListener(view -> {
                timestampText.setVisibility(timestampText.getVisibility() == VISIBLE ? GONE : VISIBLE);
            });

            senderImage.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), UserProfileActivity.class);
                intent.putExtra("userId", message.getSenderId());
                itemView.getContext().startActivity(intent);
            });


            saveFileButton.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(message.getFileUrl()));
                itemView.getContext().startActivity(intent);
            });
        }
    }

    static class SharedSetCurrentUserViewHolder extends RecyclerView.ViewHolder {
        TextView sharedTitle, sharedType, sharedDescription, timestampText, timeSeparator;
        Button btnViewSet;
        ImageView saveSetBtn, savedIndicator;

        public SharedSetCurrentUserViewHolder(@NonNull View itemView) {
            super(itemView);
            sharedTitle = itemView.findViewById(R.id.sharedTitle);
            sharedType = itemView.findViewById(R.id.sharedType);
            sharedDescription = itemView.findViewById(R.id.sharedDescription);
            timestampText = itemView.findViewById(R.id.timestampText);
            btnViewSet = itemView.findViewById(R.id.btnViewSet);
            saveSetBtn = itemView.findViewById(R.id.saveQuizBtn);
            savedIndicator = itemView.findViewById(R.id.savedIndicator);
            timeSeparator = itemView.findViewById(R.id.timeSeparatorText);

            itemView.setOnClickListener(v -> {
                timestampText.setVisibility(timestampText.getVisibility() == VISIBLE ? GONE : VISIBLE);
            });
        }

        public void bind(ChatMessage message) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            FirebaseAuth auth = FirebaseAuth.getInstance();
            String currentUserId = auth.getCurrentUser().getUid();

            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));

            String setId = message.getSetId();
            String setType = message.getSetType();

            if (setId == null || setId.trim().isEmpty()) {
                sharedTitle.setText("Shared Set Unavailable");
                sharedType.setText("Error: Missing Data");
                sharedDescription.setText("This set link is corrupted and cannot be loaded.");
                btnViewSet.setVisibility(View.GONE);
                saveSetBtn.setVisibility(GONE);
                return;
            }

            if ("flashcard".equals(setType)) {
                db.collection("flashcards").document(setId).get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Flashcard flashcard = snapshot.toObject(Flashcard.class);
                        if (flashcard != null) {
                            sharedTitle.setText(flashcard.getTitle());
                            sharedType.setText("Flashcard Set");

                            db.collection("users").document(flashcard.getOwnerUid()).get()
                                    .addOnSuccessListener(ownerDoc -> {
                                        User owner = ownerDoc.toObject(User.class);
                                        String desc = flashcard.getNumber_Of_Items() + " terms" +
                                                (owner != null ? " · by " + owner.getUsername() : "");
                                        sharedDescription.setText(desc);
                                    })
                                    .addOnFailureListener(e ->
                                            sharedDescription.setText(flashcard.getNumber_Of_Items() + " terms"));
                        }
                    } else {
                        sharedTitle.setText("Flashcard Set Unavailable");
                        sharedType.setText("Flashcard Set");
                        sharedDescription.setText("This flashcard set has been deleted.");
                        btnViewSet.setVisibility(View.GONE);
                        saveSetBtn.setVisibility(GONE);
                    }
                });
            } else if ("quiz".equals(setType)) {
                db.collection("quiz").document(setId).get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Quiz quiz = snapshot.toObject(Quiz.class);
                        if (quiz != null) {
                            sharedTitle.setText(quiz.getTitle());
                            sharedType.setText("Quiz Set");

                            db.collection("users").document(quiz.getOwner_uid()).get()
                                    .addOnSuccessListener(ownerDoc -> {
                                        User owner = ownerDoc.toObject(User.class);
                                        String desc = quiz.getNumber_of_items() + " items" +
                                                (owner != null ? " · by " + owner.getUsername() : "");
                                        sharedDescription.setText(desc);
                                    })
                                    .addOnFailureListener(e ->
                                            sharedDescription.setText(quiz.getNumber_of_items() + " items"));
                        }
                    } else {
                        sharedTitle.setText("Quiz Set Unavailable");
                        sharedType.setText("Quiz Set");
                        sharedDescription.setText("This quiz set has been deleted.");
                        btnViewSet.setVisibility(View.GONE);
                        saveSetBtn.setVisibility(GONE);
                    }
                });
            }

            db.collection("users").document(currentUserId).get().addOnSuccessListener(userDoc -> {
                AtomicBoolean isSaved = new AtomicBoolean(false);
                boolean isOwned = false;

                List<Map<String, Object>> savedSets = (List<Map<String, Object>>) userDoc.get("saved_sets");
                List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) userDoc.get("owned_sets");

                if (savedSets != null) {
                    for (Map<String, Object> set : savedSets) {
                        if (setId.equals(set.get("id")) && setType.equals(set.get("type"))) {
                            isSaved.set(true);
                            break;
                        }
                    }
                }

                if (ownedSets != null) {
                    for (Map<String, Object> set : ownedSets) {
                        if (setId.equals(set.get("id")) && setType.equals(set.get("type"))) {
                            isOwned = true;
                            break;
                        }
                    }
                }

                if (isOwned) {
                    updateBookmarkIcon(true);
                    saveSetBtn.setEnabled(false);
                    saveSetBtn.setClickable(false);
                    saveSetBtn.setVisibility(GONE);
                    savedIndicator.setVisibility(VISIBLE);
                } else {
                    updateBookmarkIcon(isSaved.get());
                    savedIndicator.setVisibility(isSaved.get() ? VISIBLE : GONE);

                    saveSetBtn.setOnClickListener(v -> {
                        Map<String, Object> setData = new HashMap<>();
                        setData.put("id", setId);
                        setData.put("type", setType);

                        if (isSaved.get()) {
                            db.collection("users").document(currentUserId)
                                    .update("saved_sets", FieldValue.arrayRemove(setData))
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(itemView.getContext(), "Set unsaved", Toast.LENGTH_SHORT).show();
                                        isSaved.set(false);
                                        updateBookmarkIcon(false);
                                        savedIndicator.setVisibility(GONE);
                                    });
                        } else {
                            db.collection("users").document(currentUserId)
                                    .update("saved_sets", FieldValue.arrayUnion(setData))
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(itemView.getContext(), "Set saved!", Toast.LENGTH_SHORT).show();
                                        isSaved.set(true);
                                        updateBookmarkIcon(true);
                                        savedIndicator.setVisibility(VISIBLE);
                                    });
                        }
                    });
                }
            });

            btnViewSet.setOnClickListener(v -> {
                Intent intent;
                if ("flashcard".equals(setType)) {
                    intent = new Intent(itemView.getContext(), FlashcardPreviewActivity.class);
                    intent.putExtra("setId", setId);
                } else {
                    intent = new Intent(itemView.getContext(), QuizPreviewActivity.class);
                    intent.putExtra("quizId", setId);
                }
                itemView.getContext().startActivity(intent);
            });
        }

        private void updateBookmarkIcon(boolean isSaved) {
            if (isSaved) {
                saveSetBtn.setImageResource(R.drawable.bookmark_filled);
                saveSetBtn.setColorFilter(itemView.getContext().getResources().getColor(R.color.white));
            } else {
                saveSetBtn.setImageResource(R.drawable.bookmark);
                saveSetBtn.setColorFilter(itemView.getContext().getResources().getColor(R.color.white));
            }
        }
    }

    private static String readableFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    static class CurrentUserViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timestampText, timeSeparator;
        ImageView imageView, videoPreview;
        boolean timestampVisible = false;

        public CurrentUserViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timestampText = itemView.findViewById(R.id.timestampText);
            imageView = itemView.findViewById(R.id.imageView);
            videoPreview = itemView.findViewById(R.id.videoPreview);
            timeSeparator = itemView.findViewById(R.id.timeSeparatorText);
        }

        public void bind(ChatMessage message) {
            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));
            timestampText.setVisibility(GONE);


            if ("image".equals(message.getType())) {
                messageText.setVisibility(GONE);
                imageView.setVisibility(VISIBLE);
                Glide.with(itemView.getContext()).load(message.getImageUrl()).into(imageView);

                imageView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), ImageViewerActivity.class);
                    intent.putExtra("imageUrl", message.getImageUrl());
                    itemView.getContext().startActivity(intent);
                });

            } else if ("video".equals(message.getType())) {
                messageText.setVisibility(GONE);
                videoPreview.setVisibility(VISIBLE);
                Glide.with(itemView.getContext())
                        .load(message.getVideoUrl())
                        .thumbnail(0.1f)
                        .into(videoPreview);

                videoPreview.setImageDrawable(ContextCompat.getDrawable(itemView.getContext(), R.drawable.playbtn));
                videoPreview.setScaleType(ImageView.ScaleType.CENTER);
                Drawable drawable = ContextCompat.getDrawable(itemView.getContext(), R.drawable.playbtn);
                if (drawable != null) {
                    drawable.setTint(Color.WHITE);
                    videoPreview.setImageDrawable(drawable);
                }


                videoPreview.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(message.getVideoUrl()), "video/*");
                    itemView.getContext().startActivity(intent);
                });

            } else {
                imageView.setVisibility(GONE);
                messageText.setVisibility(VISIBLE);
                messageText.setText(message.getText());
            }

            itemView.setOnClickListener(v -> {
                timestampVisible = !timestampVisible;
                timestampText.setVisibility(timestampVisible ? VISIBLE : GONE);
            });
        }
    }

    static class OtherUserViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, senderName, timestampText, timeSeparator;
        ImageView senderImage, imageView, videoPreview;
        LinearLayout messageHolder;
        boolean timestampVisible = false;

        public OtherUserViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            senderName = itemView.findViewById(R.id.senderName);
            senderImage = itemView.findViewById(R.id.senderImage);
            timestampText = itemView.findViewById(R.id.timestampText);
            imageView = itemView.findViewById(R.id.imageView);
            videoPreview = itemView.findViewById(R.id.videoPreview);
            messageHolder = itemView.findViewById(R.id.messageHolder);
            timeSeparator = itemView.findViewById(R.id.timeSeparatorText);
        }

        public void bind(ChatMessage message, boolean isSameSender) {
            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));
            timestampText.setVisibility(GONE);


            if ("image".equals(message.getType())) {
                messageText.setVisibility(GONE);
                imageView.setVisibility(VISIBLE);
                messageHolder.setVisibility(GONE);

                int radiusInPx = (int) (15 * itemView.getContext().getResources().getDisplayMetrics().density);

                Glide.with(itemView.getContext())
                        .load(message.getImageUrl())
                        .transform(new RoundedCorners(radiusInPx))
                        .into(imageView);

                imageView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), ImageViewerActivity.class);
                    intent.putExtra("imageUrl", message.getImageUrl());
                    itemView.getContext().startActivity(intent);
                });
            } else if ("video".equals(message.getType())) {
                messageText.setVisibility(GONE);
                videoPreview.setVisibility(VISIBLE);
                messageHolder.setVisibility(GONE);
                Glide.with(itemView.getContext())
                        .load(message.getVideoUrl())
                        .thumbnail(0.1f)
                        .into(videoPreview);

                videoPreview.setImageDrawable(ContextCompat.getDrawable(itemView.getContext(), R.drawable.playbtn));
                videoPreview.setScaleType(ImageView.ScaleType.CENTER);
                Drawable drawable = ContextCompat.getDrawable(itemView.getContext(), R.drawable.playbtn);
                if (drawable != null) {
                    drawable.setTint(Color.WHITE);
                    videoPreview.setImageDrawable(drawable);
                }


                videoPreview.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(message.getVideoUrl()), "video/*");
                    itemView.getContext().startActivity(intent);
                });

            } else {
                imageView.setVisibility(GONE);
                messageText.setVisibility(VISIBLE);
                messageText.setText(message.getText());
            }

            if (isSameSender) {
                senderName.setVisibility(GONE);
                senderImage.setVisibility(View.INVISIBLE);
            } else {
                senderName.setText(message.getSenderName());
                senderName.setVisibility(VISIBLE);
                senderImage.setVisibility(VISIBLE);
                Glide.with(itemView.getContext())
                        .load(message.getSenderPhotoUrl())
                        .placeholder(R.drawable.user_profile)
                        .circleCrop()
                        .into(senderImage);

                if (!"Live Quiz Manager".equals(message.getSenderName())) {
                    senderImage.setOnClickListener(v -> {
                        Intent intent = new Intent(itemView.getContext(), UserProfileActivity.class);
                        intent.putExtra("userId", message.getSenderId());
                        itemView.getContext().startActivity(intent);
                    });
                } else {
                    senderImage.setClickable(false);
                }

            }

            itemView.setOnClickListener(v -> {
                timestampVisible = !timestampVisible;
                timestampText.setVisibility(timestampVisible ? VISIBLE : GONE);
            });
        }

    }

    static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        TextView systemMessageText;

        public SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            systemMessageText = itemView.findViewById(R.id.systemMessageText);
        }

        public void bind(ChatMessage message) {
            systemMessageText.setText(message.getText());
        }
    }
    private String formatTimeSeparator(Context context, Date currentTimestamp, Date previousTimestamp) {
        long TEN_MINUTES_MILLIS = 10 * 60 * 1000;
        if (previousTimestamp != null && currentTimestamp.getTime() - previousTimestamp.getTime() < TEN_MINUTES_MILLIS) {
            return null;
        }

        Calendar now = Calendar.getInstance();
        Calendar messageCal = Calendar.getInstance();
        messageCal.setTime(currentTimestamp);

        String timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT).format(currentTimestamp);

        if (now.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageCal.get(Calendar.DAY_OF_YEAR)) {
            return timeFormat;
        }

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (yesterday.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == messageCal.get(Calendar.DAY_OF_YEAR)) {
            return "Yesterday at " + timeFormat;
        }


        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        if (currentTimestamp.getTime() >= sevenDaysAgo && !DateUtils.isToday(currentTimestamp.getTime())) {
            String dayOfWeek = new SimpleDateFormat("EEE", Locale.getDefault()).format(currentTimestamp); // EEE for Sat
            return dayOfWeek + " at " + timeFormat;
        }

        if (now.get(Calendar.YEAR) == messageCal.get(Calendar.YEAR)) {
            String monthDay = new SimpleDateFormat("MMM dd", Locale.getDefault()).format(currentTimestamp); // e.g., Oct 27
            return monthDay + " at " + timeFormat;
        }

        String fullDate = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(currentTimestamp); // e.g., Oct 27, 2024
        return fullDate + " at " + timeFormat;
    }

}
