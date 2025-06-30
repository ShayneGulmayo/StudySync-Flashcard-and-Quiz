package com.labactivity.studysync.adapters;

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
import com.labactivity.studysync.models.User;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SELECTABLE = 0;
    private static final int TYPE_SELECTED = 1;

    private List<User> userList;
    private List<User> selectedUsers;
    private OnUserSelectedListener listener;
    private boolean isSelectionList; // true = list w/ radio buttons, false = selected w/ X

    public interface OnUserSelectedListener {
        void onUserSelected(User user, boolean selected, int position);
    }

    public UserAdapter(List<User> userList, List<User> selectedUsers, boolean isSelectionList, OnUserSelectedListener listener) {
        this.userList = userList;
        this.selectedUsers = selectedUsers;
        this.listener = listener;
        this.isSelectionList = isSelectionList;
    }

    @Override
    public int getItemViewType(int position) {
        return isSelectionList ? TYPE_SELECTABLE : TYPE_SELECTED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SELECTED) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selected_user, parent, false);
            return new SelectedViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new SelectableViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        User user = userList.get(position);

        if (holder.getItemViewType() == TYPE_SELECTED) {
            SelectedViewHolder viewHolder = (SelectedViewHolder) holder;
            viewHolder.fullName.setText(user.getFullName());
            Glide.with(holder.itemView.getContext())
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.user_profile)
                    .circleCrop()
                    .into(viewHolder.profileImage);

            viewHolder.removeButton.setOnClickListener(v -> {
                selectedUsers.remove(user);
                listener.onUserSelected(user, false, position);
            });

        } else {
            SelectableViewHolder viewHolder = (SelectableViewHolder) holder;
            viewHolder.fullName.setText(user.getFullName());
            viewHolder.username.setText("@" + user.getUsername());
            Glide.with(holder.itemView.getContext())
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.user_profile)
                    .circleCrop()
                    .into(viewHolder.profileImage);

            boolean isSelected = selectedUsers.contains(user);
            viewHolder.selectRadio.setChecked(isSelected);

            View.OnClickListener toggleSelection = v -> {
                if (isSelected) {
                    selectedUsers.remove(user);
                    listener.onUserSelected(user, false, position);
                } else {
                    selectedUsers.add(user);
                    userList.remove(user);
                    userList.add(0, user);
                    notifyItemMoved(position, 0);
                    listener.onUserSelected(user, true, 0);
                }
                notifyDataSetChanged();
            };

            viewHolder.itemView.setOnClickListener(toggleSelection);
            viewHolder.selectRadio.setOnClickListener(toggleSelection);
        }
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class SelectableViewHolder extends RecyclerView.ViewHolder {
        ImageView profileImage;
        TextView fullName, username;
        RadioButton selectRadio;

        public SelectableViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            fullName = itemView.findViewById(R.id.full_name);
            username = itemView.findViewById(R.id.username);
            selectRadio = itemView.findViewById(R.id.select_radio);
        }
    }

    static class SelectedViewHolder extends RecyclerView.ViewHolder {
        ImageView profileImage, removeButton;
        TextView fullName;

        public SelectedViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            removeButton = itemView.findViewById(R.id.remove_button);
            fullName = itemView.findViewById(R.id.full_name);
        }
    }
}
