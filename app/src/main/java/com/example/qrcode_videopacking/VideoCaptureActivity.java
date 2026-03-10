package com.example.qrcode_videopacking;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraEffect;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.effects.OverlayEffect;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.ReturnCode;
import com.example.qrcode_videopacking.data.RestApi;
import com.example.qrcode_videopacking.data.RetroFit;
import com.example.qrcode_videopacking.libs.DatabaseHandler;
import com.example.qrcode_videopacking.libs.GalleryFilePath;
import com.example.qrcode_videopacking.model.ResponseCheckBeforeRecord;
import com.example.qrcode_videopacking.model.ResponseUploadChunkFile;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VideoCaptureActivity extends AppCompatActivity {
    final private int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    ExecutorService service;
    Recording recording = null;
    VideoCapture<Recorder> videoCapture = null;
    ImageButton capture, toggleFlash, flipCamera;
    PreviewView previewView;
    int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private final ActivityResultLauncher<String> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera(cameraFacing);
        }
    });
    String qrcode = "";
    String video_packing = "";
    CountDownTimer currentRecordCountDownTimer;
    CountDownTimer waitToStartCountDownTimer;
    CountDownTimer waitToStopCountDownTimer;
    CountDownTimer waitToCloseDialogUploading;
    CountDownTimer waitToCloseDialogCompressing;
    MediaPlayer mp_start;
    MediaPlayer mp_stop;
    MediaPlayer mp_tick;
    MediaPlayer mp_warn;
    int count_of_files = 5;
    int[] progress_upload_file = new int[count_of_files];
    boolean isRecord = false;
    boolean prosesStop = false;
    long maxDuration = 600000;
    int detik = 0;
    boolean waitToStart = false;
    boolean waitToStop  = false;
    Context context;
    DatabaseHandler dh;
    ProgressBar pbVideoUpload;
    TextView stVideoUpload;
    TextView stAllFiles;
    TextView ttlVideoUpload;

    RelativeLayout panelUpload;
    RelativeLayout panelCompressing;
    TextView ttlCompressing;

    private ActivityResultLauncher<Intent> storageActivityResultLauncher;

    private boolean isRunning = false;
    private boolean isDrawing = false;
    private DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    private Paint paint = new Paint();
    private String currentDate = dateFormat.format(Calendar.getInstance().getTime());

    // 1. Initialize Handler and Runnable
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            // 2. Define the task (update UI)
            currentDate = dateFormat.format(Calendar.getInstance().getTime());

            // 3. Schedule next execution in 1000ms (1 second)
            handler.postDelayed(this, 1000);

            isDrawing = false;
        }
    };

    private void checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                storageActivityResultLauncher.launch(intent);
            } else {
                // All files access already granted
                //Log.e("RERE", "All files access already granted");
                //Toast.makeText(this, "All files access already granted", Toast.LENGTH_SHORT).show();
            }
        } else {
            // For Android versions below 11, use traditional permissions
            // ActivityCompat.requestPermissions(...)
        }
    }

    private int total_file_compressed = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video_capture);

        storageActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        // All files access granted
                        Toast.makeText(this, "All files access granted", Toast.LENGTH_SHORT).show();
                    } else {
                        // All files access denied
                        Toast.makeText(this, "All files access denied", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        );

        Objects.requireNonNull(getSupportActionBar()).hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        builder.detectFileUriExposure();

        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setTextSize(45f);
        paint.setAntiAlias(true);

        pbVideoUpload    = findViewById(R.id.pbVideoUpload);
        stVideoUpload    = findViewById(R.id.stVideoUpload);
        stAllFiles       = findViewById(R.id.stAllFiles);
        panelUpload      = findViewById(R.id.panelUpload);
        ttlVideoUpload   = findViewById(R.id.titleVideoUpload);
        panelCompressing = findViewById(R.id.panelCompress);
        ttlCompressing   = findViewById(R.id.titleCompressing);

        panelUpload.setVisibility(View.GONE);
        panelCompressing.setVisibility(View.GONE);

        context = VideoCaptureActivity.this;
        dh = new DatabaseHandler(context);
        dh.createTable();

        mp_start = MediaPlayer.create(this, R.raw.start);
        mp_stop = MediaPlayer.create(this, R.raw.stop);
        mp_tick = MediaPlayer.create(this, R.raw.tick);
        mp_warn = MediaPlayer.create(this, R.raw.warn);

        previewView = findViewById(R.id.viewFinder);
        capture = findViewById(R.id.capture);
        toggleFlash = findViewById(R.id.toggleFlash);
        flipCamera = findViewById(R.id.flipCamera);
        capture.setOnClickListener(view -> {
            if (total_file_compressed>2) {
                showErrorDialog("Error", "Mohon tunggu sejenak.");
            } else {
                qrcode = "";
                isDrawing = false;
                captureVideo();
            }
        });

        flipCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                    cameraFacing = CameraSelector.LENS_FACING_FRONT;
                } else {
                    cameraFacing = CameraSelector.LENS_FACING_BACK;
                }
                startCamera(cameraFacing);
            }
        });

        service = Executors.newSingleThreadExecutor();

        currentRecordCountDownTimer = new CountDownTimer(maxDuration, 1000) { // 30 seconds, 1-second intervals
            public void onTick(long millisUntilFinished) {
                detik = (int) millisUntilFinished / 1000;
                Log.i("TIME COUNTDOWN", detik + " seconds remaining to stop");
                if(detik==3) {
                    mp_warn.start();
                } else
                if (detik>3) {
                    mp_tick.start();
                }
            }

            public void onFinish() {
                Log.i("TIME COUNTDOWN", "Timer finished!");
                captureVideo();
            }
        };

        waitToStartCountDownTimer = new CountDownTimer(3000, 1000) { // 30 seconds, 1-second intervals
            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                waitToStart = false;
                waitToStop  = false;
            }
        };

        waitToStopCountDownTimer = new CountDownTimer(500, 500) { // 30 seconds, 1-second intervals
            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                waitToStart = true;
                prosesStop = true;
                captureVideo();
            }
        };

        waitToCloseDialogUploading = new CountDownTimer(1000, 1000) { // 30 seconds, 1-second intervals
            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                panelUpload.setVisibility(View.GONE);
            }
        };

        waitToCloseDialogCompressing = new CountDownTimer(3000, 1000) { // 30 seconds, 1-second intervals
            public void onTick(long millisUntilFinished) {
            }

            public void onFinish() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(total_file_compressed==0) {
                            panelCompressing.setVisibility(View.GONE);
                        } else {
                            if(panelCompressing.getVisibility()==View.GONE) {
                                panelCompressing.setVisibility(View.VISIBLE);
                            }
                            ttlCompressing.setText(total_file_compressed+ " file "+(total_file_compressed>1?"s":"")+" compressing...");
                        }
                    }
                });
            }
        };

        for(int i=0; i<count_of_files; i++) {
            progress_upload_file[i] = -1;
        }

        checkAndRequestStoragePermission();
        insertDummyContactWrapper();

        String filePath = "/storage/emulated/0/Movies/CameraX-Video";
        File directory = new File(filePath);
        // Check if the directory exists and is actually a directory
        if (directory.exists() && directory.isDirectory()) {
            // Get all files and directories in the directory
            File[] files = directory.listFiles();

            if (files != null) {
                // Iterate over the results and add only directories to the list
                for (File file : files) {
                    //if (file.isDirectory()) {
                        Log.d("LISTDIR", file.getName());   
                    //}
                }
            }
        }


        /*String filePath = "/storage/emulated/0/Movies/CameraX-Video/bigfile.mp4";
        File file = new File(filePath);
        if(file.exists()) {
            Log.e("CIMOY", filePath);
            Uri mFileCaptured = Uri.fromFile(file);
            uploadChuckFile_(mFileCaptured, 0, "JXJXJXJX");
        } else {
            Log.e("CIMOY", "zonkkkkk");
        }*/
    }

    private void startTimer() {
        if (!isRunning) {
            handler.postDelayed(timerRunnable, 1000);
            isRunning = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer(); // Recommended to stop when activity is not visible
    }

    private void stopTimer() {
        // 4. Stop the timer to prevent memory leaks or unwanted background activity
        handler.removeCallbacks(timerRunnable);
        isRunning = false;
    }

    public void captureVideo() {
        Recording recording1 = recording;
        if (recording1 != null) {
            recording1.stop();
            recording = null;
            return;
        }

        isRecord = true;
        prosesStop = false;
        mp_start.start();
        currentRecordCountDownTimer.start();
        capture.setImageResource(R.drawable.round_stop_circle_24);
        video_packing = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.getDefault()).format(System.currentTimeMillis()) + "_" + qrcode;
        checkBeforeRecord(qrcode);

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, video_packing);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video");
        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues).build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        recording = videoCapture.getOutput().prepareRecording(this, options).withAudioEnabled().start(ContextCompat.getMainExecutor(this), videoRecordEvent -> {
            if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                capture.setEnabled(true);
            } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                if (!((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                    Uri mFileCaptured = ((VideoRecordEvent.Finalize) videoRecordEvent).getOutputResults().getOutputUri();
                    dh.inserUploadtData(mFileCaptured.toString());
                    for(int i=0; i<count_of_files; i++) {
                        if(progress_upload_file[i]==-1) {
                            progress_upload_file[i]=0;
                            compressFile_(mFileCaptured, qrcode, i);
                            break;
                        }
                    }
                } else {
                    recording.close();
                    recording = null;
                    String msg = "Error: " + ((VideoRecordEvent.Finalize) videoRecordEvent).getError();
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }

                waitToStartCountDownTimer.start();
                currentRecordCountDownTimer.cancel();
                mp_stop.start();

                capture.setImageResource(R.drawable.round_fiber_manual_record_24);
                isRecord = false;
                prosesStop = false;
                qrcode = "";
                isDrawing = false;
            }
        });
    }

    private void showErrorDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        //builder.setIcon(R.drawable.ic_error); // Optional: add an error icon (make sure you have this in your drawables)
        builder.setCancelable(false); // Prevents closing the dialog by tapping outside or pressing the back button

        // Add a button to dismiss the dialog
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Action when the user clicks "OK" (optional)
            dialog.dismiss();
        });

        // Create and show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void startCamera(int cameraFacing) {
        ListenableFuture<ProcessCameraProvider> processCameraProvider = ProcessCameraProvider.getInstance(this);
        processCameraProvider.addListener(() -> {
            try {

                OverlayEffect overlayEffect = getOverlayEffect();

                ProcessCameraProvider cameraProvider = processCameraProvider.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                //.setTargetResolution(new Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new QRCodeImageAnalyzer(new QRCodeFoundListener() {
                    @Override
                    public void onQRCodeFound(String _qrCode) {
                        int condition = (int) maxDuration/1000;
                        if(detik<condition-10 && _qrCode.equalsIgnoreCase(qrcode)) {
                            if(!waitToStop) {
                                if (isRecord && !prosesStop) {
                                    waitToStop = true;
                                    waitToStopCountDownTimer.start();
                                }
                            }

                            return;
                        }

                        if(!waitToStart && !isRecord) {
                            if (total_file_compressed>2) {
                                showErrorDialog("Error", "Mohon tunggu sejenak.");
                            } else {
                                qrcode = _qrCode;
                                isDrawing = false;
                                captureVideo();


                            }
                        }
                    }

                    @Override
                    public void qrCodeNotFound() {
                        //qrCodeFoundButton.setVisibility(View.INVISIBLE);
                    }
                }));

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.LOWEST))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                // Build the use case group and apply the effect
                UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(videoCapture)
                        .addUseCase(imageAnalysis)
                        // Add other use cases like ImageCapture or VideoCapture as needed
                        .addEffect(overlayEffect) // Apply the effect here
                        .build();

                cameraProvider.unbindAll();
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraFacing).build();
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup);
                toggleFlash.setOnClickListener(view -> toggleFlash(camera));
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }

        }, ContextCompat.getMainExecutor(this));
    }

    @NonNull
    private OverlayEffect getOverlayEffect() {

        OverlayEffect overlayEffect = new OverlayEffect(
                CameraEffect.PREVIEW | CameraEffect.VIDEO_CAPTURE,
                0,
                handler,
                null);

        overlayEffect.setOnDrawListener(frame -> {
            if(!isDrawing) {
                Canvas canvas = frame.getOverlayCanvas();
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                float left = 60f;
                float top = (canvas.getHeight() / 1.25f);
                canvas.rotate(-90, left, top);

                canvas.drawText(currentDate, left, top, paint);
                canvas.drawText(qrcode, left, top + left - 10f, paint);

                Log.e("CHECKPOINT", currentDate);
                isDrawing = true;
            }
            return true;
        });

        return overlayEffect;
    }

    public static String getFileNameWithoutExtension(String fileNameWithExtension) {
        int dotIndex = fileNameWithExtension.lastIndexOf('.');
        if (dotIndex > 0) { // Check if a dot exists and is not at the beginning
            return fileNameWithExtension.substring(0, dotIndex);
        }
        return fileNameWithExtension; // No extension found, return original filename
    }

    void compressFile_(Uri mFileCapture, String qrcode, int index_of_file) {
        total_file_compressed++;

        // Create a new Thread
        Thread backgroundThread = new Thread(new Runnable() {
            @Override
            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(panelCompressing.getVisibility()==View.GONE) {
                            panelCompressing.setVisibility(View.VISIBLE);
                        }
                        ttlCompressing.setText(total_file_compressed+ " file"+(total_file_compressed>1?"s":"")+" compressing...");
                    }
                });

                File file              = new File(Objects.requireNonNull(GalleryFilePath.getPath(context, mFileCapture)));
                String inputFilePath   = file.getAbsolutePath();
                String outputFilePath  = inputFilePath.substring(0, inputFilePath.length()-4)+"_compressed.mp4";
                String command         = "-i "+inputFilePath+" -r 24 -b:v 1500k -c:a aac -b:a 32k -ar 16000 -c:v libx264 -preset fast "+outputFilePath;

                FFmpegKit.executeAsync(command, new FFmpegSessionCompleteCallback() {
                    @Override
                    public void apply(FFmpegSession session) {

                        if (ReturnCode.isSuccess(session.getReturnCode())) {
                            // Compression successful
                            //file.delete();  //delete original file

                            total_file_compressed--;

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if(total_file_compressed==0) {
                                        panelCompressing.setVisibility(View.GONE);
                                    } else {
                                        ttlCompressing.setText(total_file_compressed+ " file "+(total_file_compressed>1?"s":"")+" compressing...");
                                    }
                                }
                            });
                            int lastSpaceIndex = session.getCommand().lastIndexOf(" ");
                            String output_file = session.getCommand().substring(lastSpaceIndex + 1);
                            File fileCompress  = new File(output_file);
                            Uri mFileCompress  = Uri.fromFile(fileCompress);
                            uploadChuckFile_(mFileCompress, 0, qrcode, index_of_file);

                        } else if (ReturnCode.isCancel(session.getReturnCode())) {
                            // Compression cancelled
                            Log.d("FFmpeg", "Video compression cancelled.");
                            total_file_compressed--;

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ttlCompressing.setText("Video compression cancelled.");
                                }
                            });
                            waitToCloseDialogCompressing.start();

                            //Toast.makeText(context, "Video compression cancelled.", Toast.LENGTH_SHORT).show();
                            uploadChuckFile_(mFileCapture, 0, qrcode, index_of_file);
                        } else {
                            // Compression failed
                            Log.e("FFmpeg", "Video compression failed: " + session.getFailStackTrace());
                            total_file_compressed--;

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ttlCompressing.setText("Video compression failed.");
                                }
                            });

                            waitToCloseDialogCompressing.start();

                            //Toast.makeText(context, "Video compression failed: " + session.getFailStackTrace(), Toast.LENGTH_SHORT).show();
                            uploadChuckFile_(mFileCapture, 0, qrcode, index_of_file);
                        }
                    }
                });
            }
        });

        // Start the background thread
        backgroundThread.start();

    }

    void uploadChuckFile_(Uri mFileCapture, int currentChunk, String tracking_number, int index_of_file) {
        // Create a new Thread
        Thread backgroundThread = new Thread(new Runnable() {
            @Override
            public void run() {

                File file          = new File(Objects.requireNonNull(GalleryFilePath.getPath(context, mFileCapture)));
                String filename    = file.getName();
                if(currentChunk==0) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(panelUpload.getVisibility()==View.GONE) {
                                pbVideoUpload.setProgress(0);
                                stVideoUpload.setText("");
                                stAllFiles.setText("");

                                pbVideoUpload.setVisibility(View.VISIBLE);
                                stVideoUpload.setVisibility(View.VISIBLE);
                                stAllFiles.setVisibility(View.VISIBLE);
                                panelUpload.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }
                // Simulate a long-running background task
                try {
                    FileInputStream f           = new FileInputStream(file.getAbsolutePath());
                    final int size              = f.available(); // Size of original file
                    final int chunkSize         = 1*1024*1024; // == 1MB
                    final int totalChunks       = (int) Math.ceil((double) size / chunkSize);
                    final String fileIdentifier = getFileNameWithoutExtension(filename); // Unique identifier
                    final String fileExtension  = getFileExtension(filename);

                    final int start = currentChunk * chunkSize;
                    final int end = (start + chunkSize) >= size ? size : (start + chunkSize);

                    Log.e("RERE", "INDEX " + currentChunk + " " + start + " sd. " + end + "~~~");
                    byte[] data = new byte[size];  // Size of original file
                    byte[] subData = new byte[chunkSize];  // 1MB Sized Array

                    f.read(data); // Read The Data
                    subData = Arrays.copyOfRange(data, start, end);
                    RequestBody requestFile = RequestBody.create(MediaType.parse("*/*"), subData);

                    // MultipartBody.Part is used to send also the actual file name
                    MultipartBody.Part ax_file_chunk = MultipartBody.Part.createFormData("fileChunk",
                            filename, // filename, this is optional
                            requestFile
                    );
                    RequestBody ax_file_name       = RequestBody.create(MediaType.parse("text/plain"), filename);
                    RequestBody ax_chunk_index     = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(currentChunk));
                    RequestBody ax_total_chunk     = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(totalChunks));
                    RequestBody ax_file_identifier = RequestBody.create(MediaType.parse("text/plain"), fileIdentifier);
                    RequestBody ax_file_extension  = RequestBody.create(MediaType.parse("text/plain"), fileExtension);
                    RequestBody ax_tracking_number = RequestBody.create(MediaType.parse("text/plain"), tracking_number);

                    RestApi api = RetroFit.getInstanceRetrofit();
                    Call<ResponseUploadChunkFile> uploadChunkFileCall = api.uploadChunkFile(
                            ax_file_chunk,
                            ax_file_name,
                            ax_chunk_index,
                            ax_total_chunk,
                            ax_file_identifier,
                            ax_file_extension,
                            ax_tracking_number
                    );

                    uploadChunkFileCall.enqueue(new Callback<>() {
                        @Override
                        public void onResponse(@NonNull Call<ResponseUploadChunkFile> call, @NonNull Response<ResponseUploadChunkFile> response) {

                            String status  = Objects.requireNonNull(response.body()).getStatus();
                            //String message = Objects.requireNonNull(response.body()).getMessage();

                            if(status.equalsIgnoreCase("chunk_received")) {
                                progress_upload_file[index_of_file] = (int) (100 * ((double) end/(double) size));

                                double total    = 0;
                                double progress = 0;
                                for(int i=0; i<count_of_files; i++) {
                                    if(progress_upload_file[i]>-1) {
                                        total=total+100;
                                        progress=progress+progress_upload_file[i];
                                    }
                                }

                                int persentase = (int) (100 * (progress/total));
                                int total_files = (int) (total/100);
                                Log.e("CIMO", "file upload: " + persentase + " % ");
                                uploadChuckFile_(mFileCapture, currentChunk+1, tracking_number, index_of_file);
                                runOnUiThread(new Runnable() {
                                    @SuppressLint("SetTextI18n")
                                    @Override
                                    public void run() {
                                        pbVideoUpload.setProgress(persentase);
                                        stVideoUpload.setText(persentase + " % ");
                                        stAllFiles.setText(total_files + " files");
                                    }
                                });
                            } else
                            if(status.equalsIgnoreCase("success")) {
                                //DONE
                                dh.deleteUploadData(mFileCapture.toString());
                                progress_upload_file[index_of_file] = -1;

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        boolean hideDialog = true;
                                        for(int i=0; i<count_of_files; i++) {
                                            if(progress_upload_file[i]>-1) {
                                                hideDialog = false;
                                                break;
                                            }
                                        }
                                        if(hideDialog) panelUpload.setVisibility(View.GONE);

                                        Toast.makeText(context, "Upload File " + filename + " Berhasil!", Toast.LENGTH_SHORT).show();
                                        file.delete(); //delete compress file
                                    }
                                });
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ResponseUploadChunkFile> call, @NonNull Throwable e) {
                            Log.e("CIMO", "error file upload: " + e.getMessage());
                            progress_upload_file[index_of_file] = -1;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    pbVideoUpload.setVisibility(View.GONE);
                                    stVideoUpload.setText(e.getMessage());
                                    waitToCloseDialogUploading.start();
                                }
                            });
                        }
                    });
                } catch (IOException e) {
                    // Update the UI on the main thread using runOnUiThread
                    Log.e("CIMO", "error file upload: " + e.getMessage());
                    progress_upload_file[index_of_file] = -1;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pbVideoUpload.setVisibility(View.GONE);
                            stVideoUpload.setText(e.getMessage());
                            waitToCloseDialogUploading.start();
                        }
                    });
                }
            }
        });

        // Start the background thread
        backgroundThread.start();

    }

    private void insertDummyContactWrapper() {
        List<String> permissionsNeeded = new ArrayList<>();
        final List<String> permissionsList = new ArrayList<>();

        if (addPermission(permissionsList, Manifest.permission.CAMERA))
            permissionsNeeded.add("CAMERA");
        if (addPermission(permissionsList, Manifest.permission.RECORD_AUDIO))
            permissionsNeeded.add("RECORD_AUDIO");

        if (!permissionsList.isEmpty()) {
            if (!permissionsNeeded.isEmpty()) {
                // Need Rationale
                String message = "You need to grant access to " + permissionsNeeded.get(0);
                for (int i = 1; i < permissionsNeeded.size(); i++)
                    message = message + ", " + permissionsNeeded.get(i);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(permissionsList.toArray(new String[permissionsList.size()]), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                }

                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(permissionsList.toArray(new String[permissionsList.size()]), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
            }

        } else {
            startCamera(cameraFacing);
        }
    }

    private boolean addPermission(List<String> permissionsList, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsList.add(permission);

                // Check for Rationale Option
                if (!shouldShowRequestPermissionRationale(permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<String, Integer>();

                // Initial
                perms.put(Manifest.permission.CAMERA, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.RECORD_AUDIO, PackageManager.PERMISSION_GRANTED);

                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);

                if (
                    perms.get(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    perms.get(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                ) {
                    // All Permissions Granted
                    startCamera(cameraFacing);
                } else {
                    // Permission Denied
                    Toast.makeText(context, "Some Permission is Denied!", Toast.LENGTH_SHORT).show();
                }
            }
            break;

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }




    void checkBeforeRecord(String tracking_number) {
        // Create a new Thread
        Thread backgroundThread = new Thread(new Runnable() {
            @Override
            public void run() {
                RestApi api = RetroFit.getInstanceRetrofit();
                Call<ResponseCheckBeforeRecord> splashCall = api.checkBeforeRecord(tracking_number);
                splashCall.enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseCheckBeforeRecord> call, @NonNull Response<ResponseCheckBeforeRecord> response) {
                        //SUCCESS TRUE OR FALSE
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseCheckBeforeRecord> call, @NonNull Throwable t) {
                        //ERROR FOUND


                    }
                });
            }
        });

        // Start the background thread
        backgroundThread.start();
    }

    private void toggleFlash(Camera camera) {
        if (camera.getCameraInfo().hasFlashUnit()) {
            if (camera.getCameraInfo().getTorchState().getValue() == 0) {
                camera.getCameraControl().enableTorch(true);
                toggleFlash.setImageResource(R.drawable.round_flash_off_24);
            } else {
                camera.getCameraControl().enableTorch(false);
                toggleFlash.setImageResource(R.drawable.round_flash_on_24);
            }
        } else {
            runOnUiThread(() -> Toast.makeText(this, "Flash is not available currently", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        service.shutdown();
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int dotIndex = fileName.lastIndexOf('.');

        // Handle cases with no extension or where the dot is the last character
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return "";
        } else {
            return fileName.substring(dotIndex + 1);
        }
    }
}