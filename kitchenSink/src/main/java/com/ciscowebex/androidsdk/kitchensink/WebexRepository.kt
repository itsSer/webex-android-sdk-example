package com.ciscowebex.androidsdk.kitchensink

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.ciscowebex.androidsdk.Webex
import com.ciscowebex.androidsdk.WebexDelegate
import com.ciscowebex.androidsdk.phone.Call
import com.ciscowebex.androidsdk.message.Message
import com.ciscowebex.androidsdk.people.Person
import com.ciscowebex.androidsdk.space.Space
import com.ciscowebex.androidsdk.CompletionHandler
import com.ciscowebex.androidsdk.auth.PhoneServiceRegistrationFailureReason
import com.ciscowebex.androidsdk.auth.UCLoginServerConnectionStatus
import com.ciscowebex.androidsdk.phone.NotificationCallType
import com.ciscowebex.androidsdk.phone.CallParticipant

class WebexRepository(val webex: Webex) : WebexDelegate {
    private val tag = "WebexRepository"

    enum class CucmEvent {
        ShowSSOLogin,
        ShowNonSSOLogin,
        OnUCLoginFailed,
        OnUCLoggedIn,
        OnUCServerConnectionStateChanged
    }

    enum class CallEvent {
        DialCompleted,
        DialFailed,
        OnConnected,
        OnDisconnected,
        OnRinging,
        OnFailed,
        OnInfoChanged,
        OnAudioStateChanged,
        OnHoldStateChanged,
        OnVideoStreamingChanged,
        OnSharingStateChanged,
        OnSharingVideoStreamInUseChanged,
        AnswerCompleted,
        AnswerFailed,
        AssociationCallCompleted,
        AssociationCallFailed
    }

    data class CallLiveData(val event: CallEvent,
                            val call: Call? = null,
                            val sharingLabel: String? = null,
                            val errorMessage: String? = null) {}

    var isAddedCall = false
    var currentCallId: String? = null
    var oldCallId: String? = null
    var shouldMute = false
    var shouldMuteAll = true
    var isIncomingCallJoined = false
    var isLocalVideoMuted = true
    var isRemoteScreenShareON = false

    val participantMuteMap = hashMapOf<String, Boolean>()
    var isCUCMServerLoggedIn = false
    var ucServerConnectionStatus: UCLoginServerConnectionStatus = UCLoginServerConnectionStatus.Idle
    var ucServerConnectionFailureReason: PhoneServiceRegistrationFailureReason = PhoneServiceRegistrationFailureReason.Unknown

    var _participantsLiveData: MutableLiveData<List<CallParticipant>>? = null
    var _muteAllLiveData: MutableLiveData<Boolean>? = null
    var _cucmLiveData: MutableLiveData<Pair<CucmEvent, String>>? = null
    var _callingLiveData: MutableLiveData<CallLiveData>? = null
    var _startAssociationLiveData: MutableLiveData<CallLiveData>? = null

    init {
        webex.delegate = this
    }

    fun clearCallData() {
        isAddedCall = false
        currentCallId = null
        oldCallId = null
        shouldMute = false
        shouldMuteAll = true
        isIncomingCallJoined = false
        isLocalVideoMuted = true
        isRemoteScreenShareON = false

        _participantsLiveData = null
        _muteAllLiveData = null
        _callingLiveData = null
        _startAssociationLiveData = null
    }

    fun getCall(callId: String): Call {
        return webex.phone.getCall(callId)
    }

    fun getCallIdByNotificationId(notificationId: String, callType: NotificationCallType): String {
        return webex.getCallIdByNotificationId(notificationId, callType)
    }

    fun startShare(callId: String) {
        webex.phone.startShare(callId)
    }

    fun stopShare(callId: String) {
        webex.phone.stopShare(callId)
    }

    fun getSpace(spaceId: String, handler: CompletionHandler<Space>){
        webex.spaces.get(spaceId, handler)
    }

    fun getPerson(personId: String, handler: CompletionHandler<Person>){
        webex.people.get(personId, handler)
    }

    fun listMessages(spaceId: String, handler: CompletionHandler<List<Message>>){
        webex.messages.list(spaceId, null, 10000, null, handler)
    }

    // Callbacks
    override fun showUCSSOLoginView(ssoUrl: String) {
        _cucmLiveData?.postValue(Pair(CucmEvent.ShowSSOLogin, ssoUrl))
        Log.d(tag, "showUCSSOLoginView")
    }

    override fun showUCNonSSOLoginView() {
        _cucmLiveData?.postValue(Pair(CucmEvent.ShowNonSSOLogin, ""))
        Log.d(tag, "showUCNonSSOLoginView")
    }

    override fun onUCLoginFailed() {
        _cucmLiveData?.postValue(Pair(CucmEvent.OnUCLoginFailed, ""))
        Log.d(tag, "onUCLoginFailed")
        isCUCMServerLoggedIn = false
    }

    override fun onUCLoggedIn() {
        _cucmLiveData?.postValue(Pair(CucmEvent.OnUCLoggedIn, ""))
        Log.d(tag, "onUCLoggedIn")
        isCUCMServerLoggedIn = true
    }

    override fun onUCServerConnectionStateChanged(status: UCLoginServerConnectionStatus, failureReason: PhoneServiceRegistrationFailureReason) {
        _cucmLiveData?.postValue(Pair(CucmEvent.OnUCServerConnectionStateChanged, ""))
        Log.d(tag, "onUCServerConnectionStateChanged status: $status failureReason: $failureReason")
        ucServerConnectionStatus = status
        ucServerConnectionFailureReason = failureReason
    }
}