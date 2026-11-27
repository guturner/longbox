package com.guturner.longbox.domain.model

data class ContainerId(val value: String)

enum class ContainerKind { STACK, BOX }

data class Container(
    val id: ContainerId,
    val name: String,
    val kind: ContainerKind,
    val entryCount: Int,
    val createdAtEpochMillis: Long,
)
