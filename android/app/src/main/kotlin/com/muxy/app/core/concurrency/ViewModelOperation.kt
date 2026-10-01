package com.muxy.app.core.concurrency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async

suspend fun <T> ViewModel.inViewModelScope(operation: suspend () -> T): T = viewModelScope.async { operation() }.await()
