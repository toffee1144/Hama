package com.example.hama2

import android.Manifest
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.example.hama2.databinding.FragmentSecondBinding
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject
import java.util.*

class SecondFragment : Fragment() {
    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    // Launcher for the POST_NOTIFICATIONS permission dialog
    private val requestNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // user just granted—re-send a test or pending notification if any
                showDetectionNotification("Notifications enabled", "You’ll now receive alerts")
            } else {
                Log.w("SecondFragment", "User denied POST_NOTIFICATIONS")
            }
        }

    private lateinit var mqttService: MqttService
    private var lastFetchedHour: Int = -1
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private lateinit var txtVolume: TextView
    private lateinit var txtLuxThreshold: TextView
    private lateinit var txtAutoHour: TextView

    private lateinit var btnSpeaker: Button
    private lateinit var btnUV: Button
    private lateinit var btnRat: Button
    private lateinit var btnAutomation: Button
    private var ratStatus = false
    private var uvStatus = false
    private var soundStatus = false
    private var automationStatus = false

    private var automationEnabled = false
    private val chartDataPoints = mutableListOf<ChartData>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- MQTT setup ---
        mqttService = MqttService().apply {
            connect()
            subscribe(MqttService.TOPIC_DATA)
            subscribe(MqttService.CONTROL_DATA)
            setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    activity?.runOnUiThread { binding.txtSignal.text = "Connection lost" }
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    val payload = message.toString()
                    activity?.runOnUiThread {
                        try {
                            val json = JSONObject(payload)

                            if (topic == MqttService.TOPIC_DATA) {
                                binding.txtLux.text = json.optInt("lux").toString()
                                binding.txtVibration.text = json.optInt("vibration").toString()
                                binding.txtSignal.text = json.optInt("signal").toString()
                                binding.txtUpdate.text =
                                    DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString()

                                val vibration = json.optInt("vibration")
                                if (vibration > 0) {
                                    showDetectionNotification(
                                        "Vibration detected!",
                                        "Vibration level: $vibration"
                                    )
                                }
                            }

                            if (topic == MqttService.CONTROL_DATA) {
                                ratStatus = json.optInt("set_status_rat", if (ratStatus) 1 else 0) == 1
                                soundStatus = json.optInt("set_status_sound", if (soundStatus) 1 else 0) == 1
                                uvStatus = json.optInt("set_status_uv_lamp", if (uvStatus) 1 else 0) == 1
                                automationStatus = json.optInt(
                                    "set_automation_uv_lamp",
                                    if (automationStatus) 1 else 0
                                ) == 1

                                updateButtonState(btnRat, ratStatus)
                                updateButtonState(btnSpeaker, soundStatus)
                                updateButtonState(btnUV, uvStatus)
                                updateButtonState(btnAutomation, automationStatus)
                                updateButtonEnabledState()
                            }

                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Invalid MQTT data", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
        }

        // --- UI refs & listeners ---
        txtVolume = binding.txtVolume
        txtLuxThreshold = binding.txtLuxThreshold
        txtAutoHour = binding.txtAutoHour

        btnSpeaker = binding.btnSpeaker
        btnUV = binding.btnUV
        btnRat = binding.btnRat
        btnAutomation = binding.btnAutomation

        btnSpeaker.setOnClickListener { toggleDevice("set_status_sound", btnSpeaker) }
        btnUV.setOnClickListener { toggleDevice("set_status_uv_lamp", btnUV) }
        btnRat.setOnClickListener { toggleDevice("set_status_rat", btnRat) }
        btnAutomation.setOnClickListener { toggleAutomation() }

        fetchAndRenderChart()
        createNotificationChannel()

        lastFetchedHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        refreshHandler.post(refreshRunnable)

        // Weather fetch
        ApiService.fetchWeatherInfo(requireContext()) { city, temperature, error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            } else {
                binding.txtLocation.text = city
                binding.txtTemperature.text = temperature
            }
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour != lastFetchedHour) {
                lastFetchedHour = currentHour
                fetchAndRenderChart()
            }
            refreshHandler.postDelayed(this, 2000)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Detection Alerts"
            val descriptionText = "Notifications for detection events"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel =
                android.app.NotificationChannel("detection_channel", name, importance).apply {
                    description = descriptionText
                }
            val notificationManager =
                requireContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                        as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showDetectionNotification(title: String, content: String) {
        // On Android 13+ we need POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPerm) {
                requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val builder = NotificationCompat.Builder(requireContext(), "detection_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(requireContext())
        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (secEx: SecurityException) {
            Log.w("SecondFragment", "Notification permission missing", secEx)
        }
    }

    private fun fetchAndRenderChart() {
        ApiService.fetchChartData { data, error ->
            activity?.runOnUiThread {
                if (!data.isNullOrEmpty()) {
                    chartDataPoints.clear()
                    chartDataPoints.addAll(data)
                    renderChart(chartDataPoints)
                } else {
                    Log.w("SecondFragment", "No chart data: $error")
                }
            }
        }
    }

    private fun renderChart(data: List<ChartData>) {
        val entries = data.map { Entry(it.index.toFloat(), it.value) }
        val dataSet = LineDataSet(entries, "API Data").apply {
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return data.firstOrNull { it.index == value.toInt() }?.hour ?: ""
                }
            }
        }

        binding.lineChart.description = Description().apply {
            text = "24-hour data"
            textSize = 12f
        }

        binding.lineChart.invalidate()
    }

    private fun toggleDevice(field: String, btn: Button) {
        val current = btn.tag as? Boolean ?: false
        val newValue = if (current) 0 else 1
        val json = JSONObject().put(field, newValue)
        mqttService.publish(MqttService.CONTROL_DATA, json.toString())

        when (field) {
            "set_status_rat"      -> ratStatus = newValue == 1
            "set_status_uv_lamp"  -> uvStatus = newValue == 1
            "set_status_sound"    -> soundStatus = newValue == 1
        }

        updateButtonState(btn, newValue == 1)
        updateButtonText(btn, newValue == 1)
    }

    private fun toggleAutomation() {
        automationEnabled = !automationEnabled
        val json = JSONObject().apply {
            put("set_automation_uv_lamp", if (automationEnabled) 1 else 0)
            put("set_automation_rat", if (automationEnabled) 1 else 0)
            put("set_automation_sound", if (automationEnabled) 1 else 0)
        }
        mqttService.publish(MqttService.CONTROL_DATA, json.toString())
        updateButtonState(btnAutomation, automationEnabled)
        // Enable/disable manual buttons
        btnRat.isEnabled = !automationEnabled
        btnSpeaker.isEnabled = !automationEnabled
        btnUV.isEnabled = !automationEnabled

        // Update texts
        if (automationEnabled) {
            listOf(btnRat, btnSpeaker, btnUV).forEach { it.text = "AUTO" }
        } else {
            updateButtonText(btnRat, ratStatus)
            updateButtonText(btnSpeaker, soundStatus)
            updateButtonText(btnUV, uvStatus)
        }
    }

    private fun updateButtonState(button: Button, isOn: Boolean) {
        val color = if (isOn) ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
        else ContextCompat.getColor(requireContext(), android.R.color.holo_purple)
        button.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun updateButtonText(button: Button, isOn: Boolean) {
        button.text = when {
            automationEnabled -> "AUTO"
            isOn              -> "Turn Off"
            else              -> "Turn On"
        }
    }

    private fun updateButtonEnabledState() {
        val enabled = !automationEnabled
        btnRat.isEnabled = enabled
        btnSpeaker.isEnabled = enabled
        btnUV.isEnabled = enabled
    }

    override fun onDestroyView() {
        _binding = null
        mqttService.disconnect()
        refreshHandler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }
}
