package com.example.hama2

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

object ApiService {
    private val client = OkHttpClient.Builder()
        .addNetworkInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

    private const val BASE_URL = "http://192.168.1.28:5000/api/"
    private const val WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?lat=-6.20927&lon=106.82&appid=b069d73bbbf77d5a83df8387fa85def1"

    fun sendMessage(
        context: Context,
        prompt: String?,
        imageUri: Uri?,
        onResult: (ServerResponse) -> Unit
    ) {
        val resolver = context.contentResolver

        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        prompt?.let { requestBodyBuilder.addFormDataPart("prompt", it) }
        imageUri?.let { uri ->
            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                requestBodyBuilder.addFormDataPart(
                    "image",
                    "upload.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                )
            }
        }

        val request = Request.Builder()
            .url("$BASE_URL/message")
            .post(requestBodyBuilder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "Network failure", e)
                (context as? androidx.fragment.app.FragmentActivity)?.runOnUiThread {
                    val msg = "Unable to reach server: ${e.localizedMessage}"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    onResult(ServerResponse(error = msg))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string()
                (context as? androidx.fragment.app.FragmentActivity)?.runOnUiThread {
                    if (!response.isSuccessful || bodyString.isNullOrEmpty()) {
                        val errMsg = "Error ${response.code}: ${bodyString ?: "No details"}"
                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                        onResult(ServerResponse(error = errMsg))
                    } else {
                        try {
                            val json = JSONObject(bodyString)
                            val aiReply = json.optString("response", bodyString)
                            onResult(ServerResponse(response = aiReply))
                        } catch (e: Exception) {
                            Log.e("ApiService", "JSON parse error", e)
                            onResult(ServerResponse(response = bodyString))
                        }
                    }
                }
            }
        })
    }


    // ----------------- HOME FRAGMENTS ---------------------

    fun fetchChartData(
        onResult: (List<ChartData>?, String?) -> Unit
    ) {
        val request = Request.Builder()
            .url("$BASE_URL/chart")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "Chart data fetch failed", e)
                onResult(null, "Network failure: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()
                if (!response.isSuccessful || bodyStr.isNullOrEmpty()) {
                    onResult(null, "Error ${response.code}: ${bodyStr ?: "no details"}")
                    return
                }

                try {
                    // Parse top-level object, then extract its "data" array:
                    val rootObj = JSONObject(bodyStr)
                    val dataArray = rootObj.getJSONArray("data")

                    val list = mutableListOf<ChartData>()
                    for (i in 0 until dataArray.length()) {
                        val obj = dataArray.getJSONObject(i)
                        val hour  = obj.optString("hour", "00:00")
                        val value = obj.optDouble("value", 0.0).toFloat()
                        val idx   = obj.optInt("index", i + 1)
                        list.add(ChartData(hour, value, idx))
                    }

                    onResult(list, null)
                } catch (e: Exception) {
                    Log.e("ApiService", "JSON parse error", e)
                    onResult(null, "Data parse error")
                }
            }
        })
    }

