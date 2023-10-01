package com.example.humansensing;

import android.Manifest;
import android.app.Activity;

import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class BLE_Permission {

    private String[] permission = {
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_NOTIFICATION_POLICY,
            Manifest.permission.BLUETOOTH
    };
    private List<String> permissionList = new ArrayList<>();
    private void requestPermission(Activity activity){
        ActivityCompat.requestPermissions(activity,permissionList.toArray(new String[permissionList.size()]),REQUST_CODE);
    }

    public static final int REQUST_CODE = 1000;
    public void checkPermission(Activity activity){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            for (int i = 0; i < permission.length; i++) {
                if (ContextCompat.checkSelfPermission(activity,permission[i]) != PackageManager.PERMISSION_GRANTED){
                    permissionList.add(permission[i]);
                }
            }
            if (permissionList.size() > 0){
                requestPermission(activity);
            }
        }
    }
}
