package com.example.hama2

import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttService {

    companion object {
        private const val BROKER_URL = "tcp://8.215.5.183:1883"
        const val TOPIC_DATA = "sim800l/hama/data"
        const val CONTROL_DATA = "sim800l/hama/control"
    }

    private var client: MqttClient? = null

    fun connect() {
        try {
            val persistence = MemoryPersistence()
            val clientId = MqttClient.generateClientId()
            client = MqttClient(BROKER_URL, clientId, persistence)

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
