package com.example.btqt3;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager; // Thư viện cho đèn Flash
import android.media.AudioManager; // Thư viện cho Âm lượng
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;

    private long lastUpdateAccel = 0;
    private long lastUpdateGyro = 0;
    private TextView tvStatus;
    private MediaPlayer mediaPlayer;

    private boolean isLightOn = false;

    // Biến cho đèn Flash
    private CameraManager cameraManager;
    private String cameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tvStatus);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }

        // Khởi tạo CameraManager để chuẩn bị bật Flash
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null) {
                // Lấy ID của camera sau (thường là "0")
                cameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        // Tắt đèn khi thoát app cho chắc ăn
        toggleFlashlight(false);
    }

    // --- CÁC HÀM XỬ LÝ (ÂM THANH, FLASH, VOLUME, ĐIỀU KHIỂN NHẠC) ---

    private void playSound(int soundResource, String actionName) {
        tvStatus.setText(actionName);
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = MediaPlayer.create(this, soundResource);
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }
    }

    // Hàm bật/tắt đèn Flash thật
    private void toggleFlashlight(boolean state) {
        try {
            if (cameraManager != null && cameraId != null) {
                cameraManager.setTorchMode(cameraId, state);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Hàm tăng/giảm âm lượng thật của máy
    private void adjustSystemVolume(int direction) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            // FLAG_SHOW_UI sẽ làm cho cái thanh âm lượng hiện lù lù trên màn hình luôn -> Rất trực quan!
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        }
    }

    // Hàm bấm nút bài trước/tiếp theo ảo
    private void sendMediaCommand(int keyCode, String actionName) {
        tvStatus.setText(actionName);
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            audioManager.dispatchMediaKeyEvent(downEvent);
            KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
            audioManager.dispatchMediaKeyEvent(upEvent);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();

        // XỬ LÝ GIA TỐC KẾ (Vẫy tay & Nghiêng)
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if ((curTime - lastUpdateAccel) > 1200) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

                if (acceleration > 8.0f) { // Vẫy tay lắc mạnh
                    if (isLightOn) {
                        playSound(R.raw.tatden, "Cử chỉ: Vẫy tay -> TẮT ĐÈN FLASH");
                        toggleFlashlight(false); // Tắt đèn flash thật
                        isLightOn = false;
                    } else {
                        playSound(R.raw.batden, "Cử chỉ: Vẫy tay -> BẬT ĐÈN FLASH");
                        toggleFlashlight(true);  // Bật đèn flash thật
                        isLightOn = true;
                    }
                    lastUpdateAccel = curTime;
                }
                else if (y > 5.0f) { // Nghiêng máy lên
                    playSound(R.raw.tangamluong, "Cử chỉ: Nghiêng lên -> TĂNG ÂM LƯỢNG");
                    adjustSystemVolume(AudioManager.ADJUST_RAISE); // Tăng volume thật
                    lastUpdateAccel = curTime;
                }
                else if (y < -5.0f) { // Nghiêng máy xuống
                    playSound(R.raw.giamamluong, "Cử chỉ: Nghiêng xuống -> GIẢM ÂM LƯỢNG");
                    adjustSystemVolume(AudioManager.ADJUST_LOWER); // Giảm volume thật
                    lastUpdateAccel = curTime;
                }
            }
        }

        // XỬ LÝ CON QUAY HỒI CHUYỂN (Xoay tay)
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if ((curTime - lastUpdateGyro) > 1500) {
                float rotateZ = event.values[2];

                if (rotateZ > 3.0f) { // Xoay trái
                    playSound(R.raw.nextbai, "Cử chỉ: Xoay trái -> NEXT BÀI THẬT");
                    sendMediaCommand(KeyEvent.KEYCODE_MEDIA_NEXT, "Đang chuyển bài tiếp theo...");
                    lastUpdateGyro = curTime;
                }
                else if (rotateZ < -3.0f) { // Xoay phải
                    playSound(R.raw.prevbai, "Cử chỉ: Xoay phải -> LÙI BÀI THẬT");
                    sendMediaCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Đang lùi bài trước...");
                    lastUpdateGyro = curTime;
                }
            }
        }
    }
}