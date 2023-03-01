package com.example.facedetection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.Utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    // UI view
    private ImageView originalImageIv;
    private Button detectFacesBtn;
    private ImageView croppedImageIv;

    // Tag for debugging
    private static final String TAG = "FACE_DETECT_TAG";

    //This factor is used to make the detecting image smaller, to make the process faster
    private static final int SCALING_FACTOR = 10;

    private FaceDetector detector;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Init UI Views
        originalImageIv = findViewById(R.id.originalImageIv);
        detectFacesBtn = findViewById(R.id.detectFacesBtn);
        croppedImageIv = findViewById(R.id.croppedImageIv);

        FaceDetectorOptions realTimeFdo = new FaceDetectorOptions.Builder()
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();

        // Init FaceDetector object
        detector = FaceDetection.getClient(realTimeFdo);

        // Handle click, start detecting/cropping face from original image
        detectFacesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Bitmap from drawable
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pic2);

//                // Bitmap from URI, in case you want to detect
//                // face from an image picked from gallery/camera
//                Uri imageUri = null;
//                try {
//                    Bitmap bitmap1 = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }

//                // Bitmap from ImageView, in case your image is in ImageView may be got from URL/Web
//                BitmapDrawable bitmapDrawable = (BitmapDrawable) originalImageIv.getDrawable();
//                Bitmap bitmap1 = bitmapDrawable.getBitmap();

                analyzePhoto(bitmap);
            }
        });
    }
    private void analyzePhoto(Bitmap bitmap) {
        Log.d(TAG, "analyzedPhoto: ");

        // Get smaller Bitmap to do analyze process faster
        Bitmap smallerBitmap = Bitmap.createScaledBitmap(
                bitmap,
                bitmap.getWidth()/SCALING_FACTOR,
                bitmap.getHeight()/SCALING_FACTOR,
                false);



        // Get input image using bitmap, you may use fromUri method
        InputImage inputImage = InputImage.fromBitmap(smallerBitmap, 0);

        // Start detection process
        detector.process(inputImage)
                .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                    @Override
                    public void onSuccess(List<Face> faces) {
                        // There can be multiple faces detected from an image,
                        // manage then using loop from List<Face> faces
                        Log.d(TAG, "onSuccess: No of faces detected: " + faces.size());

                        for (Face face: faces) {
                            // Get detected faces as rectangle
                            Rect rect = face.getBoundingBox();
                            rect.set(rect.left*SCALING_FACTOR,
                                    rect.top*(SCALING_FACTOR-1),
                                    rect.right*SCALING_FACTOR,
                                    (rect.bottom*SCALING_FACTOR)+90
                            );
                        }
                        cropDetectedFaces(bitmap, faces);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Detection failed
                        Log.e(TAG, "onFailure: ", e);
                        Toast.makeText(MainActivity.this, "Detection failed due to " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

    }

    private void cropDetectedFaces(Bitmap bitmap, List<Face> faces) {
        Log.d(TAG, "cropDetectedFaces: ");
        // Face was detected, Now we will crop the face part of the image
        // There can be multiple faces, you can use loop to manage each
        // I'm managing the first face from the list List<Face> faces
        Rect rect = faces.get(0).getBoundingBox();

        int x = Math.max(rect.left, 0);
        int y = Math.max(rect.top, 0);
        int width = rect.width();
        int height = rect.height();

        Bitmap croppedBitmap = Bitmap.createBitmap(
                bitmap,
                x,
                y,
                (x + width > bitmap.getWidth()) ? bitmap.getWidth() - x : width,
                (y + height > bitmap.getHeight()) ? bitmap.getHeight() - y : height
        );

//        croppedImageIv.setImageBitmap(croppedBitmap);

        try {
            // Draw bounding box on overlay detected face
            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);
            Paint p = new Paint();
            p.setStyle(Paint.Style.FILL_AND_STROKE);
            p.setAntiAlias(true);
            p.setFilterBitmap(true);
            p.setDither(true);
            p.setColor(Color.RED);
            p.setStrokeWidth(10f);

            float x1 = (float) rect.left;
            float x2 = (float) rect.right;
            float y1 = (float) rect.bottom;
            float y2 = (float) rect.top;
            canvas.drawLine(x1, y1, x2, y1, p);
            canvas.drawLine(x1, y1, x1, y2, p);
            canvas.drawLine(x1, y2, x2, y2, p);
            canvas.drawLine(x2, y2, x2, y1, p);


            croppedImageIv.setImageBitmap(mutableBitmap);
            croppedImageIv.draw(canvas);
        } catch (Exception e) {
            Log.d("You got an error: ", e.toString());
        }



        // Save the detected face to the device
        OutputStream fos;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "detected face");
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                fos = resolver.openOutputStream(Objects.requireNonNull(imageUri));
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                Objects.requireNonNull(fos);
            }
        } catch (Exception e) {
            Log.d("Error", e.toString());
        }

    }
}