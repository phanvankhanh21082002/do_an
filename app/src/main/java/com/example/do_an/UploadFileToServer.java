package com.example.do_an;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;

public class UploadFileToServer {

    /* The address to server where the PHP file is stored */
    static String upLoadServerUri = "http://34.126.66.46:3000/upload_file";
    String test = null;

    /* Function which is used to create connection and file upload */
    public static int uploadFile(String sourceFileUri) {
        /* File path of the file which is going to be scanned */
        File sourceFile = new File(sourceFileUri);

        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("uploaded_file", sourceFile.getName(),
                        RequestBody.create(MediaType.parse("multipart/form-data"), sourceFile))
                .build();

        Request request = new Request.Builder()
                .url(upLoadServerUri)
                .post(requestBody)
                .build();

        try {
            Response response = client.newCall(request).execute();
            return response.code();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

}
