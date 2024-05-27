package com.example.do_an;

import static com.example.do_an.UploadFileToServer.uploadFile;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class MainActivity extends Activity {

    final int ACTIVITY_CHOOSE_FILE = 1;
    static int uploadResponseCode = 0;
    String selectedFilePath = null;

    Button fileSelectorButton;
    TextView selectedFileTextView;
    Button uploadButton;
    ProgressDialog uploadScanDialog;
    ProgressDialog downloadScanDialog;
    TextView scanCompleteTextView;
    Button downloadButton;
    TextView downloadedTextView;
    Button showDatabaseButton;

    DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        databaseHelper = new DatabaseHelper(this);

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE},
                PackageManager.PERMISSION_GRANTED);

        fileSelectorButton = findViewById(R.id.fileSelectorButton);
        selectedFileTextView = findViewById(R.id.selectedFileTextView);
        uploadButton = findViewById(R.id.uploadButton);
        scanCompleteTextView = findViewById(R.id.scanCompleteTextView);
        downloadButton = findViewById(R.id.downloadButton);
        downloadedTextView = findViewById(R.id.downloadedSizeTextView);
        showDatabaseButton = findViewById(R.id.showDatabaseButton);

        fileSelectorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadedTextView.setVisibility(View.GONE);
                downloadButton.setVisibility(View.GONE);
                Intent chooseFile;
                Intent intent;
                chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
                chooseFile.setType("*/*");
                intent = Intent.createChooser(chooseFile, "Choose a file");
                startActivityForResult(intent, ACTIVITY_CHOOSE_FILE);
            }
        });

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanCompleteTextView.setVisibility(View.GONE);
                if (selectedFilePath != null) {
                    uploadAndScanFile();
                } else {
                    Toast.makeText(MainActivity.this,
                                    "Please select a file to upload!!", Toast.LENGTH_LONG)
                            .show();
                }
            }
        });

        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadScanDialog = ProgressDialog.show(MainActivity.this, "",
                        "Preparing to Download Scan Results...", true);

                // Handler to add a delay
                new Handler().postDelayed(() -> {
                    String fileName = new File(selectedFilePath).getName();
                    String URL_STRING = "http://34.126.66.46/reports/" + fileName + ".txt";
                    new Thread(() -> {
                        try {
                            URL url = new URL(URL_STRING);
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setRequestMethod("GET");

                            int responseCode = connection.getResponseCode();
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                // Process the response as before
                                InputStream inputStream = connection.getInputStream();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                StringBuilder stringBuilder = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    stringBuilder.append(line).append("\n");
                                }
                                inputStream.close();
                                reader.close();
                                String finalResult = stringBuilder.toString();

                                // Save the result to the database
                                String fileHash = getFileHash(selectedFilePath);
                                databaseHelper.insertScanResult(fileName, fileHash, finalResult);

                                runOnUiThread(() -> {
                                    downloadedTextView.setVisibility(View.VISIBLE);
                                    downloadedTextView.setText(finalResult);
                                    downloadScanDialog.dismiss();
                                });
                            } else {
                                runOnUiThread(() -> {
                                    downloadedTextView.setVisibility(View.VISIBLE);
                                    downloadedTextView.setText("Failed to download: Server responded with code " + responseCode);
                                    downloadScanDialog.dismiss();
                                });
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                downloadedTextView.setVisibility(View.VISIBLE);
                                downloadedTextView.setText("Error: " + e.toString());
                                downloadScanDialog.dismiss();
                            });
                        }
                    }).start();
                }, 30000); // Delay for 30 seconds before attempting to download
            }
        });

        showDatabaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ShowDatabaseActivity.class);
                startActivity(intent);
            }
        });
    }

    private void uploadAndScanFile() {
        uploadScanDialog = ProgressDialog.show(MainActivity.this, "",
                "Upload and Scan Process Started...", true);
        new Thread(new Runnable() {
            public void run() {
                int uploadResponseCode = uploadFile(selectedFilePath);
                uploadScanDialog.dismiss();
                if (uploadResponseCode == 200) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            scanCompleteTextView.setVisibility(View.VISIBLE);
                            scanCompleteTextView.setText("File Scan Complete!!");
                            downloadButton.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            scanCompleteTextView.setVisibility(View.VISIBLE);
                            scanCompleteTextView.setText("File Scan Failed!!");
                            downloadButton.setVisibility(View.GONE);
                        }
                    });
                }
            }
        }).start();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ACTIVITY_CHOOSE_FILE: {
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    System.out.println(uri.toString());
                    selectedFilePath = getPathFromUri(this, uri);
                    System.out.println(selectedFilePath);
                    selectedFileTextView.setText("Selected File: "
                            + selectedFilePath);
                }
            }
        }
    }

    public static String getPathFromUri(Context context, Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                if (split.length >= 2) {
                    final String type = split[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    }
                }
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                if (id != null && id.startsWith("raw:")) {
                    return id.substring(4);
                }
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
                return getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }
            return getDataColumn(context, uri, null, null);
        }
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }
    private static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String column = "_data";
        String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(columnIndex);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public static String getFileHash(String filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(new File(filePath));
            byte[] byteArray = new byte[1024];
            int bytesCount;

            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }

            fis.close();

            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void reset() {
        runOnUiThread(new Runnable() {
            public void run() {
                downloadButton.setVisibility(View.GONE);
                selectedFilePath = null;
                selectedFileTextView.setText(null);
                scanCompleteTextView.setText(null);
            }
        });
    }
}



