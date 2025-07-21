package com.labactivity.studysync.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.labactivity.studysync.FlashcardPreviewActivity;
import com.labactivity.studysync.QuizPreviewActivity;
import com.labactivity.studysync.R;

import java.io.File;
import java.util.List;
import java.util.Map;

public class DownloadedSetsAdapter extends RecyclerView.Adapter<DownloadedSetsAdapter.ViewHolder> {

    private List<Map<String, Object>> sets;
    private Context context;

    public DownloadedSetsAdapter(List<Map<String, Object>> sets, Context context) {
        this.sets = sets;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_downloaded_set, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> set = sets.get(position);
        String title = (String) set.get("title");
        String username = (String) set.get("username");
        if (username == null) username = "Unknown User";

        Object itemsObj = set.get("number_of_items");
        String items;
        if (itemsObj instanceof Number) {
            items = String.valueOf(((Number) itemsObj).intValue());
        } else {
            items = "No Items Found";
        }

        if (title == null) title = "Untitled Set";
        if (title.length() > 30) {
            title = title.substring(0, 27) + "...";
        }
        holder.title.setText(title);
        holder.username.setText(username);
        holder.items.setText(items + (items.equals("1") ? " item" : " items"));

        // OPEN FILE FOR OFFLINE VIEW
        holder.itemView.setOnClickListener(v -> {
            String type = (String) set.get("type"); // flashcard or quiz
            String fileName = (String) set.get("fileName");

            if (fileName != null) {
                File file = new File(context.getFilesDir(), fileName);
                if (file.exists()) {
                    Intent intent;
                    if ("quiz".equalsIgnoreCase(type)) {
                        intent = new Intent(context, QuizPreviewActivity.class);
                    } else if ("flashcard".equalsIgnoreCase(type)) {
                        intent = new Intent(context, FlashcardPreviewActivity.class);
                    } else {
                        Toast.makeText(context, "Unknown set type.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    intent.putExtra("offlineFileName", fileName);
                    intent.putExtra("source", "offline");
                    intent.putExtra("isOffline", true);
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "File not found.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(context, "Missing file name.", Toast.LENGTH_SHORT).show();
            }
        });

        // DELETE FILE
        holder.deleteBtn.setOnClickListener(v -> {
            String fileName = (String) set.get("fileName");
            if (fileName != null) {
                new androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle("Delete Set")
                        .setMessage("Are you sure you want to delete this downloaded set?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            File file = new File(context.getFilesDir(), fileName);
                            if (file.exists()) {
                                if (file.delete()) {
                                    Toast.makeText(context, "Set deleted.", Toast.LENGTH_SHORT).show();
                                    sets.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position, sets.size());
                                } else {
                                    Toast.makeText(context, "Failed to delete set.", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(context, "File not found.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                Toast.makeText(context, "File not found.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return sets.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, username, items;
        ImageView deleteBtn;

        public ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.setTitle);
            username = itemView.findViewById(R.id.setUsername);
            items = itemView.findViewById(R.id.setItems);
            deleteBtn = itemView.findViewById(R.id.deleteBtn);
        }
    }
}
