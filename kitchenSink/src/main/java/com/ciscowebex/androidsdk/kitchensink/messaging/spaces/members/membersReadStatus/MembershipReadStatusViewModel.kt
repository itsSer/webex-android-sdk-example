package com.ciscowebex.androidsdk.kitchensink.messaging.spaces.members.membersReadStatus

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ciscowebex.androidsdk.kitchensink.BaseViewModel
import com.ciscowebex.androidsdk.kitchensink.messaging.spaces.members.MembershipRepository
import io.reactivex.android.schedulers.AndroidSchedulers

class MembershipReadStatusViewModel(private val membershipRepo: MembershipRepository) : BaseViewModel() {
    private val _membershipsReadStatus = MutableLiveData<List<MembershipReadStatusModel>>()
    val membershipsReadStatus: LiveData<List<MembershipReadStatusModel>> = _membershipsReadStatus

    private val _membershipReadStatusError = MutableLiveData<String>()
    val membershipReadStatusError: LiveData<String> = _membershipReadStatusError

    fun getMembershipsWithReadStatus(spaceId: String?) {
        membershipRepo.listMembershipsWithReadStatus(spaceId
                ?: "").observeOn(AndroidSchedulers.mainThread()).subscribe({ membershipsReadStatus ->
            _membershipsReadStatus.postValue(membershipsReadStatus)
        }, { _membershipReadStatusError.postValue(it.message) }).autoDispose()
    }
}