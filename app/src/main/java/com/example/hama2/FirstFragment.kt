package com.example.hama2

import org.json.JSONObject
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hama2.databinding.FragmentFirstBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import android.widget.Toast

class FirstFragment : Fragment() {
    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MessageAdapter
    private var pickedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null

    // Launcher to take picture
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pickedImageUri = cameraImageUri
            binding.ivPreview.visibility = View.VISIBLE
            binding.ivPreview.setImageURI(cameraImageUri)
        }
    }


    // Launcher to request permission
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            // show a short message
            Toast.makeText(
                requireContext(),
                "Camera permission was denied. You won't be able to take photos.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun sendMessageToServer(prompt: String?, imageUri: Uri?, onResult: (String) -> Unit) {
        val resolver = requireContext().contentResolver
        val client = OkHttpClient()

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
            .url("http://192.168.1.13:5000/api/message")
            .post(requestBodyBuilder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Log the exception
                Log.e("FirstFragment", "Network failure", e)

                requireActivity().runOnUiThread {
                    val msg = "Unable to reach server: ${e.localizedMessage}"
                    Log.d("FirstFragment", msg)  // Log the toast message
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    onResult("Failed to connect to server.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyString = response.body?.string()
                requireActivity().runOnUiThread {
                    if (!response.isSuccessful || bodyString.isNullOrEmpty()) {
                        val errMsg = "Error ${response.code}: ${bodyString ?: "No details"}"
                        Log.w("FirstFragment", errMsg)
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show()
                        onResult(errMsg)
                    } else {
                        try {
                            // Parse the JSON and extract the "response" field
                            val json = JSONObject(bodyString)
                            val aiReply = json.optString("response", bodyString)
                            Log.i("FirstFragment", "AI reply: $aiReply")
                            onResult(aiReply)
                        } catch (e: Exception) {
                            // If parsing fails, just fallback to the raw body
                            Log.e("FirstFragment", "JSON parse error", e)
                            onResult(bodyString)
                        }
                    }
                }
            }

        })
    }



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // init RecyclerView + Adapter
        adapter = MessageAdapter(mutableListOf())
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = this@FirstFragment.adapter
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim().takeIf { it.isNotEmpty() }
            val uri = pickedImageUri

            if (text == null && uri == null) {
                return@setOnClickListener
            }

            adapter.addMessage(
                Message(
                    text = text,
                    imageUri = uri,
                    isUser = true
                )
            )

            binding.etMessage.text?.clear()
            pickedImageUri = null
            binding.ivPreview.visibility = View.GONE

            sendMessageToServer(text, uri) { serverResponse ->
                adapter.addMessage(
                    Message(
                        text = serverResponse,
                        isUser = false
                    )
                )
                binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
            }
        }


        binding.btnInsertPhoto.setOnClickListener {
            checkCameraPermissionAndOpenCamera()
        }
    }

    private fun checkCameraPermissionAndOpenCamera() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                openCamera()
            }
            else -> {
                // Request permission
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        val resolver = requireContext().contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        cameraImageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        takePictureLauncher.launch(cameraImageUri)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
