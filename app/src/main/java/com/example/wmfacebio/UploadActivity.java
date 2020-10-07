/*
 * Copyright 2015-2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.example.wmfacebio;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferType;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.CreateCollectionRequest;
import com.amazonaws.services.rekognition.model.CreateCollectionResult;
import com.amazonaws.services.rekognition.model.DescribeCollectionRequest;
import com.amazonaws.services.rekognition.model.DescribeCollectionResult;
import com.amazonaws.services.rekognition.model.FaceRecord;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.IndexFacesRequest;
import com.amazonaws.services.rekognition.model.IndexFacesResult;
import com.amazonaws.services.rekognition.model.ResourceAlreadyExistsException;
import com.amazonaws.services.rekognition.model.ResourceNotFoundException;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.s3.AmazonS3Client;
import com.example.wmfacebio.utils.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * UploadActivity is a ListActivity of uploading, and uploaded records as well
 * as buttons for managing the uploads and creating new ones.
 */
public class UploadActivity extends ListActivity {

    // Indicates that no upload is currently selected
    private static final int INDEX_NOT_CHECKED = -1;

    private Uri imageUri;
    // TAG for logging;

    private Bitmap bmImg;
    private static final String TAG = "UploadActivity";

    private static final int UPLOAD_REQUEST_CODE = 0;

    private static final int UPLOAD_IN_BACKGROUND_REQUEST_CODE = 1;



    private AmazonRekognitionClient rekognitionClient;

    private AmazonS3Client s3;

    // The TransferUtility is the primary class for managing transfer to S3
    static TransferUtility transferUtility;

    // The SimpleAdapter adapts the data about transfers to rows in the UI
    static SimpleAdapter simpleAdapter;

    // A List of all transfers
    static List<TransferObserver> observers;

    boolean objectExist;

    /**
     * This map is used to provide data to the SimpleAdapter above. See the
     * fillMap() function for how it relates observers to rows in the displayed
     * activity.
     */

    static ArrayList<HashMap<String, Object>> transferRecordMaps;

    // Which row in the UI is currently checked (if any)
    static int checkedIndex;

    // Reference to the utility class
    static Util util;



    private String userID;

