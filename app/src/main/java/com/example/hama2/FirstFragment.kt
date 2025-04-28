package com.example.hama2

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hama2.databinding.FragmentFirstBinding

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
            // permission denied
            // you can show a Toast or Dialog here if you want
        }
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

            adapter.addMessage(Message(text = "Okay response", isUser = false))
            binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
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
