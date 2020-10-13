package com.example.wmfacebio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.DescribeCollectionRequest;
import com.amazonaws.services.rekognition.model.DescribeCollectionResult;
import com.amazonaws.services.rekognition.model.FaceMatch;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.InvalidParameterException;
import com.amazonaws.services.rekognition.model.ResourceNotFoundException;
import com.amazonaws.services.rekognition.model.SearchFacesByImageRequest;
import com.amazonaws.services.rekognition.model.SearchFacesByImageResult;
import com.amazonaws.util.IOUtils;
import com.example.wmfacebio.utils.Util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;

import butterknife.ButterKnife;

public class VerifyActivity extends AppCompatActivity {


    private static final int VERIFY_REQUEST_CODE = 0;

    private AmazonRekognitionClient rekognitionClient;

    private Uri imageUri;

    static Util util;

    byte[] base64Image;

    private TextView aproval;

    private TextView tvUserId;

    private String userID;

    private boolean registered;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify);
        ButterKnife.bind(this);

        registered = true;

        userID = getId(this);
        Log.i("SearchId",userID+"for search");

        util = new Util();
        rekognitionClient = util.getRekognitionClient(this);


        aproval = findViewById(R.id.txtAproval);
        tvUserId = findViewById(R.id.tvUserId);


        try {

            threadCollection.start();
            threadCollection.join();
            if (!registered) {
                throw new ResourceNotFoundException("Please register first");
            }



            Intent intent2 = new Intent(this, FaceTrackerActivity.class);

//            ContentValues values = new ContentValues();
//            values.put(MediaStore.Images.Media.TITLE, "MyPicture");
//            values.put(MediaStore.Images.Media.DESCRIPTION, "Photo taken on " + System.currentTimeMillis());
//
//            Intent intent = new Intent();
//            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
//            intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
//
//            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
//            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//
              startActivityForResult(intent2, VERIFY_REQUEST_CODE);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }catch (ResourceNotFoundException e2){

            Log.i("register",e2.toString());
        }




    }

    public Uri getImg(Context context) {


        SharedPreferences prefs = context.getSharedPreferences("imgUri",0);

        String uri = prefs.getString("imgUri","not found");

        String result = getRealPathFromURI(Uri.parse(uri));

        return Uri.parse("file://"+result);

    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    public static String getId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("ids", 0);
        return prefs.getString("id", "");

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {


        super.onActivityResult(requestCode, resultCode, data);





        while (imageUri==null){
           imageUri= getImg(this);

            File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

            Log.w("uriimg", folder.toString());
            //imageUri = Uri.parse("/external_primary/images/media/10040");
        }


        if (requestCode == VERIFY_REQUEST_CODE) {

            Log.w("reconocio",   "primer if");
            threadCompareFaces.start();

            if (resultCode == Activity.RESULT_OK) {




            }

        }
    }

    /**
     * Verify if the user is alredy registered before starting face recognition.
     */
    Thread threadCollection = new Thread(new Runnable(){
        @Override
        public void run() {



            DescribeCollectionRequest request2 = new DescribeCollectionRequest().withCollectionId(userID);



            try {
                DescribeCollectionResult describeCollectionResult = rekognitionClient.describeCollection(request2);

            }catch (ResourceNotFoundException e) {

                registered = false;
                Handler mHandler = new Handler(Looper.getMainLooper());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),"Please register first", Toast.LENGTH_LONG).show();
                        finish();

                    }
                });

            }



        }
    });



    Thread threadCompareFaces = new Thread(new Runnable() {
        @Override
        public void run() {


            Image image = null;
            ByteBuffer imageBytes = null;



            Log.w("reconocio",   imageUri.toString());
            Log.w("reconocio",   "entro thread");

            try {


                //String picturePath = getRealPathFromURI(imageUri);
                Log.w("reconocio",   imageUri.toString());
                File file = readContentToFile(imageUri);

                Log.w("reconocio",   imageUri.toString());
                InputStream inputStream = new FileInputStream(file);
                imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));

                InputStream iStream = getContentResolver().openInputStream(imageUri);


                image = new Image().withBytes(imageBytes);



                SearchFacesByImageRequest req = new SearchFacesByImageRequest()
                        .withCollectionId(userID)
                        .withImage(image)
                        .withFaceMatchThreshold(70F);

                SearchFacesByImageResult searchFacesByImageResult = rekognitionClient.searchFacesByImage(req);

                List<FaceMatch> faceImageMatches = searchFacesByImageResult.getFaceMatches();

                if (faceImageMatches.isEmpty()) {

                    throw new Exception("No face found");
                }

                for (FaceMatch face: faceImageMatches) {


                    Log.w("reconocio",     face.getFace().getConfidence().toString());
                    Log.w("reconocio",     face.getSimilarity().toString());

                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            String msg = "Approved";
                            aproval.setText(msg);
                            tvUserId.setText(userID);
                        }
                    });

                }

            } catch (IOException e){
                e.printStackTrace();
            } catch (InvalidParameterException e) {


                Log.w("reconocio", "Face not Found");
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String err = "Face not Found";
                        aproval.setText(err);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Log.w("reconocio", "Face not Found");
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String err = "Face not Found";
                        String msg = "error";
                        aproval.setText(err);
                        tvUserId.setText(msg);
                    }
                });
            }

        }

    });


    private File readContentToFile(Uri uri) throws IOException {
        final File file = new File( getDisplayName(uri));

        try (
                final InputStream in = getContentResolver().openInputStream(uri);
                //final InputStream in = getContentResolver().openInputStream(Uri.parse("file://"+uri.toString()));
                final OutputStream out = new FileOutputStream(file, false);
        ) {
            byte[] buffer = new byte[1024];
            for (int len; (len = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, len);
            }
            return file;
        }
    }


    private String getDisplayName(Uri uri) {
//        final String[] projection = { MediaStore.Images.Media.DISPLAY_NAME };
//        try (
//                Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
//               //Cursor cursor = getContentResolver().openInputStream(uri)
//        ){
//            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
//            if (cursor.moveToFirst()) {
//                return cursor.getString(columnIndex);
//            }
//        }
        // If the display name is not found for any reason, use the Uri path as a fallback.
        Log.w("file", "Couldnt determine DISPLAY_NAME for Uri.  Falling back to Uri path: " + uri.getPath());
        return uri.getPath();
    }



    public byte[] getImage(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }


        byte[] base64Image = Base64.encode(byteBuffer.toByteArray(), Base64.URL_SAFE);



        return base64Image;
    }
}
