package com.example.btqt3;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;

    private long lastUpdateAccel = 0;
    private long lastUpdateGyro = 0;
    private TextView tvStatus;

    private MediaPlayer mediaPlayer;

    // THÊM BIẾN NÀY ĐỂ LƯU TRẠNG THÁI ĐÈN
    private boolean isLightOn = false; // Mặc định ban đầu coi như đèn đang tắt

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
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

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

    @Override
    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if ((curTime - lastUpdateAccel) > 1000) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
                float SHAKE_THRESHOLD = 8.0f;

                // LOGIC BẬT/TẮT ĐÈN MỚI
                if (acceleration > SHAKE_THRESHOLD) {
                    if (isLightOn) {
                        // Đang bật -> Vẫy tay thì Tắt
                        playSound(R.raw.tatden, "Vẫy tay: Tắt đèn");
                        isLightOn = false; // Cập nhật trạng thái
                    } else {
                        // Đang tắt -> Vẫy tay thì Bật
                        playSound(R.raw.batden, "Vẫy tay: Bật đèn");
                        isLightOn = true; // Cập nhật trạng thái
                    }
                    lastUpdateAccel = curTime;

                } else if (y > 5.0f) {
                    playSound(R.raw.tangamluong, "Nghiêng lên: Tăng âm lượng");
                    lastUpdateAccel = curTime;
                } else if (y < -5.0f) {
                    playSound(R.raw.giamamluong, "Nghiêng xuống: Giảm âm lượng");
                    lastUpdateAccel = curTime;
                }
            }
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if ((curTime - lastUpdateGyro) > 1000) {
                float z = event.values[2];
                float ROTATE_THRESHOLD = 3.0f;

                if (z > ROTATE_THRESHOLD) {
                    playSound(R.raw.chuyenbai, "Xoay trái: Bài tiếp theo");
                    lastUpdateGyro = curTime;
                } else if (z < -ROTATE_THRESHOLD) {
                    playSound(R.raw.chuyenbai, "Xoay phải: Lùi bài");
                    lastUpdateGyro = curTime;
                }
            }
        }
    }
}