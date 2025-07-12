package com.labactivity.studysync;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.labactivity.studysync.adapters.FlashcardCarouselAdapter;
import com.labactivity.studysync.models.Flashcard;
import com.labactivity.studysync.receivers.ReminderReceiver;
import com.labactivity.studysync.utils.SupabaseUploader;
import com.tbuonomo.viewpagerdotsindicator.SpringDotsIndicator;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.firebase.firestore.ListenerRegistration;

public class FlashcardPreviewActivity extends AppCompatActivity {

    private TextView titleTextView, ownerTextView, createdAtTextView, numberOfItemsTextView, privacyText, reminderTextView, downloadTxt;
    private ImageView ownerPhotoImageView, backButton, moreButton, privacyIcon, reminderIcon, saveSetBtn, downloadIcon, createdAtIcon;
    private Button startFlashcardBtn;
    private ViewPager2 carouselViewPager;
    private String currentPrivacy, setId, currentReminder, offlineFileName, ownerUid;
    private String accessLevel = "none";
    private SpringDotsIndicator dotsIndicator;
    private ListenerRegistration reminderListener;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private BottomSheetDialog bottomSheetDialog;
    private AlertDialog deleteConfirmationDialog;
    private DatePickerDialog datePickerDialog;
    private TimePickerDialog timePickerDialog;
    private boolean isSaved = false;
    private boolean isDownloaded = false;
    private boolean isOffline;
    private final ArrayList<Flashcard> flashcards = new ArrayList<>();
    private Map<String, Object> setData;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard_preview);

        auth = FirebaseAuth.getInstance();
        initializeViews();
        db = FirebaseFirestore.getInstance();

        isOffline = getIntent().getBooleanExtra("isOffline", false);

        if (isOffline) {
            offlineFileName = getIntent().getStringExtra("offlineFileName");
            if (offlineFileName != null) {
                loadOfflineSet(offlineFileName);
            } else {
                Toast.makeText(this, "No offline file specified.", Toast.LENGTH_SHORT).show();
                finish();
            }

        } else {
            if (getIntent().hasExtra("setId")) {
                setId = getIntent().getStringExtra("setId");
                fetchSetFromFirestore(setId);
                loadFlashcardSet();
                loadFlashcards();
                listenToReminderUpdates();
                checkIfSaved();
            } else {
                Toast.makeText(this, "No flashcard id provided.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (reminderListener != null) reminderListener.remove();
        if (bottomSheetDialog != null && bottomSheetDialog.isShowing()) bottomSheetDialog.dismiss();
        if (deleteConfirmationDialog != null && deleteConfirmationDialog.isShowing()) deleteConfirmationDialog.dismiss();
        if (datePickerDialog != null && datePickerDialog.isShowing()) datePickerDialog.dismiss();
        if (timePickerDialog != null && timePickerDialog.isShowing()) timePickerDialog.dismiss();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        titleTextView = findViewById(R.id.flashcard_title);
        ownerTextView = findViewById(R.id.owner_username);
        createdAtTextView = findViewById(R.id.createdAt_txt);
        createdAtIcon = findViewById(R.id.createdAt_icon);
        numberOfItemsTextView = findViewById(R.id.item_txt);
        ownerPhotoImageView = findViewById(R.id.owner_profile);
        carouselViewPager = findViewById(R.id.carousel_viewpager);
        dotsIndicator = findViewById(R.id.dots_indicator);
        startFlashcardBtn = findViewById(R.id.start_flashcard_btn);
        moreButton = findViewById(R.id.more_button);
        privacyIcon = findViewById(R.id.privacy_icon);
        privacyText = findViewById(R.id.privacy_txt);
        reminderTextView = findViewById(R.id.reminder_txt);
        reminderIcon = findViewById(R.id.reminder_icon);
        saveSetBtn = findViewById(R.id.saveSetBtn);
        downloadIcon = findViewById(R.id.download_icon);
        downloadTxt = findViewById(R.id.download_txt);

        boolean fromNotification = getIntent().getBooleanExtra("fromNotification", false);

        downloadIcon.setOnClickListener(v -> {
            Intent intent = new Intent(FlashcardPreviewActivity.this, DownloadedSetsActivity.class);
            startActivity(intent);
        });

        downloadTxt.setOnClickListener(v -> {
            Intent intent = new Intent(FlashcardPreviewActivity.this, DownloadedSetsActivity.class);
            startActivity(intent);
        });

        privacyIcon.setOnClickListener(v -> {
            startActivity(new Intent(this, PrivacyActivity.class).putExtra("setId", setId));
        });

        privacyText.setOnClickListener(v -> {
            startActivity(new Intent(this, PrivacyActivity.class).putExtra("setId", setId));
        });

        reminderIcon.setOnClickListener(v -> {
            showReminderDialog();
        });

        reminderTextView.setOnClickListener(v -> {
            showReminderDialog();
        });

        ownerTextView.setOnClickListener(v -> {
            if (ownerUid != null) {
                Intent intent = new Intent(FlashcardPreviewActivity.this, UserProfileActivity.class);
                intent.putExtra("userId", ownerUid);
                startActivity(intent);
            } else if (isOffline){
                Toast.makeText(this, "You are Offline.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "User Not Found.", Toast.LENGTH_SHORT).show();
            }
        });

        ownerPhotoImageView.setOnClickListener(v -> {
            if (ownerUid != null) {
                Intent intent = new Intent(FlashcardPreviewActivity.this, UserProfileActivity.class);
                intent.putExtra("userId", ownerUid);
                startActivity(intent);
            } else if (isOffline){
                Toast.makeText(this, "You are Offline.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "User Not Found.", Toast.LENGTH_SHORT).show();
            }
        });

        backButton.setOnClickListener(v -> {
            if (fromNotification) {
                Intent intent = new Intent(FlashcardPreviewActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
            finish();
        });

        saveSetBtn.setOnClickListener(view -> {
            toggleSaveState();
        });

        moreButton.setOnClickListener(v -> showMoreBottomSheet());

        startFlashcardBtn.setOnClickListener(v -> {
            if ("owner".equals(accessLevel) || "edit".equals(accessLevel) || "view".equals(accessLevel)) {
                Intent intent = new Intent(FlashcardPreviewActivity.this, FlashcardViewerActivity.class);
                intent.putExtra("setId", setId);

                if (isOffline) {
                    intent.putExtra("isOffline", true);
                    intent.putExtra("offlineFileName", offlineFileName);
                }

                startActivity(intent);
            } else {
                Toast.makeText(this, "You don't have access to start this flashcard set.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMoreBottomSheet() {
        if (isFinishing() || isDestroyed()) return;

        bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_more_preview, null);
        bottomSheetDialog.setContentView(view);

        TextView downloadBtn = view.findViewById(R.id.download);
        TextView copyBtn = view.findViewById(R.id.copy);
        TextView privacyBtn = view.findViewById(R.id.privacy);
        TextView reminderBtn = view.findViewById(R.id.reminder);
        TextView sendToChatBtn = view.findViewById(R.id.sendToChat);
        TextView editBtn = view.findViewById(R.id.edit);
        TextView deleteBtn = view.findViewById(R.id.delete);
        TextView reqAccessBtn = view.findViewById(R.id.reqAccess);
        TextView reqEditBtn = view.findViewById(R.id.reqEdit);

        switch (accessLevel) {
            case "owner":
                break;

            case "edit":
                privacyBtn.setVisibility(View.GONE);
                reminderBtn.setVisibility(View.GONE);
                sendToChatBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
                reqAccessBtn.setVisibility(View.GONE);
                copyBtn.setVisibility(View.VISIBLE);
                downloadBtn.setVisibility(View.VISIBLE);
                editBtn.setVisibility(View.VISIBLE);
                break;

            case "view":
                privacyBtn.setVisibility(View.GONE);
                reminderBtn.setVisibility(View.GONE);
                sendToChatBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
                editBtn.setVisibility(View.GONE);
                downloadBtn.setVisibility(View.VISIBLE);
                reqAccessBtn.setVisibility(View.GONE);
                copyBtn.setVisibility(View.VISIBLE);
                reqEditBtn.setVisibility(View.VISIBLE);
                break;

            default:
                privacyBtn.setVisibility(View.GONE);
                reminderBtn.setVisibility(View.GONE);
                sendToChatBtn.setVisibility(View.GONE);
                editBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
                downloadBtn.setVisibility(View.GONE);
                copyBtn.setVisibility(View.GONE);
                reqAccessBtn.setVisibility(View.VISIBLE);
                break;
        }

        downloadBtn.setOnClickListener(v -> {
            showDownloadOptionsDialog();
            bottomSheetDialog.dismiss();
        });

        copyBtn.setOnClickListener(v -> {
            makeCopy();
            bottomSheetDialog.dismiss();
        });

        privacyBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            startActivity(new Intent(this, PrivacyActivity.class).putExtra("setId", setId));
        });

        reminderBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showReminderDialog();
        });

        sendToChatBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, ChatRoomPickerActivity.class);
            intent.putExtra("setId", setId);
            intent.putExtra("setType", "flashcard");
            startActivity(intent);
        });

        editBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(this, CreateFlashcardActivity.class);
            intent.putExtra("setId", setId);
            startActivity(intent);
        });

        deleteBtn.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showDeleteConfirmationDialog();
        });

        reqEditBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Requst Edit clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        reqAccessBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Request Access clicked", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void showDownloadOptionsDialog() {
        String[] options = {"Download as PDF", "Download for Offline Use"};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Download Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        downloadOfflinePdf(setId);
                    } else if (which == 1) {
                        downloadSet();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void fetchSetFromFirestore(String setId) {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        setData = documentSnapshot.getData();
                        if (setData != null) {
                            setData.put("id", setId);
                            String ownerUid = documentSnapshot.getString("owner_uid");

                            if (ownerUid != null) {
                                db.collection("users").document(ownerUid)
                                        .get()
                                        .addOnSuccessListener(userDoc -> {
                                            if (userDoc.exists()) {
                                                String username = userDoc.getString("username");
                                                setData.put("username", username != null ? username : "Unknown User");
                                                String photoUrl = userDoc.getString("photoUrl");
                                                setData.put("photoUrl", photoUrl != null ? photoUrl : "");
                                            } else {
                                                setData.put("username", "Unknown User");
                                                setData.put("photoUrl", "");
                                            }
                                            loadFlashcardSet();
                                        })
                                        .addOnFailureListener(e -> {
                                            setData.put("username", "Unknown User");
                                            setData.put("photoUrl", "");
                                            loadFlashcardSet();
                                        });
                            } else {
                                setData.put("username", "Unknown User");
                                setData.put("photoUrl", "");
                                loadFlashcardSet();
                            }
                        } else {
                            Toast.makeText(this, "Set data is empty.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Set not found.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to fetch set.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void downloadSet() {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> setData = documentSnapshot.getData();
                        if (setData != null) {
                            setData.put("id", setId);

                            String ownerUid = documentSnapshot.getString("owner_uid");
                            if (ownerUid != null) {
                                db.collection("users").document(ownerUid)
                                        .get()
                                        .addOnSuccessListener(userDoc -> {
                                            if (userDoc.exists()) {
                                                String username = userDoc.getString("username");
                                                String photoUrl = userDoc.getString("photoUrl");

                                                setData.put("username", username != null ? username : "Unknown User");
                                                setData.put("photoUrl", photoUrl != null ? photoUrl : "");

                                            } else {
                                                setData.put("username", "Unknown User");
                                                setData.put("photoUrl", "");
                                            }

                                            saveSetOffline(setData, setId);

                                            Intent intent = new Intent(this, DownloadedSetsActivity.class);
                                            startActivity(intent);
                                            if (bottomSheetDialog != null) bottomSheetDialog.dismiss();

                                        })
                                        .addOnFailureListener(e -> {
                                            setData.put("username", "Unknown User");
                                            setData.put("photoUrl", "");
                                            saveSetOffline(setData, setId);

                                            Intent intent = new Intent(this, DownloadedSetsActivity.class);
                                            startActivity(intent);
                                            if (bottomSheetDialog != null) bottomSheetDialog.dismiss();
                                        });
                            } else {
                                setData.put("username", "Unknown User");
                                setData.put("photoUrl", "");
                                saveSetOffline(setData, setId);

                                Intent intent = new Intent(this, DownloadedSetsActivity.class);
                                startActivity(intent);
                                if (bottomSheetDialog != null) bottomSheetDialog.dismiss();
                            }
                        } else {
                            Toast.makeText(this, "Set data is empty.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Set not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to fetch set data.", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveSetOffline(Map<String, Object> setData, String setId) {
        File dir = getFilesDir();
        File file = new File(dir, "set_" + setId + ".json");

        if (file.exists()) {
            Toast.makeText(this, "Set already downloaded.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String json = new Gson().toJson(setData);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(json.getBytes());
            fos.close();
            Toast.makeText(this, "Set downloaded successfully.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to download set.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadOfflineSet(String fileName) {
        File dir = getFilesDir();
        File file = new File(dir, fileName);

        if (!file.exists()) {
            Toast.makeText(this, "Offline file not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();

            String json = new String(data);

            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            setData = new Gson().fromJson(json, type);

            loadFlashcardSet();
            loadFlashcards();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load offline set.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void downloadOfflinePdf(String setId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        if (title == null || title.isEmpty()) {
                            title = "Flashcard_" + setId;
                        }

                        Object termsObj = documentSnapshot.get("terms");

                        if (termsObj == null) {
                            Toast.makeText(this, "No flashcards found in this set.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        List<Object> rawTerms;

                        if (termsObj instanceof Map) {
                            Map<String, Object> termsMap = (Map<String, Object>) termsObj;
                            rawTerms = new ArrayList<>(termsMap.values());
                        } else if (termsObj instanceof List) {
                            rawTerms = (List<Object>) termsObj;
                        } else {
                            Toast.makeText(this, "Terms data is invalid format.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (rawTerms.isEmpty()) {
                            Toast.makeText(this, "No flashcards in this set.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        writePdfFile(title, rawTerms);

                    } else {
                        Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch flashcards: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                });
    }

    private void writePdfFile(String title, List<Object> termsList) {
        try {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);

            PdfDocument pdfDocument = new PdfDocument();
            Paint paint = new Paint();
            paint.setTextSize(14f);
            paint.setAntiAlias(true);

            int pageWidth = 595;
            int pageHeight = 842;
            int margin = 40;
            int y = margin;

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            Paint titlePaint = new Paint();
            titlePaint.setTextSize(18f);
            titlePaint.setFakeBoldText(true);
            titlePaint.setAntiAlias(true);
            canvas.drawText(title, margin, y, titlePaint);
            y += 40;

            for (Object termItem : termsList) {
                if (termItem instanceof Map) {
                    Map<String, Object> termMap = (Map<String, Object>) termItem;
                    String term = (String) termMap.get("term");
                    String definition = (String) termMap.get("definition");
                    String photoUrl = (String) termMap.get("photoUrl");

                    if (term != null) {
                        y = drawWrappedText(canvas, "Term: " + term, paint, margin, y, pageWidth - margin);
                        y += 10;
                    }

                    if (definition != null) {
                        y = drawWrappedText(canvas, "Definition: " + definition, paint, margin + 20, y, pageWidth - margin);
                        y += 10;
                    }

                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        try {
                            URL url = new URL(photoUrl);
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setDoInput(true);
                            connection.connect();

                            InputStream input = connection.getInputStream();
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            byte[] data = new byte[4096];
                            int n;
                            while ((n = input.read(data)) != -1) {
                                buffer.write(data, 0, n);
                            }
                            input.close();
                            byte[] imageBytes = buffer.toByteArray();

                            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                            if (bitmap == null) {
                                Log.e("PDF", "Failed to decode bitmap from URL: " + photoUrl);
                                continue;
                            }

                            bitmap = getRoundedCornerBitmap(bitmap, dpToPx(20));

                            int imageSize = 2 * 72;

                            if (y + imageSize > pageHeight - margin) {
                                pdfDocument.finishPage(page);
                                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.getPages().size() + 1).create();
                                page = pdfDocument.startPage(pageInfo);
                                canvas = page.getCanvas();
                                y = margin;
                            }

                            Rect destRect = new Rect(margin, y, margin + imageSize, y + imageSize);
                            canvas.drawBitmap(bitmap, null, destRect, null);
                            y += imageSize + 20;

                        } catch (Exception e) {
                            Log.e("PDF", "Error downloading image: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    if (y > pageHeight - margin) {
                        pdfDocument.finishPage(page);
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.getPages().size() + 1).create();
                        page = pdfDocument.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = margin;
                    }
                }
            }

            pdfDocument.finishPage(page);

            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = safeTitle + ".pdf";

            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }

            if (uri != null) {
                OutputStream outputStream = resolver.openOutputStream(uri);
                pdfDocument.writeTo(outputStream);
                pdfDocument.close();
                outputStream.close();

                Toast.makeText(this, "PDF downloaded to Downloads/" + fileName, Toast.LENGTH_LONG).show();
                openPdfFile(uri);
            } else {
                Toast.makeText(this, "Failed to create PDF file.", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Failed to generate PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private Bitmap getRoundedCornerBitmap(Bitmap bitmap, int cornerRadius) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private int drawWrappedText(Canvas canvas, String text, Paint paint, int x, int y, int rightMargin) {
        int maxWidth = rightMargin - x;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (paint.measureText(line + word + " ") > maxWidth) {
                canvas.drawText(line.toString(), x, y, paint);
                y += 20;
                line = new StringBuilder();
            }
            line.append(word).append(" ");
        }
        if (!line.toString().isEmpty()) {
            canvas.drawText(line.toString(), x, y, paint);
            y += 20;
        }
        return y;
    }

    private void openPdfFile(Uri uri) throws ActivityNotFoundException {
        if (uri == null) {
            Toast.makeText(this, "File not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "Open PDF File"));
    }

    private void makeCopy() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DocumentReference userRef = db.collection("users").document(userId);

        userRef.get()
                .addOnSuccessListener(userSnapshot -> {
                    if (!userSnapshot.exists()) {
                        Toast.makeText(this, "User record not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Now fetch the original flashcard set
                    db.collection("flashcards").document(setId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (!documentSnapshot.exists()) {
                                    Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Map<String, Object> originalData = documentSnapshot.getData();
                                if (originalData == null) {
                                    Toast.makeText(this, "No data to copy.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Map<String, Object> copyData = new HashMap<>(originalData);
                                copyData.remove("reminder");
                                copyData.put("owner_uid", userId);
                                copyData.put("createdAt", Timestamp.now());
                                copyData.put("privacy", "private");
                                copyData.put("privacyRole", "view");
                                copyData.put("accessUsers", new HashMap<String, Object>());

                                db.collection("flashcards").add(copyData)
                                        .addOnSuccessListener(newDocRef -> {
                                            Map<String, Object> ownedSetData = new HashMap<>();
                                            ownedSetData.put("id", newDocRef.getId());
                                            ownedSetData.put("type", "flashcard");

                                            db.collection("users").document(userId)
                                                    .update("owned_sets", FieldValue.arrayUnion(ownedSetData))
                                                    .addOnSuccessListener(unused -> {
                                                        Toast.makeText(this, "Flashcard set copied successfully!", Toast.LENGTH_SHORT).show();
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Toast.makeText(this, "Copy succeeded but failed to update owned sets.", Toast.LENGTH_SHORT).show();
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(this, "Failed to copy flashcard set.", Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error fetching original flashcard set.", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching user data.", Toast.LENGTH_SHORT).show();
                });
    }

    private void showReminderDialog() {
        if (isFinishing() || isDestroyed()) return;

        Calendar calendar = Calendar.getInstance();

        datePickerDialog = new DatePickerDialog(this, R.style.DialogTheme, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            timePickerDialog = new TimePickerDialog(this, R.style.DialogTheme, (timeView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);

                if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
                    Toast.makeText(this, "Please select a future time.", Toast.LENGTH_SHORT).show();
                    return;
                }

                setReminder(calendar);

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false);

            if (!isFinishing() && !isDestroyed()) timePickerDialog.show();

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        if (!isFinishing() && !isDestroyed()) datePickerDialog.show();
    }

    private void listenToReminderUpdates() {
        if (setId == null) return;

        reminderListener = db.collection("flashcards").document(setId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        Log.e("ReminderListener", "Error or document missing");
                        return;
                    }

                    String reminder = snapshot.getString("reminder");
                    if (reminder != null && !reminder.isEmpty()) {
                        reminderTextView.setText("Reminder: " + reminder);
                        reminderIcon.setImageResource(R.drawable.notifications);
                    } else {
                        reminderTextView.setText("Reminder: None");
                        reminderIcon.setImageResource(R.drawable.off_notifications);
                    }
                });
    }

    @SuppressLint("ScheduleExactAlarm")
    private void setReminder(Calendar calendar) {
        String formattedDateTime = formatDateTime(calendar);

        db.collection("flashcards").document(setId)
                .update("reminder", formattedDateTime)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Reminder set for " + formattedDateTime, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to set reminder.", Toast.LENGTH_SHORT).show();
                });

        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("setId", setId);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, setId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
    }

    private String formatDateTime(Calendar calendar) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy | hh:mm a", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    private void showDeleteConfirmationDialog() {
        if (isFinishing() || isDestroyed()) return;

        deleteConfirmationDialog = new AlertDialog.Builder(this)
                .setTitle("Delete Flashcard Set")
                .setMessage("Are you sure you want to delete this flashcard set? This action cannot be undone.")
                .setPositiveButton("Yes", (dialog, which) -> deleteFlashcardSet())
                .setNegativeButton("No", null)
                .create();

        deleteConfirmationDialog.show();
    }

    private void deleteFlashcardSet() {
        db.collection("flashcards").document(setId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> data = documentSnapshot.getData();
                    if (data != null && data.containsKey("terms")) {
                        Object termsObj = data.get("terms");
                        if (termsObj instanceof Map) {
                            Map<String, Object> terms = (Map<String, Object>) termsObj;
                            for (Map.Entry<String, Object> entry : terms.entrySet()) {
                                Object value = entry.getValue();
                                if (value instanceof Map) {
                                    Map<String, Object> termEntry = (Map<String, Object>) value;
                                    String photoPath = termEntry.get("photoPath") != null ? termEntry.get("photoPath").toString() : null;
                                    if (photoPath != null && !photoPath.isEmpty()) {
                                        SupabaseUploader.deleteFile("flashcard-images", photoPath, new SupabaseUploader.UploadCallback() {
                                            @Override
                                            public void onUploadComplete(boolean success, String message, String publicUrl) {
                                                if (success) {
                                                    Log.d("Supabase", "Deleted image: " + photoPath);
                                                } else {
                                                    Log.e("Supabase", "Failed to delete image: " + photoPath + " Reason: " + message);
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    }

                    db.collection("flashcards").document(setId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                String userId = auth.getCurrentUser().getUid();
                                DocumentReference userRef = db.collection("users").document(userId);

                                db.runTransaction(transaction -> {
                                    DocumentSnapshot snapshot = transaction.get(userRef);
                                    List<Map<String, Object>> ownedSets = (List<Map<String, Object>>) snapshot.get("owned_sets");

                                    if (ownedSets != null) {
                                        List<Map<String, Object>> updatedOwnedSets = new ArrayList<>();
                                        for (Map<String, Object> item : ownedSets) {
                                            if (!item.get("id").equals(setId)) {
                                                updatedOwnedSets.add(item);
                                            }
                                        }
                                        transaction.update(userRef, "owned_sets", updatedOwnedSets);
                                    }
                                    return null;
                                }).addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Flashcard set deleted.", Toast.LENGTH_SHORT).show();
                                    finish();
                                }).addOnFailureListener(e -> {
                                    Toast.makeText(this, "Failed to clean owned_sets.", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to delete flashcard set.", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error fetching flashcard set.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadFlashcardSet() {
        if (isOffline) {
            String title = (String) setData.get("title");
            titleTextView.setText(title != null ? title : "Untitled");

            Long numberOfItems = setData.get("number_of_items") instanceof Number ? ((Number) setData.get("number_of_items")).longValue() : 0;
            numberOfItemsTextView.setText(numberOfItems + (numberOfItems == 1 ? " item" : " items"));

            String ownerName = (String) setData.get("username");
            ownerTextView.setText(ownerName != null ? ownerName : "Unknown");

            String photoUrl = (String) setData.get("photoUrl");
            if (photoUrl != null && !photoUrl.isEmpty()) {
                Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.user_profile)
                        .circleCrop()
                        .into(ownerPhotoImageView);
            } else {
                ownerPhotoImageView.setImageResource(R.drawable.user_profile);
            }

            Object createdAtObj = setData.get("createdAt");
            if (createdAtObj != null) {
                createdAtTextView.setText(createdAtObj.toString());
            } else {
                createdAtTextView.setText("Unknown");
            }

            downloadIcon.setImageResource(R.drawable.downloaded);
            downloadTxt.setText(R.string.downloaded);

            if (currentReminder != null && !currentReminder.isEmpty()) {
                reminderTextView.setText("Reminder: " + currentReminder);
                reminderIcon.setImageResource(R.drawable.notifications);
            } else {
                reminderTextView.setText("Reminder: None");
                reminderIcon.setImageResource(R.drawable.off_notifications);
            }

            moreButton.setVisibility(View.GONE);
            privacyText.setVisibility(View.GONE);
            privacyIcon.setVisibility(View.GONE);
            saveSetBtn.setVisibility(View.GONE);
            createdAtTextView.setVisibility(View.GONE);
            createdAtIcon.setVisibility(View.GONE);

            accessLevel = "view";

            loadFlashcards();

        } else {
            db.collection("flashcards").document(setId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (!documentSnapshot.exists()) {
                            Toast.makeText(this, "Flashcard not found", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        ownerUid = documentSnapshot.getString("owner_uid");

                        String title = documentSnapshot.getString("title");
                        String ownerUid = documentSnapshot.getString("owner_uid");
                        Long numberOfItems = documentSnapshot.getLong("number_of_items");
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

                        titleTextView.setText(title != null ? title : "Untitled");

                        currentPrivacy = documentSnapshot.getString("privacy") != null ? documentSnapshot.getString("privacy") : "Public";
                        currentReminder = documentSnapshot.getString("reminder");

                        File file = new File(getFilesDir(), "set_" + setId + ".json");
                        if (file.exists()) {
                            downloadIcon.setImageResource(R.drawable.downloaded);
                            downloadTxt.setText(R.string.downloaded);
                        } else {
                            downloadIcon.setImageResource(R.drawable.download);
                            downloadTxt.setText(R.string.not_download);
                        }

                        if (currentUser != null && ownerUid != null) {
                            if (ownerUid.equals(currentUser.getUid())) {
                                accessLevel = "owner";
                                saveSetBtn.setVisibility(View.GONE);
                            } else {
                                accessLevel = "view";
                                saveSetBtn.setVisibility(View.VISIBLE);
                            }
                        } else {
                            accessLevel = "view";
                            saveSetBtn.setVisibility(View.VISIBLE);
                        }

                        loadFlashcards();

                        if (numberOfItems != null) {
                            numberOfItemsTextView.setText(numberOfItems + (numberOfItems == 1 ? " item" : " items"));
                        } else {
                            numberOfItemsTextView.setText("0 items");
                        }

                        if (currentReminder != null && !currentReminder.isEmpty()) {
                            reminderTextView.setText("Reminder: " + currentReminder);
                            reminderIcon.setImageResource(R.drawable.notifications);
                        } else {
                            reminderTextView.setText("Reminder: None");
                            reminderIcon.setImageResource(R.drawable.off_notifications);
                        }

                        if ("private".equals(currentPrivacy)) {
                            privacyIcon.setImageResource(R.drawable.lock);
                            privacyText.setText("Private");
                        } else {
                            privacyIcon.setImageResource(R.drawable.public_icon);
                            privacyText.setText("Public");
                        }

                        if (ownerUid != null) {
                            db.collection("users").document(ownerUid)
                                    .get()
                                    .addOnSuccessListener(userDoc -> {
                                        if (userDoc.exists()) {
                                            String username = userDoc.getString("username");
                                            ownerTextView.setText(username != null ? username : "Unknown");
                                        } else {
                                            ownerTextView.setText("Unknown");
                                        }
                                    });
                        } else {
                            ownerTextView.setText("Unknown");
                        }

                        loadOwnerProfile(ownerUid);

                        String currentUserId = auth.getCurrentUser().getUid();

                        if (ownerUid != null && ownerUid.equals(currentUserId)) {
                            accessLevel = "owner";
                        } else if ("public".equals(currentPrivacy)) {
                            String privacyRole = documentSnapshot.getString("privacyRole");
                            if ("edit".equalsIgnoreCase(privacyRole)) {
                                accessLevel = "edit";
                            } else if ("view".equalsIgnoreCase(privacyRole)) {
                                accessLevel = "view";
                            } else {
                                accessLevel = "none";
                            }
                        } else {
                            Map<String, String> accessUsers = (Map<String, String>) documentSnapshot.get("accessUsers");
                            if (accessUsers != null && accessUsers.containsKey(currentUserId)) {
                                String userRole = accessUsers.get(currentUserId);
                                if ("edit".equalsIgnoreCase(userRole)) {
                                    accessLevel = "edit";
                                } else if ("view".equalsIgnoreCase(userRole)) {
                                    accessLevel = "view";
                                } else {
                                    accessLevel = "none";
                                }
                            } else {
                                accessLevel = "none";
                            }
                        }

                        if ("none".equals(accessLevel)) {
                            Intent intent = new Intent(this, NoAccessActivity.class);
                            intent.putExtra("setId", setId);
                            startActivity(intent);
                            finish();
                            return;
                        }

                        loadFlashcards();

                        Object createdAtObj = documentSnapshot.get("createdAt");
                        if (createdAtObj instanceof Timestamp) {
                            Timestamp createdAtTimestamp = (Timestamp) createdAtObj;
                            String formattedDate = new SimpleDateFormat("MM/dd/yyyy | hh:mm a", Locale.getDefault()).format(createdAtTimestamp.toDate());
                            createdAtTextView.setText(formattedDate);
                        } else if (createdAtObj instanceof String) {
                            createdAtTextView.setText((String) createdAtObj);
                        } else {
                            createdAtTextView.setText("Unknown");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to load flashcard", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        }
    }

    private void loadFlashcards() {
        if (isOffline) {
            Object termsObj = setData.get("terms");
            if (termsObj instanceof Map) {
                Map<String, Object> terms = (Map<String, Object>) termsObj;
                flashcards.clear();

                for (Map.Entry<String, Object> entry : terms.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Map) {
                        Map<String, Object> termEntry = (Map<String, Object>) value;
                        String term = termEntry.get("term") != null ? termEntry.get("term").toString() : "";
                        String definition = termEntry.get("definition") != null ? termEntry.get("definition").toString() : "";
                        String photoUrl = termEntry.get("photoUrl") != null ? termEntry.get("photoUrl").toString() : "";
                        String photoPath = termEntry.get("photoPath") != null ? termEntry.get("photoPath").toString() : "";
                        flashcards.add(new Flashcard(term, definition, photoUrl, photoPath));
                    }
                }
            }

            Log.d("Flashcards", "Loaded flashcards offline: " + flashcards.size());
            if (!flashcards.isEmpty()) {
                setupCarousel();
            } else {
                Toast.makeText(this, "No flashcards found in this set.", Toast.LENGTH_SHORT).show();
            }
        } else {
            db.collection("flashcards").document(setId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.exists()) {
                            Toast.makeText(this, "Flashcard set not found.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Map<String, Object> data = snapshot.getData();
                        if (data != null && data.containsKey("terms")) {
                            Object termsObj = data.get("terms");
                            if (termsObj instanceof Map) {
                                Map<String, Object> terms = (Map<String, Object>) termsObj;
                                flashcards.clear();

                                for (Map.Entry<String, Object> entry : terms.entrySet()) {
                                    Object value = entry.getValue();
                                    if (value instanceof Map) {
                                        Map<String, Object> termEntry = (Map<String, Object>) value;
                                        String term = termEntry.get("term") != null ? termEntry.get("term").toString() : "";
                                        String definition = termEntry.get("definition") != null ? termEntry.get("definition").toString() : "";
                                        String photoUrl = termEntry.get("photoUrl") != null ? termEntry.get("photoUrl").toString() : "";
                                        String photoPath = termEntry.get("photoPath") != null ? termEntry.get("photoPath").toString() : "";
                                        flashcards.add(new Flashcard(term, definition, photoUrl, photoPath));
                                    }
                                }
                            }
                        }

                        Log.d("Flashcards", "Loaded flashcards: " + flashcards.size());

                        if (!flashcards.isEmpty()) {
                            setupCarousel();
                        } else {
                            Toast.makeText(this, "No flashcards found in this set.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to load flashcards.", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    });
        }
    }

    private void loadOwnerProfile(String ownerUid) {
        if (ownerUid == null) {
            ownerPhotoImageView.setImageResource(R.drawable.user_profile);
            return;
        }

        db.collection("users").document(ownerUid)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        String photoUrl = userDoc.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(photoUrl)
                                    .placeholder(R.drawable.user_profile)
                                    .circleCrop()
                                    .into(ownerPhotoImageView);
                        } else {
                            ownerPhotoImageView.setImageResource(R.drawable.user_profile);
                        }
                    } else {
                        ownerPhotoImageView.setImageResource(R.drawable.user_profile);
                    }
                })
                .addOnFailureListener(e -> ownerPhotoImageView.setImageResource(R.drawable.user_profile));
    }

    private void setupCarousel() {
        FlashcardCarouselAdapter carouselAdapter = new FlashcardCarouselAdapter(flashcards);
        carouselViewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        carouselViewPager.setAdapter(carouselAdapter);
        dotsIndicator.setViewPager2(carouselViewPager);
    }

    private void updateSaveIcon() {
        if (isSaved) {
            saveSetBtn.setImageResource(R.drawable.bookmark_filled);
        } else {
            saveSetBtn.setImageResource(R.drawable.bookmark);
        }
    }

    private void toggleSaveState() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DocumentReference userRef = db.collection("users").document(userId);

        userRef.get().addOnSuccessListener(documentSnapshot -> {
            List<Map<String, Object>> savedSets = (List<Map<String, Object>>) documentSnapshot.get("saved_sets");
            if (savedSets == null) {
                savedSets = new ArrayList<>();
            }

            Map<String, Object> setData = new HashMap<>();
            setData.put("id", setId);
            setData.put("type", "flashcard");

            if (isSaved) {
                Iterator<Map<String, Object>> iterator = savedSets.iterator();
                while (iterator.hasNext()) {
                    Map<String, Object> item = iterator.next();
                    if (setId.equals(item.get("id"))) {
                        iterator.remove();
                        break;
                    }
                }

                userRef.update("saved_sets", savedSets)
                        .addOnSuccessListener(unused -> {
                            isSaved = false;
                            updateSaveIcon();
                            Toast.makeText(this, "Set unsaved.", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to unsave.", Toast.LENGTH_SHORT).show());

            } else {
                boolean alreadySaved = false;
                for (Map<String, Object> item : savedSets) {
                    if (setId.equals(item.get("id"))) {
                        alreadySaved = true;
                        break;
                    }
                }

                if (!alreadySaved) {
                    savedSets.add(setData);
                }

                userRef.update("saved_sets", savedSets)
                        .addOnSuccessListener(unused -> {
                            isSaved = true;
                            updateSaveIcon();
                            Toast.makeText(this, "Set saved!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Failed to save.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void checkIfSaved() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    List<Map<String, Object>> savedSets = (List<Map<String, Object>>) documentSnapshot.get("saved_sets");
                    isSaved = false;
                    if (savedSets != null) {
                        for (Map<String, Object> item : savedSets) {
                            if (setId.equals(item.get("id"))) {
                                isSaved = true;
                                break;
                            }
                        }
                    }
                    updateSaveIcon();
                });
    }
}