package com.guturner.longbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.repository.ContainerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContainersState(
    val containers: List<Container> = emptyList(),
    val isLoading: Boolean = false,
    val message: UiMessage? = null,
) {
    val stacks: List<Container> get() = containers.filter { it.kind == ContainerKind.STACK }
    val shortBoxes: List<Container> get() = containers.filter { it.kind == ContainerKind.SHORT_BOX }
    val longBoxes: List<Container> get() = containers.filter { it.kind == ContainerKind.LONG_BOX }
}

class ContainersViewModel(
    private val containers: ContainerRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())
    private data class Transient(val isLoading: Boolean = false, val message: UiMessage? = null)

    val state: StateFlow<ContainersState> =
        combine(containers.observeContainers(), transient) { list, t ->
            ContainersState(
                containers = list.sortedBy { it.createdAtEpochMillis },
                isLoading = t.isLoading,
                message = t.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContainersState(isLoading = true))

    init {
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true) }
            containers.refresh()
            transient.update { it.copy(isLoading = false) }
        }
    }

    fun createContainer(name: String, kind: ContainerKind) {
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true) }
            val result = containers.createContainer(name, kind)
            transient.update { it.copy(isLoading = false, message = (result as? Outcome.Failure)?.error?.toUiMessage()) }
        }
    }

    fun renameContainer(id: ContainerId, newName: String) {
        viewModelScope.launch {
            val result = containers.renameContainer(id, newName)
            if (result is Outcome.Failure) transient.update { it.copy(message = result.error.toUiMessage()) }
        }
    }

    fun deleteContainer(id: ContainerId) {
        viewModelScope.launch {
            val result = containers.deleteContainer(id)
            if (result is Outcome.Failure) transient.update { it.copy(message = result.error.toUiMessage()) }
        }
    }

    fun dismissMessage() = transient.update { it.copy(message = null) }
}
