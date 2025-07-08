package fr.bearit.template

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Service for interacting with OpenStreetMap's Overpass API to fetch points of interest.
 */
class OpenStreetMapService {
    private val client = OkHttpClient()
    private val objectMapper = ObjectMapper()

    /**
     * Fetches points of interest near the specified coordinates.
     *
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     * @param radius The search radius in meters (default: 1000)
     * @return The count of points of interest found
     */
    fun fetchPointsOfInterestCount(latitude: Double, longitude: Double, radius: Int = 1000): Int {
        val query = """
            [out:json];
            (
              node["amenity"](around:$radius,$latitude,$longitude);
              node["shop"](around:$radius,$latitude,$longitude);
              node["tourism"](around:$radius,$latitude,$longitude);
              node["leisure"](around:$radius,$latitude,$longitude);
            );
            out count;
        """.trimIndent()

        val url = "https://overpass-api.de/api/interpreter?data=${query.replace("\n", " ")}"

        val request = Request.Builder()
            .url(url)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseBody = response.body?.string() ?: return 0
                val jsonNode = objectMapper.readTree(responseBody)

                // The Overpass API returns an array of elements, so we count them
                return jsonNode.path("elements").size()
            }
        } catch (e: Exception) {
            println("Error fetching points of interest: ${e.message}")
            return 0
        }
    }

    /**
     * Extracts coordinates from a GeoJsonFeature and fetches points of interest.
     *
     * @param feature The GeoJsonFeature to extract coordinates from
     * @return The count of points of interest found
     */
    fun getPointsOfInterestCountForFeature(feature: GeoJsonFeature): Int {
        val coordinates = extractCoordinates(feature)
        return if (coordinates != null) {
            fetchPointsOfInterestCount(coordinates.first, coordinates.second)
        } else {
            0
        }
    }

    /**
     * Extracts the first set of coordinates from a GeoJsonFeature.
     * Handles different geometry types (Point, LineString, Polygon).
     *
     * @param feature The GeoJsonFeature to extract coordinates from
     * @return A pair of latitude and longitude, or null if coordinates couldn't be extracted
     */
    private fun extractCoordinates(feature: GeoJsonFeature): Pair<Double, Double>? {
        return when (feature.geometry.type) {
            "Point" -> {
                val coords = feature.geometry.coordinates as List<*>
                if (coords.size >= 2) {
                    // GeoJSON uses [longitude, latitude] order
                    Pair(coords[1] as Double, coords[0] as Double)
                } else null
            }
            "LineString" -> {
                val coords = (feature.geometry.coordinates as List<*>).firstOrNull() as? List<*>
                if (coords != null && coords.size >= 2) {
                    // Take the first point of the line
                    Pair(coords[1] as Double, coords[0] as Double)
                } else null
            }
            "Polygon" -> {
                val outerRing = (feature.geometry.coordinates as List<*>).firstOrNull() as? List<*>
                val firstPoint = outerRing?.firstOrNull() as? List<*>
                if (firstPoint != null && firstPoint.size >= 2) {
                    // Take the first point of the outer ring
                    Pair(firstPoint[1] as Double, firstPoint[0] as Double)
                } else null
            }
            else -> null
        }
    }
}
