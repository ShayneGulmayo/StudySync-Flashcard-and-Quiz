package com.labactivity.studysync;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;

import okhttp3.*;

public class SupabaseStorageHelper {

    private static final String TAG = "SupabaseStorageHelper";

    private static final String SUPABASE_URL = "https://agnosyltikewhdzmdcwp.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFnbm9zeWx0aWtld2hkem1kY3dwIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc0NjE0OTE3MSwiZXhwIjoyMDYxNzI1MTcxfQ.qkGXFasln2NZxBO7K9y97KLxhJ6-7HHr4FNVAGc4W08";
    private static final String BUCKET_NAME = "chat-room-photos";
    private static final String FOLDER = "chat-room-profile";

    private final OkHttpClient client = new OkHttpClient();

    public interface UploadCallback {
        void onSuccess(String publicUrl);
        void onFailure(String errorMessage);
    }

    public void uploadChatRoomImage(@NonNull File imageFile, @NonNull UploadCallback callback) {
        String fileName = UUID.randomUUID().toString() + ".jpg";
        String path = FOLDER + "/" + fileName;

        MediaType mediaType = MediaType.parse("image/jpeg");

        byte[] fileBytes;
        try {
            FileInputStream fis = new FileInputStream(imageFile);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            fis.close();
            fileBytes = bos.toByteArray();
        } catch (IOException e) {
            callback.onFailure("File read error: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(fileBytes, mediaType);
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_NAME + "/" + path)
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("Content-Type", "image/jpeg")
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure("Upload failed: " + e.getMessage());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful()) {
                    callback.onFailure("Upload error: " + response.code() + " " + response.message());
                    return;
                }

                String publicUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_NAME + "/" + path;
                callback.onSuccess(publicUrl);
            }
        });
    }
}
