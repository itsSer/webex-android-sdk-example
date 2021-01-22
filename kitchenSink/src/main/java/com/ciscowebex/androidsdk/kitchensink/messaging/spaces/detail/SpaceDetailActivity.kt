package com.ciscowebex.androidsdk.kitchensink.messaging.spaces.detail

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.ciscowebex.androidsdk.kitchensink.BaseActivity
import com.ciscowebex.androidsdk.kitchensink.R
import com.ciscowebex.androidsdk.kitchensink.databinding.ActivitySpaceDetailBinding
import com.ciscowebex.androidsdk.kitchensink.databinding.DialogPostMessageHandlerBinding
import com.ciscowebex.androidsdk.kitchensink.databinding.ListItemSpaceMessageBinding
import com.ciscowebex.androidsdk.kitchensink.messaging.composer.MessageComposerActivity
import com.ciscowebex.androidsdk.kitchensink.messaging.spaces.ReplyMessageModel
import com.ciscowebex.androidsdk.kitchensink.messaging.spaces.SpaceMessageModel
import com.ciscowebex.androidsdk.kitchensink.utils.Constants
import com.ciscowebex.androidsdk.kitchensink.utils.showDialogWithMessage
import com.ciscowebex.androidsdk.message.Message
import org.koin.android.ext.android.inject

class SpaceDetailActivity : BaseActivity() {

    companion object {
        fun getIntent(context: Context, spaceId: String): Intent {
            val intent = Intent(context, SpaceDetailActivity::class.java)
            intent.putExtra(Constants.Intent.SPACE_ID, spaceId)
            return intent
        }
    }

    lateinit var messageClientAdapter: MessageClientAdapter
    lateinit var binding: ActivitySpaceDetailBinding

    private val spaceDetailViewModel: SpaceDetailViewModel by inject()
    private lateinit var spaceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tag = "SpaceDetailActivity"

        spaceId = intent.getStringExtra(Constants.Intent.SPACE_ID) ?: ""
        spaceDetailViewModel.spaceId = spaceId
        DataBindingUtil.setContentView<ActivitySpaceDetailBinding>(this, R.layout.activity_space_detail)
                .also { binding = it }
                .apply {
                    val messageActionBottomSheetFragment = MessageActionBottomSheetFragment({ message -> spaceDetailViewModel.deleteMessage(message) },
                            { message -> spaceDetailViewModel.markMessageAsRead(message) },
                            { message -> replyMessageListener(message) })

                    messageClientAdapter = MessageClientAdapter(messageActionBottomSheetFragment, supportFragmentManager)
                    spaceMessageRecyclerView.adapter = messageClientAdapter
                    spaceMessageRecyclerView.addItemDecoration(DividerItemDecoration(baseContext, DividerItemDecoration.VERTICAL))

                    setUpObservers()

                    swipeContainer.setOnRefreshListener {
                        spaceDetailViewModel.getMessages()
                    }
                    postMessageFAB.setOnClickListener {
                        ContextCompat.startActivity(this@SpaceDetailActivity,
                                MessageComposerActivity.getIntent(this@SpaceDetailActivity, MessageComposerActivity.Companion.ComposerType.POST_SPACE, spaceDetailViewModel.spaceId, null), null)
                    }
                }
    }

    private fun replyMessageListener(message: SpaceMessageModel) {
        val model = ReplyMessageModel(
                        message.spaceId,
                        message.messageId,
                        message.created,
                        message.isSelfMentioned,
                        message.parentId,
                        message.isReply,
                        message.personId,
                        message.personEmail,
                        message.toPersonId,
                        message.toPersonEmail)
        ContextCompat.startActivity(this@SpaceDetailActivity,
                MessageComposerActivity.getIntent(this@SpaceDetailActivity, MessageComposerActivity.Companion.ComposerType.POST_SPACE, spaceDetailViewModel.spaceId, model), null)
    }

    override fun onResume() {
        super.onResume()
        spaceDetailViewModel.getSpaceById()
        getMessages()
    }

    private fun getMessages() {
        binding.noMessagesLabel.visibility = View.GONE
        binding.progressLayout.visibility = View.VISIBLE
        spaceDetailViewModel.getMessages()
    }

    private fun setUpObservers() {
        spaceDetailViewModel.space.observe(this@SpaceDetailActivity, Observer {
            binding.space = it
        })

        spaceDetailViewModel.spaceMessages.observe(this@SpaceDetailActivity, Observer { list ->
            list?.let {
                binding.progressLayout.visibility = View.GONE
                binding.swipeContainer.isRefreshing = false

                if (it.isEmpty()) {
                    binding.noMessagesLabel.visibility = View.VISIBLE
                } else {
                    binding.noMessagesLabel.visibility = View.GONE
                }

                messageClientAdapter.messages.clear()
                messageClientAdapter.messages.addAll(it)
                messageClientAdapter.notifyDataSetChanged()
            }
        })

        spaceDetailViewModel.deleteMessage.observe(this@SpaceDetailActivity, Observer { model ->
            model?.let {
                val position = messageClientAdapter.messages.indexOf(it)
                messageClientAdapter.messages.removeAt(position)
                messageClientAdapter.notifyItemRemoved(position)
            }
        })

        spaceDetailViewModel.messageError.observe(this@SpaceDetailActivity, Observer { errorMessage ->
            errorMessage?.let {
                showErrorDialog(it)
            }
        })

        spaceDetailViewModel.markMessageAsReadStatus.observe(this@SpaceDetailActivity, Observer { model ->
            model?.let {
                showDialogWithMessage(this@SpaceDetailActivity, R.string.success, "Message with id ${it.messageId} marked as read")
            }
        })

        spaceDetailViewModel.getMeData.observe(this@SpaceDetailActivity, Observer { model ->
            model?.let {
                MessageActionBottomSheetFragment.selfPersonId = it.personId
            }
        })
    }
}


class MessageClientAdapter(private val messageActionBottomSheetFragment: MessageActionBottomSheetFragment, private val fragmentManager: FragmentManager) : RecyclerView.Adapter<MessageClientViewHolder>() {
    var messages: MutableList<SpaceMessageModel> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageClientViewHolder {
        return MessageClientViewHolder(ListItemSpaceMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
                messageActionBottomSheetFragment, fragmentManager)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: MessageClientViewHolder, position: Int) {
        holder.bind(messages[position])
    }

}

class MessageClientViewHolder(private val binding: ListItemSpaceMessageBinding, private val messageActionBottomSheetFragment: MessageActionBottomSheetFragment, private val fragmentManager: FragmentManager) : RecyclerView.ViewHolder(binding.root) {
    var messageItem: SpaceMessageModel? = null
    val tag = "MessageClientViewHolder"
    init {
        binding.root.setOnClickListener {
            messageItem?.let { message ->
                MessageDetailsDialogFragment.newInstance(message.messageId).show(fragmentManager, "MessageDetailsDialogFragment")
            }
        }
    }

    fun bind(message: SpaceMessageModel) {
        binding.message = message
        messageItem = message
        binding.membershipContainer.setOnLongClickListener { view ->
            messageActionBottomSheetFragment.message = message
            messageActionBottomSheetFragment.show(fragmentManager, MessageActionBottomSheetFragment.TAG)
            true
        }
        binding.executePendingBindings()
    }
}