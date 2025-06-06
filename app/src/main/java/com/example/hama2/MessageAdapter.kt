package com.example.hama2

import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(
    private val items: MutableList<Message>,
    private val textToSpeech: TextToSpeech,
    private val isTtsReady: () -> Boolean

) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER     = 0
        private const val TYPE_RESPONSE = 1
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].isUser) TYPE_USER else TYPE_RESPONSE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            val view = inflater.inflate(R.layout.message_prompt, parent, false)
            UserVH(view)
        } else {
            val view = inflater.inflate(R.layout.message_response, parent, false)
            ResponseVH(view)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        if (holder is UserVH) {
            holder.tv.text = msg.text ?: ""
            if (msg.imageUri != null) {
                holder.iv.visibility = View.VISIBLE
                holder.iv.setImageURI(msg.imageUri)
            } else {
                holder.iv.visibility = View.GONE
            }
        } else if (holder is ResponseVH) {
            holder.bind(msg)  // Use the new bind method for ResponseVH
        }
    }

    // ViewHolder for user
    inner class UserVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iv: ImageView  = itemView.findViewById(R.id.ivMsgImage)
        val tv: TextView   = itemView.findViewById(R.id.tvMessage)
    }
    // ViewHolder for response
    inner class ResponseVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tv: TextView = itemView.findViewById(R.id.tvMessage)
        val micButton: ImageView = itemView.findViewById(R.id.btnSpeak)

        private var currentText: String? = null

        init {
            micButton.setOnClickListener {
                if (isTtsReady() && !currentText.isNullOrEmpty()) {
                    textToSpeech.speak(currentText, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }

        fun bind(message: Message) {
            currentText = message.text
            tv.text = currentText ?: ""
        }
    }

    /** Add one message and notify */
    fun addMessage(msg: Message) {
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }
}
