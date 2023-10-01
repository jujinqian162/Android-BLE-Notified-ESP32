package com.example.humansensing;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String CHANAL_ID = "jjq";
    public static final int NOTIFICATION_ID = 114514;
    private ProgressBar progressBar;

    private NotificationManagerCompat mNotificationManager;

    public static final String DESCRIPTER_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISIC_UUID_RX = "19198102-9b0f-48fa-bfc3-e11234c74301";
    private List<BluetoothGattCharacteristic> mCharaList;
    private BluetoothGattCharacteristic mNeedChara;
    private Button mbutton;
    private List<BluetoothGattService> mGattServiceList;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice mESP32;
    private static final int SCAN_PERIOD = 15000;
    private boolean scanning;

    private String desc;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner scanner;
    private TextView deviceText;

    private boolean isSensing = false;

    public static final String mBLEDeviceName = "ESP32";
    private BLE_Permission permission;
    private String TAG = "MainActivity_______________";

    private void initBluetooth() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "不支持低功耗蓝牙", Toast.LENGTH_SHORT).show();
            finish();
        } else {
//            Toast.makeText(this, "低功耗蓝牙", Toast.LENGTH_SHORT).show();
            BluetoothManager bm = (BluetoothManager)
                    getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bm.getAdapter();
            if (mBluetoothAdapter != null) {
                Log.d(TAG, "initBluetooth: 支持蓝牙");
                if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED)
                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "initBluetooth: 无权限BLUETOOTH and BLUETOOTH_ADMIN");
                    Toast.makeText(this, "无权限BLUETOOTH", Toast.LENGTH_SHORT).show();
                }
                scanner = mBluetoothAdapter.getBluetoothLeScanner();

                if (scanner == null) {
                    Log.w(TAG, "initBluetooth: scanner null");
                    showToast("scanner null");
                }
            } else {
                Toast.makeText(this, "不支持蓝牙", Toast.LENGTH_LONG).show();
                Log.d(TAG, "initBluetooth: 不支持蓝牙");
                finish();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNotificationManager = NotificationManagerCompat.from(this);

        initBluetooth();

        progressBar  = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);

        deviceText = findViewById(R.id.textView);
        desc = deviceText.getText().toString();
        deviceText.setText(desc);
        deviceText.setText("已准备连接至" + MainActivity.mBLEDeviceName);

        permission = new BLE_Permission();
        permission.checkPermission(this);
        mbutton = (Button) findViewById(R.id.button);
        mbutton.setOnClickListener(v -> {
            if(mbutton.getText().equals("开始监测")){
                isSensing = true;
                deviceText.setText("开始监听");
                mbutton.setText("停止监测");
                return;
            }else if (mbutton.getText().equals("停止监测")){
                isSensing = false;
                deviceText.setText("点击按钮开始监听");
                mbutton.setText("开始监测");
                return;
            }

            Log.d(TAG, "onCreate: 开始扫描");
//            Toast.makeText(this, "开始扫描", Toast.LENGTH_SHORT).show();
            mbutton.setText("正在扫描...");
            scanner.startScan(mScanCallback);
            deviceText.setText("扫描中...");
            scanning = true;
            mbutton.setEnabled(false);
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (scanning) {

                        Toast.makeText(MainActivity.this, "未连接成功", Toast.LENGTH_SHORT).show();
                        deviceText.setText("未连接成功");
                        Log.d(TAG, "run: 未连接成功");
                        scanner.stopScan(mScanCallback);
                        scanning = false;
                        mbutton.setEnabled(true);
                        mbutton.setText("开始扫描");
                    }
                }
            }, SCAN_PERIOD);

        });

    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.i(TAG, "onScanResult: found device");
            if (scanning) {
                BluetoothDevice bluetoothDevice = result.getDevice();
                String DeviceName = bluetoothDevice.getName();
                Log.d(TAG, "onScanResult: DeviceName = " + DeviceName);
                if (DeviceName != null) {
                    if (DeviceName.equals(mBLEDeviceName)) {
                        mbutton.setText("已发现"+MainActivity.mBLEDeviceName);
                        deviceText.setText("连接至" + mBLEDeviceName+"...");

                        scanning = false;
                        Log.i(TAG, "onScanResult: 发现" + mBLEDeviceName);
                        mESP32 = bluetoothDevice;
                        Log.d(TAG, "onScanResult: 得到" + mBLEDeviceName);
                        mBluetoothGatt = mESP32.connectGatt(MainActivity.this, false, mGattCallback);

                        Log.d(TAG, "onScanResult: 连接Gatt");
                        if (mBluetoothGatt != null) Log.d(TAG, "onScanResult: Gatt 不为空");
                        else Log.d(TAG, "onScanResult: Gatt 为空");
                    }
                }
            } else {
                scanner.stopScan(mScanCallback);
                scanning = false;
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed" );
        }
    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange: newState:" + newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                deviceText.setText("成功连接" + mBLEDeviceName);
                mbutton.setText("开始监测");
                runOnUiThread(()->mbutton.setEnabled(true));
                Log.d(TAG, "onConnectionStateChange: STATE_CONNECTED");
                mBluetoothGatt.discoverServices();

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                mBluetoothGatt.close();
                Log.i(TAG, "onConnectionStateChange: 断开连接");
