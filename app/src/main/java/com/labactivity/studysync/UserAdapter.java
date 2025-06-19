package com.labactivity.studysync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.labactivity.studysync.R;
import com.labactivity.studysync.User;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private List<User> userList;
    private List<User> selectedUsers;
    private OnUserSelectedListener listener;

    public interface OnUserSelectedListener {
        void onUserSelected(User user, boolean selected);
    }

    public UserAdapter(List<User> userList, List<User> selectedUsers, OnUserSelectedListener listener) {
        this.userList = userList;
        this.selectedUsers = selectedUsers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);

        holder.fullName.setText(user.getFullName());
        holder.username.setText("@" + user.getUsername());

        Glide.with(holder.itemView.getContext())
                .load(user.getPhotoUrl())
                .placeholder(R.drawable.user_profile)
                .circleCrop()
                .into(holder.profileImage);

        boolean isSelected = selectedUsers.contains(user);
        holder.selectRadio.setChecked(isSelected);

        // Click on entire item to toggle selection
        holder.itemView.setOnClickListener(v -> {
            if (isSelected) {
                selectedUsers.remove(user);
                listener.onUserSelected(user, false);
            } else {
                selectedUsers.add(user);
                listener.onUserSelected(user, true);
            }
            notifyItemChanged(position);
        });

        // Also toggle via radio button directly
        holder.selectRadio.setOnClickListener(v -> {
            if (isSelected) {
                selectedUsers.remove(user);
                listener.onUserSelected(user, false);
            } else {
                selectedUsers.add(user);
                listener.onUserSelected(user, true);
            }
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        ImageView profileImage;
        TextView fullName, username;
        RadioButton selectRadio;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            fullName = itemView.findViewById(R.id.full_name);
            username = itemView.findViewById(R.id.username);
            selectRadio = itemView.findViewById(R.id.select_radio);
        }
    }
}

