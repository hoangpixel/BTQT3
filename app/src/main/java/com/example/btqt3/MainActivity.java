package com.example.btqt3;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;

    private long lastUpdateAccel = 0;
    private long lastUpdateGyro = 0;
    private TextView tvStatus, tvSpeechResult;
    private ImageButton btnMic;
    private MediaPlayer mediaPlayer;

    private boolean isLightOn = false;
    private CameraManager cameraManager;
    private String cameraId;

    private static final long SENSOR_ACTION_COOLDOWN_MS = 2200;
    private static final float FLAT_Z_THRESHOLD = 8.5f;
    private static final float FLAT_XY_MAX = 4.0f;

    private volatile boolean isDeviceFlat = false;

    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvSpeechResult = findViewById(R.id.tvSpeechResult);
        btnMic = findViewById(R.id.btnMic);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null && cameraManager.getCameraIdList().length > 0) {
                cameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        btnMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSpeechToText();
            }
        });
    }

    // --- HÀM GỌI MICRO ---
    private void startSpeechToText() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ní nói đi, tui đang nghe...");

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(this, "Máy ảo chưa bật Mic ní ơi!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String spokenText = result.get(0).toLowerCase();
            tvSpeechResult.setText("Ní vừa nói: " + spokenText);

            // LOGIC XỬ LÝ LỆNH
            if (spokenText.contains("bật đèn")) {
                toggleFlashlight(true);
                isLightOn = true;
                playSound(R.raw.batden, "Lệnh: BẬT ĐÈN");
            } else if (spokenText.contains("tắt đèn")) {
                toggleFlashlight(false);
                isLightOn = false;
                playSound(R.raw.tatden, "Lệnh: TẮT ĐÈN");
            } else if (spokenText.contains("tăng âm")) {
                adjustSystemVolume(AudioManager.ADJUST_RAISE);
                playSound(R.raw.tangamluong, "Lệnh: TĂNG ÂM");
            } else if (spokenText.contains("giảm âm")) {
                adjustSystemVolume(AudioManager.ADJUST_LOWER);
                playSound(R.raw.giamamluong, "Lệnh: GIẢM ÂM");
            } else if (spokenText.contains("tiếp theo") || spokenText.contains("chuyển bài")) {
                sendMediaCommand(KeyEvent.KEYCODE_MEDIA_NEXT, "Lệnh: NEXT BÀI");
                playSound(R.raw.nextbai, "Đang chuyển bài...");
            } else if (spokenText.contains("quay lại") || spokenText.contains("lùi bài")) {
                sendMediaCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Lệnh: LÙI BÀI");
                playSound(R.raw.prevbai, "Đang lùi bài...");
            }
        }
    }

    // --- HÀM PHÁT THANH PHẢN HỒI ---
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

    // Hàm gọi AI: Phát "Dạ em đây" xong mới mở Mic
    private void wakeUpAI() {
        tvStatus.setText("AI: Dạ em đây ní!");
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = MediaPlayer.create(this, R.raw.hey);
        if (mediaPlayer != null) {
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    startSpeechToText(); // Phát xong mới lắng nghe
                }
            });
            mediaPlayer.start();
        }
    }

    // --- ĐIỀU KHIỂN HỆ THỐNG ---
    private void toggleFlashlight(boolean state) {
        try {
            if (cameraManager != null && cameraId != null) {
                cameraManager.setTorchMode(cameraId, state);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void adjustSystemVolume(int direction) {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        }
    }

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

    // --- XỬ LÝ CẢM BIẾN ---
    @Override
    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // Khi đặt máy nằm ngang (face up/down trên bàn) thì bỏ qua toàn bộ tín hiệu để tránh bị nhạy
            isDeviceFlat = Math.abs(z) >= FLAT_Z_THRESHOLD && Math.abs(x) <= FLAT_XY_MAX && Math.abs(y) <= FLAT_XY_MAX;
            if (isDeviceFlat) {
                return;
            }

            if ((curTime - lastUpdateAccel) > SENSOR_ACTION_COOLDOWN_MS) { // Giới hạn thời gian giữa các lần
                float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

                // 1. LẮC MẠNH -> GỌI AI (Như gọi Hey Siri)
                if (acceleration > 18.0f) {
                    wakeUpAI();
                    lastUpdateAccel = curTime;
                }
                // 2. LẮC VỪA -> BẬT/TẮT ĐÈN NHANH
                else if (acceleration > 9.0f) {
                    if (isLightOn) {
                        playSound(R.raw.tatden, "Lắc: TẮT ĐÈN");
                        toggleFlashlight(false);
                        isLightOn = false;
                    } else {
                        playSound(R.raw.batden, "Lắc: BẬT ĐÈN");
                        toggleFlashlight(true);
                        isLightOn = true;
                    }
                    lastUpdateAccel = curTime;
                }
                // 3. NGHIÊNG MÁY -> CHỈNH ÂM LƯỢNG
                else if (y > 6.0f) {
                    playSound(R.raw.tangamluong, "Nghiêng: TĂNG ÂM");
                    adjustSystemVolume(AudioManager.ADJUST_RAISE);
                    lastUpdateAccel = curTime;
                } else if (y < -6.0f) {
                    playSound(R.raw.giamamluong, "Nghiêng: GIẢM ÂM");
                    adjustSystemVolume(AudioManager.ADJUST_LOWER);
                    lastUpdateAccel = curTime;
                }
            }
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if (isDeviceFlat) {
                return;
            }

            if ((curTime - lastUpdateGyro) > SENSOR_ACTION_COOLDOWN_MS) {
                float rotateZ = event.values[2];
                if (rotateZ > 3.5f) { // Xoay trái
                    playSound(R.raw.nextbai, "Xoay: TIẾP THEO");
                    sendMediaCommand(KeyEvent.KEYCODE_MEDIA_NEXT, "Chuyển bài...");
                    lastUpdateGyro = curTime;
                } else if (rotateZ < -3.5f) { // Xoay phải
                    playSound(R.raw.prevbai, "Xoay: QUAY LẠI");
                    sendMediaCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Lùi bài...");
                    lastUpdateGyro = curTime;
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (sensorManager != null && gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
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
        }
        toggleFlashlight(false);
    }
}