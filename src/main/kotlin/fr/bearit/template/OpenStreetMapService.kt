package fr.bearit.template

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Represents a Point of Interest from OpenStreetMap.
 *
 * @property id The OSM ID of the point of interest
 * @property type The type of the element (node, way, relation)
 * @property tags The tags associated with the point of interest
 * @property lat The latitude of the point of interest
 * @property lon The longitude of the point of interest
 * @property name The name of the point of interest (if available)
 */
data class PointOfInterest(
    val id: Long,
    val type: String,
    val tags: Map<String, String>,
    val lat: Double,
    val lon: Double,
    val name: String? = null
)

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
     * Fetches points of interest within a bounding box.
     *
     * @param minLat The minimum latitude of the bounding box
     * @param minLon The minimum longitude of the bounding box
     * @param maxLat The maximum latitude of the bounding box
     * @param maxLon The maximum longitude of the bounding box
     * @return A list of points of interest found
     */
    fun fetchPointsOfInterestInBoundingBox(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<PointOfInterest> {
        val query = """
            [out:json];
            (
              node["natural"]["natural" != "tree"]["natural" != "shrub"]($minLat,$minLon,$maxLat,$maxLon);
              node["tourism"]($minLat,$minLon,$maxLat,$maxLon);
            );
            out geom;
        """.trimIndent()

        println(query)

        val url = "https://overpass-api.de/api/interpreter?data=${query.replace("\n", " ")}"

        val request = Request.Builder()
            .url(url)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val responseBody = response.body?.string() ?: return emptyList()
                val jsonNode = objectMapper.readTree(responseBody)
                val elements = jsonNode.path("elements")

                val pointsOfInterest = mutableListOf<PointOfInterest>()

                elements.forEach { element ->
                    val id = element.path("id").asLong()
                    val type = element.path("type").asText()
                    val lat = element.path("lat").asDouble()
                    val lon = element.path("lon").asDouble()

                    // Extract tags
                    val tagsNode = element.path("tags")
                    val tags = mutableMapOf<String, String>()
                    tagsNode.fields().forEach { (key, value) ->
                        tags[key] = value.asText()
                    }

                    // Get name if available
                    val name = tags["name"]

                    pointsOfInterest.add(
                        PointOfInterest(
                            id = id,
                            type = type,
                            tags = tags,
                            lat = lat,
                            lon = lon,
                            name = name
                        )
                    )
                }

                return pointsOfInterest
            }
        } catch (e: Exception) {
            println("Error fetching points of interest in bounding box: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Extracts coordinates from a GeoJsonFeature and fetches points of interest.
     *
     * @param feature The GeoJsonFeature to extract coordinates from
     * @return A list of points of interest found
     */
    fun getPointsOfInterestForFeature(feature: GeoJsonFeature): List<PointOfInterest> {
        val boundingBox = calculateBoundingBox(feature)
        return if (boundingBox != null) {
            fetchPointsOfInterestInBoundingBox(
                boundingBox.minLat,
                boundingBox.minLon,
                boundingBox.maxLat,
                boundingBox.maxLon
            )
        } else {
            emptyList()
        }
    }

    /**
     * Extracts coordinates from a GeoJsonFeature and fetches points of interest count.
     *
     * @param feature The GeoJsonFeature to extract coordinates from
     * @return The count of points of interest found
     */
    fun getPointsOfInterestCountForFeature(feature: GeoJsonFeature): Int {
        return getPointsOfInterestForFeature(feature).size
    }

    /**
     * Represents a geographic bounding box with minimum and maximum coordinates.
     */
    data class BoundingBox(
        val minLat: Double,
        val minLon: Double,
        val maxLat: Double,
        val maxLon: Double
    )

    /**
     * Calculates a bounding box that encompasses all coordinates in a GeoJsonFeature.
     * Handles different geometry types (Point, LineString, Polygon).
     *
     * @param feature The GeoJsonFeature to calculate the bounding box for
     * @return A BoundingBox object, or null if coordinates couldn't be extracted
     */
    private fun calculateBoundingBox(feature: GeoJsonFeature): BoundingBox? {
        val allCoordinates = mutableListOf<Pair<Double, Double>>()

        when (feature.geometry.type) {
            "Point" -> {
                val coords = feature.geometry.coordinates as? List<*>
                if (coords != null && coords.size >= 2) {
                    // GeoJSON uses [longitude, latitude] order
                    allCoordinates.add(Pair(coords[1] as Double, coords[0] as Double))
                }
            }
            "LineString" -> {
                val lineCoords = feature.geometry.coordinates as? List<*>
                lineCoords?.forEach { point ->
                    (point as? List<*>)?.let {
                        if (it.size >= 2) {
                            // GeoJSON uses [longitude, latitude] order
                            allCoordinates.add(Pair(it[1] as Double, it[0] as Double))
                        }
                    }
                }
            }
            "Polygon" -> {
                val polygonCoords = feature.geometry.coordinates as? List<*>
                polygonCoords?.forEach { ring ->
                    (ring as? List<*>)?.forEach { point ->
                        (point as? List<*>)?.let {
                            if (it.size >= 2) {
                                // GeoJSON uses [longitude, latitude] order
                                allCoordinates.add(Pair(it[1] as Double, it[0] as Double))
                            }
                        }
                    }
                }
            }
        }

        if (allCoordinates.isEmpty()) {
            return null
        }

        // Calculate the bounding box
        var minLat = Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLat = Double.MIN_VALUE
        var maxLon = Double.MIN_VALUE

        allCoordinates.forEach { (lat, lon) ->
            minLat = minOf(minLat, lat)
            minLon = minOf(minLon, lon)
            maxLat = maxOf(maxLat, lat)
            maxLon = maxOf(maxLon, lon)
        }

        return BoundingBox(minLat, minLon, maxLat, maxLon)
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
