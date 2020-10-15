package com.example.wmfacebio;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity{

    int PERMISSION_ALL = 1;
    boolean flagPermissions = false;



    private Button btnSignUp;
    private Button btnVerifyID;
    EditText userId;


    String[] PERMISSIONS = {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        checkPermissions();
        btnSignUp = findViewById(R.id.buttonSignUp);
        btnVerifyID = findViewById((R.id.btnVerifyID));
        userId = findViewById(R.id.txtUserId);
        if (getId(this)!=null){

            userId.setText(getId(this));
        }


    }

    public static String getId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("ids", 0);
        return prefs.getString("id", "");

    }

    @OnClick(R.id.buttonSignUp)
    public void onClick(View arg0) {
        String id = userId.getText().toString();
        setId(this,id);
        Intent intent = new Intent(MainActivity.this, UploadActivity.class);
        startActivity(intent);
    }

    @OnClick(R.id.btnVerifyID)
    public void onClick2(View arg0) {
        String id = userId.getText().toString();
        setId(this,id);
        Intent intent = new Intent(MainActivity.this, VerifyActivity.class);
        startActivity(intent);
    }

    public static void setId(Context context, String id) {
        SharedPreferences prefs = context.getSharedPreferences("ids", 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("id", id);
        editor.apply();
    }





    void checkPermissions() {
        if (!hasPermissions(this, PERMISSIONS)) {
            requestPermissions(PERMISSIONS,
                    PERMISSION_ALL);
            flagPermissions = false;
        }
        flagPermissions = true;

    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }







}







