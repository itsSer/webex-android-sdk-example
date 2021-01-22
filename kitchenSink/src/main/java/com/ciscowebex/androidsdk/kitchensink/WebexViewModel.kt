package com.ciscowebex.androidsdk.kitchensink

import android.net.Uri
import android.util.Log
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ciscowebex.androidsdk.Webex
import com.ciscowebex.androidsdk.kitchensink.firebase.RegisterTokenService
import com.ciscowebex.androidsdk.kitchensink.person.PersonModel
import com.ciscowebex.androidsdk.phone.Call
import com.ciscowebex.androidsdk.phone.CallObserver
import com.ciscowebex.androidsdk.phone.MediaOption
import com.ciscowebex.androidsdk.phone.Phone
import com.ciscowebex.androidsdk.CompletionHandler
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import com.ciscowebex.androidsdk.phone.CallParticipant
import com.ciscowebex.androidsdk.phone.CallAssociationType
import com.ciscowebex.androidsdk.auth.PhoneServiceRegistrationFailureReason
import com.ciscowebex.androidsdk.auth.UCLoginServerConnectionStatus


class WebexViewModel(val webex: Webex, val repository: WebexRepository) : BaseViewModel() {
    private val tag = "WebexViewModel"

    var _participantsLiveData = MutableLiveData<List<CallParticipant>>()
    val _muteAllLiveData = MutableLiveData<Boolean>()
    val _cucmLiveData = MutableLiveData<Pair<WebexRepository.CucmEvent, String>>()
    val _callingLiveData = MutableLiveData<WebexRepository.CallLiveData>()
    val _startAssociationLiveData = MutableLiveData<WebexRepository.CallLiveData>()

    var participantsLiveData: LiveData<List<CallParticipant>> = _participantsLiveData
    val muteAllLiveData: LiveData<Boolean> = _muteAllLiveData
    val cucmLiveData: LiveData<Pair<WebexRepository.CucmEvent, String>> = _cucmLiveData
    val callingLiveData: LiveData<WebexRepository.CallLiveData> = _callingLiveData
    val startAssociationLiveData: LiveData<WebexRepository.CallLiveData> = _startAssociationLiveData

    private val _incomingListenerLiveData = MutableLiveData<Call?>()
    val incomingListenerLiveData: LiveData<Call?> = _incomingListenerLiveData

    private val _signOutListenerLiveData = MutableLiveData<Boolean>()
    val signOutListenerLiveData: LiveData<Boolean> = _signOutListenerLiveData

    private val _tokenLiveData = MutableLiveData<Pair<String?, PersonModel>>()
    val tokenLiveData: LiveData<Pair<String?, PersonModel>> = _tokenLiveData

    var isAddedCall: Boolean
        get() = repository.isAddedCall
        set(value) {
            repository.isAddedCall = value
        }

    var currentCallId: String?
        get() = repository.currentCallId
        set(value) {
            repository.currentCallId = value
        }

    var oldCallId: String?
        get() = repository.oldCallId
        set(value) {
            repository.oldCallId = value
        }

    var shouldMute: Boolean
        get() = repository.shouldMute
        set(value) {
            repository.shouldMute = value
        }

    var shouldMuteAll: Boolean
        get() = repository.shouldMuteAll
        set(value) {
            repository.shouldMuteAll = value
        }

    var isIncomingCallJoined: Boolean
        get() = repository.isIncomingCallJoined
        set(value) {
            repository.isIncomingCallJoined = value
        }

    var isLocalVideoMuted: Boolean
        get() = repository.isLocalVideoMuted
        set(value) {
            repository.isLocalVideoMuted = value
        }

    var isCUCMServerLoggedIn: Boolean
        get() = repository.isCUCMServerLoggedIn
        set(value) {
            repository.isCUCMServerLoggedIn = value
        }

    var ucServerConnectionStatus: UCLoginServerConnectionStatus
        get() = repository.ucServerConnectionStatus
        set(value) {
            repository.ucServerConnectionStatus = value
        }

    var ucServerConnectionFailureReason: PhoneServiceRegistrationFailureReason
        get() = repository.ucServerConnectionFailureReason
        set(value) {
            repository.ucServerConnectionFailureReason = value
        }

