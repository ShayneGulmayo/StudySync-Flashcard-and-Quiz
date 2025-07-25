package com.labactivity.studysync;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.ExecutionException;

public class DocumentScanActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextRecognizer recognizer;
    private ImageCapture imageCapture;
    private String setType;
    private ImageView captureButton;

    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private String lastDetectedText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_scan);

        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.captureButton);
        setType = getIntent().getStringExtra("setType");

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }

        captureButton.setOnClickListener(v -> {
            if (!lastDetectedText.isEmpty()) {
                sendToAI(lastDetectedText);
            } else {
                Toast.makeText(this, "No text detected yet", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    if (imageProxy.getImage() != null) {
                        @SuppressLint("UnsafeOptInUsageError")
                        InputImage image = InputImage.fromMediaImage(
                                imageProxy.getImage(),
                                imageProxy.getImageInfo().getRotationDegrees()
                        );

                        recognizer.process(image)
                                .addOnSuccessListener(result -> {
                                    String text = result.getText().trim();
                                    if (!text.isEmpty()) {
                                        lastDetectedText = text;
                                    }
                                    imageProxy.close();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Text recognition failed", Toast.LENGTH_SHORT).show();
                                    imageProxy.close();
                                });
                    } else {
                        imageProxy.close();
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void sendToAI(String text) {
        Intent intent = new Intent(this, LoadingSetActivity.class);
        intent.putExtra("textPrompt", text);
        intent.putExtra("type", "text");
        intent.putExtra("setType", setType);
        startActivity(intent);
        finish();
    }
}
