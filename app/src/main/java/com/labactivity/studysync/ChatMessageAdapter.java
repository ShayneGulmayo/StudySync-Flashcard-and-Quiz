package com.labactivity.studysync;

import android.icu.text.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;

public class ChatMessageAdapter extends FirestoreRecyclerAdapter<ChatMessage, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_CURRENT_USER = 1;
    private static final int VIEW_TYPE_OTHER_USER = 2;
    private static final int VIEW_TYPE_SYSTEM_MESSAGE = 3;

    private String currentUserId;

    public ChatMessageAdapter(@NonNull FirestoreRecyclerOptions<ChatMessage> options, String currentUserId) {
        super(options);
        this.currentUserId = currentUserId;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = getItem(position);
        if ("system".equals(message.getType())) {
            return VIEW_TYPE_SYSTEM_MESSAGE;
        }
        return message.getSenderId().equals(currentUserId) ? VIEW_TYPE_CURRENT_USER : VIEW_TYPE_OTHER_USER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_CURRENT_USER) {
            View view = inflater.inflate(R.layout.item_chat_message_current_user, parent, false);
            return new CurrentUserViewHolder(view);
        } else if (viewType == VIEW_TYPE_OTHER_USER) {
            View view = inflater.inflate(R.layout.item_chat_message_other_user, parent, false);
            return new OtherUserViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_system_message, parent, false);
            return new SystemMessageViewHolder(view);
        }
    }

    @Override
    protected void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull ChatMessage message) {
        if (holder instanceof CurrentUserViewHolder) {
            ((CurrentUserViewHolder) holder).bind(message);
        } else if (holder instanceof OtherUserViewHolder) {
            boolean isSameSender = false;
            if (position > 0) {
                ChatMessage prev = getItem(position - 1);
                isSameSender = prev.getSenderId().equals(message.getSenderId());
            }
            ((OtherUserViewHolder) holder).bind(message, isSameSender);
        } else if (holder instanceof SystemMessageViewHolder) {
            ((SystemMessageViewHolder) holder).bind(message);
        }
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
            if ("image".equals(message.getType())) {
                messageText.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(message.getImageUrl()).into(imageView);
            } else {
                imageView.setVisibility(View.GONE);
                messageText.setVisibility(View.VISIBLE);
                messageText.setText(message.getText());
            }

            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));
            timestampText.setVisibility(View.GONE);

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
            if ("image".equals(message.getType())) {
                messageText.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(message.getImageUrl()).into(imageView);
            } else {
                imageView.setVisibility(View.GONE);
                messageText.setVisibility(View.VISIBLE);
                messageText.setText(message.getText());
            }

            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));
            timestampText.setVisibility(View.GONE);

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
