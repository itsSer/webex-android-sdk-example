package com.ciscowebex.androidsdk.kitchensink.calling

import android.app.Activity
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.cisco.wme.appshare.ScreenShareContext
import com.ciscowebex.androidsdk.kitchensink.R
import com.ciscowebex.androidsdk.kitchensink.WebexRepository
import com.ciscowebex.androidsdk.kitchensink.WebexViewModel
import com.ciscowebex.androidsdk.kitchensink.calling.ScreenShareForegroundService.Companion.startScreenShareForegroundService
import com.ciscowebex.androidsdk.kitchensink.calling.ScreenShareForegroundService.Companion.stopScreenShareForegroundService
import com.ciscowebex.androidsdk.kitchensink.calling.ScreenShareForegroundService.Companion.updateScreenShareForegroundService
import com.ciscowebex.androidsdk.kitchensink.calling.participants.ParticipantsFragment
import com.ciscowebex.androidsdk.kitchensink.databinding.FragmentCallControlsBinding
import com.ciscowebex.androidsdk.kitchensink.utils.AudioManagerUtils
import com.ciscowebex.androidsdk.kitchensink.utils.Constants
import com.ciscowebex.androidsdk.phone.Call
import com.ciscowebex.androidsdk.phone.MediaOption
import com.ciscowebex.androidsdk.phone.CallAssociationType
import org.koin.android.ext.android.inject


class CallControlsFragment : Fragment(), OnClickListener {
    private val TAG = "CallControlsFragment"
    private lateinit var webexViewModel: WebexViewModel
    private lateinit var binding: FragmentCallControlsBinding
    private var callFailed = false
    private var isIncomingActivity = false
    private var callingActivity = 0
    private var audioManagerUtils: AudioManagerUtils? = null
    private var screenSharingIntent: Intent? = null
    private var isPreparingScreenShare = false
    var onLockSelfVideoMutedState = true
    var onLockRemoteSharingStateON = false
    private val ringerManager: RingerManager by inject()

    enum class ShareButtonState {
        OFF,
        ON,
        DISABLED
    }

    companion object {
        const val REQUEST_CODE = 1212
        const val TAG = "CallControlsFragment"
        private const val CALLER_ID = "callerId"
        const val MEDIA_PROJECTION_REQUEST = 1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return DataBindingUtil.inflate<FragmentCallControlsBinding>(LayoutInflater.from(context),
                R.layout.fragment_call_controls, container, false).also { binding = it }.apply {
            webexViewModel = (activity as? CallActivity)?.webexViewModel!!
            Log.d(TAG, "CallControlsFragment onCreateView webexViewModel: $webexViewModel")
            setUpViews()
            observerCallLiveData()
            initAudioManager()
        }.root
    }

    private fun initAudioManager() {
        audioManagerUtils = AudioManagerUtils(requireContext())
        audioManagerUtils?.initAudioManager()
    }

    override fun onResume() {
        Log.d(TAG, "CallControlsFragment onResume")
        super.onResume()
        checkIsOnHold()
        webexViewModel.currentCallId?.let {
            onVideoStreamingChanged(it)
        }
    }

    private fun checkIsOnHold() {
        val isOnHold = webexViewModel.currentCallId?.let { webexViewModel.isOnHold(it) }
        binding.ibHoldCall.isSelected = isOnHold ?: false
    }

    fun dialOutgoingCall(callerId: String) {
        Log.d(TAG, "dialOutgoingCall")
        //MediaOption.audioVideo(binding.localView as View, binding.remoteView as View)
        webexViewModel.dial(callerId, MediaOption.audioOnly())
    }