//                String desc = "已准备等待连接至"+MainActivity.mBLEDeviceName;
                deviceText.setText(String.format("失败,请重启蓝牙后再试一次"));
                mbutton.setText("再试一次");
                runOnUiThread(() -> mbutton.setEnabled(true));
                showToast("断开连接");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered: 触发");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mGattServiceList = mBluetoothGatt.getServices();
                if (mGattServiceList != null) {
                    for (BluetoothGattService gattService : mGattServiceList) {
                        mCharaList = gattService.getCharacteristics();
                        for (BluetoothGattCharacteristic chara : mCharaList) {
                            Log.i(TAG, " ---------chara---------- ");

                            if (chara.getUuid().toString().equals(CHARACTERISIC_UUID_RX)) {
                                Log.i(TAG, "onServicesDiscovered: getMyChara");
                                mNeedChara = chara;
                                boolean notification = mBluetoothGatt.setCharacteristicNotification(mNeedChara, true);
                                Log.i(TAG, "onServicesDiscovered: notification:" + notification);
                                HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
                                handlerThread.start();
                                Looper looper = handlerThread.getLooper();
                                Handler handler = new Handler(looper);
                                handler.postDelayed(() -> {
                                    Log.d(TAG, "onServicesDiscovered: postDelay");
                                    BluetoothGattDescriptor clientConfig_rx = mNeedChara.getDescriptor(UUID.fromString(DESCRIPTER_UUID));
                                    if (clientConfig_rx == null) {
                                        Log.e(TAG, "onServicesDiscovered: clientConfig_rx null !!!");
                                    } else {
                                        clientConfig_rx.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                        mBluetoothGatt.writeDescriptor(clientConfig_rx);

                                    }
                                }, 500);

                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.e(TAG, "onCharacteristicChanged: 触发！！——————————————" );
            Log.i(TAG, "onCharacteristicChanged: value = "+new String(characteristic.getValue(), StandardCharsets.UTF_8));
            if (isSensing){
                deviceText.setText("开始监听:value = "+new String(characteristic.getValue(), StandardCharsets.UTF_8));
                createNotificationChannal(NotificationManager.IMPORTANCE_HIGH);
                NotificationCompat.Builder builder = Notification(NotificationCompat.PRIORITY_HIGH);
                mNotificationManager.notify(NOTIFICATION_ID,builder.build());
                isSensing = false;
                mbutton.setText("开始监测");
                deviceText.setText("收到notify,点击按钮重新开始监听");
            } else {
                deviceText.setText("点击按钮开始监听");
            }
        }
    };


    private void showToast(String message) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    private boolean createNotificationChannal(int improtance){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            CharSequence name = getString(R.string.channal1);
            String description = getString(R.string.description);
//            int improtance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channal = new NotificationChannel(CHANAL_ID,name,improtance);
            channal.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channal);
            return true;
        }else {
            return false;
        }

    }

    private NotificationCompat.Builder Notification(int Priority){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, CHANAL_ID)
                .setSmallIcon(R.drawable.human_sensing_jjq_foreground)
                .setContentTitle("Human Sensing")
                .setContentText("请重新监听")
                .setPriority(Priority)
                .setAutoCancel(true);
        return builder;
    }

}