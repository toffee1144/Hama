package com.example.hama2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

private const val TYPE_USER     = 0
private const val TYPE_RESPONSE = 1

class MessageAdapter(
    private val items: MutableList<Message>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // 1️⃣ Tell RecyclerView which type to use
    override fun getItemViewType(position: Int): Int =
        if (items[position].isUser) TYPE_USER else TYPE_RESPONSE

    // 2️⃣ Inflate the correct layout per type
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == TYPE_USER)
            R.layout.item_message
        else
            R.layout.message_response

        val view = LayoutInflater.from(parent.context)
            .inflate(layout, parent, false)
        return object : RecyclerView.ViewHolder(view) {}
    }

    // 3️⃣ Bind text into whichever holder we have
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val tv = holder.itemView.findViewById<TextView>(R.id.tvMessage)
        tv.text = items[position].text
    }

    override fun getItemCount(): Int = items.size

    /** Adds a new message (user or bot) */
    fun addMessage(msg: Message) {
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }
}
