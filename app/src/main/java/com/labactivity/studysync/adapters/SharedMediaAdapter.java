package com.labactivity.studysync.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.labactivity.studysync.ImageViewerActivity;
import com.labactivity.studysync.R;
import com.labactivity.studysync.models.ChatMessage;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

public class SharedMediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_IMAGE = 0;
    private static final int TYPE_VIDEO = 1;
    private static final int TYPE_FILE = 2;

    private Context context;
    private List<ChatMessage> mediaList;

    public SharedMediaAdapter(Context context, List<ChatMessage> mediaList) {
        this.context = context;
        this.mediaList = mediaList;
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = mediaList.get(position);
        if (msg.getImageUrl() != null) return TYPE_IMAGE;
        if (msg.getVideoUrl() != null) return TYPE_VIDEO;
        return TYPE_FILE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == TYPE_IMAGE) {
            view = LayoutInflater.from(context).inflate(R.layout.item_shared_image, parent, false);
            return new ImageViewHolder(view);
        } else if (viewType == TYPE_VIDEO) {
            view = LayoutInflater.from(context).inflate(R.layout.item_shared_video, parent, false);
            return new VideoViewHolder(view);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.item_shared_file, parent, false);
            return new FileViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = mediaList.get(position);
        String meta = "sent by " + msg.getSenderName();

        if (holder instanceof ImageViewHolder) {
            ImageViewHolder vh = (ImageViewHolder) holder;
            vh.textDescription.setText(meta);

            if (msg.getImageUrl() != null) {
                try {
                    String decodedUrl = URLDecoder.decode(msg.getImageUrl(), "UTF-8");
                    int lastSlashIndex = decodedUrl.lastIndexOf('/');
                    int questionMarkIndex = decodedUrl.indexOf('?', lastSlashIndex);
                    String filename = questionMarkIndex != -1
                            ? decodedUrl.substring(lastSlashIndex + 1, questionMarkIndex)
                            : decodedUrl.substring(lastSlashIndex + 1);

                    vh.filename.setText(filename);
                } catch (UnsupportedEncodingException e) {
                    vh.filename.setText("Image");
                }
            }

            int radiusInPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 15, context.getResources().getDisplayMetrics());

            RequestOptions options = new RequestOptions()
                    .transform(new RoundedCorners(radiusInPx));

            Glide.with(context)
                    .load(msg.getImageUrl())
                    .apply(options)
                    .into(vh.image);

            vh.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ImageViewerActivity.class);
                intent.putExtra("imageUrl", msg.getImageUrl());
                context.startActivity(intent);
            });
        } else if (holder instanceof VideoViewHolder) {
            VideoViewHolder vh = (VideoViewHolder) holder;
            vh.textDescription.setText(meta);

            if (msg.getVideoUrl() != null) {
                try {
                    String decodedUrl = URLDecoder.decode(msg.getVideoUrl(), "UTF-8");
                    int lastSlashIndex = decodedUrl.lastIndexOf('/');
                    int questionMarkIndex = decodedUrl.indexOf('?', lastSlashIndex);
                    String filename = questionMarkIndex != -1
                            ? decodedUrl.substring(lastSlashIndex + 1, questionMarkIndex)
                            : decodedUrl.substring(lastSlashIndex + 1);

                    vh.fileName.setText(filename);
                } catch (UnsupportedEncodingException e) {
                    vh.fileName.setText("Video");
                }
            }

            vh.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getVideoUrl()));
                intent.setDataAndType(Uri.parse(msg.getVideoUrl()), "video/*");
                context.startActivity(intent);
            });
        } else if (holder instanceof FileViewHolder) {
            FileViewHolder vh = (FileViewHolder) holder;
            vh.fileName.setText(msg.getFileName());
            vh.textDescription.setText(meta + " · " + Formatter.formatShortFileSize(context, msg.getFileSize()));
            vh.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getFileUrl()));
                intent.setDataAndType(Uri.parse(msg.getFileUrl()), msg.getFileType());
                context.startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView textDescription, filename;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imageType);
            textDescription = itemView.findViewById(R.id.textDescription);
            filename = itemView.findViewById(R.id.fileName);
        }
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, textDescription;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            textDescription = itemView.findViewById(R.id.textDescription);
        }
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, textDescription;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            textDescription = itemView.findViewById(R.id.textDescription);
        }
    }
}