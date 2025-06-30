package com.example.hama2

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Toast
import java.util.Locale
import android.content.Intent
import android.speech.RecognizerIntent
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.example.hama2.databinding.FragmentChatbotBinding

class ChatbotFragment : Fragment(), TextToSpeech.OnInitListener {
    private lateinit var textToSpeech: TextToSpeech
    private var isTtsReady = false
    private var _binding: FragmentChatbotBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MessageAdapter
    private var pickedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null
    private var isPredictMode = false

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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.US
            isTtsReady = true
        }
    }

    private val speechRecognizerLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                binding.etMessage.setText(matches[0])
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatbotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // init RecyclerView + Adapter
        textToSpeech = TextToSpeech(requireContext(), this)

        adapter = MessageAdapter(
            mutableListOf(),
            textToSpeech
        ) { isTtsReady }
        binding.rvMessages.adapter = adapter

        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = this@ChatbotFragment.adapter
        }

        val speechSupported = requireActivity().packageManager
            .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()

        binding.btnMic.isEnabled = speechSupported

        if (speechSupported) {
            binding.btnMic.setOnClickListener {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                }
                speechRecognizerLauncher.launch(intent)
            }
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim().takeIf { it.isNotEmpty() }
            val uri = pickedImageUri

            if (text == null && uri == null) {
                return@setOnClickListener
            }

            // Add user message to chat (for both predict and normal)
            adapter.addMessage(
                Message(
                    text = text,
                    imageUri = uri,
                    isUser = true
                )
            )


            // Clear input and hide preview
            binding.etMessage.text?.clear()
            pickedImageUri = null
            binding.ivPreview.visibility = View.GONE

            if (isPredictMode) {
                // Send to /api/predict
                ApiService.sendPredictRequest(requireContext(), text ?: "") { response ->
                    val replyText = response.response ?: response.error ?: "Unknown error."
                    adapter.addMessage(
                        Message(
                            text = replyText,
                            isUser = false
                        )
                    )
                    binding.rvMessages.scrollToPosition(adapter.itemCount - 1)

                    // Optionally reset predict mode off
                    isPredictMode = false
                    binding.btnPredict.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.color_primary)
                    )
                }
            } else {
                // Send to /api/message (with possible image)
                ApiService.sendMessage(requireContext(), text, uri) { serverResponse ->
                    val replyText = serverResponse.response ?: serverResponse.error ?: "Unknown error."
                    adapter.addMessage(
                        Message(
                            text = replyText,
                            isUser = false
                        )
                    )
                    binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }



        binding.btnPredict.setOnClickListener {
            isPredictMode = !isPredictMode  // toggle predict mode

            if (isPredictMode) {
                // change Predict button to active color
                binding.btnPredict.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.color_primary_dark) // active color
                )
                Toast.makeText(requireContext(), "Predict mode ON", Toast.LENGTH_SHORT).show()
            } else {
                // revert Predict button color
                binding.btnPredict.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.color_primary) // normal color
                )
                Toast.makeText(requireContext(), "Predict mode OFF", Toast.LENGTH_SHORT).show()
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
        textToSpeech.stop()
        textToSpeech.shutdown()
        _binding = null
    }
}
