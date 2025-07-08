package fr.bearit.template

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Represents a GeoJSON feature with an ID.
 *
 * @property id The unique identifier of the feature
 * @property type The type of GeoJSON object (usually "Feature")
 * @property properties The properties of the feature
 * @property geometry The geometry of the feature
 */
data class GeoJsonFeature(
    val id: String,
    val type: String = "Feature",
    val properties: GeoJsonProperties = GeoJsonProperties(),
    val geometry: GeoJsonGeometry
) {
    companion object {
        private val objectMapper: ObjectMapper = jacksonObjectMapper()

        /**
         * Creates a GeoJsonFeature from a JSON string.
         *
         * @param id The unique identifier of the feature
         * @param geoJsonString The GeoJSON data as a string
         * @return A GeoJsonFeature object
         */
        fun fromJson(id: String, geoJsonString: String): GeoJsonFeature {
            // Handle CSV-escaped JSON strings (e.g., "{""type"":""Feature"",...}")
            val cleanedJson = if (geoJsonString.startsWith("\"") && geoJsonString.endsWith("\"")) {
                // Remove the outer quotes and unescape the inner quotes
                geoJsonString.substring(1, geoJsonString.length - 1).replace("\"\"", "\"")
            } else {
                geoJsonString
            }

            val geoJsonData = objectMapper.readValue<GeoJsonData>(cleanedJson)
            return GeoJsonFeature(
                id = id,
                type = geoJsonData.type,
                properties = geoJsonData.properties,
                geometry = geoJsonData.geometry
            )
        }
    }

    /**
     * Converts the GeoJsonFeature to a JSON string.
     *
     * @return The GeoJSON data as a string
     */
    fun toJson(): String {
        return objectMapper.writeValueAsString(this)
    }
}

/**
 * Internal class used for parsing GeoJSON data from a string.
 * This class doesn't include the id field, which is provided separately.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
private data class GeoJsonData(
    val type: String,
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = GeoJsonPropertiesDeserializer::class)
    val properties: GeoJsonProperties = GeoJsonProperties(),
    val geometry: GeoJsonGeometry
)

/**
 * Custom deserializer for GeoJsonProperties to handle both object and array values.
 */
private class GeoJsonPropertiesDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<GeoJsonProperties>() {
    override fun deserialize(
        p: com.fasterxml.jackson.core.JsonParser,
        ctxt: com.fasterxml.jackson.databind.DeserializationContext
    ): GeoJsonProperties {
        val node = p.codec.readTree<com.fasterxml.jackson.databind.JsonNode>(p)
        return when {
            node.isObject -> {
                val map = mutableMapOf<String, Any>()
                node.fields().forEach { (key, value) ->
                    map[key] = when {
                        value.isTextual -> value.asText()
                        value.isNumber -> value.asDouble()
                        value.isBoolean -> value.asBoolean()
                        else -> value.toString()
                    }
                }
                GeoJsonProperties(map)
            }
            node.isArray -> {
                // Handle array as empty properties
                GeoJsonProperties()
            }
            else -> {
                // Handle other cases as empty properties
                GeoJsonProperties()
            }
        }
    }
}

/**
 * Represents the properties of a GeoJSON feature.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeoJsonProperties(
    val properties: Map<String, Any> = emptyMap()
)

/**
 * Represents the geometry of a GeoJSON feature.
 *
 * @property type The type of geometry (e.g., "Point", "LineString", "Polygon")
 * @property coordinates The coordinates of the geometry
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeoJsonGeometry(
    val type: String,
    val coordinates: Any
)
