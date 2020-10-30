package com.example.wmfacebio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageView;
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
import com.bumptech.glide.GenericTransitionOptions;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
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

    private TextView aproval;

    private TextView tvUserId;

    private String userID;

    private boolean registered;

    private ImageView ivAproval;


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
        ivAproval = findViewById(R.id.ivAnswer);
        tvUserId = findViewById(R.id.tvUserId);


        try {

            threadCollection.start();
            threadCollection.join();
            if (!registered) {
                throw new ResourceNotFoundException("Please register first");
            }



            Intent intent2 = new Intent(this, FaceTrackerActivity.class);

            startActivityForResult(intent2, VERIFY_REQUEST_CODE);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }catch (ResourceNotFoundException e2){

            Log.i("register",e2.toString());
        }




    }

    /**
     * get image path from shared preferences.
     * @param context
     * @return image path
     */
    public Uri getImg(Context context) {


        SharedPreferences prefs = context.getSharedPreferences("imgUri",0);

        String uri = prefs.getString("imgUri","not found");

        String result = getRealPathFromURI(Uri.parse(uri));

        return Uri.parse("file://"+result);

    }

    /**
     * fail safe to check the path.
     * @param contentURI
     * @return image path as String
     */
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

    /**
     * Get user id for face validation
     * @param context
     * @return
     */
    public static String getId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("ids", 0);
        return prefs.getString("id", "");

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {


        super.onActivityResult(requestCode, resultCode, data);





        while (imageUri==null){
           imageUri= getImg(this);


        }


        if (requestCode == VERIFY_REQUEST_CODE) {

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


    /**
     * Compare users face with AWS rekognition the user Id is the user collection.
     */
    Thread threadCompareFaces = new Thread(new Runnable() {
        @Override
        public void run() {


            Image image = null;
            ByteBuffer imageBytes = null;



            Log.w("reconocio",   imageUri.toString());
            Log.w("reconocio",   "entro thread");

            try {



                Log.w("reconocio",   imageUri.toString());

                try (InputStream inputStream = new FileInputStream(new File(imageUri.getPath()))) {
                    imageBytes = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));
                }


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
                            Glide.with(getApplicationContext()).load(R.drawable.check_ic)
                                    .transition(GenericTransitionOptions.with(R.anim.approval_anim))
                                    .into(ivAproval);
                            tvUserId.setText(userID);
                        }
                    });

                }

            } catch (IOException e){
                e.printStackTrace();
            } catch (InvalidParameterException e) {


                Log.w("reconocio", "Invalid parametrer");
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String err = "Invalid parametrer";
                        aproval.setText(err);
                        Glide.with(getApplicationContext()).load(R.drawable.cross_ic)
                                .transition(GenericTransitionOptions.with(R.anim.approval_anim))
                                .into(ivAproval);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Log.w("reconocio", "No match found");
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String err = "No match found";
                        String msg = "error";
                        aproval.setText(err);
                        tvUserId.setText(msg);
                        Glide.with(getApplicationContext()).load(R.drawable.cross_ic)
                                .transition(GenericTransitionOptions.with(R.anim.approval_anim))
                                .into(ivAproval);
                    }
                });
            }

        }

    });


//    /**
//     * used for test
//     * @param uri image path
//     * @return
//     * @throws IOException
//     */
//    private File readContentToFile(Uri uri) throws IOException {
//
//        String x = "file:///data/user/0/com.example.wmfacebio/cache/1602694638617.jpg";
//
//        Uri urix = Uri.parse(x);
//        final File file = new File(getCacheDir(),"1602694638617.jpg");
//
//        try (
//
//                final InputStream in = new FileInputStream(file);
//                final OutputStream out = new FileOutputStream(file, false);
//                
//        ) {
//            byte[] buffer = new byte[1024];
//
//            for (int len; (len = in.read(buffer)) != -1; ) {
//
//
//                Log.i("entroAlFor","xxxx");
//                out.write(buffer, 0, len);
//
//
//            }
//
//            String fileSize = Long.toString(file.length());
//            return file;
//        }
//    }


}
