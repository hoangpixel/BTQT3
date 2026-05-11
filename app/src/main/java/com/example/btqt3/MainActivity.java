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
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;

    private long lastUpdateAccel = 0;
    private long lastUpdateGyro = 0;
    private TextView tvStatus, tvSpeechResult;
    private FloatingActionButton btnMic;
    private MediaPlayer mediaPlayer;

    private boolean isLightOn = false;
    private CameraManager cameraManager;
    private String cameraId;

    private static final long SENSOR_ACTION_COOLDOWN_MS = 2200;
    private static final float FLAT_Z_THRESHOLD = 8.5f;
    private static final float FLAT_XY_MAX = 4.0f;

    // Tilt-to-switch-song (nghiêng trái/phải để lùi/chuyển bài)
    private static final float TILT_TRIGGER_DEG = 18.0f;
    private static final float TILT_NEUTRAL_DEG = 8.0f;
    private static final long TILT_HOLD_MS = 220;
    private static final long TILT_ACTION_COOLDOWN_MS = 1200;

    private final float[] gravity = new float[]{0f, 0f, 0f};
    private long tiltCandidateStartMs = 0;
    private int tiltCandidateDir = 0; // -1: left, +1: right, 0: none
    private boolean tiltArmed = true;
    private long lastTiltSongActionMs = 0;

    private volatile boolean isDeviceFlat = false;

    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;

    private Sensor proximitySensor;
    private long lastUpdateProximity = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvSpeechResult = findViewById(R.id.tvSpeechResult);
        btnMic = findViewById(R.id.btnMic);

        // Khởi tạo SensorManager TRƯỚC
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            // Khởi tạo proximitySensor ở đây mới đúng nè
            proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
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

            // THÊM 2 LỆNH MỚI Ở ĐÂY NÈ
            else if (spokenText.contains("bật nhạc") || spokenText.contains("mở nhạc")) {
                sendMediaCommand(KeyEvent.KEYCODE_MEDIA_PLAY, "Lệnh: BẬT NHẠC");
                // Tui để sẵn hàm playSound, ông có file mp3 thì bỏ vào, không thì xóa dòng playSound này đi nha
                 playSound(R.raw.batnhac, "Đang phát nhạc...");
            } else if (spokenText.contains("tắt nhạc") || spokenText.contains("dừng nhạc")) {
                sendMediaCommand(KeyEvent.KEYCODE_MEDIA_PAUSE, "Lệnh: DỪNG NHẠC");
                 playSound(R.raw.tatnhac, "Đã dừng nhạc...");
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

        // ==========================================
        // 1. CẢM BIẾN TIỆM CẬN (VẪY TAY -> BẬT/TẮT ĐÈN)
        // ==========================================
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            float distance = event.values[0];
            // Nếu có vật cản (tay che qua) cách cảm biến dưới 2cm
            if (distance < 2.0f) {
                if ((curTime - lastUpdateProximity) > SENSOR_ACTION_COOLDOWN_MS) {
                    if (isLightOn) {
                        playSound(R.raw.tatden, "Vẫy tay: TẮT ĐÈN");
                        toggleFlashlight(false);
                        isLightOn = false;
                    } else {
                        playSound(R.raw.batden, "Vẫy tay: BẬT ĐÈN");
                        toggleFlashlight(true);
                        isLightOn = true;
                    }
                    lastUpdateProximity = curTime;
                }
            }
        }

        // ==========================================
        // 2. GIA TỐC KẾ (LẮC MẠNH -> GỌI AI)
        // ==========================================
