package com.example.geupjo_bus

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class StepCountService : Service() {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var stepCount = 0
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    override fun onCreate() {
        super.onCreate()

        // 센서 매니저 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        // 알림 채널 생성 (Android O 이상에서 필수)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "StepCountServiceChannel",
                "Step Count Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // 포그라운드 서비스로 실행할 알림 설정
        notificationBuilder = NotificationCompat.Builder(this, "StepCountServiceChannel")
            .setContentTitle("오늘의 걸음 수")
            .setContentText("걸음 수: $stepCount")
            .setSmallIcon(android.R.drawable.ic_notification_overlay)

        startForeground(1, notificationBuilder.build()) // 포그라운드 서비스로 실행
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 센서 리스너 등록
        if (stepSensor != null) {
            sensorManager.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }

        return START_STICKY // 서비스가 종료되지 않도록 유지
    }

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
                stepCount += 1
                Log.d("StepCountService", "걸음 수: $stepCount")
                saveStepCount(stepCount)
                updateNotification(stepCount)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // 정확도 변경 시 처리 (필요한 경우)
        }
    }

    // 걸음 수를 SharedPreferences에 저장하는 함수
    private fun saveStepCount(stepCount: Int) {
        val sharedPreferences = getSharedPreferences("step_data", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("step_count", stepCount)
        editor.apply()
    }

    // 알림을 갱신하여 걸음 수를 표시
    private fun updateNotification(stepCount: Int) {
        val updatedNotification = notificationBuilder
            .setContentText("걸음 수: $stepCount") // 걸음 수 업데이트
            .build()

        notificationManager.notify(1, updatedNotification) // 알림 갱신
    }
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(stepListener) // 리스너 해제
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}