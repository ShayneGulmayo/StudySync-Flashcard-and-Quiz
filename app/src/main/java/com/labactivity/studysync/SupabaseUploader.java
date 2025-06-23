package com.labactivity.studysync;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;

public class SupabaseUploader {

    private static final String SUPABASE_URL = "https://agnosyltikewhdzmdcwp.supabase.co";
    private static final String SUPABASE_API_KEY = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFnbm9zeWx0aWtld2hkem1kY3dwIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc0NjE0OTE3MSwiZXhwIjoyMDYxNzI1MTcxfQ.qkGXFasln2NZxBO7K9y97KLxhJ6-7HHr4FNVAGc4W08";

    private static final OkHttpClient client = new OkHttpClient();

    public interface UploadCallback {
        void onUploadComplete(boolean success, String message, String publicUrl);
    }

    /**
     * Upload a file to Supabase Storage (public bucket).
     * @param file      The file to upload
     * @param bucket    Bucket name (e.g., "user-files" or "chat-room-photos")
     * @param path      Full path within the bucket (e.g., "chat-room-profile/uuid.jpg")
     * @param callback  Result callback
     */
    public static void uploadFile(File file, String bucket, String path, UploadCallback callback) {
        String mimeType = getMimeType(file);
        if (mimeType == null) {
            callback.onUploadComplete(false, "Unsupported file type", null);
            return;
        }

        long maxFileSize = 20L * 1024 * 1024; // 20MB
        if (file.length() > maxFileSize) {
            callback.onUploadComplete(false, "File too large. Max allowed is 20MB", null);
            return;
        }

        MediaType mediaType = MediaType.parse(mimeType);
        RequestBody body = RequestBody.create(file, mediaType);

        String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + path;

        Request request = new Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", SUPABASE_API_KEY)
                .addHeader("Content-Type", mimeType)
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onUploadComplete(false, "Upload failed: " + e.getMessage(), null);
            }

            @Override public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    callback.onUploadComplete(false, "Upload error: " + response.message(), null);
                    return;
                }

                String publicUrl = SUPABASE_URL + "/storage/v1/object/public/" + bucket + "/" + path;
                callback.onUploadComplete(true, "Upload successful", publicUrl);
            }
        });
    }

    /**
     * Delete a file from Supabase Storage.
     * @param bucket    Bucket name
     * @param path      Full path within the bucket
     * @param callback  Result callback
     */
    public static void deleteFile(String bucket, String path, UploadCallback callback) {
        String deleteUrl = SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + path;

        Request request = new Request.Builder()
                .url(deleteUrl)
                .addHeader("Authorization", SUPABASE_API_KEY)
                .delete()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onUploadComplete(false, "Delete failed: " + e.getMessage(), null);
            }

            @Override public void onResponse(Call call, Response response) {
                callback.onUploadComplete(response.isSuccessful(), response.message(), null);
            }
        });
    }

    private static String getMimeType(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf(".");
        if (lastDot == -1) return null;

        String extension = name.substring(lastDot + 1).toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "webp": return "image/webp";
            case "gif": return "image/gif";
            case "pdf": return "application/pdf";
            default: return null;
        }
    }
}
