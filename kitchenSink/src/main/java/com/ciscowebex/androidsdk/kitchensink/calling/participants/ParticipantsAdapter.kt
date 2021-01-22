package com.ciscowebex.androidsdk.kitchensink.calling.participants

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ciscowebex.androidsdk.kitchensink.R
import com.ciscowebex.androidsdk.kitchensink.databinding.ParticipantsListItemBinding
import com.ciscowebex.androidsdk.phone.CallParticipant

class ParticipantsAdapter(private val participants: ArrayList<CallParticipant>, private val itemClickListener: OnItemActionListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ParticipantsListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ParticipantViewHolder(binding)
    }

    override fun getItemCount(): Int {
       return participants.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ParticipantViewHolder).bind()
    }

    fun refreshData(list: ArrayList<CallParticipant>) {
        participants.clear()
        participants.addAll(list)
    }

    inner class ParticipantViewHolder(private val binding: ParticipantsListItemBinding): RecyclerView.ViewHolder(binding.root){

        fun bind(){
            binding.tvName.text = participants[adapterPosition].name
            binding.imgMute.setImageResource(R.drawable.ic_mic_off_24)
            binding.imgMute.visibility = if(participants[adapterPosition].isAudioMuted) View.VISIBLE else View.INVISIBLE
            binding.root.setOnClickListener { itemClickListener.onParticipantMuted(participants[adapterPosition].contactId)}
        }
    }

    interface OnItemActionListener{
        fun onParticipantMuted(participantId: String)
    }
}