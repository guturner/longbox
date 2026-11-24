package com.guturner.longbox.server.metron

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Field names follow Metron's v1 serializers (github.com/Metron-Project/metron, api/v1_0/serializers)
 * and the official client's schemas (github.com/Metron-Project/mokkari). Unknown fields are ignored.
 */

@Serializable
data class MetronPage<T>(
    val count: Int,
    val next: String? = null,
    val results: List<T>,
)

@Serializable
data class MetronGeneric(val id: Long, val name: String)

@Serializable
data class MetronBasicSeries(
    val id: Long,
    val name: String,
    val volume: Int = 1,
    @SerialName("year_began") val yearBegan: Int = 0,
)

/** Issue list item (`/api/issue/?...`). */
@Serializable
data class MetronIssueListItem(
    val id: Long,
    val number: String,
    @SerialName("cover_date") val coverDate: String? = null,
    @SerialName("store_date") val storeDate: String? = null,
    val image: String? = null,
    val series: MetronBasicSeries,
)

@Serializable
data class MetronIssueSeries(
    val id: Long,
    val name: String,
    @SerialName("sort_name") val sortName: String = "",
    val volume: Int = 1,
    @SerialName("year_began") val yearBegan: Int = 0,
)

/** Credit ids are creator ids (CreditReadSerializer: id = creator.id). */
@Serializable
data class MetronCredit(
    val id: Long,
    val creator: String,
    val role: List<MetronGeneric> = emptyList(),
)

@Serializable
data class MetronVariant(
    val name: String = "",
    val upc: String = "",
    val image: String? = null,
)

/** Issue detail (`/api/issue/{id}/`). */
@Serializable
data class MetronIssue(
    val id: Long,
    val publisher: MetronGeneric,
    val series: MetronIssueSeries,
    val number: String,
    /** Collection title (trade paperbacks); usually empty for single issues. */
    val title: String = "",
    /** Story titles. */
    val name: List<String> = emptyList(),
    @SerialName("cover_date") val coverDate: String? = null,
    @SerialName("store_date") val storeDate: String? = null,
    val upc: String = "",
    val desc: String = "",
    val image: String? = null,
    val arcs: List<MetronGeneric> = emptyList(),
    val credits: List<MetronCredit> = emptyList(),
    val variants: List<MetronVariant> = emptyList(),
)

@Serializable
data class MetronCreator(
    val id: Long,
    val name: String,
    val desc: String = "",
    val image: String? = null,
    val birth: String? = null,
    val death: String? = null,
)

@Serializable
data class MetronArc(
    val id: Long,
    val name: String,
    val desc: String = "",
    val image: String? = null,
)