    var isRemoteScreenShareON: Boolean
        get() = repository.isRemoteScreenShareON
        set(value) {
            repository.isRemoteScreenShareON = value
        }

    init {
        repository._participantsLiveData = _participantsLiveData
        repository._cucmLiveData = _cucmLiveData
        repository._muteAllLiveData = _muteAllLiveData
        repository._callingLiveData = _callingLiveData
        repository._startAssociationLiveData = _startAssociationLiveData
    }

    override fun onCleared() {
        repository.clearCallData()
    }

    fun setIncomingListener() {
        webex.phone.setIncomingCallListener(object : Phone.IncomingCallListener {
            override fun onIncomingCall(call: Call?) {
                call?.let {
                    _incomingListenerLiveData.postValue(it)
                    setCallObserver(it)
                } ?: run {
                    Log.d(tag, "setIncomingCallListener Call object null")
                }
            }
        })
    }

    fun signOut() {
        webex.signOut(CompletionHandler { result ->
            result?.let {
                _signOutListenerLiveData.postValue(result.isSuccessful)
            }
        })
    }

    fun dial(input: String, option: MediaOption) {
        webex.phone.dial(input, option, CompletionHandler { result ->
            if (result.isSuccessful) {
                result.data.let { _call ->
                    _call?.let {
                        currentCallId = it.callId
                        setCallObserver(it)
                        _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.DialCompleted, it))
                    }
                }
            } else {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.DialFailed, null, null, result.error?.errorMessage))
            }
        })
    }

    fun answer(call: Call) {
        call.answer(MediaOption.audioOnly(), CompletionHandler { result ->
            if (result.isSuccessful) {
                result.data.let {
                    _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.AnswerCompleted, call))
                }
            } else {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.AnswerFailed, null, null, result.error?.errorMessage))
            }
        })
    }

    private fun setCallObserver(call: Call) {
        call.setObserver(object : CallObserver {
            override fun onConnected(call: Call?) {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.OnConnected, call))
            }

            override fun onRinging(call: Call?) {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.OnRinging, call))
            }

            override fun onDisconnected(event: CallObserver.CallDisconnectedEvent?) {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.OnDisconnected, event?.call))
            }

            override fun onFailed(call: Call?) {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.OnFailed, call))
            }

            override fun onInfoChanged(call: Call?) {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.OnInfoChanged, call))
            }

            override fun onAudioStateChanged(call: Call?) {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.OnAudioStateChanged, call))
            }

            override fun onHoldStateChanged(call: Call?) {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.OnHoldStateChanged, call))
            }

            override fun onVideoStreamingChanged(call: Call?) {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.OnVideoStreamingChanged, call))
            }

            override fun onSharingStateChanged(call: Call?, label: String) {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.OnSharingStateChanged, call, label))
            }

            override fun onSharingVideoStreamInUseChanged(call: Call?) {
                _callingLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.OnSharingVideoStreamInUseChanged, call))
            }
        })
    }


    fun muteSelfVideo(callId: String, isMuted: Boolean) {
        webex.phone.muteSelfVideo(callId, isMuted)
    }

    fun addSelfVideoView(callId: String, videoView: View) {
        webex.phone.addSelfVideoView(callId, videoView)
    }

    fun addRemoteVideoView(callId: String, videoView: View) {
        webex.phone.addRemoteVideoView(callId, videoView)
    }

    fun addScreenSharingView(callId: String, rendererView: View) {
        webex.phone.addScreenSharingView(callId, rendererView)
    }

    fun removeRemoteVideoView(callId: String, videoView: View) {
        webex.phone.removeRemoteVideoView(callId, videoView)
    }

    fun removeSelfVideoView(callId: String, videoView: View) {
        webex.phone.removeSelfVideoView(callId, videoView)
    }

    fun removeScreenSharingView(callId: String, rendererView: View) {
        webex.phone.removeScreenSharingView(callId, rendererView)
    }

    fun swapCamera(callId: String) {
        webex.phone.swapCamera(callId)
    }

    fun getCall(callId: String): Call {
        return repository.getCall(callId)
    }

    fun muteAllParticipantAudio(callId: String) {
        webex.phone.muteAllParticipantAudio(callId, shouldMuteAll)
        shouldMuteAll = !shouldMuteAll
        repository._muteAllLiveData?.postValue(shouldMuteAll)
    }

    fun muteParticipant(callId: String, participantId: String) {
        repository.participantMuteMap[participantId]?.let { isMuted ->
            webex.phone.muteParticipantAudio(callId, participantId, isMuted)
            repository.participantMuteMap[participantId] = !isMuted
        }
    }

    fun muteSelfAudio(callId: String) {
        webex.phone.muteSelfAudio(callId, shouldMute)
        shouldMute = !shouldMute
    }

    fun startShare(callId: String) {
        repository.startShare(callId)
    }

    fun stopShare(callId: String) {
        repository.stopShare(callId)
    }

    fun endCall(callId: String) {
        webex.phone.endCall(callId)
    }

    fun declineCall(callId: String) {
        webex.phone.declineCall(callId)
    }

    fun holdCall(callId: String) {
        val callInfo = getCall(callId)
        val isOnHold = callInfo.isOnHold
        Log.d(tag, "holdCall isOnHold = $isOnHold")
        webex.phone.holdCall(callId, !isOnHold)
    }

    fun isOnHold(callId: String) = webex.phone.isOnHold(callId)

    fun getParticipants(_callId: String) {
        val callParticipants = webex.phone.getParticipants(_callId)
        repository._participantsLiveData?.postValue(callParticipants)

        callParticipants.forEach {
            repository.participantMuteMap[it.contactId] = !it.isAudioMuted
        }
    }

    fun setUCDomainServerUrl(ucDomain: String, serverUrl: String) {
        webex.setUCDomainServerUrl(ucDomain, serverUrl)
    }

    fun setCUCMCredential(username: String, password: String) {
        webex.setCUCMCredential(username, password)
    }

    fun isUCLoggedIn(): Boolean {
        return webex.isUCLoggedIn()
    }

    fun getUCServerConnectionStatus(): UCLoginServerConnectionStatus {
        return webex.getUCServerConnectionStatus()
    }

    fun startAssociatedCall(callId: String, dialNumber: String, associationType: CallAssociationType, audioCall: Boolean) {
        webex.phone.startAssociatedCall(callId, dialNumber, associationType, audioCall, CompletionHandler { result ->
            Log.d(tag, "startAssociatedCall Lambda")
            if (result.isSuccessful) {
                Log.d(tag, "startAssociatedCall Lambda isSuccessful")
                result.data?.let {
                    setCallObserver(it)
                    _startAssociationLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.AssociationCallCompleted, it))
                }
            } else {
                Log.d(tag, "startAssociatedCall Lambda isSuccessful 5")
                _startAssociationLiveData.postValue(WebexRepository.CallLiveData(WebexRepository.CallEvent.AssociationCallFailed, null, null, result.error?.errorMessage))
                Log.d(tag, "startAssociatedCall Lambda isSuccessful 6")
            }
        })
    }

    fun transferCall(fromCallId: String, toCallId: String) {
        return webex.phone.transferCall(fromCallId, toCallId)
    }

    fun mergeCalls(currentCallId: String, targetCallId: String) {
        return webex.phone.mergeCalls(currentCallId, targetCallId)
    }

    fun getlogFileUri(includelastRunLog: Boolean = false): Uri {
        return webex.getlogFileUri(includelastRunLog)
    }

    fun getFCMToken(personModel: PersonModel) {
        FirebaseMessaging.getInstance().token
                .addOnCompleteListener(object : OnCompleteListener<String?> {
                    override fun onComplete(task: Task<String?>) {
                        if (!task.isSuccessful) {
                            Log.w(tag, "Fetching FCM registration token failed", task.exception)
                            return
                        }

                        // Get new FCM registration token
                        val token: String? = task.result
                        Log.d(tag, "$token")
                        sendTokenToServer(Pair(token, personModel))
                    }
                })
    }

    private fun sendTokenToServer(it: Pair<String?, PersonModel>) {
        val json = JSONObject()
        json.put("token", it.first)
        json.put("personId", it.second.personId)
        json.put("email", it.second.emailList)
        RegisterTokenService().execute(json.toString())
    }

    fun postParticipantData(data: List<CallParticipant>?) {
        _participantsLiveData.postValue(data)
    }
}