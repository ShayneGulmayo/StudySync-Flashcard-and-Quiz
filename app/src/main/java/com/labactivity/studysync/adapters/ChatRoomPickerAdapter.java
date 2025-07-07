package com.labactivity.studysync.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.ChatMessage;
import com.labactivity.studysync.models.ChatRoom;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatRoomPickerAdapter extends RecyclerView.Adapter<ChatRoomPickerAdapter.ViewHolder> {

    private final Context context;
    private List<ChatRoom> chatRooms;
    private final String currentUserId;
    private final String setId;
    private final String setType;

    private final Set<String> sentChatRoomIds = new HashSet<>();

    public ChatRoomPickerAdapter(Context context, List<ChatRoom> chatRooms, String currentUserId, String setId, String setType) {
        this.context = context;
        this.chatRooms = chatRooms;
        this.currentUserId = currentUserId;
        this.setId = setId;
        this.setType = setType;
    }

    public void updateList(List<ChatRoom> updatedList) {
        this.chatRooms = updatedList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatRoomPickerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_select_chat_room, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatRoomPickerAdapter.ViewHolder holder, int position) {
        ChatRoom room = chatRooms.get(position);
        holder.groupName.setText(room.getChatRoomName());

        if (room.getPhotoUrl() != null && !room.getPhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(room.getPhotoUrl())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.user_profile)
                    .error(R.drawable.user_profile)
                    .into(holder.groupPhoto);
        } else {
            holder.groupPhoto.setImageResource(R.drawable.user_profile);
        }

        int memberCount = room.getMembers() != null ? room.getMembers().size() : 0;
        holder.membersTxt.setText(memberCount + " member" + (memberCount == 1 ? "" : "s"));

        boolean alreadySent = sentChatRoomIds.contains(room.getId());
        holder.sendIcon.setEnabled(!alreadySent);
        holder.sendIcon.setAlpha(alreadySent ? 0.4f : 1.0f);

        holder.sendIcon.setOnClickListener(v -> {
            if (alreadySent || room.getId() == null) return;

            holder.sendIcon.setEnabled(false);
            holder.sendIcon.setAlpha(0.4f);

            FirebaseFirestore db = FirebaseFirestore.getInstance();

            db.collection("users").document(currentUserId)
                    .get()
                    .addOnSuccessListener(userDoc -> {
                        String senderName = userDoc.getString("firstName") + " " + userDoc.getString("lastName");
                        String photoUrl = userDoc.getString("photoUrl");

                        ChatMessage message = new ChatMessage();
                        message.setSenderId(currentUserId);
                        message.setSenderName(senderName);
                        message.setSenderPhotoUrl(photoUrl);
                        message.setTimestamp(new Date());
                        message.setType("set");
                        message.setSetId(setId);
                        message.setSetType(setType);

                        db.collection("chat_rooms")
                                .document(room.getId())
                                .collection("messages")
                                .add(message)
                                .addOnSuccessListener(doc -> {
                                    db.collection("chat_rooms")
                                            .document(room.getId())
                                            .update(
                                                    "lastMessage", "Shared a set",
                                                    "lastMessageSender", senderName,
                                                    "type", "set"
                                            );

                                    Toast.makeText(context, "Sent to " + room.getChatRoomName(), Toast.LENGTH_SHORT).show();
                                    sentChatRoomIds.add(room.getId());
                                    notifyItemChanged(holder.getAdapterPosition());
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(context, "Send failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    holder.sendIcon.setEnabled(true);
                                    holder.sendIcon.setAlpha(1.0f);
                                });
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "Failed to load user info: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        holder.sendIcon.setEnabled(true);
                        holder.sendIcon.setAlpha(1.0f);
                    });
        });

    }

    @Override
    public int getItemCount() {
        return chatRooms != null ? chatRooms.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView groupPhoto, sendIcon;
        TextView groupName, membersTxt;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            groupPhoto = itemView.findViewById(R.id.image_group_photo);
            sendIcon = itemView.findViewById(R.id.sendIcon);
            groupName = itemView.findViewById(R.id.text_group_name);
            membersTxt = itemView.findViewById(R.id.membersTxt);
        }
    }
}
