package com.labactivity.studysync.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.labactivity.studysync.ChatRoomActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.ChatRoom;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatRoomAdapter extends RecyclerView.Adapter<ChatRoomAdapter.ChatRoomViewHolder> {

    private List<ChatRoom> fullList;
    private List<ChatRoom> filteredList;
    private final String currentUserId;
    private Map<String, Date> lastOpenedMap = new HashMap<>();
    private final Context context;

    public ChatRoomAdapter(Context context, List<ChatRoom> chatRooms, String currentUserId) {
        this.context = context;
        this.fullList = chatRooms;
        this.filteredList = new ArrayList<>(chatRooms);
        this.currentUserId = currentUserId;
    }

    public void updateData(List<ChatRoom> newList) {
        this.fullList = newList;
        this.filteredList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public void setUserLastOpenedMap(Map<String, Date> map) {
        this.lastOpenedMap = map != null ? map : new HashMap<>();
    }

    public void filterByName(String query) {
        filteredList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredList.addAll(fullList);
        } else {
            for (ChatRoom room : fullList) {
                if (room.getChatRoomName() != null &&
                        room.getChatRoomName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                    filteredList.add(room);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatRoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_rooms, parent, false);
        return new ChatRoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatRoomViewHolder holder, int position) {
        ChatRoom room = filteredList.get(position);
        holder.groupName.setText(room.getChatRoomName());

        String type = room.getLastMessageType() != null ? room.getLastMessageType() : "text";
        String preview;

        switch (type) {
            case "file":
                preview = "Sent a file";
                break;
            case "image":
                preview = "Sent an image";
                break;
            case "video":
                preview = "Sent a video";
                break;
            case "set":
                preview = "Shared a set";
                break;
            case "system":
                preview = room.getLastMessage() != null ? room.getLastMessage() : "System update";
                break;
            case "text":
            default:
                preview = room.getLastMessage() != null ? room.getLastMessage() : "No messages yet";
                break;
        }

        if (!type.equals("system") && !type.equals("announcements") && !type.equals("request") && room.getLastMessageSender() != null) {
            preview = room.getLastMessageSender() + ": " + preview;
        }


        holder.lastMessage.setText(preview);

        if (room.getPhotoUrl() != null && !room.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(room.getPhotoUrl())
                    .placeholder(R.drawable.user_profile)
                    .circleCrop()
                    .into(holder.groupImage);
        } else {
            holder.groupImage.setImageResource(R.drawable.user_profile);
        }

        boolean isUnread = false;
        Date lastMessageTime = room.getLastMessageTimestamp();
        Date lastOpened = lastOpenedMap != null ? lastOpenedMap.get(room.getId()) : null;

        if (lastMessageTime != null && (lastOpened == null || lastMessageTime.after(lastOpened))) {
            isUnread = true;
        }

        holder.groupName.setTypeface(null, isUnread ? Typeface.BOLD : Typeface.NORMAL);
        holder.lastMessage.setTypeface(null,
                type.equals("system") ? Typeface.ITALIC : (isUnread ? Typeface.BOLD : Typeface.NORMAL));

        holder.itemView.setOnClickListener(v -> {
            Map<String, Object> update = new HashMap<>();
            update.put(room.getId(), Timestamp.now());

            FirebaseFirestore.getInstance()
                    .collection("user_chat_status")
                    .document(currentUserId)
                    .set(update, SetOptions.merge());

            Intent intent = new Intent(context, ChatRoomActivity.class);
            intent.putExtra("roomId", room.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    static class ChatRoomViewHolder extends RecyclerView.ViewHolder {
        ImageView groupImage;
        TextView groupName, lastMessage;

        public ChatRoomViewHolder(@NonNull View itemView) {
            super(itemView);
            groupImage = itemView.findViewById(R.id.image_group_photo);
            groupName = itemView.findViewById(R.id.text_group_name);
            lastMessage = itemView.findViewById(R.id.text_last_message);
        }
    }
}
