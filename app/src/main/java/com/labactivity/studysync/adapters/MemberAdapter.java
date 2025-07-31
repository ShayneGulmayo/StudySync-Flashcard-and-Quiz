package com.labactivity.studysync.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.labactivity.studysync.R;
import com.labactivity.studysync.SeeMembersActivity;
import com.labactivity.studysync.models.User;
import com.labactivity.studysync.models.UserWithRole;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {

    private final Context context;
    private List<UserWithRole> userList;
    private final String currentUserId;
    private final String chatRoomId;
    private String ownerId;
    private List<String> admins;
    private final SeeMembersActivity parentActivity;

    public MemberAdapter(Context context, List<UserWithRole> userList, String currentUserId, String chatRoomId, SeeMembersActivity parentActivity) {
        this.context = context;
        this.userList = userList;
        this.currentUserId = currentUserId;
        this.chatRoomId = chatRoomId;
        this.parentActivity = parentActivity;
    }

    public void updateData(List<UserWithRole> list, String ownerId, List<String> admins) {
        this.userList = list;
        this.ownerId = ownerId;
        this.admins = admins;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserWithRole userWithRole = userList.get(position);
        User user = userWithRole.getUser();
        String role = userWithRole.getRole();

        holder.fullName.setText(user.getFullName());
        holder.username.setText("@" + user.getUsername());
        holder.txtRole.setText(role);
        holder.txtRole.setVisibility(role.equals("Member") ? View.GONE : View.VISIBLE);

        Glide.with(context)
                .load(user.getPhotoUrl())
                .placeholder(R.drawable.user_profile)
                .error(R.drawable.user_profile)
                .circleCrop()
                .into(holder.profileImage);

        boolean isAdmin = admins != null && admins.contains(currentUserId);
        boolean isOwner = currentUserId.equals(ownerId);

        holder.itemView.setOnClickListener(v -> {
            if (isOwner || isAdmin) {
                showBottomSheet(userWithRole);
            } else {
                openUserProfile(user.getUid());
            }
        });

    }

    private void openUserProfile(String userId) {
        Intent intent = new Intent(context, com.labactivity.studysync.UserProfileActivity.class);
        intent.putExtra("userId", userId);
        context.startActivity(intent);
    }


    private void showBottomSheet(UserWithRole target) {
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_member, null);
        sheet.setContentView(view);

        TextView setOwner = view.findViewById(R.id.setOwner);
        TextView setAdmin = view.findViewById(R.id.setAdmin);
        TextView remove = view.findViewById(R.id.remove);

        String targetUid = target.getUser().getUid();
        String targetName = target.getUser().getFullName();
        boolean isOwner = currentUserId.equals(ownerId);
        boolean isAdmin = admins != null && admins.contains(currentUserId);
        boolean targetIsAdmin = admins != null && admins.contains(targetUid);

        boolean isDeleted = target.getUser().isDeleted();
        boolean isUserNotFound = "User Not Found".equals(targetName);

        if (isUserNotFound || isDeleted) {
            setOwner.setVisibility(View.GONE);
            setAdmin.setVisibility(View.GONE);
            remove.setVisibility(View.VISIBLE);
        } else {
            setOwner.setVisibility(isOwner ? View.VISIBLE : View.GONE);
            setAdmin.setVisibility((isOwner || isAdmin) ? View.VISIBLE : View.GONE);
            remove.setVisibility((isOwner || isAdmin) ? View.VISIBLE : View.GONE);
        }

        setAdmin.setText(targetIsAdmin ? "Remove from Admin" : "Set as Admin");

        setOwner.setOnClickListener(v -> {
            sheet.dismiss();
            confirm("Transfer ownership to " + targetName + "?", () -> {
                updateChatRoom("ownerId", targetUid);
                updateChatRoom("type", "system");
                writeSystemMessage(targetName + " is now the owner.");
            });
        });

        setAdmin.setOnClickListener(v -> {
            sheet.dismiss();
            String action = targetIsAdmin ? "removed from admin" : "set as admin";
            confirm("Are you sure you want to " + action + "?", () -> {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.collection("chat_rooms").document(chatRoomId)
                        .update("admins", targetIsAdmin ?
                                FieldValue.arrayRemove(targetUid) :
                                FieldValue.arrayUnion(targetUid),
                                "type", "system")
                        .addOnSuccessListener(aVoid -> {
                            writeSystemMessage(targetName + " was " + action + ".");
                            refreshView();
                        });
            });
        });

        remove.setOnClickListener(v -> {
            sheet.dismiss();
            confirm("Remove " + targetName + " from the chat room?", () -> {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.collection("chat_rooms").document(chatRoomId)
                        .update("members", FieldValue.arrayRemove(targetUid),
                                "admins", FieldValue.arrayRemove(targetUid),
                                "type", "system")

                        .addOnSuccessListener(aVoid -> {
                            writeSystemMessage(targetName + " was removed from the chat.");
                            refreshView();
                        });
            });
        });

        sheet.show();
    }

    private void confirm(String message, Runnable onConfirm) {
        new AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton("Yes", (d, w) -> onConfirm.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshView() {
        parentActivity.refreshView();
    }

    private void writeSystemMessage(String content) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> message = new HashMap<>();
        message.put("senderId", "system");
        message.put("senderName", "System");
        message.put("text", content);
        message.put("timestamp", new Date());
        message.put("type", "system");

        db.collection("chat_rooms")
                .document(chatRoomId)
                .collection("messages")
                .add(message);
    }

    private void updateChatRoom(String field, Object value) {
        FirebaseFirestore.getInstance()
                .collection("chat_rooms")
                .document(chatRoomId)
                .update(field, value)
                .addOnSuccessListener(aVoid -> refreshView());
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView profileImage;
        TextView fullName, username, txtRole;

        ViewHolder(View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            fullName = itemView.findViewById(R.id.full_name);
            username = itemView.findViewById(R.id.username);
            txtRole = itemView.findViewById(R.id.txtRole);
        }
    }
}