/*
    fun listenToChartStream(onPointReceived: (ChartData) -> Unit) {
        val request = Request.Builder()
            .url("${BASE_URL}chart/stream")
            .build()

        EventSources.createFactory(client)
            .newEventSource(request, object : EventSourceListener() {

                override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                    Log.d("ApiService", "SSE connected")
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    try {
                        val obj = JSONObject(data)
                        val hourStr = obj.optString("hour", "00:00")
                        val hour = try {
                            hourStr.substringBefore(":").toFloat()
                        } catch (e: Exception) {
                            0f
                        }
                        val value = obj.optInt("value", 0).toFloat()
                        onPointReceived(ChartData(hour, value))
                    } catch (e: Exception) {
                        Log.e("ApiService", "Failed to parse SSE data", e)
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    Log.d("ApiService", "SSE closed")
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: okhttp3.Response?
                ) {
                    Log.e("ApiService", "SSE error", t)
                }
            })
    }
*/

    fun fetchWeatherInfo(
        context: Context,
        onResult: (String?, String?, String?) -> Unit
    ) {
        val request = Request.Builder()
            .url(WEATHER_URL)
            .get()
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onResult(null, null, "Network error: ${e.localizedMessage}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onResult(null, null, "Error ${response.code}")
                    }
                    return
                }

                try {
                    val json = JSONObject(body)
                    val city = json.optString("name", "Unknown location")
                    val tempKelvin = json.getJSONObject("main").optDouble("temp", 0.0)
                    val tempCelsius = (tempKelvin - 273.15).toInt().toString() + "°C"

                    // Post result to main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onResult(city, tempCelsius, null)
                    }
                } catch (e: Exception) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onResult(null, null, "Parsing error")
                    }
                }
            }
        })
    }


    /* 4 ICON VALUES FROM API
    fun fetchIconValues(
        context: Context,
        onResult: (IconData?, String?) -> Unit
    ) {
        val request = Request.Builder()
            .url("${BASE_URL}/status") // change endpoint if needed
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "Failed to fetch icons", e)
                onResult(null, "Network error: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    onResult(null, "Error ${response.code}")
                    return
                }

                try {
                    val json = JSONObject(body)
                    val data = IconData(
                        signal = json.optString("signal", "N/A"),
                        lastUpdate = json.optString("last_update", "N/A"),
                        vibration = json.optString("vibration", "N/A"),
                        sunlight = json.optString("sunlight", "N/A")
                    )
                    onResult(data, null)
                } catch (e: Exception) {
                    Log.e("ApiService", "JSON parsing error", e)
                    onResult(null, "Parsing error")
                }
            }
        })
    }

    fun fetchFeatures(
        context: Context,
        onResult: (FeaturesResponse?, String?) -> Unit
    ) {
        val request = Request.Builder()
            .url("${BASE_URL}/features")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "fetchFeatures failed", e)
                onResult(null, "Network error: ${e.localizedMessage}")
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    onResult(null, "Error ${response.code}")
                    return
                }
                try {
                    val j = JSONObject(body)
                    // parse each feature
                    val a = j.getJSONObject("speaker")
                    val b = j.getJSONObject("uv_lamp")
                    val c = j.getJSONObject("rat")

                    val resp = FeaturesResponse(
                        volume = FeatureData(
                            subtitle = a.optString("subtitle", ""),
                            value    = a.optString("value", ""),
                        ),
                        luxThreshold = FeatureData(
                            subtitle = b.optString("subtitle", ""),
                            value    = b.optString("value", "")
                        ),
                        autoHour = FeatureData(
                            subtitle = c.optString("subtitle", ""),
                            value    = c.optString("value", "")
                        )
                    )
                    onResult(resp, null)
                } catch (e: Exception) {
                    Log.e("ApiService", "JSON parse error", e)
                    onResult(null, "Parse error")
                }
            }
        })
    }

    fun toggleFeature(
        context: Context,
        featureKey: String,
        newState: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        // JSON body: { "state": true }
        val json = JSONObject().put("state", newState)
        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
                .url("$BASE_URL/features/$featureKey/toggle")
            .post(body)
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "toggleFeature failure", e)
                onResult(false, e.localizedMessage)
            }
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string()
                if (!response.isSuccessful || text.isNullOrEmpty()) {
                    onResult(false, "Error ${response.code}")
                    return
                }
                try {
                    val j = JSONObject(text)
                    val ok = j.optBoolean("success", response.isSuccessful)
                    val msg = j.optString("message", null)
                    onResult(ok, msg)
                } catch (e: Exception) {
                    Log.e("ApiService", "toggle parse error", e)
                    onResult(false, "Parse error")
                }
            }
        })
    }
    */

    // ------------------ THIRD FRAGMENT --------------------------

    fun sendDeviceId(
        req1: Context,
        req: DeviceRequest,
        onResult: (ApiResponse) -> Unit
    ) {
        val body = JSONObject()
            .put("device_id", req.deviceId)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("${BASE_URL}device")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "sendDeviceId failed", e)
                onResult(ApiResponse(false, e.localizedMessage))
            }
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string()
                if (!response.isSuccessful || text.isNullOrEmpty()) {
                    onResult(ApiResponse(false, "Error ${response.code}"))
                    return
                }
                try {
                    val j = JSONObject(text)
                    onResult(
                        ApiResponse(
                            success = j.optBoolean("success", true),
                            message = j.optString("message", null)
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ApiService", "sendDeviceId parse error", e)
                    onResult(ApiResponse(false, "Parse error"))
                }
            }
        })
    }

    fun sendVolume(
        context: Context,
        req: VolumeRequest,
        onResult: (ApiResponse) -> Unit
    ) {
        val body = JSONObject()
            .put("volume", req.volume)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("${BASE_URL}volume")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "sendVolume failed", e)
                onResult(ApiResponse(false, e.localizedMessage))
            }
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string()
                if (!response.isSuccessful || text.isNullOrEmpty()) {
                    onResult(ApiResponse(false, "Error ${response.code}"))
                    return
                }
                try {
                    val j = JSONObject(text)
                    onResult(
                        ApiResponse(
                            success = j.optBoolean("success", true),
                            message = j.optString("message", null)
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ApiService", "sendVolume parse error", e)
                    onResult(ApiResponse(false, "Parse error"))
                }
            }
        })
    }

    fun sendLux(
        context: Context,
        req: LuxRequest,
        onResult: (ApiResponse) -> Unit
    ) {
        val jsonBody = JSONObject()
            .put("lux", req.lux)
            .toString()

        Log.d("ApiService", "→ POST /api/lux  JSON = $jsonBody")

        val requestBody = jsonBody
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("${BASE_URL}lux")
            .post(requestBody)
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiService", "sendLux failed", e)
                onResult(ApiResponse(false, e.localizedMessage))
            }
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string()
                if (!response.isSuccessful || text.isNullOrEmpty()) {
                    onResult(ApiResponse(false, "Error ${response.code}"))
                    return
                }
                try {
                    val j = JSONObject(text)
                    onResult(
                        ApiResponse(
                            success = j.optBoolean("success", true),
                            message = j.optString("message", null)
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ApiService", "sendLux parse error", e)
                    onResult(ApiResponse(false, "Parse error"))
                }
            }
        })
    }

}
