package com.labactivity.studysync.adapters;

import static android.view.View.VISIBLE;

import android.content.Intent;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
        if (holder instanceof CurrentUserViewHolder) {
            ((CurrentUserViewHolder) holder).bind(message);
        } else if (holder instanceof OtherUserViewHolder) {
            boolean isSameSender = position > 0 && getItem(position - 1).getSenderId().equals(message.getSenderId());
            ((OtherUserViewHolder) holder).bind(message, isSameSender);
        } else if (holder instanceof SystemMessageViewHolder) {
            ((SystemMessageViewHolder) holder).bind(message);
        } else if (holder instanceof SharedSetViewHolder) {
            ((SharedSetViewHolder) holder).bind(message);
        } else if (holder instanceof SharedSetCurrentUserViewHolder) {
            ((SharedSetCurrentUserViewHolder) holder).bind(message);
        } else if (holder instanceof FileCurrentUserViewHolder) {
            ((FileCurrentUserViewHolder) holder).bind(message);
        } else if (holder instanceof FileOtherUserViewHolder) {
            ((FileOtherUserViewHolder) holder).bind(message);
        }
    }

    public class SharedSetViewHolder extends RecyclerView.ViewHolder {
        TextView senderName, sharedTitle, sharedType, sharedDescription, timestampText;
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

            itemView.setOnClickListener(v -> {
                timestampText.setVisibility(timestampText.getVisibility() == VISIBLE ? View.GONE : VISIBLE);
            });

        }

        public void bind(ChatMessage message) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            FirebaseAuth auth = FirebaseAuth.getInstance();
            String currentUserId = auth.getCurrentUser().getUid();

            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));

            db.collection("users").document(message.getSenderId()).get().addOnSuccessListener(userSnap -> {
                User user = userSnap.toObject(User.class);
                if (user != null) {
                    senderName.setText(user.getUsername());
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
                    Flashcard flashcard = snapshot.toObject(Flashcard.class);
                    if (flashcard != null) {
                        sharedTitle.setText(flashcard.getTitle());
                        sharedType.setText("Flashcard Set");
                        db.collection("users").document(flashcard.getOwnerUid()).get().addOnSuccessListener(ownerDoc -> {
                            User owner = ownerDoc.toObject(User.class);
                            String desc = flashcard.getNumber_Of_Items() + " terms" +
                                    (owner != null ? " · by " + owner.getUsername() : "");
                            sharedDescription.setText(desc);
                        });
                    }
                });
            } else if ("quiz".equals(message.getSetType())) {
                db.collection("quiz").document(message.getSetId()).get().addOnSuccessListener(snapshot -> {
                    Quiz quiz = snapshot.toObject(Quiz.class);
                    if (quiz != null) {
                        sharedTitle.setText(quiz.getTitle());
                        sharedType.setText("Quiz Set");
                        db.collection("users").document(quiz.getOwner_uid()).get().addOnSuccessListener(ownerDoc -> {
                            User owner = ownerDoc.toObject(User.class);
                            String desc = quiz.getNumber_of_items() + " items" +
                                    (owner != null ? " · by " + owner.getUsername() : "");
                            sharedDescription.setText(desc);
                        });
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
                    savedIndicator.setVisibility(VISIBLE);
                } else {
                    updateBookmarkIcon(isSaved.get());
                    savedIndicator.setVisibility(isSaved.get() ? VISIBLE : View.GONE);

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
                                        savedIndicator.setVisibility(View.GONE);
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
        TextView fileName, fileDetails, timestampText;
        ImageView saveFileButton;

        public FileCurrentUserViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            fileDetails = itemView.findViewById(R.id.fileDetails);
            timestampText = itemView.findViewById(R.id.timestampText);
            saveFileButton = itemView.findViewById(R.id.saveFileButton);
        }

        public void bind(ChatMessage message) {
            fileName.setText(message.getFileName());
            fileDetails.setText(message.getFileType().toUpperCase() + " · " + readableFileSize(message.getFileSize()));
            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));

            itemView.setOnClickListener(view -> {
                timestampText.setVisibility(timestampText.getVisibility() == VISIBLE ? View.GONE : VISIBLE);
            });
            saveFileButton.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(message.getFileUrl()));
                itemView.getContext().startActivity(intent);
            });
        }
    }

    static class FileOtherUserViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, fileDetails, timestampText, senderName;
        ImageView saveFileButton, senderImage;

        public FileOtherUserViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            fileDetails = itemView.findViewById(R.id.fileDetails);
            timestampText = itemView.findViewById(R.id.timestampText);
            saveFileButton = itemView.findViewById(R.id.saveFileButton);
            senderName = itemView.findViewById(R.id.senderName);
            senderImage = itemView.findViewById(R.id.senderImage);
        }

        public void bind(ChatMessage message) {
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
                timestampText.setVisibility(timestampText.getVisibility() == VISIBLE ? View.GONE : VISIBLE);
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
        TextView sharedTitle, sharedType, sharedDescription, timestampText;
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

            itemView.setOnClickListener(v -> {
                timestampText.setVisibility(timestampText.getVisibility() == VISIBLE ? View.GONE : VISIBLE);
            });
        }

        public void bind(ChatMessage message) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            FirebaseAuth auth = FirebaseAuth.getInstance();
            String currentUserId = auth.getCurrentUser().getUid();

            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));

            if ("flashcard".equals(message.getSetType())) {
                db.collection("flashcards").document(message.getSetId()).get().addOnSuccessListener(snapshot -> {
                    Flashcard flashcard = snapshot.toObject(Flashcard.class);
                    if (flashcard != null) {
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
                    }
                });
            } else if ("quiz".equals(message.getSetType())) {
                db.collection("quiz").document(message.getSetId()).get().addOnSuccessListener(snapshot -> {
                    Quiz quiz = snapshot.toObject(Quiz.class);
                    if (quiz != null) {
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
                    savedIndicator.setVisibility(VISIBLE);
                } else {
                    updateBookmarkIcon(isSaved.get());
                    savedIndicator.setVisibility(isSaved.get() ? VISIBLE : View.GONE);

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
                                        savedIndicator.setVisibility(View.GONE);
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
        TextView messageText, timestampText;
        ImageView imageView;
        boolean timestampVisible = false;

        public CurrentUserViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timestampText = itemView.findViewById(R.id.timestampText);
            imageView = itemView.findViewById(R.id.imageView);
        }

        public void bind(ChatMessage message) {
            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));
            timestampText.setVisibility(View.GONE);

            if ("image".equals(message.getType())) {
                messageText.setVisibility(View.GONE);
                imageView.setVisibility(VISIBLE);
                Glide.with(itemView.getContext()).load(message.getImageUrl()).into(imageView);

                imageView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), ImageViewerActivity.class);
                    intent.putExtra("imageUrl", message.getImageUrl());
                    itemView.getContext().startActivity(intent);
                });
            } else if ("file".equals(message.getType())) {
                imageView.setVisibility(View.GONE);
                messageText.setVisibility(VISIBLE);
                messageText.setText("\uD83D\uDCCE " + message.getText());

                messageText.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(message.getFileUrl()));
                    itemView.getContext().startActivity(intent);
                });
            } else {
                imageView.setVisibility(View.GONE);
                messageText.setVisibility(VISIBLE);
                messageText.setText(message.getText());
            }

            itemView.setOnClickListener(v -> {
                timestampVisible = !timestampVisible;
                timestampText.setVisibility(timestampVisible ? VISIBLE : View.GONE);
            });
        }
    }

    static class OtherUserViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, senderName, timestampText;
        ImageView senderImage, imageView;
        boolean timestampVisible = false;

        public OtherUserViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            senderName = itemView.findViewById(R.id.senderName);
            senderImage = itemView.findViewById(R.id.senderImage);
            timestampText = itemView.findViewById(R.id.timestampText);
            imageView = itemView.findViewById(R.id.imageView);
        }

        public void bind(ChatMessage message, boolean isSameSender) {
            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));
            timestampText.setVisibility(View.GONE);

            if ("image".equals(message.getType())) {
                messageText.setVisibility(View.GONE);
                imageView.setVisibility(VISIBLE);
                Glide.with(itemView.getContext()).load(message.getImageUrl()).into(imageView);

                imageView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), ImageViewerActivity.class);
                    intent.putExtra("imageUrl", message.getImageUrl());
                    itemView.getContext().startActivity(intent);
                });
            } else if ("file".equals(message.getType())) {
                imageView.setVisibility(View.GONE);
                messageText.setVisibility(VISIBLE);
                messageText.setText("\uD83D\uDCCE " + message.getText());

                messageText.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(message.getFileUrl()));
                    itemView.getContext().startActivity(intent);
                });
            } else {
                imageView.setVisibility(View.GONE);
                messageText.setVisibility(VISIBLE);
                messageText.setText(message.getText());
            }

            if (isSameSender) {
                senderName.setVisibility(View.GONE);
                senderImage.setVisibility(View.INVISIBLE);
            } else {
                senderName.setText(message.getSenderName());
                senderName.setVisibility(VISIBLE);
                senderImage.setVisibility(VISIBLE);
                Glide.with(itemView.getContext())
                        .load(message.getSenderPhotoUrl())
                        .circleCrop()
                        .into(senderImage);
                senderImage.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), UserProfileActivity.class);
                    intent.putExtra("userId", message.getSenderId());
                    itemView.getContext().startActivity(intent);
                });

            }

            itemView.setOnClickListener(v -> {
                timestampVisible = !timestampVisible;
                timestampText.setVisibility(timestampVisible ? VISIBLE : View.GONE);
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

}
