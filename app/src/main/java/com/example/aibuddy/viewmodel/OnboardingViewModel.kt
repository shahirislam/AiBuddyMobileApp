package com.example.aibuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aibuddy.data.AiBuddyRepository
import com.example.aibuddy.data.local.UserFact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AiBuddyRepository(application)

    private val _onboardingStep: MutableStateFlow<OnboardingStep> = MutableStateFlow(OnboardingStep.Name)
    val onboardingStep: StateFlow<OnboardingStep> = _onboardingStep.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun onNameChanged(name: String) {
        _userName.value = name
    }

    fun saveName() {
        viewModelScope.launch {
            repository.insertUserFact(UserFact(key = "name", value = _userName.value))
            _onboardingStep.value = OnboardingStep.Country
        }
    }

    private val _country = MutableStateFlow("")
    val country: StateFlow<String> = _country.asStateFlow()

    fun onCountryChanged(country: String) {
        _country.value = country
    }

    fun saveCountry() {
        viewModelScope.launch {
            if (_country.value.isNotBlank()) {
                repository.insertUserFact(UserFact(key = "country", value = _country.value))
            }
            _onboardingStep.value = OnboardingStep.Occupation
        }
    }

    private val _occupation = MutableStateFlow("")
    val occupation: StateFlow<String> = _occupation.asStateFlow()

    fun onOccupationChanged(occupation: String) {
        _occupation.value = occupation
    }

    fun saveOccupation() {
        viewModelScope.launch {
            if (_occupation.value.isNotBlank()) {
                repository.insertUserFact(UserFact(key = "occupation", value = _occupation.value))
            }
            _onboardingStep.value = OnboardingStep.Interests
        }
    }

    private val _interests = MutableStateFlow<List<String>>(emptyList())
    val interests: StateFlow<List<String>> = _interests.asStateFlow()

    fun addInterest(interest: String) {
        if (interest.isNotBlank() && !_interests.value.contains(interest)) {
            _interests.value = _interests.value + interest
        }
    }

    fun removeInterest(interest: String) {
        _interests.value = _interests.value - interest
    }

    fun saveInterests() {
        viewModelScope.launch {
            if (_interests.value.isNotEmpty()) {
                repository.insertUserFact(UserFact(key = "interests", value = _interests.value.joinToString(", ")))
            }
            _onboardingStep.value = OnboardingStep.Passion
        }
    }

    private val _passion = MutableStateFlow("")
    val passion: StateFlow<String> = _passion.asStateFlow()

    fun onPassionChanged(passion: String) {
        _passion.value = passion
    }

    fun savePassionAndFinish() {
        viewModelScope.launch {
            if (_passion.value.isNotBlank()) {
                repository.insertUserFact(UserFact(key = "passion", value = _passion.value))
            }
            _onboardingStep.value = OnboardingStep.Finished
        }
    }
}

sealed class OnboardingStep {
    object Name : OnboardingStep()
    object Country : OnboardingStep()
    object Occupation : OnboardingStep()
    object Interests : OnboardingStep()
    object Passion : OnboardingStep()
    object Finished : OnboardingStep()
}
