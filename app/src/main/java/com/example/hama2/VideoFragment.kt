package com.example.hama2

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.fragment.app.Fragment
import androidx.core.net.toUri
import androidx.navigation.fragment.findNavController

class VideoFragment : Fragment() {
    private lateinit var videoView: VideoView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_video, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoView = view.findViewById(R.id.videoView)
        val videoUri = "android.resource://${requireContext().packageName}/${R.raw.roll}".toUri()

        val mediaController = MediaController(requireContext()).apply {
            setAnchorView(videoView)
        }
        videoView.setMediaController(mediaController)
        videoView.setVideoURI(videoUri)
        videoView.requestFocus()
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            videoView.start()
        }
    }

    override fun onPause() {
        super.onPause()
        // 1) stop & release the MediaPlayer
        if (::videoView.isInitialized && videoView.isPlaying) {
            videoView.stopPlayback()
        }
        // 2) pop this fragment off the nav back-stack
        findNavController().navigateUp()
    }

}

