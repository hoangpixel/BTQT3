package com.example.btqt3;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer; // Thư viện phát nhạc
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

    // Khai báo biến phát nhạc
    private MediaPlayer mediaPlayer;

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

    // Nhớ giải phóng bộ nhớ khi tắt app để không bị lỗi RAM
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

    // HÀM PHÁT NHẠC MỚI (Thay thế cho gửi lệnh IoT)
    private void playSound(int soundResource, String actionName) {
        // Cập nhật chữ trên màn hình
        tvStatus.setText(actionName);

        // Nếu đang phát âm thanh cũ thì dừng lại để phát âm mới
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        // Tạo và phát âm thanh mới
        mediaPlayer = MediaPlayer.create(this, soundResource);
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if ((curTime - lastUpdateAccel) > 1000) { // Giãn cách 1 giây để Google voice đọc xong
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
                float SHAKE_THRESHOLD = 8.0f;

                if (acceleration > SHAKE_THRESHOLD) {
                    // Gọi file MP3 từ thư mục raw
                    playSound(R.raw.battatden, "Vẫy tay: Bật/Tắt đèn");
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