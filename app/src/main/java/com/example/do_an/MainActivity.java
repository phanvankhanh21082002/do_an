package com.example.do_an;

import static com.example.do_an.UploadFileToServer.uploadFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE},
                PackageManager.PERMISSION_GRANTED);

        fileSelectorButton = (Button) findViewById(R.id.fileSelectorButton);
        selectedFileTextView = (TextView) findViewById(R.id.selectedFileTextView);
        uploadButton = (Button) findViewById(R.id.uploadButton);
        scanCompleteTextView = (TextView) findViewById(R.id.scanCompleteTextView);
        downloadButton = (Button) findViewById(R.id.downloadButton);
        downloadedTextView = (TextView) findViewById(R.id.downloadedSizeTextView);

        fileSelectorButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
                if (selectedFilePath != null) {
                    uploadScanDialog = ProgressDialog.show(MainActivity.this, "",
                            "Upload and Scan Process Started...", true);
                    new Thread(new Runnable() {
                        public void run() {
                            //new thread to start the activity
                            int uploadResponseCode = uploadFile(selectedFilePath);
                            uploadScanDialog.dismiss();
                            if (uploadResponseCode == 200) {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        scanCompleteTextView
                                                .setText("File Scan Complete!!");
                                        downloadButton.setVisibility(View.VISIBLE);
                                    }
                                });
                            } else {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        downloadButton.setVisibility(View.VISIBLE);
                                    }
                                });
                            }
                        }
                    }).start();
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
                                    stringBuilder.append(line + "\n");
                                }
                                inputStream.close();
                                reader.close();
                                String finalResult = stringBuilder.toString();

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

    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ACTIVITY_CHOOSE_FILE: {
                if (resultCode == RESULT_OK) {
                    Uri uri = data.getData();
                    System.out.println(uri.toString());
                    selectedFilePath = getPathFromUri(this,uri);
                    System.out.println(selectedFilePath);
                    selectedFileTextView.setText("Selected File: "
                            + selectedFilePath);
                }
            }
        }
    }

    public static String getPathFromUri(Context context, Uri uri) {
        // Kiểm tra xem URI có thể được sử dụng với DocumentProvider không
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
            // Nếu là URI của ExternalStorageProvider, ta cần lấy ID của tài liệu
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return context.getExternalFilesDir(null) + "/" + split[1];
                }
            }
            // Nếu là URI của DownloadsProvider, ta cần lấy ID của tài liệu
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
                return getDataColumn(context, contentUri, null, null);
            }
            // Nếu là URI của MediaProvider, ta cần lấy ID của tài liệu
            else if (isMediaDocument(uri)) {
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
        }
        // Nếu không phải là DocumentProvider, ta sử dụng cách tiêu chuẩn để lấy đường dẫn
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // Nếu là URI của FileProvider, ta lấy đường dẫn trực tiếp từ URI
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
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
    // reset all the controls to null
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
