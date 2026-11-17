package com.example.feature.map

import com.example.core.model.GeoCoordinate
import com.example.core.model.Severity
import com.example.data.incidents.IncidentDocument
import kotlin.math.floor
import kotlin.math.pow

sealed interface MapRenderNode {
    data class SinglePin(val incident: IncidentDocument) : MapRenderNode
    data class ClusterPin(
        val id: String,
        val center: GeoCoordinate,
        val count: Int,
        val highestSeverity: Severity,
        val items: List<IncidentDocument>,
    ) : MapRenderNode
}

/**
 * Agrupador espacial leve por grade proporcional ao nível de zoom.
 * Em zoom aproximado (>= 15.5) ou células unitárias, exibe marcadores individuais.
 */
object IncidentClusterer {

    private const val INDIVIDUAL_PIN_ZOOM_THRESHOLD = 15.5

    fun cluster(
        incidents: List<IncidentDocument>,
        zoomLevel: Double,
    ): List<MapRenderNode> {
        if (incidents.isEmpty()) return emptyList()
        if (zoomLevel >= INDIVIDUAL_PIN_ZOOM_THRESHOLD) {
            return incidents.map { MapRenderNode.SinglePin(it) }
        }

        // Tamanho da célula em graus diminui exponencialmente conforme o zoom aumenta
        val cellSizeDegrees = 16.0 / 2.0.pow(zoomLevel.coerceIn(3.0, 18.0))
        val buckets = LinkedHashMap<Pair<Long, Long>, MutableList<IncidentDocument>>()

        for (incident in incidents) {
            val latBucket = floor(incident.latitude / cellSizeDegrees).toLong()
            val lngBucket = floor(incident.longitude / cellSizeDegrees).toLong()
            buckets.getOrPut(latBucket to lngBucket) { mutableListOf() }.add(incident)
        }

        return buckets.entries.map { (cell, items) ->
            if (items.size == 1) {
                MapRenderNode.SinglePin(items.first())
            } else {
                val avgLat = items.sumOf { it.latitude } / items.size
                val avgLng = items.sumOf { it.longitude } / items.size
                val maxSeverity = items.maxByOrNull { severityRank(it.resolvedSeverity()) }
                    ?.resolvedSeverity()
                    ?: Severity.MEDIUM
                MapRenderNode.ClusterPin(
                    id = "cluster_${cell.first}_${cell.second}",
                    center = GeoCoordinate(latitude = avgLat, longitude = avgLng),
                    count = items.size,
                    highestSeverity = maxSeverity,
                    items = items,
                )
            }
        }
    }

    private fun severityRank(severity: Severity): Int = when (severity) {
        Severity.LOW -> 1
        Severity.MEDIUM -> 2
        Severity.HIGH -> 3
        Severity.CRITICAL -> 4
    }
}
