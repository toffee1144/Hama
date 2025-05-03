package com.example.hama2

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.hama2.databinding.FragmentSecondBinding
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import java.util.Calendar

class SecondFragment : Fragment() {
    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    private var lastFetchedHour: Int = -1
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // TextView all features
    private lateinit var txtVolume: TextView
    private lateinit var txtLuxThreshold: TextView
    private lateinit var txtAutoHour: TextView

    // Toggle Button state
    private val featureState = mutableMapOf(
        "speaker"    to false,
        "uv_lamp"    to false,
        "rat"        to false,
        "automation" to false
    )
    private val chartDataPoints = mutableListOf<ChartData>()

    private lateinit var btnSpeaker: Button
    private lateinit var btn_UV: Button
    private lateinit var btn_Rat: Button
    private lateinit var btn_Automation: Button

    private val refreshRunnable = object : Runnable {
        override fun run() {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour != lastFetchedHour) {
                lastFetchedHour = currentHour
                fetchAndRenderChart()
            }
            fetchAndUpdateStatus()
            fetchAndUpdateFeatures()
            refreshHandler.postDelayed(this, 2_000) // Default 60_000
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 4 card
        txtVolume     = view.findViewById(R.id.txt_Volume)
        txtLuxThreshold     = view.findViewById(R.id.txt_Lux_Threshold)
        txtAutoHour     = view.findViewById(R.id.txt_Auto_Hour)

        // buttons
        btnSpeaker = view.findViewById(R.id.btn_Speaker)
        btn_UV = view.findViewById(R.id.btn_UV) // add btn in XML for B/C/D
        btn_Rat = view.findViewById(R.id.btn_Rat)
        btn_Automation = view.findViewById(R.id.btn_Automation)

        listOf(
            "speaker"    to btnSpeaker,
            "uv_lamp"    to btn_UV,
            "rat"        to btn_Rat,
            "automation" to btn_Automation
        ).forEach { (key, btn) ->
            updateButtonTint(btn, featureState[key] == true)
            btn.setOnClickListener { toggleFeature(key, btn) }
        }

        ApiService.listenToChartStream { point ->
            activity?.runOnUiThread {
                chartDataPoints.add(point)
                renderChart(chartDataPoints)
            }
        }

        // initial fetches
        fetchAndRenderChart()
        fetchAndUpdateStatus()
        fetchAndUpdateFeatures()
        lastFetchedHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        refreshHandler.post(refreshRunnable)

    }

    private fun fetchAndUpdateStatus() {
        val txtSignal = view?.findViewById<TextView>(R.id.txt_Signal)
        val txtLastUpdate = view?.findViewById<TextView>(R.id.txt_Update)
        val txtVibration = view?.findViewById<TextView>(R.id.txt_Vibration)
        val txtSunlight = view?.findViewById<TextView>(R.id.txt_Lux)

        ApiService.fetchIconValues(requireContext()) { data, error ->
            activity?.runOnUiThread {
                if (data != null) {
                    txtSignal?.text = data.signal
                    txtLastUpdate?.text = data.lastUpdate
                    txtVibration?.text = data.vibration
                    txtSunlight?.text = data.sunlight
                } else {
                    Toast.makeText(context, error ?: "Unknown error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchAndUpdateFeatures() {
        ApiService.fetchFeatures(requireContext()) { resp, err ->
            activity?.runOnUiThread {
                if (resp != null) {
                    txtLuxThreshold.text = resp.luxThreshold.value
                    txtVolume.text = resp.volume.value
                    txtAutoHour.text = resp.autoHour.value
                }
            } ?: run {
                Toast.makeText(context, err ?: "Couldn't load features", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFeature(featureKey: String, btn: Button) {
        val current = featureState[featureKey] ?: false
        val next    = !current

        // Optimistically update UI
        updateButtonTint(btn, next)
        featureState[featureKey] = next

        ApiService.toggleFeature(requireContext(), featureKey, next) { success, err ->
            activity?.runOnUiThread {
                if (!success) {
                    // revert
                    featureState[featureKey] = current
                    updateButtonTint(btn, current)
                    Toast.makeText(context, err ?: "Toggle failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateButtonTint(btn: Button, isOn: Boolean) {
        // green when ON, light gray when OFF
        val color = if (isOn)
            ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
        else
            ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        btn.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun fetchAndRenderChart() {
        ApiService.fetchChartData { data, error ->
            if (error != null) {
                Log.e("SecondFragment", "Failed to load chart data: $error")
            } else if (data != null) {
                chartDataPoints.clear()
                chartDataPoints.addAll(data)
                renderChart(chartDataPoints)
            }
        }
    }


    private fun renderChart(data: List<ChartData>) {
        val entries = data.map { Entry(it.hour, it.value) }
        val maxValue = data.maxOf { it.value }

        val dataSet = LineDataSet(entries, "API Data").apply {
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(true)
            setDrawValues(true)
            color = Color.BLUE
            setCircleColor(Color.BLUE)
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.apply {
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = maxValue
                granularity = 1f
            }
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                axisMinimum = 0f
                axisMaximum = 24f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val hourVal = value.toInt().coerceIn(0, 24)
                        return String.format("%02d:00", hourVal)
                    }
                }
            }

            description = Description().apply { text = "Hourly Data (24h)" }
            setTouchEnabled(true)

            // Show hour when a data point is selected
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val selectedHour = it.x.toInt().coerceIn(0, 24)
                        Toast.makeText(
                            requireContext(),
                            String.format("Selected Hour: %02d:00", selectedHour),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onNothingSelected() {
                    // no-op
                }
            })

            animateX(500)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        refreshHandler.removeCallbacks(refreshRunnable)
    }
}
