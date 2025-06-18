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
    private static final String BUCKET_NAME = "user-files";
    private static final String SUPABASE_API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFnbm9zeWx0aWtld2hkem1kY3dwIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc0NjE0OTE3MSwiZXhwIjoyMDYxNzI1MTcxfQ.qkGXFasln2NZxBO7K9y97KLxhJ6-7HHr4FNVAGc4W08";
    public static void uploadFile(File file, String fileName, UploadCallback callback) {
        OkHttpClient client = new OkHttpClient();

        // 1. Validate file type
        String mimeType = getMimeType(file);
        if (mimeType == null) {
            callback.onUploadComplete(false, "Unsupported file type");
            return;
        }

        // 2. Validate file size (max 5MB)
        long maxFileSize = 5 * 1024 * 1024; // 5MB
        if (file.length() > maxFileSize) {
            callback.onUploadComplete(false, "File too large. Maximum allowed is 5MB.");
            return;
        }

        // 3. Prepare and send request
        MediaType mediaType = MediaType.parse(mimeType);
        RequestBody body = RequestBody.create(file, mediaType);

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + fileName)
                .addHeader("Authorization", "Bearer " + SUPABASE_API_KEY)
                .addHeader("Content-Type", mimeType)
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onUploadComplete(false, "Upload failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                callback.onUploadComplete(response.isSuccessful(), response.message());
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
            default: return null;
        }
    }



    public interface UploadCallback {
        void onUploadComplete(boolean success, String message);
    }

    public static void deleteFile(String fileName, UploadCallback callback) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + fileName)
                .delete()
                .addHeader("Authorization", "Bearer " + SUPABASE_API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onUploadComplete(false, "Delete failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                callback.onUploadComplete(response.isSuccessful(), response.message());
            }
        });
    }

}
