package com.example.hama2

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit

class SettingFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?)
            = inflater.inflate(R.layout.fragment_setting, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val label2 = view.findViewById<TextView>(R.id.label2)
        val label3 = view.findViewById<TextView>(R.id.label3)

        val edtDevice = view.findViewById<EditText>(R.id.EspID)
        val btn1      = view.findViewById<Button>(R.id.button1)
        val seek2     = view.findViewById<SeekBar>(R.id.seekBar2)
        val btn2      = view.findViewById<Button>(R.id.button2)
        val seek3     = view.findViewById<SeekBar>(R.id.seekBar3)
        val btn3      = view.findViewById<Button>(R.id.button3)


        seek2.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val display = progress * 10
                label2.text = "$display"

                val thumbX = sb.paddingLeft +
                        (sb.width - sb.paddingLeft - sb.paddingRight) * progress / sb.max
                val labelWidth = label2.measuredWidth.takeIf { it>0 } ?: label2.paint.measureText(label2.text.toString()).toInt()
                label2.x = (thumbX - labelWidth/2).toFloat()
                label2.visibility = View.VISIBLE
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                label2.visibility = View.GONE
            }
        })

        seek3.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val display = progress * 10
                label3.text = "$display"

                val thumbX = sb.paddingLeft +
                        (sb.width - sb.paddingLeft - sb.paddingRight) * progress / sb.max
                val labelWidth = label3.measuredWidth.takeIf { it>0 } ?: label3.paint.measureText(label3.text.toString()).toInt()
                label3.x = (thumbX - labelWidth/2).toFloat()
                label3.visibility = View.VISIBLE
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                label3.visibility = View.GONE
            }
        })


        // force measurement so measuredWidth is valid
        label2.post { label2.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        ) }

        label3.post { label3.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        ) }

        btn1.setOnClickListener {
            val id = edtDevice.text.toString().trim()
            if (id.isEmpty()) {
                Toast.makeText(context, "Please enter a device ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val numericId = id.removePrefix("Dev-").trim()

            ApiService.sendDeviceId(requireContext(), DeviceRequest(id)) { res ->
                activity?.runOnUiThread {
                    if (res.success) {
                        // Save only the numeric part (e.g., "215")
                        val prefs = requireContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                        prefs.edit { putString("espId", numericId) }

                        edtDevice.hint = "Current ID : $id"
                        edtDevice.setText("")

                        Toast.makeText(context, "Device ID set", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, res.message ?: "Failed to set device ID", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btn2.setOnClickListener {
            val volume = seek2.progress * 10
            ApiService.sendVolume(requireContext(), VolumeRequest(volume)) { res ->
                activity?.runOnUiThread {
                    Toast.makeText(context, res.message ?: "Success", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btn3.setOnClickListener {
            val lux = seek3.progress * 10
            ApiService.sendLux(requireContext(), LuxRequest(lux)) { res ->
                activity?.runOnUiThread {
                    Toast.makeText(context, res.message ?: "Success", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }
}
