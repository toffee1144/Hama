package com.example.hama2

import android.content.Context
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttService(private val context: Context) {

    companion object {
        const val TOPIC_DATA = "sim800l/hama/data"
        const val CONTROL_DATA = "sim800l/hama/control"

        // ✅ Static function to get broker URL using context
        fun getBrokerUrl(context: Context): String {
            val prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val deviceId = prefs.getString("espId", "000") ?: "000"
            return "tcp://8.$deviceId.5.183:1883"
        }
    }

    private var client: MqttClient? = null

    fun connect() {
        try {
            val brokerUrl = getBrokerUrl(context)  // Uses the static method
            val persistence = MemoryPersistence()
            val clientId = MqttClient.generateClientId()
            client = MqttClient(brokerUrl, clientId, persistence)

            val options = MqttConnectOptions().apply {
                isCleanSession = true
            }

            client?.connect(options)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            client?.disconnect()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun publish(topic: String, message: String) {
        try {
            val mqttMessage = MqttMessage(message.toByteArray())
            client?.publish(topic, mqttMessage)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun subscribe(topic: String) {
        try {
            client?.subscribe(topic)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun setCallback(callback: MqttCallback) {
        client?.setCallback(callback)
    }
}
