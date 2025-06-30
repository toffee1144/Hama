package com.example.hama2

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.example.hama2.databinding.FragmentHomepageBinding
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

class HomepageFragment : Fragment() {

    private var _binding: FragmentHomepageBinding? = null
    private val binding get() = _binding!!

    private lateinit var mqttService: MqttService
    private var lastFetchedHour: Int = -1
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Device / automation states
    private var ratStatus = false
    private var uvStatus = false
    private var soundStatus = false
    private var automationEnabled = false

    // Launcher for POST_NOTIFICATIONS
    private val requestNotificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) showDetectionNotification("Notifications enabled")
            else Log.w("SecondFragment", "User denied POST_NOTIFICATIONS")
        }

    private fun getSavedDeviceId(): String? {
        val prefs = requireContext().getSharedPreferences("MyPrefs", android.content.Context.MODE_PRIVATE)
        return prefs.getString("espId", null)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomepageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val deviceId = getSavedDeviceId()
        if (deviceId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Please set Device ID first", Toast.LENGTH_SHORT).show()

            // Disable all control buttons
            binding.btnRat.isEnabled = false
            binding.btnSpeaker.isEnabled = false
            binding.btnUV.isEnabled = false
            binding.btnAutomation.isEnabled = false

            // Optional: Set click listeners to show warning
            val showWarning = View.OnClickListener {
                Toast.makeText(requireContext(), "Please set Device ID first", Toast.LENGTH_SHORT).show()
            }

            binding.btnRat.setOnClickListener(showWarning)
            binding.btnSpeaker.setOnClickListener(showWarning)
            binding.btnUV.setOnClickListener(showWarning)
            binding.btnAutomation.setOnClickListener(showWarning)

            return  // Exit early to prevent further setup
        }
        val brokerUrl = MqttService.getBrokerUrl(requireContext())

        // 1) MQTT setup + callback
        mqttService = MqttService(requireContext()).apply {
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

                            // DATA topic: update sensor readings
                            if (topic == MqttService.TOPIC_DATA) {
                                binding.txtLux.text = json.optInt("lux").toString()
                                binding.txtVibration.text = json.optInt("vibration").toString()
                                binding.txtSignal.text = json.optInt("signal").toString()
                                binding.txtUpdate.text = DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString()

                                if (json.optInt("vibration") > 0) {
                                    showDetectionNotification("Vibration detected!")
                                }

                                val radar0 = json.optInt("radar_0", 0)
                                val radar1 = json.optInt("radar_1", 0)
                                binding.txtDetection.text = if (radar0 == 1 || radar1 == 1) "Detected!" else "No Detection"
                            }

                            // CONTROL topic: update states
                            if (topic == MqttService.CONTROL_DATA) {
                                ratStatus = json.optInt("set_status_rat", if (ratStatus) 1 else 0) == 1
                                soundStatus = json.optInt("set_status_sound", if (soundStatus) 1 else 0) == 1
                                uvStatus = json.optInt("set_status_uv_lamp", if (uvStatus) 1 else 0) == 1

                                // Read each automation flag separately
                                val autoRat = json.optInt("set_automation_rat", if (automationEnabled) 1 else 0) == 1
                                val autoSound = json.optInt("set_automation_sound", if (automationEnabled) 1 else 0) == 1
                                val autoUV = json.optInt("set_automation_uv_lamp", if (automationEnabled) 1 else 0) == 1

                                // Combine them
                                automationEnabled = autoRat || autoSound || autoUV

                                // Sync buttons
                                syncButton(binding.btnRat, ratStatus)
                                syncButton(binding.btnSpeaker, soundStatus)
                                syncButton(binding.btnUV, uvStatus)
                                syncButton(binding.btnAutomation, automationEnabled)

                                setManualEnabled(!automationEnabled)
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

        // 2) Initialize all buttons
        syncButton(binding.btnRat, ratStatus)
        syncButton(binding.btnSpeaker, soundStatus)
        syncButton(binding.btnUV, uvStatus)
        syncButton(binding.btnAutomation, automationEnabled)
        setManualEnabled(!automationEnabled)

        // 3) Button click listeners
        binding.btnRat.setOnClickListener {
            toggleDevice(
                "set_status_rat",
                "set_automation_rat",
                binding.btnRat
            )
        }

        binding.btnSpeaker.setOnClickListener {
            toggleDevice(
                "set_status_sound",
                "set_automation_sound",
                binding.btnSpeaker
            )
        }

        binding.btnUV.setOnClickListener {
            toggleDevice(
                "set_status_uv_lamp",
                "set_automation_uv_lamp",
                binding.btnUV
            )
        }

// leave toggleAutomation() unchanged for your AUTO button
        binding.btnAutomation.setOnClickListener {
            toggleAutomation()
        }

        // 4) Chart + notif-channel + periodic refresh
        fetchAndRenderChart()
        createNotificationChannel()
        lastFetchedHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        refreshHandler.post(refreshRunnable)

        // 5) Weather
        ApiService.fetchWeatherInfo(requireContext()) { city, temperature, error ->
            if (error != null) Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            else {
                binding.txtLocation.text = city
                binding.txtTemperature.text = temperature
            }
        }
    }

    private fun toggleDevice(
        statusField: String,
        automationField: String,
        btn: View
    ) {
        // flip the manual status
        val newValue = !(btn.tag as? Boolean ?: false)

        // build JSON with just the two keys:
        val json = JSONObject().apply {
            put(statusField, if (newValue) 1 else 0)
            put(automationField, if (automationEnabled) 1 else 0)
        }

        // publish the bundle
        mqttService.publish(MqttService.CONTROL_DATA, json.toString())

        // update this button’s UI immediately
        syncButton(btn, newValue)
    }

    private fun toggleAutomation() {
        automationEnabled = !automationEnabled
        val json = JSONObject().apply {
            put("set_automation_rat", if (automationEnabled) 1 else 0)
            put("set_automation_sound", if (automationEnabled) 1 else 0)
            put("set_automation_uv_lamp", if (automationEnabled) 1 else 0)
        }
        mqttService.publish(MqttService.CONTROL_DATA, json.toString())
        syncButton(binding.btnAutomation, automationEnabled)
        setManualEnabled(!automationEnabled)
    }

    private fun syncButton(button: View, isOn: Boolean) {
        button.tag = isOn
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(),
                if (isOn) R.color.color_primary else R.color.color_button_off
            )
        )
        (button as? Button)?.text = if (isOn) "ON" else "OFF"
    }

    private fun setManualEnabled(enabled: Boolean) {
        binding.btnRat.isEnabled = enabled
        binding.btnSpeaker.isEnabled = enabled
        binding.btnUV.isEnabled = enabled
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

    private fun fetchAndRenderChart() {
        ApiService.fetchChartData { data, error ->
            activity?.runOnUiThread {
                if (!data.isNullOrEmpty()) {
                    val entries = data.map { Entry(it.index.toFloat(), it.value) }

                    val cPrimary = ContextCompat.getColor(requireContext(), R.color.color_primary)
                    val cOnBackground = ContextCompat.getColor(requireContext(), R.color.color_on_background)

                    val dataSet = LineDataSet(entries, "Radar Turned On").apply {
                        color = cPrimary // Line color
                        setCircleColor(color) // Circle color
                        valueTextColor = cOnBackground // Value label text color

                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawValues(true)
                    }
                    binding.lineChart.data = LineData(dataSet)
                    binding.lineChart.xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        granularity = 1f
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float) =
                                data.firstOrNull { it.index == value.toInt() }?.hour ?: ""
                        }
                    }
                    binding.lineChart.description = Description().apply {
                        text = "24-hour data"
                        textSize = 12f
                    }

                    binding.lineChart.invalidate()
                } else {
                    Log.w("SecondFragment", "No chart data: $error")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            "detection_channel",
            "Detection Alerts",
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Notifications for detection events" }
        val nm = requireContext().getSystemService(
            android.content.Context.NOTIFICATION_SERVICE
        ) as android.app.NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun showDetectionNotification(title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        val builder = NotificationCompat.Builder(requireContext(), "detection_channel")
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        NotificationManagerCompat.from(requireContext())
            .notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::mqttService.isInitialized) {
            mqttService.disconnect()
        }
        _binding = null
    }
}
