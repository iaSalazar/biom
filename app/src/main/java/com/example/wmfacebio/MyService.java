package com.example.wmfacebio;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.example.wmfacebio.utils.Util;

import java.io.File;

public class MyService extends Service {

    private TransferUtility transferUtility;

    final static String INTENT_KEY_NAME = "key";
    final static String INTENT_FILE = "file";
    final static String INTENT_TRANSFER_OPERATION = "transferOperation";

    final static String TRANSFER_OPERATION_UPLOAD = "upload";
    final static String TRANSFER_OPERATION_DOWNLOAD = "download";

    private final static String TAG = MyService.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        Util util = new Util();
        transferUtility = util.getTransferUtility(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String key = intent.getStringExtra(INTENT_KEY_NAME);
        final File file = (File) intent.getSerializableExtra(INTENT_FILE);
        final String transferOperation = intent.getStringExtra(INTENT_TRANSFER_OPERATION);
        TransferObserver transferObserver;

        //it is a Switch because we had the option to upload or download from S3 bucket
        switch (transferOperation) {

            case TRANSFER_OPERATION_UPLOAD:
                Log.d(TAG, "Uploading " + key);
                transferObserver = transferUtility.upload(key, file);
                transferObserver.setTransferListener(new UploadListener());
                break;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    private class UploadListener implements TransferListener {

        private boolean notifyUploadActivityNeeded = true;

        // Simply updates the list when notified.
        @Override
        public void onError(int id, Exception e) {
            Log.e(TAG, "onError: " + id, e);
            if (notifyUploadActivityNeeded) {
                UploadActivity.initData();
                notifyUploadActivityNeeded = false;
            }
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            Log.d(TAG, String.format("onProgressChanged: %d, total: %d, current: %d",
                    id, bytesTotal, bytesCurrent));
            if (notifyUploadActivityNeeded) {
                UploadActivity.initData();
                notifyUploadActivityNeeded = false;
            }
        }

        @Override
        public void onStateChanged(int id, TransferState state) {
            Log.d(TAG, "onStateChanged: " + id + ", " + state);
            if (notifyUploadActivityNeeded) {
                UploadActivity.initData();
                notifyUploadActivityNeeded = false;
            }
        }
    }
}
