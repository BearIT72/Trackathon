package fr.bearit.template

/**
 * Represents a GeoJSON feature with an ID.
 *
 * @property id The unique identifier of the feature
 * @property geoJson The GeoJSON data as a string
 */
data class GeoJsonFeature(
    val id: String,
    val geoJson: String
)