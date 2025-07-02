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
import com.labactivity.studysync.models.UserWithRole;

import java.util.List;

public class PrivacyUserAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SELECTABLE = 0;
    private static final int TYPE_SELECTED = 1;

    private final List<?> userList;
    private final List<UserWithRole> selectedUsers;
    private final boolean isSelectionList;
    private boolean isPublic;
    private final OnUserSelectedListener listener;

    public interface OnUserSelectedListener {
        void onUserSelected(User user, boolean selected, int position);
    }

    public PrivacyUserAdapter(List<?> userList, List<UserWithRole> selectedUsers, boolean isSelectionList, boolean isPublic, OnUserSelectedListener listener) {
        this.userList = userList;
        this.selectedUsers = selectedUsers;
        this.isSelectionList = isSelectionList;
        this.isPublic = isPublic;
        this.listener = listener;
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
        if (holder.getItemViewType() == TYPE_SELECTED) {
            UserWithRole uwr = (UserWithRole) userList.get(position);
            User user = uwr.getUser();
            SelectedViewHolder viewHolder = (SelectedViewHolder) holder;

            viewHolder.fullName.setText(user.getFullName());
            viewHolder.username.setText("@" + user.getUsername());

            boolean isOwner = user.getUid().equals(FirebaseAuth.getInstance().getCurrentUser().getUid());
            String roleText = isOwner ? "Owner" : uwr.getRole();
            viewHolder.txtRole.setText(roleText);

            int colorRes = isOwner ? R.color.text_gray : R.color.primary;
            viewHolder.txtRole.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), colorRes));

            Glide.with(holder.itemView.getContext())
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.user_profile)
                    .circleCrop()
                    .into(viewHolder.profileImage);

            if (isOwner) return;

            viewHolder.txtRole.setOnClickListener(v -> {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION) return;

                PopupMenu popupMenu = new PopupMenu(holder.itemView.getContext(), viewHolder.txtRole);
                popupMenu.getMenuInflater().inflate(R.menu.privacy_role_menu, popupMenu.getMenu());

                if (isPublic) {
                    popupMenu.getMenu().findItem(R.id.edit_role).setVisible(false);
                }

                popupMenu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.id.view_role) {
                        uwr.setRole("View");
                        notifyItemChanged(adapterPos);
                    } else if (item.getItemId() == R.id.edit_role) {
                        uwr.setRole("Edit");
                        notifyItemChanged(adapterPos);
                    } else if (item.getItemId() == R.id.remove_access) {
                        listener.onUserSelected(user, false, adapterPos);
                    }
                    return true;
                });

                popupMenu.show();
            });

        } else {
            User user = (User) userList.get(position);
            SelectableViewHolder viewHolder = (SelectableViewHolder) holder;

            viewHolder.fullName.setText(user.getFullName());
            viewHolder.username.setText("@" + user.getUsername());

            Glide.with(holder.itemView.getContext())
                    .load(user.getPhotoUrl())
                    .placeholder(R.drawable.user_profile)
                    .circleCrop()
                    .into(viewHolder.profileImage);

            boolean isSelected = isUserSelected(user);
            viewHolder.selectRadio.setChecked(isSelected);

            View.OnClickListener toggleSelection = v -> {
                int adapterPos = holder.getAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION) return;

                boolean nowSelected = !isSelected;
                listener.onUserSelected(user, nowSelected, adapterPos);
                notifyItemChanged(adapterPos);
            };

            viewHolder.itemView.setOnClickListener(toggleSelection);
            viewHolder.selectRadio.setOnClickListener(toggleSelection);
        }
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    private boolean isUserSelected(User user) {
        for (UserWithRole u : selectedUsers) {
            if (u.getUser().getUid().equals(user.getUid())) {
                return true;
            }
        }
        return false;
    }

    static class SelectableViewHolder extends RecyclerView.ViewHolder {
        ImageView profileImage;
        TextView fullName, username;
        RadioButton selectRadio;

        SelectableViewHolder(@NonNull View itemView) {
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

        SelectedViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            fullName = itemView.findViewById(R.id.full_name);
            username = itemView.findViewById(R.id.username);
            txtRole = itemView.findViewById(R.id.txtRole);
        }
    }
}