    private fun observerCallLiveData() {
        webexViewModel.callingLiveData.observe(viewLifecycleOwner, Observer {
            it?.let {
                val event = it.event
                val call = it.call
                val sharingLabel = it.sharingLabel
                val errorMessage = it.errorMessage

                when (event) {
                    WebexRepository.CallEvent.DialCompleted -> {
                        Log.d(tag, "callingLiveData DIAL_COMPLETED callerId: ${call?.callId}")
                        onCallJoined(call?.callId ?: "")
                        handleCUCMControls(call)
                    }
                    WebexRepository.CallEvent.DialFailed -> {
                        val callActivity = activity as CallActivity?
                        callActivity?.alertDialog(true, "")
                    }
                    WebexRepository.CallEvent.OnRinging -> {
                        Log.d(TAG, "CallObserver OnRinging : " + call?.callId)
                        ringerManager.startRinger(RingerManager.RingerType.Outgoing)
                    }
                    WebexRepository.CallEvent.OnConnected -> {
                        Log.d(TAG, "CallObserver onConnected : " + call?.callId)
                        onCallConnected(call?.callId ?: "")

                        ringerManager.stopRinger(if (isIncomingActivity) RingerManager.RingerType.Incoming else RingerManager.RingerType.Outgoing)
                    }
                    WebexRepository.CallEvent.OnDisconnected -> {
                        Log.d(TAG, "CallObserver onDisconnected : " + call?.callId)
                        onCallTerminated(call?.callId ?: "")

                        ringerManager.stopRinger(if (isIncomingActivity) RingerManager.RingerType.Incoming else RingerManager.RingerType.Outgoing)
                    }
                    WebexRepository.CallEvent.OnFailed -> {
                        Log.d(TAG, "CallObserver onFailed : " + call?.callId)
                        onCallFailed(call?.callId ?: "")

                        ringerManager.stopRinger(if (isIncomingActivity) RingerManager.RingerType.Incoming else RingerManager.RingerType.Outgoing)
                    }
                    WebexRepository.CallEvent.OnInfoChanged -> {
                        Log.d(TAG, "CallObserver onInfoChanged : " + call?.callId)

                        Handler(Looper.getMainLooper()).post {
                            call?.let { _call ->
                                webexViewModel.shouldMute = !_call.isAudioMuted
                                webexViewModel.postParticipantData(_call.participants)
                                showCallHeader(_call.callId ?: "")
                            }
                        }
                    }
                    WebexRepository.CallEvent.OnAudioStateChanged -> {
                        Log.d(TAG, "CallObserver onAudioStateChanged : " + call?.callId)
                        Handler(Looper.getMainLooper()).post {
                            call?.let {_call ->
                                if (_call.isAudioMuted) {
                                    showMutedIcon(true)
                                } else {
                                    showMutedIcon(false)
                                }
                            }
                        }
                    }
                    WebexRepository.CallEvent.OnHoldStateChanged -> {
                        Log.d(TAG, "CallObserver onHoldStateChanged : " + call?.callId)
                        Handler(Looper.getMainLooper()).post {
                            call?.let { _call ->
                                binding.ibHoldCall.isSelected = webexViewModel.getCall(_call.callId
                                        ?: "").isOnHold
                            }
                        }
                    }
                    WebexRepository.CallEvent.OnVideoStreamingChanged -> {
                        Log.d(TAG, "CallObserver onVideoStreamingChanged : " + call?.callId)
                        onVideoStreamingChanged(call?.callId ?: "")
                    }
                    WebexRepository.CallEvent.OnSharingStateChanged -> {
                        Log.d(TAG, "CallObserver onSharingStateChanged : " + call?.callId)
                        onScreenShareStateChanged(call?.callId ?: "", sharingLabel ?: "")
                    }
                    WebexRepository.CallEvent.OnSharingVideoStreamInUseChanged -> {
                        Log.d(TAG, "CallObserver onSharingVideoStreamInUseChanged : " + call?.callId)
                        onScreenShareVideoStreamInUseChanged(call?.callId ?: "")
                    }
                    WebexRepository.CallEvent.AnswerCompleted -> {
                        Log.d(TAG, "answer Lambda callInfo Id: ${call?.callId}")
                        call?.callId?.let { callId ->
                            onCallJoined(callId)
                            handleCUCMControls(null)
                        }
                    }
                    WebexRepository.CallEvent.AnswerFailed -> {
                        Log.d(TAG, "answer Lambda failed $errorMessage")
                    }
                    WebexRepository.CallEvent.AssociationCallCompleted -> { }
                    WebexRepository.CallEvent.AssociationCallFailed -> { }
                }
            }
        })

        webexViewModel.startAssociationLiveData.observe(viewLifecycleOwner, Observer {
            it?.let {
                val event = it.event
                val call = it.call
                val errorMessage = it.errorMessage

                when (event) {
                    WebexRepository.CallEvent.AssociationCallCompleted -> {
                        webexViewModel.isAddedCall = true
                        webexViewModel.oldCallId = webexViewModel.currentCallId
                        webexViewModel.currentCallId = call?.callId?:""

                        onCallJoined(call?.callId ?: "")
                        handleCUCMControls(call)
                        Log.d(tag, "startAssociatedCall currentCallId = ${webexViewModel.currentCallId}, oldCallId = ${webexViewModel.oldCallId}")
                    }
                    WebexRepository.CallEvent.AssociationCallFailed -> {
                        Log.d(TAG, "startAssociatedCall Lambda failed $errorMessage")
                        val callActivity = activity as CallActivity?
                        callActivity?.alertDialog(false, resources.getString(R.string.start_associated_call_failed))
                    }
                    else -> {}
                }
            }
        })
    }

