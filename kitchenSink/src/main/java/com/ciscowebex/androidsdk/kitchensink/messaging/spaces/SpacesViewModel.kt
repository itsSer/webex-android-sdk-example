package com.ciscowebex.androidsdk.kitchensink.messaging.spaces

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ciscowebex.androidsdk.kitchensink.BaseViewModel
import com.ciscowebex.androidsdk.kitchensink.messaging.spaces.members.MembershipModel
import com.ciscowebex.androidsdk.kitchensink.messaging.spaces.members.MembershipRepository
import com.ciscowebex.androidsdk.kitchensink.messaging.teams.TeamsRepository
import io.reactivex.android.schedulers.AndroidSchedulers

class SpacesViewModel(private val spacesRepo: SpacesRepository,
                      private val membershipRepo: MembershipRepository,
                      private val messagingRepo: TeamsRepository) : BaseViewModel() {
    private val _spaces = MutableLiveData<List<SpaceModel>>()
    val spaces: LiveData<List<SpaceModel>> = _spaces

    private val _readStatusList = MutableLiveData<List<SpaceReadStatusModel>>()
    val readStatusList: LiveData<List<SpaceReadStatusModel>> = _readStatusList

    private val _addSpace = MutableLiveData<SpaceModel>()
    val addSpace: LiveData<SpaceModel> = _addSpace

    private val _spaceMeetingInfo = MutableLiveData<SpaceMeetingInfoModel>()
    val spaceMeetingInfo: LiveData<SpaceMeetingInfoModel> = _spaceMeetingInfo

    private val _spaceError = MutableLiveData<String>()
    val spaceError: LiveData<String> = _spaceError

    private val _createMemberData = MutableLiveData<MembershipModel>()
    val createMemberData: LiveData<MembershipModel> = _createMemberData

    private val _markSpaceRead = MutableLiveData<Boolean>()
    val markSpaceRead: LiveData<Boolean> = _markSpaceRead

    private val _deleteSpace = MutableLiveData<String>()
    val deleteSpace: LiveData<String> = _deleteSpace

    fun getSpacesList(maxSpaces: Int) {
        spacesRepo.fetchSpacesList(null, maxSpaces).observeOn(AndroidSchedulers.mainThread()).subscribe({ spacesList ->
            _spaces.postValue(spacesList)
        }, { _spaces.postValue(emptyList()) }).autoDispose()
    }

    fun addSpace(title: String, teamId: String?) {
        spacesRepo.addSpace(title, teamId).observeOn(AndroidSchedulers.mainThread()).subscribe({ createdSpace ->
            _addSpace.postValue(createdSpace)
        }, { _addSpace.postValue(null) }).autoDispose()

    }

    fun getSpaceReadStatusList(maxSpaces: Int) {
        spacesRepo.fetchSpaceReadStatusList(maxSpaces).observeOn(AndroidSchedulers.mainThread()).subscribe({ listReadStatus ->
            _readStatusList.postValue(listReadStatus)
        }, { _readStatusList.postValue(null) }).autoDispose()
    }

    fun updateSpace(spaceId: String, spaceName: String) {
        spacesRepo.updateSpace(spaceId, spaceName).observeOn(AndroidSchedulers.mainThread()).subscribe({
            refreshSpaces()
        }, { error -> _spaceError.postValue(error.message) }).autoDispose()
    }

    fun delete(spaceId: String) {
        spacesRepo.delete(spaceId).observeOn(AndroidSchedulers.mainThread()).subscribe({
            _deleteSpace.postValue(spaceId)
        }, {error -> _spaceError.postValue(error.message) }).autoDispose()
    }

    fun getMeetingInfo(spaceId: String) {
        spacesRepo.getMeetingInfo(spaceId).observeOn(AndroidSchedulers.mainThread()).subscribe({ meetingInfo ->
            _spaceMeetingInfo.postValue(meetingInfo)
        }, { error -> _spaceError.postValue(error.message) }).autoDispose()
    }

    fun createMembershipWithId(spaceId: String, personId: String, isModerator: Boolean = false) {
        membershipRepo.createMembershipWithId(spaceId, personId, isModerator).observeOn(AndroidSchedulers.mainThread()).subscribe({ membership ->
            _createMemberData.postValue(membership)
        }, { error -> _spaceError.postValue(error.message) }).autoDispose()
    }

    fun createMembershipWithEmailId(spaceId: String, emailId: String, isModerator: Boolean = false) {
        membershipRepo.createMembershipWithEmail(spaceId, emailId, isModerator).observeOn(AndroidSchedulers.mainThread()).subscribe({ membership ->
            _createMemberData.postValue(membership)
        }, { error -> _spaceError.postValue(error.message) }).autoDispose()
    }

    fun markSpaceRead(spaceId: String) {
        messagingRepo.markMessageAsRead(spaceId).observeOn(AndroidSchedulers.mainThread()).subscribe({ success ->
            _markSpaceRead.postValue(success)
        }, { error -> _spaceError.postValue(error.message) }).autoDispose()
    }

    private fun refreshSpaces() {
        getSpacesList(0)
    }
}

