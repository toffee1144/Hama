package com.example.hama2

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
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
import java.util.concurrent.TimeUnit


object ApiService {

    private const val BASE_URL = "http://8.215.5.183:5000/api/"
    private const val WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?lat=-6.20927&lon=106.82&appid=b069d73bbbf77d5a83df8387fa85def1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)

        // 1) Interceptor that injects the Connection header
        .addInterceptor { chain ->
            val original = chain.request()
            val withConnectionClose = original.newBuilder()
                .header("Connection", "close")   // replace any existing header
                .build()
            chain.proceed(withConnectionClose)
        }

        // 2) Your logging interceptor (as a network interceptor is fine for logging bodies)
        .addNetworkInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

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
            .url("${BASE_URL}message")
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

    fun sendPredictRequest(
        context: Context,
        prompt: String,
        onResult: (ServerResponse) -> Unit
    ) {
        // 1) Build JSON request body
        val json = JSONObject().put("prompt", prompt).toString()
        val requestBody = json
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        // 2) Build request, asking for plain text
        val request = Request.Builder()
            .url("${BASE_URL}predict")
            .addHeader("Accept", "text/plain")
            .post(requestBody)
            .build()

        // 3) Fire it off
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                (context as? FragmentActivity)?.runOnUiThread {
                    onResult(ServerResponse(error = "Network error: ${e.localizedMessage}"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string().orEmpty()
                (context as? FragmentActivity)?.runOnUiThread {
                    if (!response.isSuccessful) {
                        onResult(ServerResponse(error = "Error ${response.code}: $bodyString"))
                    } else {
                        // bodyString is already raw text from the model
                        onResult(ServerResponse(response = bodyString))
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
            .url("${BASE_URL}chart")
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