    private fun handleCUCMControls(call: Call?) {
        Handler(Looper.getMainLooper()).post {
            Log.d(TAG, "handleCUCMControls isAddedCall = ${webexViewModel.isAddedCall}")
            webexViewModel.currentCallId?.let { callId ->

                var _call = call

                if (_call == null) {
                    _call = webexViewModel.getCall(callId)
                }

                when {
                    _call.isEccCall && webexViewModel.isAddedCall -> {
                        binding.ibTransferCall.visibility = View.VISIBLE
                        binding.ibMerge.visibility = View.VISIBLE
                        binding.ibAdd.visibility = View.INVISIBLE
                        binding.ibVideo.visibility = View.INVISIBLE
                    }
                    !_call.isEccCall -> {
                        binding.ibAdd.visibility = View.GONE
                        binding.ibTransferCall.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    private fun showMutedIcon(showMuted: Boolean) {
        binding.ibMute.isSelected = showMuted
    }

    private fun setUpViews() {
        Log.d(TAG, "setUpViews fragment")

        videoViewState(true)

        callingActivity = activity?.intent?.getIntExtra(Constants.Intent.CALLING_ACTIVITY_ID, 0)!!
        if (callingActivity == 1) {
            isIncomingActivity = true
            incomingButtonState(false)
            binding.incomingCallHeader.visibility = View.VISIBLE
            binding.mainContentLayout.visibility = View.GONE

            webexViewModel.setIncomingListener()
            webexViewModel.incomingListenerLiveData.observe(viewLifecycleOwner, Observer {
                it?.let {
                    ringerManager.startRinger(RingerManager.RingerType.Incoming)
                    onIncomingCall(it)
                }
            })
        } else {
            incomingButtonState(true)
            binding.incomingCallHeader.visibility = View.GONE
            binding.mainContentLayout.visibility = View.VISIBLE

            binding.callingHeader.text = getString(R.string.calling)
            val callerId = activity?.intent?.getStringExtra(Constants.Intent.OUTGOING_CALL_CALLER_ID)
            binding.tvName.text = callerId
        }

        binding.ibMute.setOnClickListener(this)
        binding.ibParticipants.setOnClickListener(this)
        binding.ibSpeaker.setOnClickListener(this)
        binding.ibAdd.setOnClickListener(this)
        binding.ibTransferCall.setOnClickListener(this)
        binding.ibHoldCall.setOnClickListener(this)
        binding.ivCancelCall.setOnClickListener(this)
        binding.ibVideo.setOnClickListener(this)
        binding.ibSwapCamera.setOnClickListener(this)
        binding.ibMerge.setOnClickListener(this)
        binding.ibScreenShare.setOnClickListener(this)
        binding.mainContentLayout.setOnClickListener(this)

        initAddedCallControls()

    }

    override fun onClick(v: View?) {
        webexViewModel.currentCallId?.let { callId ->
            when (v) {
                binding.ibMute -> {
                    webexViewModel.muteSelfAudio(callId)
                }
                binding.ibParticipants -> {
                    val dialog = ParticipantsFragment.newInstance(callId)
                    dialog.show(childFragmentManager, ParticipantsFragment::javaClass.name)
                }
                binding.ibSpeaker -> {
                    toggleSpeaker(v)
                }
                binding.ibAdd -> {
                    //while associating a call, existing call needs to be put on hold
                    webexViewModel.holdCall(callId)
                    startActivityForResult(DialerActivity.getIntent(requireContext()), REQUEST_CODE)
                }
                binding.ibTransferCall -> {
                    transferCall()
                    initAddedCallControls()
                }
                binding.ibMerge -> {
                    mergeCalls()
                    initAddedCallControls()
                }
                binding.ibHoldCall -> {
                    webexViewModel.holdCall(callId)
                }
                binding.ivCancelCall -> {
                    endCall()
                }
                binding.ibVideo -> {
                    muteSelfVideo(!webexViewModel.isLocalVideoMuted)
                }
                binding.ibSwapCamera -> {
                    webexViewModel.swapCamera(webexViewModel.currentCallId!!)
                }
                binding.ibScreenShare -> {
                    shareScreen()
                }
                binding.mainContentLayout -> {
                    mainContentLayoutClickListener()
                }
                else -> {
                }
            }
        }
    }

    private fun mainContentLayoutClickListener() {
        Log.d(TAG, "mainContentLayoutClickListener")
        if (binding.ivPickCall.visibility == View.VISIBLE) {
            return
        }

        if (binding.controlGroup.visibility == View.VISIBLE) {
            binding.controlGroup.visibility = View.GONE
        } else {
            binding.controlGroup.visibility = View.VISIBLE
        }
    }

    private fun screenShareButtonVisibilityState() {
        webexViewModel.currentCallId?.let {
            val canShare = webexViewModel.getCall(it).canShare
            Log.d(TAG, "CallControlsFragment screenShareButtonVisibilityState canShare: $canShare")

            if (canShare) {
                binding.ibScreenShare.visibility = View.VISIBLE
            } else {
                binding.ibScreenShare.visibility = View.INVISIBLE
            }

        } ?: run {
            binding.ibScreenShare.visibility = View.INVISIBLE
        }
    }

    private fun updateScreenShareButtonState(state: ShareButtonState) {
        when (state) {
            ShareButtonState.OFF -> {
                binding.ibScreenShare.isEnabled = true
                binding.ibScreenShare.alpha = 1.0f
                binding.ibScreenShare.background = ContextCompat.getDrawable(requireActivity(), R.drawable.screen_sharing_default)
            }
            ShareButtonState.ON -> {
                binding.ibScreenShare.isEnabled = true
                binding.ibScreenShare.alpha = 1.0f
                binding.ibScreenShare.background = ContextCompat.getDrawable(requireActivity(), R.drawable.screen_sharing_active)
            }
            ShareButtonState.DISABLED -> {
                binding.ibScreenShare.isEnabled = false
                binding.ibScreenShare.alpha = 0.5f
            }
        }
    }

    private fun shareScreen() {
        Log.d(TAG, "shareScreen")

        webexViewModel.currentCallId?.let {
            val isSharing = webexViewModel.getCall(it).isLocalSharing
            Log.d(TAG, "shareScreen isSharing: $isSharing")
            if (!isSharing) {
                context?.let {context ->
                    startScreenShareForegroundService()
                    updateScreenShareButtonState(ShareButtonState.DISABLED)
                    val mediaProjectionManager = context.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val intent = mediaProjectionManager.createScreenCaptureIntent()
                    if (intent.resolveActivity(context.packageManager) != null) {
                        isPreparingScreenShare = true
                        startActivityForResult(intent, MEDIA_PROJECTION_REQUEST)
                    } else {
                        Log.d(TAG, "no activity can resolve the screen capture intent.")
                    }
                }
            } else {
                updateScreenShareButtonState(ShareButtonState.DISABLED)
                webexViewModel.currentCallId?.let { id -> webexViewModel.stopShare(id) }
                stopScreenShareForegroundService()
            }
        }
    }

    private fun onProjectionActivityResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            isPreparingScreenShare = false
            updateScreenShareButtonState(ShareButtonState.OFF)
            Log.d(TAG, "User cancelled screen request")
            stopScreenShareForegroundService()
            return
        } else {
            screenSharingIntent = data

            if (ScreenShareContext.getInstance().init(requireContext(), Activity.RESULT_OK, screenSharingIntent)) {
                ScreenShareContext.getInstance().registerCallback {
                    Log.d(TAG, "onShareStopped")
                    webexViewModel.currentCallId?.let { webexViewModel.stopShare(it) }
                    stopScreenShareForegroundService()
                }
            }

            Log.d(TAG, "startShare")
            webexViewModel.currentCallId?.let { webexViewModel.startShare(it) }
        }
    }

    fun needBackPressed(): Boolean {
        if (isIncomingActivity &&
                webexViewModel.currentCallId == null) {
            return false
        }

        return true
    }

    fun onBackPressed() {
        endCall()
    }

    fun onFragmentStop() {
        webexViewModel.currentCallId?.let { stopVideoStreaming() }
    }

    fun onFragmentStart() {
        resumeVideoStreaming()
    }

    private fun resumeVideoStreaming() {
        if (onLockRemoteSharingStateON) {
            onLockRemoteSharingStateON = false
            webexViewModel.currentCallId?.let { onScreenShareVideoStreamInUseChanged(it) }
        }

        if (onLockSelfVideoMutedState) {
            webexViewModel.currentCallId?.let { onVideoStreamingChanged(it) }
        } else {
            muteSelfVideo(false)
        }
    }

    private fun endCall() {
        if (isIncomingActivity) {
            endIncomingCall()
        } else {
            webexViewModel.currentCallId?.let {
                webexViewModel.endCall(it)
            } ?: run {
                activity?.finish()
            }
        }
    }

    private fun incomingButtonState(hide: Boolean) {
        if (hide) {
            binding.ivPickCall.visibility = View.GONE
        } else {
            binding.ivPickCall.visibility = View.VISIBLE
        }
    }

    private fun videoViewTextColorState(hidden: Boolean) {
        var hide = hidden
        if (hide && webexViewModel.isRemoteScreenShareON) {
            hide = false
        }

        if (hide) {
            binding.callingHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            binding.tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        } else {
            binding.callingHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        }
    }

    private fun localVideoViewState(toHide: Boolean) {
        if (toHide) {
            binding.localViewLayout.visibility = View.GONE
            binding.ibSwapCamera.visibility = View.GONE
        } else {
            binding.localViewLayout.visibility = View.VISIBLE
            binding.ibSwapCamera.visibility = View.VISIBLE
            binding.localView.setZOrderOnTop(true)
        }
    }

    private fun screenShareViewRemoteState(toHide: Boolean, needResize: Boolean = true) {
        Log.d(TAG, "screenShareViewRemoteState toHide: $toHide")
        if (toHide) {
            binding.screenShareView.visibility = View.GONE
            webexViewModel.isRemoteScreenShareON = false
        } else {
            binding.screenShareView.visibility = View.VISIBLE
            webexViewModel.isRemoteScreenShareON = true
        }
        if (needResize) {
            resizeRemoteVideoView()
        }
    }

    private fun resizeRemoteVideoView() {
        Log.d(TAG, "resizeRemoteVideoView isRemoteScreenShareON ${webexViewModel.isRemoteScreenShareON}")
        if (webexViewModel.isRemoteScreenShareON) {
            val width = resources.getDimension(R.dimen.remote_video_view_width).toInt()
            val height = resources.getDimension(R.dimen.remote_video_view_height).toInt()

            val params = ConstraintLayout.LayoutParams(width, height)
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            params.marginStart = resources.getDimension(R.dimen.remote_video_view_margin_start).toInt()
            params.bottomMargin = resources.getDimension(R.dimen.remote_video_view_margin_Bottom).toInt()
            binding.remoteViewLayout.layoutParams = params
            binding.remoteViewLayout.background = ContextCompat.getDrawable(requireActivity(), R.drawable.surfaceview_border)
            binding.remoteView.setZOrderOnTop(true)
        } else {
            val params = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
            params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
            params.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            binding.remoteViewLayout.layoutParams = params
            binding.remoteViewLayout.background = ContextCompat.getDrawable(requireActivity(), R.drawable.surfaceview_transparent_border)
            binding.remoteView.setZOrderOnTop(false)
        }
    }

    private fun videoViewState(toHide: Boolean) {
        localVideoViewState(toHide)
        if (toHide) {
            binding.remoteViewLayout.visibility = View.GONE
        } else {
            binding.remoteViewLayout.visibility = View.VISIBLE
        }

        videoViewTextColorState(toHide)
        videoButtonState(toHide)
    }

    private fun videoButtonState(videoViewHidden: Boolean) {
        if (videoViewHidden) {
            binding.ibVideo.background = ContextCompat.getDrawable(requireActivity(), R.drawable.turn_off_video_active)
        } else {
            binding.ibVideo.background = ContextCompat.getDrawable(requireActivity(), R.drawable.turn_on_video_default)
        }
    }

    private fun endIncomingCall() {
        webexViewModel.currentCallId?.let {
            if (webexViewModel.isIncomingCallJoined) webexViewModel.endCall(it) else webexViewModel.declineCall(it)
        } ?: run {
            activity?.finish()
        }
    }

    private fun onCallConnected(callId: String) {
        Log.d(TAG, "CallControlsFragment onCallConnected callerId: $callId, currentCallId: ${webexViewModel.currentCallId}")

        Handler(Looper.getMainLooper()).post {

            if (callId == webexViewModel.currentCallId) {
                val callInfo = webexViewModel.getCall(callId)

                Log.d(TAG, "CallControlsFragment onCallConnected isAudioOnly: ${callInfo.isAudioOnly} isSelfVideoMuted: ${callInfo.isSelfVideoMuted}, hasRemoteVideo: ${callInfo.hasRemoteVideo}")

                if (isIncomingActivity) {
                    if (callId == webexViewModel.currentCallId) {
                        binding.videoCallLayout.visibility = View.VISIBLE
                        webexViewModel.addSelfVideoView(callId, binding.localView)
                        webexViewModel.addRemoteVideoView(callId, binding.remoteView)

                        incomingButtonState(true)
                    }
                }

                webexViewModel.isLocalVideoMuted = callInfo.isSelfVideoMuted

                if (callInfo.isSelfVideoMuted) {
                    localVideoViewState(true)
                    videoButtonState(true)
                } else {
                    localVideoViewState(false)
                    videoButtonState(false)
                }

                if (!callInfo.hasRemoteVideo) {
                    binding.remoteViewLayout.visibility = View.GONE
                } else {
                    binding.remoteViewLayout.visibility = View.VISIBLE
                }

                binding.controlGroup.visibility = View.VISIBLE

                screenShareButtonVisibilityState()
                videoViewTextColorState(!callInfo.hasRemoteVideo)
            }

        }
    }

    private fun onScreenShareStateChanged(callId: String, label: String) {
        Log.d(TAG, "CallControlsFragment onScreenShareStateChanged callerId: $callId, label: $label")

        if (webexViewModel.currentCallId != callId) {
            return
        }

        Handler(Looper.getMainLooper()).post {

            val callInfo = webexViewModel.getCall(callId)

            Log.d(TAG, "CallControlsFragment onScreenShareStateChanged isRemoteSharing: ${callInfo.isRemoteSharing}, isLocalSharing: ${callInfo.isLocalSharing}")

            if (callInfo.isLocalSharing) {
                updateScreenShareForegroundService(callId)
                updateScreenShareButtonState(ShareButtonState.ON)
            } else {
                updateScreenShareButtonState(ShareButtonState.OFF)
            }
        }
    }

    private fun onScreenShareVideoStreamInUseChanged(callId: String) {
        Log.d(TAG, "CallControlsFragment onScreenShareVideoStreamInUseChanged callerId: $callId")

        if (webexViewModel.currentCallId != callId) {
            return
        }

        Handler(Looper.getMainLooper()).post {

            val callInfo = webexViewModel.getCall(callId)

            Log.d(TAG, "CallControlsFragment onScreenShareVideoStreamInUseChanged isRemoteSharing: ${callInfo.isRemoteSharing}, isLocalSharing: ${callInfo.isLocalSharing}")
            if (callInfo.isRemoteSharing) {
                binding.controlGroup.visibility = View.GONE
                screenShareViewRemoteState(false)
                webexViewModel.addScreenSharingView(callId, binding.screenShareView)
            }
            else {
                onVideoStreamingChanged(callId)
                screenShareViewRemoteState(true)
                binding.controlGroup.visibility = View.VISIBLE
                webexViewModel.removeScreenSharingView(callId, binding.screenShareView)
            }

            videoViewTextColorState(!callInfo.isRemoteSharing)
        }
    }

    private fun onVideoStreamingChanged(callId: String) {
        Log.d(TAG, "CallControlsFragment onVideoStreamingChanged callerId: $callId")

        if (webexViewModel.currentCallId == null) {
            return
        }

        Handler(Looper.getMainLooper()).post {

            val callInfo = webexViewModel.getCall(callId)
            webexViewModel.isLocalVideoMuted = callInfo.isSelfVideoMuted

            if (webexViewModel.isLocalVideoMuted) {
                localVideoViewState(true)
                webexViewModel.removeSelfVideoView(callId, binding.localView)
            } else {
                localVideoViewState(false)
                webexViewModel.addSelfVideoView(callId, binding.localView)
            }

            if (!callInfo.hasRemoteVideo) {
                binding.remoteViewLayout.visibility = View.GONE
                webexViewModel.removeRemoteVideoView(callId, binding.remoteView)
            } else {
                if (webexViewModel.isRemoteScreenShareON) {
                    resizeRemoteVideoView()
                }
                binding.remoteViewLayout.visibility = View.VISIBLE
                webexViewModel.addRemoteVideoView(callId, binding.remoteView)
                putOnSpeaker()
            }

            videoViewTextColorState(!callInfo.hasRemoteVideo)

            Log.d(TAG, "CallControlsFragment onVideoStreamingChanged isLocalVideoMuted: ${callInfo.isSelfVideoMuted}, hasRemoteVideo: ${callInfo.hasRemoteVideo}")

            if (callInfo.isSelfVideoMuted) {
                videoButtonState(true)
            } else {
                videoButtonState(false)
            }
        }
    }

    private fun putOnSpeaker() {
        if (webexViewModel.currentCallId == null) return

        if (audioManagerUtils?.isHeadSetDeviceAvailable() == false) {
            audioManagerUtils?.putOnSpeaker()
            binding.ibSpeaker.isSelected = true
        } else {
            Log.d(TAG, "User is probably connected to headset")
        }
    }

    private fun toggleSpeaker(v: View) {
        v.isSelected = !v.isSelected
        audioManagerUtils?.toggleSpeaker()
    }

    internal fun handleFCMIncomingCall(callId: String) {
        Handler(Looper.getMainLooper()).post {
            onIncomingCall(webexViewModel.getCall(callId))
        }
    }

    private fun onIncomingCall(call: Call) {
        Handler(Looper.getMainLooper()).post {

            Log.d(TAG, "CallControlsFragment onIncomingCall callerId: ${call.callId}, callInfo title: ${call.title}")

            webexViewModel.currentCallId = call.callId ?: ""

            binding.tvName.text = call.title

            binding.incomingCallHeader.visibility = View.GONE
            binding.mainContentLayout.visibility = View.VISIBLE

            binding.ivPickCall.setOnClickListener {
                webexViewModel.answer(call)
                binding.ivPickCall.alpha = 0.5f
                binding.ivPickCall.isEnabled = false
            }
        }
    }

    private fun onCallJoined(callId: String) {
        Log.d(TAG, "CallControlsFragment onCallJoined callerId: $callId, currentCallId: ${webexViewModel.currentCallId}")
        Handler(Looper.getMainLooper()).post {
            if (callId == webexViewModel.currentCallId) {
                showCallHeader(callId)
            }
            if (callingActivity == 1) {
                webexViewModel.isIncomingCallJoined = true
            }
        }
    }

    private fun showCallHeader(callId: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                val callInfo = webexViewModel.getCall(callId)
                Log.d(TAG, "CallControlsFragment showCallHeader callerId: $callId, callInfo title: ${callInfo.title} isSelfVideoMuted : ${callInfo.isSelfVideoMuted}" +
                        "hasRemoteVideo : ${callInfo.hasRemoteVideo}")

                binding.tvName.text = callInfo.title
                binding.callingHeader.text = getString(R.string.onCall)
            } catch (e: Exception) {
                Log.d(TAG, "error: ${e.message}")
            }
        }
    }

    private fun onCallFailed(callId: String) {
        Log.d(TAG, "CallControlsFragment onCallFailed callerId: $callId")

        Handler(Looper.getMainLooper()).post {
            if (webexViewModel.isAddedCall) {
                resumePrevCallIfAdded(callId)
                updateCallHeader()
            }

            callFailed = !webexViewModel.isAddedCall

            val callActivity = activity as CallActivity?
            callActivity?.alertDialog(!webexViewModel.isAddedCall, "")
        }
    }

    private fun onCallTerminated(callId: String) {
        Log.d(TAG, "CallControlsFragment onCallTerminated callerId: $callId")

        Handler(Looper.getMainLooper()).post {
            webexViewModel.removeSelfVideoView(callId, binding.localView)
            webexViewModel.removeRemoteVideoView(callId, binding.remoteView)
            webexViewModel.removeScreenSharingView(callId, binding.screenShareView)
            if (webexViewModel.isAddedCall) {
                resumePrevCallIfAdded(callId)
                updateCallHeader()
                initAddedCallControls()
            }

            if (!callFailed && !webexViewModel.isAddedCall) {
                stopScreenShareForegroundService()
                activity?.finish()
            }
            webexViewModel.isAddedCall = false
        }
    }

    private fun initAddedCallControls() {
        binding.ibTransferCall.visibility = View.INVISIBLE
        binding.ibVideo.visibility = View.VISIBLE

        binding.ibAdd.visibility = View.VISIBLE
        binding.ibMerge.visibility = View.INVISIBLE
    }

    private fun onNewCallHeader(callerId: String?) {
        binding.callingHeader.text = getString(R.string.calling)
        binding.tvName.text = callerId
    }

    private fun resumePrevCallIfAdded(callId: String) {
        //resume old call
        if (callId == webexViewModel.currentCallId) {
            webexViewModel.currentCallId = webexViewModel.oldCallId
            Log.d(TAG, "resumePrevCallIfAdded currentCallId = ${webexViewModel.currentCallId}")
            webexViewModel.currentCallId?.let { _currentCallId ->
                webexViewModel.holdCall(_currentCallId)
            }
            webexViewModel.oldCallId = null //old is  disconnected need to make it null
        }
    }

    private fun updateCallHeader() {
        webexViewModel.currentCallId?.let {
            showCallHeader(it)
        }
    }

    private fun startAssociatedCall(dialNumber: String, associationType: CallAssociationType, audioCall: Boolean) {
        Log.d(tag, "startAssociatedCall dialNumber = $dialNumber : associationType = $associationType : audioCall = $audioCall")
        webexViewModel.currentCallId?.let { callId ->
            onNewCallHeader(callId)
            webexViewModel.startAssociatedCall(callId, dialNumber, associationType, audioCall)
        }
    }

    private fun transferCall() {
        Log.d(tag, "transferCall currentCallId = ${webexViewModel.currentCallId}, oldCallId = ${webexViewModel.oldCallId}")
        if (webexViewModel.currentCallId != null && webexViewModel.oldCallId != null) {
            webexViewModel.transferCall(webexViewModel.oldCallId!!, webexViewModel.currentCallId!!)
        }
    }

    private fun mergeCalls() {
        Log.d(tag, "mergeCalls currentCallId = ${webexViewModel.currentCallId}, targetCallId = ${webexViewModel.oldCallId}")
        if (webexViewModel.currentCallId != null && webexViewModel.oldCallId != null) {
            webexViewModel.mergeCalls(webexViewModel.currentCallId!!, webexViewModel.oldCallId!!)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val callNumber = data?.getStringExtra(CALLER_ID) ?: ""
            //start call association to add new person on call
            startAssociatedCall(callNumber, CallAssociationType.Transfer, true)
        } else if (requestCode == MEDIA_PROJECTION_REQUEST) {
            Log.d(TAG, "onActivityResult MEDIA_PROJECTION_REQUEST")
            onProjectionActivityResult(resultCode, data)
        }
    }

    private fun stopVideoStreaming() {
        val callId = webexViewModel.currentCallId
        Log.d(TAG, "CallControlsFragment stopVideoStreaming callerId: $callId")

        if (callId == null) {
            return
        }
        val callInfo = webexViewModel.getCall(callId)

        onLockSelfVideoMutedState = webexViewModel.isLocalVideoMuted
        if (!onLockSelfVideoMutedState) {
            muteSelfVideo(!webexViewModel.isLocalVideoMuted)
        }

        if (callInfo.hasRemoteVideo) {
            webexViewModel.removeRemoteVideoView(callId, binding.remoteView)
            binding.remoteViewLayout.visibility = View.GONE
            Log.d(TAG, "stopVideoStreaming: webexViewModel.removeRemoteVideoView called")
        } else {
            Log.d(TAG, "stopVideoStreaming: webexViewModel.removeRemoteVideoView not called")
        }

        if(webexViewModel.isRemoteScreenShareON) {
            webexViewModel.removeScreenSharingView(callId, binding.screenShareView)
            screenShareViewRemoteState(true, false)
            onLockRemoteSharingStateON = true
        }
    }

    private fun muteSelfVideo(value: Boolean) {
        webexViewModel.muteSelfVideo(webexViewModel.currentCallId!!, value)
    }
}