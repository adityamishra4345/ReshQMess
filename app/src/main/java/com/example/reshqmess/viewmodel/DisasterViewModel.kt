package com.example.reshqmess.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.reshqmess.model.SosPayload

class DisasterViewModel(application: Application) : AndroidViewModel(application) {

    // 1. CONNECTION STATUS (New!)
    private val _connectionStatus = MutableLiveData<String>("Idle: Ready to Connect")
    val connectionStatus: LiveData<String> = _connectionStatus

    // 2. LOGS & LISTS
    private val _victimList = MutableLiveData<List<SosPayload>>(emptyList())
    val victimList: LiveData<List<SosPayload>> = _victimList

    private val _chatHistory = MutableLiveData<List<SosPayload>>(emptyList())
    val chatHistory: LiveData<List<SosPayload>> = _chatHistory

    private val victimMap = mutableMapOf<String, SosPayload>()
    private val fullChatList = mutableListOf<SosPayload>()

    // Function to update status (Called by MeshManager)
    fun setStatus(status: String) {
        _connectionStatus.postValue(status)
    }

    fun addOrUpdateVictim(payload: SosPayload) {
        // Update Map
        victimMap[payload.victimName] = payload
        _victimList.postValue(victimMap.values.toList())

        // Update Chat
        if (payload.message.isNotEmpty()) {
            fullChatList.add(payload)
            _chatHistory.postValue(fullChatList.toList())
        }
    }
}