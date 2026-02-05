package com.example.reshqmess.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.reshqmess.model.SosPayload

class DisasterViewModel : ViewModel() {

    // 1. List of Victims (The Map Team watches this)
    private val _victimList = MutableLiveData<List<SosPayload>>(emptyList())
    val victimList: LiveData<List<SosPayload>> = _victimList

    // 2. Logs (For debugging)
    private val _logs = MutableLiveData<List<String>>(emptyList())
    val logs: LiveData<List<String>> = _logs

    // Called when a new SOS signal arrives
    fun addOrUpdateVictim(newVictim: SosPayload) {
        val currentList = _victimList.value.orEmpty().toMutableList()

        // Update if exists, Add if new
        val index = currentList.indexOfFirst { it.victimName == newVictim.victimName }
        if (index != -1) {
            currentList[index] = newVictim
        } else {
            currentList.add(newVictim)
        }

        _victimList.postValue(currentList)
        addLog("UPDATED: ${newVictim.victimName}")
    }

    fun addLog(msg: String) {
        val current = _logs.value.orEmpty().toMutableList()
        current.add(0, msg)
        _logs.postValue(current)
    }
}