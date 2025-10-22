package io.liriliri.eruda

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BrowserUiState(
    val url: String? = null,
    val title: String? = null,
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

class MainViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()
    
    fun onPageStarted(url: String) {
        _uiState.update { 
            it.copy(
                url = url,
                title = "Loading...",
                progress = 0,
                isLoading = true
            ) 
        }
    }
    
    fun onPageFinished(url: String, title: String?) {
        _uiState.update { 
            it.copy(
                url = url,
                title = title,
                isLoading = false
            ) 
        }
    }
    
    fun updateProgress(progress: Int) {
        _uiState.update { it.copy(progress = progress) }
    }
    
    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update { 
            it.copy(
                canGoBack = canGoBack,
                canGoForward = canGoForward
            ) 
        }
    }
    
    fun processUrlInput(input: String): String {
        val trimmed = input.trim()
        
        return when {
            trimmed.isEmpty() -> "about:blank"
            trimmed.isHttpUrl() -> trimmed
            trimmed.mayBeUrl() -> {
                if (trimmed.startsWith("www.")) "https://$trimmed" 
                else "https://www.$trimmed"
            }
            else -> trimmed.toSearchUrl()
        }
    }
}
