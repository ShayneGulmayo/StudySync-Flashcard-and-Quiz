package com.labactivity.studysync.adapters;

import android.content.Intent;
import android.icu.text.DateFormat;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.ImageViewerActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.ChatMessage;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.models.Quiz;
import com.labactivity.studysync.models.User;

import java.text.DecimalFormat;

public class ChatMessageAdapter extends FirestoreRecyclerAdapter<ChatMessage, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_CURRENT_USER = 1;
    private static final int VIEW_TYPE_OTHER_USER = 2;
    private static final int VIEW_TYPE_SYSTEM_MESSAGE = 3;
    private static final int VIEW_TYPE_SHARED_SET = 4;
    private static final int VIEW_TYPE_FILE_CURRENT_USER = 5;
    private static final int VIEW_TYPE_FILE_OTHER_USER = 6;

    private final String currentUserId;

    public ChatMessageAdapter(@NonNull FirestoreRecyclerOptions<ChatMessage> options, String currentUserId) {
        super(options);
        this.currentUserId = currentUserId;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = getItem(position);
        if ("system".equals(message.getType())) return VIEW_TYPE_SYSTEM_MESSAGE;
        if ("set".equals(message.getType())) return VIEW_TYPE_SHARED_SET;
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
        } else if (holder instanceof FileCurrentUserViewHolder) {
            ((FileCurrentUserViewHolder) holder).bind(message);
        } else if (holder instanceof FileOtherUserViewHolder) {
            ((FileOtherUserViewHolder) holder).bind(message);
        }
    }

    static class SharedSetViewHolder extends RecyclerView.ViewHolder {
        TextView senderName, sharedTitle, sharedType, sharedDescription, timestampText;
        ImageView senderImage, saveSetBtn;

        public SharedSetViewHolder(@NonNull View itemView) {
            super(itemView);
            senderName = itemView.findViewById(R.id.senderName);
            sharedTitle = itemView.findViewById(R.id.sharedTitle);
            sharedType = itemView.findViewById(R.id.sharedType);
            sharedDescription = itemView.findViewById(R.id.sharedDescription);
            timestampText = itemView.findViewById(R.id.timestampText);
            senderImage = itemView.findViewById(R.id.senderImage);
            saveSetBtn = itemView.findViewById(R.id.saveSetBtn);
        }

        public void bind(ChatMessage message) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            db.collection("users").document(message.getSenderId()).get().addOnSuccessListener(userSnap -> {
                User user = userSnap.toObject(User.class);
                if (user != null) {
                    senderName.setText(user.getUsername());
                    Glide.with(itemView.getContext()).load(user.getPhotoUrl()).placeholder(R.drawable.user_profile).circleCrop().into(senderImage);
                }
            });

            if ("flashcard".equals(message.getSetType())) {
                db.collection("flashcards").document(message.getSetId())
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            Flashcard flashcard = snapshot.toObject(Flashcard.class);
                            if (flashcard != null) {
                                sharedTitle.setText(flashcard.getTitle());
                                sharedType.setText("Flashcard Set");

                                // Fetch the owner's username from the users collection
                                db.collection("users").document(flashcard.getOwnerUid())
                                        .get()
                                        .addOnSuccessListener(ownerDoc -> {
                                            User owner = ownerDoc.toObject(User.class);
                                            if (owner != null) {
                                                String description = flashcard.getNumberOfItems() + " terms · by " + owner.getUsername();
                                                sharedDescription.setText(description);
                                            } else {
                                                sharedDescription.setText(flashcard.getNumberOfItems() + " terms");
                                            }
                                        })
                                        .addOnFailureListener(e -> {
                                            sharedDescription.setText(flashcard.getNumberOfItems() + " terms");
                                        });
                            }
                        });
        } else if ("quiz".equals(message.getSetType())) {
                db.collection("quizzes").document(message.getSetId()).get().addOnSuccessListener(snapshot -> {
                    Quiz quiz = snapshot.toObject(Quiz.class);
                    if (quiz != null) {
                        sharedTitle.setText(quiz.getTitle());
                        sharedType.setText("Quiz Set");
                        db.collection("users").document(quiz.getOwner_uid()).get().addOnSuccessListener(ownerDoc -> {
                            User owner = ownerDoc.toObject(User.class);
                            if (owner != null) {
                                sharedDescription.setText(quiz.getNumber_of_items() + " items · by " + owner.getUsername());
                            }
                        });
                    }
                });
            }

            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));
            saveSetBtn.setOnClickListener(v -> Toast.makeText(itemView.getContext(), "Set saved!", Toast.LENGTH_SHORT).show());
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

            saveFileButton.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(message.getFileUrl()));
                itemView.getContext().startActivity(intent);
            });
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
                imageView.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(message.getImageUrl()).into(imageView);

                imageView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), ImageViewerActivity.class);
                    intent.putExtra("imageUrl", message.getImageUrl());
                    itemView.getContext().startActivity(intent);
                });
            } else if ("file".equals(message.getType())) {
                imageView.setVisibility(View.GONE);
                messageText.setVisibility(View.VISIBLE);
                messageText.setText("\uD83D\uDCCE " + message.getText());

                messageText.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(message.getFileUrl()));
                    itemView.getContext().startActivity(intent);
                });
            } else {
                imageView.setVisibility(View.GONE);
                messageText.setVisibility(View.VISIBLE);
                messageText.setText(message.getText());
            }

            itemView.setOnClickListener(v -> {
                timestampVisible = !timestampVisible;
                timestampText.setVisibility(timestampVisible ? View.VISIBLE : View.GONE);
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
                imageView.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(message.getImageUrl()).into(imageView);

                imageView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), ImageViewerActivity.class);
                    intent.putExtra("imageUrl", message.getImageUrl());
                    itemView.getContext().startActivity(intent);
                });
            } else if ("file".equals(message.getType())) {
                imageView.setVisibility(View.GONE);
                messageText.setVisibility(View.VISIBLE);
                messageText.setText("\uD83D\uDCCE " + message.getText());

                messageText.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(message.getFileUrl()));
                    itemView.getContext().startActivity(intent);
                });
            } else {
                imageView.setVisibility(View.GONE);
                messageText.setVisibility(View.VISIBLE);
                messageText.setText(message.getText());
            }

            if (isSameSender) {
                senderName.setVisibility(View.GONE);
                senderImage.setVisibility(View.INVISIBLE);
            } else {
                senderName.setText(message.getSenderName());
                senderName.setVisibility(View.VISIBLE);
                senderImage.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(message.getSenderPhotoUrl())
                        .circleCrop()
                        .into(senderImage);
            }

            itemView.setOnClickListener(v -> {
                timestampVisible = !timestampVisible;
                timestampText.setVisibility(timestampVisible ? View.VISIBLE : View.GONE);
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
