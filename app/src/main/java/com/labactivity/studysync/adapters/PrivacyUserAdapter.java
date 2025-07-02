package com.labactivity.studysync.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.User;

import java.util.List;

public class PrivacyUserAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SELECTABLE = 0;
    private static final int TYPE_SELECTED = 1;

    private List<User> userList;
    private List<User> selectedUsers;
    private OnUserSelectedListener listener;
    private boolean isSelectionList;
    private boolean isPublic;  // <-- New flag for public/private privacy

    public interface OnUserSelectedListener {
        void onUserSelected(User user, boolean selected, int position);
        default void onAccessListEmpty() {}
    }

    public PrivacyUserAdapter(List<User> userList, List<User> selectedUsers, boolean isSelectionList, boolean isPublic, OnUserSelectedListener listener) {
        this.userList = userList;
        this.selectedUsers = selectedUsers;
        this.listener = listener;
        this.isSelectionList = isSelectionList;
        this.isPublic = isPublic;
    }

    public void setIsPublic(boolean isPublic) {
        this.isPublic = isPublic;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return isSelectionList ? TYPE_SELECTABLE : TYPE_SELECTED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SELECTED) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
            return new SelectedViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new SelectableViewHolder(view);
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        User user = userList.get(position);
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (holder.getItemViewType() == TYPE_SELECTED) {
            SelectedViewHolder viewHolder = (SelectedViewHolder) holder;

            viewHolder.fullName.setText(user.getFullName());
            viewHolder.username.setText("@" + user.getUsername());

            boolean isOwner = currentUserId.equals(user.getUid());
            String initialRole = isOwner ? "Owner" : "View";
            viewHolder.txtRole.setText(initialRole);

            int colorRes = isOwner ? R.color.text_gray : R.color.primary;
            viewHolder.txtRole.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), colorRes));

            Glide.with(holder.itemView.getContext())
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.user_profile)
                    .circleCrop()
                    .into(viewHolder.profileImage);

            viewHolder.txtRole.setOnClickListener(v -> {
                if (isOwner) return;
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;

                PopupMenu popupMenu = new PopupMenu(holder.itemView.getContext(), viewHolder.txtRole);
                popupMenu.getMenuInflater().inflate(R.menu.privacy_role_menu, popupMenu.getMenu());

                if (isPublic) {
                    // Hide Edit option if public
                    popupMenu.getMenu().findItem(R.id.edit_role).setVisible(false);
                }

                popupMenu.setOnMenuItemClickListener(item -> {
                    if (adapterPosition == RecyclerView.NO_POSITION) return false;

                    if (item.getItemId() == R.id.view_role) {
                        viewHolder.txtRole.setText("View");
                        viewHolder.txtRole.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary));
                    } else if (item.getItemId() == R.id.edit_role) {
                        viewHolder.txtRole.setText("Edit");
                        viewHolder.txtRole.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.primary));
                    } else if (item.getItemId() == R.id.remove_access) {
                        selectedUsers.remove(user);
                        listener.onUserSelected(user, false, position);
                    }

                    return true;
                });

                popupMenu.show();
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
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) return;

                if (selectedUsers.contains(user)) {
                    selectedUsers.remove(user);
                    listener.onUserSelected(user, false, adapterPosition);
                } else {
                    selectedUsers.add(user);
                    listener.onUserSelected(user, true, adapterPosition);
                }
                notifyItemChanged(adapterPosition);
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
        ImageView profileImage;
        TextView fullName, username, txtRole;

        public SelectedViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            fullName = itemView.findViewById(R.id.full_name);
            username = itemView.findViewById(R.id.username);
            txtRole = itemView.findViewById(R.id.txtRole);
        }
    }
}
