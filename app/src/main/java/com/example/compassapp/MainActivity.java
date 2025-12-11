package com.example.compassapp;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // UI Components
    private ImageView imgCompass;
    private TextView tvDegrees;
    private TextView tvDirection;
    private TextView tvStatus;
    private Switch switchSound;
    private Button btnSong1, btnSong2, btnSong3, btnSong4;
    private Button btnCalibrate;

    // Sensor Components
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    // Sensor Data
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float currentAzimuth = 0f;
    private float lastAzimuth = 0f;

    // Media Player
    private MediaPlayer mediaPlayer;
    private boolean isSoundEnabled = true;
    private int currentSongResId = R.raw.pac; // Default

    // Calibration
    private static final int CALIBRATION_SAMPLES = 10;
    private int calibrationCount = 0;
    private float calibrationOffset = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        initializeSensors();
    }

    private void initializeUI() {
        imgCompass = findViewById(R.id.imgCompass);
        tvDegrees = findViewById(R.id.tvDegrees);
        tvDirection = findViewById(R.id.tvDirection);
        tvStatus = findViewById(R.id.tvStatus);
        switchSound = findViewById(R.id.switchSound);

        btnSong1 = findViewById(R.id.btnSong1);
        btnSong2 = findViewById(R.id.btnSong2);
        btnSong3 = findViewById(R.id.btnSong3);
        btnSong4 = findViewById(R.id.btnSong4);
        btnCalibrate = findViewById(R.id.btnCalibrate);

        // Switch audio
        switchSound.setChecked(isSoundEnabled);
        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isSoundEnabled = isChecked;
            if (!isChecked) stopMusic();
            updateStatus();
        });

        // Pulsanti canzoni
        btnSong1.setOnClickListener(v -> selectSong(R.raw.cent));
        btnSong2.setOnClickListener(v -> selectSong(R.raw.natural));
        btnSong3.setOnClickListener(v -> selectSong(R.raw.pac));
        btnSong4.setOnClickListener(v -> selectSong(R.raw.sun));

        // Pulsante calibrazione
        btnCalibrate.setOnClickListener(v -> startCalibration());

        updateStatus();
    }

    private void selectSong(int resId) {
        currentSongResId = resId;
        if (isSoundEnabled) playMusic();
    }

    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            if (accelerometer == null || magnetometer == null) {
                Toast.makeText(this, "Sensori necessari non disponibili!",
                        Toast.LENGTH_LONG).show();
                tvStatus.setText("❌ Sensori non disponibili");
            }
        }
    }

    private void startCalibration() {
        calibrationCount = 0;
        calibrationOffset = 0f;
        Toast.makeText(this, "Calibrazione in corso... Ruota il telefono lentamente", Toast.LENGTH_LONG).show();
        tvStatus.setText("🔄 Calibrazione in corso...");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity[0] = 0.9f * gravity[0] + 0.1f * event.values[0];
            gravity[1] = 0.9f * gravity[1] + 0.1f * event.values[1];
            gravity[2] = 0.9f * gravity[2] + 0.1f * event.values[2];
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic[0] = 0.9f * geomagnetic[0] + 0.1f * event.values[0];
            geomagnetic[1] = 0.9f * geomagnetic[1] + 0.1f * event.values[1];
            geomagnetic[2] = 0.9f * geomagnetic[2] + 0.1f * event.values[2];
        }
        calculateOrientation();
    }

    private void calculateOrientation() {
        float[] R = new float[9];
        float[] I = new float[9];
        boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);

        if (success) {
            float[] orientation = new float[3];
            SensorManager.getOrientation(R, orientation);
            float azimuthDegrees = (float) Math.toDegrees(orientation[0]);
            azimuthDegrees = (azimuthDegrees + 360 + calibrationOffset) % 360;

            if (Math.abs(azimuthDegrees - currentAzimuth) > 0.5f) {
                currentAzimuth = azimuthDegrees;
                updateUI(azimuthDegrees);
                updateMusicTempo(azimuthDegrees);
            }

            if (calibrationCount < CALIBRATION_SAMPLES) {
                calibrationCount++;
                if (calibrationCount >= CALIBRATION_SAMPLES) {
                    Toast.makeText(this, "✅ Calibrazione completata!", Toast.LENGTH_SHORT).show();
                    updateStatus();
                }
            }
        }
    }

    private void updateUI(float azimuth) {
        runOnUiThread(() -> {
            tvDegrees.setText(String.format("%.1f°", azimuth));
            tvDirection.setText(getCardinalDirection(azimuth));

            int color = getColorForDirection(azimuth);
            tvDegrees.setTextColor(color);
            tvDirection.setTextColor(color);

            rotateCompass(azimuth);
        });
    }

    private void rotateCompass(float azimuth) {
        RotateAnimation rotateAnimation = new RotateAnimation(
                -lastAzimuth, -azimuth,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        rotateAnimation.setDuration(200);
        rotateAnimation.setFillAfter(true);
        imgCompass.startAnimation(rotateAnimation);
        lastAzimuth = azimuth;
    }

    private String getCardinalDirection(float azimuth) {
        if (azimuth >= 337.5 || azimuth < 22.5) return "N Nord";
        if (azimuth >= 22.5 && azimuth < 67.5) return "NE Nord-Est";
        if (azimuth >= 67.5 && azimuth < 112.5) return "E Est";
        if (azimuth >= 112.5 && azimuth < 157.5) return "SE Sud-Est";
        if (azimuth >= 157.5 && azimuth < 202.5) return "S Sud";
        if (azimuth >= 202.5 && azimuth < 247.5) return "SW Sud-Ovest";
        if (azimuth >= 247.5 && azimuth < 292.5) return "W Ovest";
        if (azimuth >= 292.5 && azimuth < 337.5) return "NW Nord-Ovest";
        return "N Nord";
    }

    private int getColorForDirection(float azimuth) {
        float distanceFromNorth = Math.min(azimuth, 360 - azimuth);
        if (distanceFromNorth < 10) return getColor(android.R.color.holo_green_dark);
        if (distanceFromNorth < 30) return getColor(android.R.color.holo_blue_dark);
        if (distanceFromNorth < 90) return getColor(android.R.color.holo_orange_dark);
        return getColor(android.R.color.holo_red_dark);
    }

    private void playMusic() {
        stopMusic();
        mediaPlayer = MediaPlayer.create(this, currentSongResId);
        mediaPlayer.setLooping(true);
        mediaPlayer.start();
    }

    private void stopMusic() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void updateMusicTempo(float azimuth) {
        if (!isSoundEnabled || mediaPlayer == null) return;
        float distanceFromNorth = Math.min(azimuth, 360 - azimuth);
        float speed = 1.0f - 0.5f * (distanceFromNorth / 180f);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
        }
    }

    private void updateStatus() {
        runOnUiThread(() -> tvStatus.setText(isSoundEnabled ? "🔊 Audio: ON" : "🔇 Audio: OFF"));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            runOnUiThread(() -> Toast.makeText(this, "⚠️ Sensore poco accurato", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null && magnetometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        stopMusic();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMusic();
    }
}