package com.example.android.whatflower;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions;
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.automl.FirebaseAutoMLLocalModel;
import com.google.firebase.ml.vision.automl.FirebaseAutoMLRemoteModel;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions;


import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DEBUG_TAG";

    Button classifyButton;


    static final int REQUEST_IMAGE_CAPTURE = 1;

    private String wikiURL = "";

    private String photoURL = "https://en.wikipedia.org/w/api.php?action=query&titles=rose&format=json&prop=pageimages&pithumbsize=500";

    private String URL = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e6/Rosa_rubiginosa_1.jpg/500px-Rosa_rubiginosa_1.jpg";

    private String flowerName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        classifyButton = findViewById(R.id.classify_button);


        classifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
            }
        });
    }


    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            Log.i(TAG, "Picture Intent started");
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(imageBitmap);


            FirebaseAutoMLLocalModel localModel =
                    new FirebaseAutoMLLocalModel.Builder().setAssetFilePath("models/manifest.json").build();
            FirebaseVisionImageLabeler detector = null;

            try {
                FirebaseVisionOnDeviceAutoMLImageLabelerOptions options =
                        new FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder(localModel)
                                .setConfidenceThreshold(0.7f)  // Evaluate your model in the Firebase console
                                // to determine an appropriate value.
                                .build();
                detector = FirebaseVision.getInstance().getOnDeviceAutoMLImageLabeler(options);
            } catch (FirebaseMLException e) {
                Log.i(TAG, e + " ");
            }


            detector.processImage(image)
                    .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
                        @Override
                        public void onSuccess(List<FirebaseVisionImageLabel> labels) {

                            if (labels.isEmpty()) {
                                Toast.makeText(getApplicationContext(), "Cannot Classify Flower", Toast.LENGTH_LONG);
                                TextView t = findViewById(R.id.textView);
                                t.setText("Cannot Classify Flower");
                            } else {
                                Log.v(TAG, "Sucess");
                                // Task completed successfully
                                for (FirebaseVisionImageLabel label : labels) {
                                    String text = label.getText();
                                    String entityId = label.getEntityId();
                                    float confidence = label.getConfidence();
                                    Log.i(TAG, "label: " + text + " confidence: " + confidence + "\n");
                                }


                                flowerName = labels.get(0).getText();
                                setTitle(flowerName);
                                getFlowerData();
                            }


                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            // Task failed with an exception
                            Log.e(TAG, "Error: " + e);
                        }
                    });

        }
    }

    public void getFlowerData() {

        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();


        if (networkInfo != null && networkInfo.isConnected()) {
            wikiURL = "https://en.wikipedia.org/w/api.php?action=query&titles=" + flowerName + "&format=json&prop=extracts&exintro=&explaintext&indexpageids&redirects=1";
            AsyncTask task = new DownloadWebpageTask().execute(wikiURL);
        } else {
            Toast.makeText(getApplicationContext(), "No network connection available", Toast.LENGTH_SHORT);
        }

    }

    // Uses AsyncTask to create a task away from the main UI thread. This task takes a
    // URL string and uses it to create an HttpUrlConnection. Once the connection
    // has been established, the AsyncTask downloads the contents of the webpage as
    // an InputStream. Finally, the InputStream is converted into a string, which is
    // displayed in the UI by the AsyncTask's onPostExecute method.
    private class DownloadWebpageTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            // params comes from the execute() call: params[0] is the url.
            try {
                return downloadUrl(urls[0]);
            } catch (IOException e) {
                return null;
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            TextView txt = findViewById(R.id.textView);
            if (result != null) {
                Log.i(TAG, result);
                txt.setText(result);

                showImage(URL);
            } else {
                Log.i(TAG, "returned String is null");
            }

        }
    }

    private String downloadUrl(String myurl) throws IOException {
        InputStream is = null;
        InputStream i = null;

        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);

            // Starts the query
            conn.connect();


            int response = conn.getResponseCode();
            Log.i(TAG, "The response is: " + response);


            is = conn.getInputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            StringBuffer sb = new StringBuffer();
            String str;

            while ((str = reader.readLine()) != null) {
                sb.append(str);
            }

//            Log.i(TAG, sb.toString());

            JSONObject json = new JSONObject(sb.toString());


            String id = json.getJSONObject("query").getJSONArray("pageids").get(0).toString();


            photoURL = "https://en.wikipedia.org/w/api.php?action=query&titles=" + flowerName + "&format=json&prop=pageimages&pithumbsize=500";


            URL u = new URL(photoURL);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();

            c.setReadTimeout(10000 /* milliseconds */);
            c.setConnectTimeout(15000 /* milliseconds */);
            c.setRequestMethod("GET");
            c.setDoInput(true);

            // Starts the query
            c.connect();


            int res = conn.getResponseCode();
            Log.i(TAG, "The response is: " + res);


            i = c.getInputStream();

            BufferedReader read = new BufferedReader(new InputStreamReader(i));

            StringBuffer s = new StringBuffer();
            String st;

            while ((st = read.readLine()) != null) {
                s.append(st);
            }

//            Log.i(TAG, sb.toString());

            JSONObject js = new JSONObject(s.toString());

            URL = js.getJSONObject("query").getJSONObject("pages").getJSONObject(id).getJSONObject("thumbnail").getString("source");

            Log.i(TAG, URL);


            return json.getJSONObject("query").getJSONObject("pages").getJSONObject(id).getString("extract");

        } catch (Exception e) {
            Log.i(TAG, e.toString());
        } finally {
            if (is != null) {
                is.close();
                i.close();
            }
        }

        return null;
    }

    // When user clicks button, calls AsyncTask.
    // Before attempting to fetch the URL, makes sure that there is a network connection.
    public void showImage(String url) {
        ImageView img = (ImageView) findViewById(R.id.flowerID);
        img.setImageBitmap(null);

        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();


        if (networkInfo != null && networkInfo.isConnected()) {
            new DownloadTask().execute(url);
        } else {
            Toast.makeText(getApplicationContext(), "No network connection available", Toast.LENGTH_SHORT);
        }
    }

    // Uses AsyncTask to create a task away from the main UI thread. This task takes a
    // URL string and uses it to create an HttpUrlConnection. Once the connection
    // has been established, the AsyncTask downloads the contents of the webpage as
    // an InputStream. Finally, the InputStream is converted into a string, which is
    // displayed in the UI by the AsyncTask's onPostExecute method.
    private class DownloadTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... urls) {

            // params comes from the execute() call: params[0] is the url.
            try {
                return downloadIcon(urls[0]);
            } catch (IOException e) {
                return null;
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(Bitmap result) {
            ImageView img = (ImageView) findViewById(R.id.flowerID);
            if (result != null) img.setImageBitmap(result);
            else {
                Log.i(TAG, "returned bitmap is null");
            }

        }
    }

    private Bitmap downloadIcon(String myurl) throws IOException {
        InputStream is = null;

        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);

            // Starts the query
            conn.connect();


            int response = conn.getResponseCode();
            Log.i(TAG, "The response is: " + response);


            is = conn.getInputStream();

            Bitmap bitmap = BitmapFactory.decodeStream(is);
            return bitmap;
        } catch (Exception e) {
            Log.i(TAG, e.toString());
        } finally {
            if (is != null) {
                is.close();
            }
        }

        return null;
    }


}

/* ************************* THINGS TO DO ***********************          */

/*


what if cannot connect to internet or classify


tulip confidence: 0.9372549
rose confidence: 0.03529412
dandelion confidence: 0.023529412
daisy confidence: 0.007843138
sunflower confidence: 0.003921569
 */