    private String pictureUserID;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);


        userID = getId(this);
        pictureUserID = userID+".jpg";
        objectExist = false;

        Log.i("SearchId",userID+"for search");

        // Initializes TransferUtility, always do this before using it.
        util = new Util();
        transferUtility = util.getTransferUtility(this);
        rekognitionClient = util.getRekognitionClient(this);
        checkedIndex = INDEX_NOT_CHECKED;
        transferRecordMaps = new ArrayList<HashMap<String, Object>>();
        s3 = util.getS3Client(this);
        initUI();



        try {
            threadCollection.start();
            threadCollection.join();
            if (objectExist) {
                throw new Exception("Already registered");
            }
            Intent intent = new Intent();

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "MyPicture");
            values.put(MediaStore.Images.Media.DESCRIPTION, "Photo taken on " + System.currentTimeMillis());
            values.put(MediaStore.Images.Media.DISPLAY_NAME, userID);

            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);

            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            //intent.setType("image/*");
            startActivityForResult(intent, UPLOAD_REQUEST_CODE);
            //startActivity(intent);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }catch (Exception e){

            Toast.makeText(getApplicationContext(),"Already registered", Toast.LENGTH_LONG).show();
            finish();
        }




    }

    @Override
    protected void onResume() {
        super.onResume();
        // Get the data from any transfer's that have already happened,
        initData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Clear transfer listeners to prevent memory leak, or
        // else this activity won't be garbage collected.
        if (observers != null && !observers.isEmpty()) {
            for (TransferObserver observer : observers) {
                observer.cleanTransferListener();
            }
        }
    }

    /**
     * Gets all relevant transfers from the Transfer Service for populating the
     * UI
     */
    static void initData() {
        transferRecordMaps.clear();
        // Use TransferUtility to get all upload transfers.
        observers = transferUtility.getTransfersWithType(TransferType.UPLOAD);
        TransferListener listener = new UploadListener();
        for (TransferObserver observer : observers) {
            observer.refresh();

            // For each transfer we will will create an entry in
            // transferRecordMaps which will display
            // as a single row in the UI
            HashMap<String, Object> map = new HashMap<String, Object>();
            util.fillMap(map, observer, false);
            transferRecordMaps.add(map);

            // Sets listeners to in progress transfers
            if (TransferState.WAITING.equals(observer.getState())
                    || TransferState.WAITING_FOR_NETWORK.equals(observer.getState())
                    || TransferState.IN_PROGRESS.equals(observer.getState())) {
                observer.setTransferListener(listener);
            }
        }
        simpleAdapter.notifyDataSetChanged();
    }

    private void initUI() {
        /**
         * This adapter takes the data in transferRecordMaps and displays it,
         * with the keys of the map being related to the columns in the adapter
         */
        simpleAdapter = new SimpleAdapter(this, transferRecordMaps,
                R.layout.record_item, new String[] {
                        "checked", "fileName", "progress", "bytes", "state", "percentage"
                },
                new int[] {
                        R.id.radioButton1, R.id.textFileName, R.id.progressBar1, R.id.textBytes,
                        R.id.textState, R.id.textPercentage
                });
        simpleAdapter.setViewBinder(new ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object data,
                                        String textRepresentation) {
                switch (view.getId()) {
                    case R.id.radioButton1:
                        RadioButton radio = (RadioButton) view;
                        radio.setChecked((Boolean) data);
                        return true;
                    case R.id.textFileName:
                        TextView fileName = (TextView) view;
                        fileName.setText((String) data);
                        return true;
                    case R.id.progressBar1:
                        ProgressBar progress = (ProgressBar) view;
                        progress.setProgress((Integer) data);
                        return true;
                    case R.id.textBytes:
                        TextView bytes = (TextView) view;
                        bytes.setText((String) data);
                        return true;
                    case R.id.textState:
                        TextView state = (TextView) view;
                        state.setText(((TransferState) data).toString());
                        return true;
                    case R.id.textPercentage:
                        TextView percentage = (TextView) view;
                        percentage.setText((String) data);
                        return true;
                }
                return false;
            }
        });
        setListAdapter(simpleAdapter);

        // Updates checked index when an item is clicked
        getListView().setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {

                if (checkedIndex != pos) {
                    transferRecordMaps.get(pos).put("checked", true);
                    if (checkedIndex >= 0) {
                        transferRecordMaps.get(checkedIndex).put("checked", false);
                    }
                    checkedIndex = pos;
                    updateButtonAvailability();
                    simpleAdapter.notifyDataSetChanged();
                }
            }
        });



    }



    /*
     * Updates the ListView according to the observers.
     */
    static void updateList() {
        TransferObserver observer = null;
        HashMap<String, Object> map = null;
        for (int i = 0; i < observers.size(); i++) {
            observer = observers.get(i);
            map = transferRecordMaps.get(i);
            util.fillMap(map, observer, i == checkedIndex);
        }
        simpleAdapter.notifyDataSetChanged();

    }

    /*
     * Enables or disables buttons according to checkedIndex.
     */
    private void updateButtonAvailability() {
        boolean availability = checkedIndex >= 0;
    }


    /**
     * used to create and verify existence of a collection
     * if a description of a collections was not found (ResourceNotFoundException) we create the collection
     * if the colelction is found an exeption is raised
     */
    Thread threadCollection = new Thread(new Runnable(){
        @Override
        public void run() {

            Log.i("collection","creando coll");
            CreateCollectionRequest request = new CreateCollectionRequest()
                    .withCollectionId(userID);
            Log.e("cara", rekognitionClient.toString());

            DescribeCollectionRequest request2 = new DescribeCollectionRequest().withCollectionId(userID);



            try {
                DescribeCollectionResult describeCollectionResult = rekognitionClient.describeCollection(request2);
                if (!describeCollectionResult.toString().isEmpty()) {

                    throw new ResourceAlreadyExistsException("Alredy registered");
                }
            }catch (ResourceNotFoundException e) {

                try {


                    Log.i("collection","entro");
                    CreateCollectionResult createCollectionResult = rekognitionClient.createCollection(request);
                    Log.i("collection",createCollectionResult.getStatusCode().toString());
                }catch (ResourceAlreadyExistsException e2) {

                    objectExist = true;
                    Log.i("collection","Collection already exist");

                }

            }catch (ResourceAlreadyExistsException e2) {

                objectExist = true;
                Log.i("collection","Collection already exist.");

            }



        }
    });

    Thread threadAddFace = new Thread(new Runnable(){
        @Override
        public void run() {


            Log.e("cara", rekognitionClient.toString());

            Image image = new Image()
                    .withS3Object(new S3Object()
                            .withBucket("pruebawm")
                            .withName(pictureUserID));
            IndexFacesRequest indexFacesRequest = new IndexFacesRequest()
                    .withImage(image)
                    .withCollectionId(userID)
                    .withExternalImageId(userID)
                    .withDetectionAttributes("ALL");
            IndexFacesResult indexFacesResult = rekognitionClient.indexFaces(indexFacesRequest);
            List<FaceRecord> faceRecords = indexFacesResult.getFaceRecords();
            Log.i("cara",indexFacesResult.toString());
            try {


                Log.i("cara",faceRecords.get(0).toString());

            }catch (IndexOutOfBoundsException e) {

                Handler mHandler = new Handler(Looper.getMainLooper());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),"No Face Found", Toast.LENGTH_LONG).show();

                    }
                });
            }
            for (FaceRecord faceRecord : faceRecords) {

                Log.i("cara",faceRecord.getFaceDetail().getGender().toString());
            }

        }
    });

    Thread threadObject = new Thread(new Runnable(){
        @Override
        public void run() {

            objectExist = s3.doesObjectExist("pruebawm",pictureUserID);
        }
    });

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        updateButtonAvailability();

        //updateButtonAvailability();

        if (requestCode == UPLOAD_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                Uri uri = imageUri;
                try {
                    File file = readContentToFile(uri);



                    if (objectExist) {
                        throw new Exception("Already registered");
                    }

                    beginUpload(file);

                    
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {

                            threadAddFace.start();
                            try {
                                threadAddFace.join();
                                Toast.makeText(getApplicationContext(), "Successful registration", Toast.LENGTH_LONG).show();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        }
                    }, 10000);   //10 seconds





                } catch (IOException e) {
                    Toast.makeText(this,
                            "Unable to find selected file. See error log for details",
                            Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Unable to upload file from the given uri", e);
                } catch (Exception e) {
                    e.printStackTrace();


                    Log.i("register","Alredy registered");
                    Handler mHandler = new Handler(Looper.getMainLooper());
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),"Alredy registered", Toast.LENGTH_LONG).show();

                        }
                    });
                }


            }
        }

    };




    /*
     * Begins to upload the file specified by the file path.
     */
    private TransferObserver beginUpload(File file) {
        TransferObserver observer = transferUtility.upload(
                file.getName(),
                file
        );
        if (observer.getState().toString().equals("COMPLETED")) {

        }
        return observer;

        /*
         * Note that usually we set the transfer listener after initializing the
         * transfer. However it isn't required in this sample app. The flow is
         * click upload button -> start an activity for image selection
         * startActivityForResult -> onActivityResult -> beginUpload -> onResume
         * -> set listeners to in progress transfers.
         */
        // observer.setTransferListener(new UploadListener());
    }

    /*
     * Begins to upload the file specified by the file path.
     */


    /**
     * Copies the resource associated with the Uri to a new File in the cache directory, and returns the File
     * @param uri the Uri
     * @return a copy of the Uri's content as a File in the cache directory
     * @throws IOException if openInputStream fails or writing to the OutputStream fails
     */
    private File readContentToFile(Uri uri) throws IOException {
        final File file = new File(getCacheDir(), getDisplayName(uri));
        try (
                final InputStream in = getContentResolver().openInputStream(uri);
                final OutputStream out = new FileOutputStream(file, false);
        ) {
            byte[] buffer = new byte[1024];
            for (int len; (len = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, len);
            }
            return file;
        }
    }

    /**
     * Returns the filename for the given Uri
     * @param uri the Uri
     * @return String representing the file name (DISPLAY_NAME)
     */
    private String getDisplayName(Uri uri) {
        final String[] projection = { MediaStore.Images.Media.DISPLAY_NAME };
        try (
                Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        ){
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            if (cursor.moveToFirst()) {
                return cursor.getString(columnIndex);
            }
        }
        // If the display name is not found for any reason, use the Uri path as a fallback.
        Log.w(TAG, "Couldnt determine DISPLAY_NAME for Uri.  Falling back to Uri path: " + uri.getPath());
        return uri.getPath();
    }

    /**
     * get user id from previus activity
     * @param context
     * @return
     */
    public static String getId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("ids", 0);
        return prefs.getString("id", "");

    }




    /*
     * A TransferListener class that can listen to a upload task and be notified
     * when the status changes.
     */
    static class UploadListener implements TransferListener {

        // Simply updates the UI list when notified.
        @Override
        public void onError(int id, Exception e) {
            Log.e(TAG, "Error during upload: " + id, e);
            updateList();
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            Log.d(TAG, String.format("onProgressChanged: %d, total: %d, current: %d",
                    id, bytesTotal, bytesCurrent));
            updateList();
        }

        @Override
        public void onStateChanged(int id, TransferState newState) {
            Log.d(TAG, "onStateChanged: " + id + ", " + newState);
            updateList();
        }
    }


}
