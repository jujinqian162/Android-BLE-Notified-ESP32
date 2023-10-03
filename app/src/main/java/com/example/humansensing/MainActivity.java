package com.example.humansensing;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String CHANAL_ID = "jjq";
    public static final int NOTIFICATION_ID = 114514;
    private Button mDeviceNameButton;
    private Button closeGattButton;

    private ProgressBar progressBar;
    private EditText DeviceNameEditText;
    private String DeviceNameText;
    private TextView countText;
    private int charaChangeCount = 0;
    private TextView deviceText;
    private TextView charaText;
    private BluetoothDevice mBluetoothDevice;
    private Vibrator vibrator;

    private NotificationManagerCompat mNotificationManager;

    public static final String DESCRIPTER_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISIC_UUID_RX = "19198102-9b0f-48fa-bfc3-e11234c74301";
    private List<BluetoothGattCharacteristic> mCharaList;
    private BluetoothGattCharacteristic mNeedChara;
    private Button mConnectButton;
    private List<BluetoothGattService> mGattServiceList;
    private BluetoothGatt mBluetoothGatt;

    private static final int SCAN_PERIOD = 15000;
    private boolean scanning;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner scanner;

    private boolean isSensing = false;

    public static String mBLEDeviceName = "ESP32";
    private BLE_Permission permission;
    private String TAG = "MainActivity_______________";

    private int GattState;

    private boolean hasConnected = false;

    private void initBluetooth() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "不支持低功耗蓝牙", Toast.LENGTH_SHORT).show();
            finish();
        } else {

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
                    showToast("请开启蓝牙后再试");
                    finish();
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

        setTitle("HumanSensing -> " + MainActivity.mBLEDeviceName);

        mNotificationManager = NotificationManagerCompat.from(this);

        initBluetooth();

        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);

        charaText = findViewById(R.id.charaText);
        charaText.setText(String.format("Characteristic UUID\n%s", CHARACTERISIC_UUID_RX));

        deviceText = findViewById(R.id.textView);
        deviceText.setText("已准备连接至" + MainActivity.mBLEDeviceName);

        countText = findViewById(R.id.countText);
        countText.setText("通知计数： " + charaChangeCount);
        countText.setVisibility(View.INVISIBLE);
        countText.setOnClickListener(v -> {
            charaChangeCount = 0;
            countText.setText("通知计数： " + charaChangeCount);
        });

        permission = new BLE_Permission();
        permission.checkPermission(this);

        closeGattButton = findViewById(R.id.closeGattButton);
        closeGattButton.setVisibility(View.INVISIBLE);
        closeGattButton.setOnClickListener(v -> {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            Log.i(TAG, "onConnectionStateChange: 断开连接");
            deviceText.setText("已准备连接至" + MainActivity.mBLEDeviceName);
            mConnectButton.setText("开始扫描");
            runOnUiThread(() -> {
                mConnectButton.setEnabled(true);
                countText.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
            });
            showToast("断开连接");
            closeGattButton.setVisibility(View.INVISIBLE);
        });

        DeviceNameEditText = (EditText) findViewById(R.id.editText);
        mDeviceNameButton = (Button) findViewById(R.id.getDeviceNameButton);
        mDeviceNameButton.setOnClickListener(v -> {
            DeviceNameText = DeviceNameEditText.getText().toString();
            if (DeviceNameText.equals("")) {
                showToast("名称不为空");
                return;
            }
//            Log.e(TAG, String.format("DeviceNameTextAsc= %s",DeviceNameText) );
//            Log.e(TAG, "onCreate: test="+ a );
            MainActivity.mBLEDeviceName = DeviceNameText;
            showToast("设备名已更改");
            setTitle("HumanSensing -> " + MainActivity.mBLEDeviceName);
            mDeviceNameButton.setEnabled(false);
            deviceText.setText("已准备连接至" + MainActivity.mBLEDeviceName);
            mDeviceNameButton.setVisibility(View.INVISIBLE);
            DeviceNameEditText.setVisibility(View.INVISIBLE);
        });


        mConnectButton = (Button) findViewById(R.id.button);
        mConnectButton.setOnClickListener(v -> {
            if (mConnectButton.getText().equals("开始监测")) {
                mDeviceNameButton.setEnabled(false);
                mDeviceNameButton.setVisibility(View.INVISIBLE);
                DeviceNameEditText.setVisibility(View.INVISIBLE);
                isSensing = true;

                deviceText.setText("监听中...");
                mConnectButton.setText("停止监测");
                countText.setVisibility(View.VISIBLE);

                return;
            } else if (mConnectButton.getText().equals("停止监测")) {
                mDeviceNameButton.setEnabled(false);
                mDeviceNameButton.setVisibility(View.INVISIBLE);
                DeviceNameEditText.setVisibility(View.INVISIBLE);

                isSensing = false;
                deviceText.setText("点击按钮开始监听");
                mConnectButton.setText("开始监测");
                return;
            }

            Log.d(TAG, "onCreate: 开始扫描");
//            Toast.makeText(this, "开始扫描", Toast.LENGTH_SHORT).show();
            mConnectButton.setText("正在扫描...");
            mDeviceNameButton.setEnabled(false);
            mDeviceNameButton.setVisibility(View.INVISIBLE);
            DeviceNameEditText.setVisibility(View.INVISIBLE);
            mBluetoothAdapter.cancelDiscovery();
            scanner.startScan(mScanCallback);
            progressBar.setVisibility(View.VISIBLE);
            deviceText.setText("扫描中...");
            scanning = true;
            mConnectButton.setEnabled(false);
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (scanning) {

                        Toast.makeText(MainActivity.this, "未连接成功", Toast.LENGTH_SHORT).show();
                        deviceText.setText("未连接成功");
                        Log.d(TAG, "run: 未连接成功");
                        scanner.stopScan(mScanCallback);
                        progressBar.setVisibility(View.INVISIBLE);
                        scanning = false;
                        mConnectButton.setEnabled(true);
                        mConnectButton.setText("开始扫描");
                        mDeviceNameButton.setEnabled(true);
                        mDeviceNameButton.setVisibility(View.VISIBLE);
                        DeviceNameEditText.setVisibility(View.VISIBLE);
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
                        mConnectButton.setText("已发现"+MainActivity.mBLEDeviceName);
                        deviceText.setText("连接至" + mBLEDeviceName+"...");
                        scanning = false;
                        Log.i(TAG, "onScanResult: 发现" + mBLEDeviceName);
                        mBluetoothDevice = bluetoothDevice;
                        Log.d(TAG, "onScanResult: 得到" + mBLEDeviceName);
                        mBluetoothGatt = mBluetoothDevice.connectGatt(MainActivity.this, false, mGattCallback);

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
            GattState = newState;
            Log.d(TAG, "onConnectionStateChange: newState:" + newState);
            Log.e(TAG, "onConnectionStateChange: status:" + status);
            if (newState == BluetoothGatt.STATE_CONNECTED) {

                deviceText.setText("成功连接" + mBLEDeviceName);

                runOnUiThread(()->{
                    closeGattButton.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.INVISIBLE);
                    countText.setVisibility(View.VISIBLE);
                });
                mConnectButton.setText("开始监测");
                runOnUiThread(()->mConnectButton.setEnabled(true));
                Log.d(TAG, "onConnectionStateChange: STATE_CONNECTED");
                mBluetoothGatt.discoverServices();

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {

                try {
                    mBluetoothGatt.disconnect();
                    mBluetoothGatt.close();
                } catch (Exception e){
                    e.printStackTrace();
                }

                Log.i(TAG, "onConnectionStateChange: 断开连接");
//                String desc = "已准备等待连接至"+MainActivity.mBLEDeviceName;
                deviceText.setText(String.format("失败,请重启蓝牙后再试一次"));
                mConnectButton.setText("再试一次");
                runOnUiThread(() -> {
                    mConnectButton.setEnabled(true);
                    countText.setVisibility(View.INVISIBLE);
                    progressBar.setVisibility(View.INVISIBLE);
                    DeviceNameEditText.setVisibility(View.VISIBLE);
                    mDeviceNameButton.setVisibility(View.VISIBLE);
                    mDeviceNameButton.setEnabled(true);
                    closeGattButton.setVisibility(View.INVISIBLE);
                });
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

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.e(TAG, "onCharacteristicChanged: 触发！！——————————————" );
            Log.i(TAG, "onCharacteristicChanged: value = "+new String(characteristic.getValue(), StandardCharsets.UTF_8));
            if (isSensing){

                createNotificationChannal(NotificationManager.IMPORTANCE_HIGH);
                NotificationCompat.Builder builder = Notification(NotificationCompat.PRIORITY_HIGH);
                mNotificationManager.notify(NOTIFICATION_ID,builder.build());
                mConnectButton.setText("开始监测");
                deviceText.setText("收到notify,点击按钮重新开始监听");
                isSensing = false;

                //震动实现

                vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator.hasVibrator()){

                    VibrationEffect vibrationEffect = VibrationEffect.createOneShot(700,255);
                    if (checkSelfPermission(Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED){
                        vibrator.vibrate(vibrationEffect);
                        delay2Run(1100,()->vibrator.vibrate(vibrationEffect));
                    }
                }

            } else {
                deviceText.setText("点击按钮开始监听");
            }

            charaChangeCount++;
            countText.setText("通知计数： " + charaChangeCount);
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
        Intent intent = new Intent(this,MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, CHANAL_ID)
                .setSmallIcon(R.drawable.human_sensing_jjq_foreground)
                .setContentTitle("Human Sensing")
                .setContentText("请重新监听")
                .setPriority(Priority)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        return builder;
    }

    private void delay2Run(int delayMillis,Runnable runnable){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(runnable,delayMillis);
    }


}