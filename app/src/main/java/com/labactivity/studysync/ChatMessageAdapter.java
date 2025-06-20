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
    private String currentUserId;

    public ChatMessageAdapter(@NonNull FirestoreRecyclerOptions<ChatMessage> options, String currentUserId) {
        super(options);
        this.currentUserId = currentUserId;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = getItem(position);
        return message.getSenderId().equals(currentUserId) ? VIEW_TYPE_CURRENT_USER : VIEW_TYPE_OTHER_USER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_CURRENT_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message_current_user, parent, false);
            return new CurrentUserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message_other_user, parent, false);
            return new OtherUserViewHolder(view);
        }
    }

    @Override
    protected void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull ChatMessage message) {
        boolean isSameSender = false;
        if (position > 0) {
            ChatMessage prev = getItem(position - 1);
            isSameSender = prev.getSenderId().equals(message.getSenderId());
        }

        if (holder instanceof CurrentUserViewHolder) {
            ((CurrentUserViewHolder) holder).bind(message);
        } else if (holder instanceof OtherUserViewHolder) {
            ((OtherUserViewHolder) holder).bind(message, isSameSender);
        }
    }

    // ViewHolder for current user
    static class CurrentUserViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timestampText;

        boolean timestampVisible = false;

        public CurrentUserViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timestampText = itemView.findViewById(R.id.timestampText);
        }

        public void bind(ChatMessage message) {
            messageText.setText(message.getText());
            timestampText.setText(DateFormat.getTimeInstance(DateFormat.SHORT).format(message.getTimestamp()));
            timestampText.setVisibility(View.GONE);

            itemView.setOnClickListener(v -> {
                timestampVisible = !timestampVisible;
                timestampText.setVisibility(timestampVisible ? View.VISIBLE : View.GONE);
            });
        }
    }

    // ViewHolder for other users
    static class OtherUserViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, senderName, timestampText;
        ImageView senderImage;

        boolean timestampVisible = false;

        public OtherUserViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            senderName = itemView.findViewById(R.id.senderName);
            senderImage = itemView.findViewById(R.id.senderImage);
            timestampText = itemView.findViewById(R.id.timestampText);
        }

        public void bind(ChatMessage message, boolean isSameSender) {
            messageText.setText(message.getText());
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
}
