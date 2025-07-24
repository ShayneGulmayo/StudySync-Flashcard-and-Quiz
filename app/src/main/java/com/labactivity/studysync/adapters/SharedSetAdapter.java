package com.labactivity.studysync.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.labactivity.studysync.GeneratingLiveQuizActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.SharedSet;

import java.util.List;

public class SharedSetAdapter extends RecyclerView.Adapter<SharedSetAdapter.SetViewHolder> {

    private final List<SharedSet> setList;
    private final Context context;
    private final String selectedSeconds;

    public SharedSetAdapter(Context context, List<SharedSet> setList, String selectedSeconds) {
        this.context = context;
        this.setList = setList;
        this.selectedSeconds = selectedSeconds;
    }

    @NonNull
    @Override
    public SetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_set, parent, false);
        return new SetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SetViewHolder holder, int position) {
        SharedSet set = setList.get(position);
        holder.setTitle.setText(set.getTitle());
        String desc = set.getSetType() + " · " + set.getItemCount() + " items · by " + set.getOwnerName() + " · shared by " + set.getSenderName();
        holder.description.setText(desc);

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, GeneratingLiveQuizActivity.class);
            intent.putExtra("setId", set.getId());
            intent.putExtra("roomId", set.getRoomId());
            intent.putExtra("secondsPerQuestion", selectedSeconds);
            intent.putExtra("type", "set");
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return setList.size();
    }

    static class SetViewHolder extends RecyclerView.ViewHolder {
        TextView setTitle, description;

        public SetViewHolder(@NonNull View itemView) {
            super(itemView);
            setTitle = itemView.findViewById(R.id.setTitle);
            description = itemView.findViewById(R.id.description);
        }
    }
}
