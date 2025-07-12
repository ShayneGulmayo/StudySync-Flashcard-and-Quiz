package com.labactivity.studysync.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.BrowseContent;

import java.util.List;

public class BrowseContentAdapter extends RecyclerView.Adapter<BrowseContentAdapter.ViewHolder> {

    private final List<BrowseContent> contentList;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(BrowseContent item);
    }

    public BrowseContentAdapter(List<BrowseContent> contentList, OnItemClickListener listener) {
        this.contentList = contentList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_browse_content, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BrowseContent item = contentList.get(position);
        String type = item.getType();

        holder.profileImage.setVisibility(View.VISIBLE);

        switch (type) {
            case "user":
                holder.title.setText(item.getUser().getFullName());
                holder.subtitle.setText("@" + item.getUser().getUsername());

                Glide.with(holder.itemView.getContext())
                        .load(item.getUser().getPhotoUrl())
                        .placeholder(R.drawable.user_profile)
                        .circleCrop()
                        .into(holder.profileImage);
                break;

            case "flashcard":
                holder.title.setText(item.getFlashcard().getTitle());
                holder.subtitle.setText("Flashcard · " + item.getFlashcard().getTermCount() +
                        " terms · by @" + item.getFlashcard().getOwnerUsername());
                holder.profileImage.setImageResource(R.drawable.flashcard); // same placeholder icon
                break;

            case "quiz":
                holder.title.setText(item.getQuiz().getTitle());
                holder.subtitle.setText("Quiz · " + item.getQuiz().getQuestionCount() +
                        " questions · by @" + item.getQuiz().getOwnerUsername());
                holder.profileImage.setImageResource(R.drawable.quiz);
                break;
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return contentList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView profileImage;
        TextView title, subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            title = itemView.findViewById(R.id.content_title);
            subtitle = itemView.findViewById(R.id.content_subtitle);
        }
    }
}