// ==========================================
        // 2. GIA TỐC KẾ (LẮC MẠNH -> GỌI AI)
        // ==========================================
        else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            isDeviceFlat = Math.abs(z) >= FLAT_Z_THRESHOLD && Math.abs(x) <= FLAT_XY_MAX && Math.abs(y) <= FLAT_XY_MAX;
            if (isDeviceFlat) return;

            // --- NGHIÊNG TRÁI/PHẢI -> LÙI/CHUYỂN BÀI (dựa trên hướng trọng lực, không cần lắc) ---
            // Low-pass filter để tách trọng lực khỏi rung/lắc
            final float alpha = 0.85f;
            gravity[0] = alpha * gravity[0] + (1 - alpha) * x;
            gravity[1] = alpha * gravity[1] + (1 - alpha) * y;
            gravity[2] = alpha * gravity[2] + (1 - alpha) * z;

            // Góc nghiêng trái/phải (độ). Dùng X so với Z: màn hình ngửa lên (z ~ +9.8) => góc ~ 0.
            float tiltDeg = (float) Math.toDegrees(Math.atan2(gravity[0], gravity[2]));

            int dir = 0;
            if (tiltDeg <= -TILT_TRIGGER_DEG) {
                dir = -1; // nghiêng trái
            } else if (tiltDeg >= TILT_TRIGGER_DEG) {
                dir = 1; // nghiêng phải
            }

            // Khi quay lại vùng trung tính thì "arm" lại để lần nghiêng sau mới bắn tiếp
            if (Math.abs(tiltDeg) <= TILT_NEUTRAL_DEG) {
                tiltArmed = true;
                tiltCandidateDir = 0;
                tiltCandidateStartMs = 0;
            } else if (tiltArmed && dir != 0) {
                // Debounce: phải giữ nghiêng ổn định một chút mới thực hiện
                if (tiltCandidateDir != dir) {
                    tiltCandidateDir = dir;
                    tiltCandidateStartMs = curTime;
                }

                boolean holdEnough = (curTime - tiltCandidateStartMs) >= TILT_HOLD_MS;
                boolean cooldownOk = (curTime - lastTiltSongActionMs) >= TILT_ACTION_COOLDOWN_MS;
                if (holdEnough && cooldownOk) {
                    if (dir > 0) {
                        playSound(R.raw.prevbai, "Nghiêng trái: LÙI BÀI");
                        sendMediaCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Lùi bài...");
                    } else {
                        playSound(R.raw.nextbai, "Nghiêng phải: TIẾP THEO");
                        sendMediaCommand(KeyEvent.KEYCODE_MEDIA_NEXT, "Chuyển bài...");
                    }
                    lastTiltSongActionMs = curTime;
                    tiltArmed = false;
                }
            }

            if ((curTime - lastUpdateAccel) > SENSOR_ACTION_COOLDOWN_MS) {
                float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

                // Điểm mấu chốt là số 18.0f ở đây
                if (acceleration > 18.0f) {
                    wakeUpAI();
                    lastUpdateAccel = curTime;
                }
            }
        }

        // ==========================================
        // 3. CON QUAY HỒI CHUYỂN (ÂM LƯỢNG & CHUYỂN BÀI)
        // ==========================================
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            if (isDeviceFlat) return;

            if ((curTime - lastUpdateGyro) > SENSOR_ACTION_COOLDOWN_MS) {
                float rotateX = event.values[0]; // Trục X: Gật / Ngửa điện thoại

                // --- CHỈNH ÂM LƯỢNG (Ngửa/Gập điện thoại) ---
                if (rotateX > 3.0f) { // Gập đầu máy xuống
                    playSound(R.raw.giamamluong, "Nghiêng: GIẢM ÂM");
                    adjustSystemVolume(AudioManager.ADJUST_LOWER);
                    lastUpdateGyro = curTime;
                } else if (rotateX < -3.0f) { // Ngửa đầu máy lên
                    playSound(R.raw.tangamluong, "Nghiêng: TĂNG ÂM");
                    adjustSystemVolume(AudioManager.ADJUST_RAISE);
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
        if (sensorManager != null) {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
            }
            // Thêm dòng này cho cảm biến tiệm cận
            if (proximitySensor != null) {
                sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
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