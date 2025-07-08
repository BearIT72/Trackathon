package fr.bearit.template

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.*

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
     * @param maxDistance The maximum distance in meters from the track to include points (default: 1000)
     * @param maxResults The maximum number of results to return, sorted by distance (default: 10)
     * @return A list of points of interest found, sorted by distance to the track
     */
    fun getPointsOfInterestForFeature(
        feature: GeoJsonFeature, 
        maxDistance: Double = 1000.0,
        maxResults: Int = 10
    ): List<PointOfInterest> {
        val boundingBox = calculateBoundingBox(feature)
        return if (boundingBox != null) {
            val allPois = fetchPointsOfInterestInBoundingBox(
                boundingBox.minLat,
                boundingBox.minLon,
                boundingBox.maxLat,
                boundingBox.maxLon
            )

            // Filter and sort POIs by distance to the track
            filterPointsOfInterestByDistanceToTrack(allPois, feature, maxDistance, maxResults)
        } else {
            emptyList()
        }
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
     * Filters points of interest based on their distance to a track.
     *
     * @param pointsOfInterest The list of points of interest to filter
     * @param feature The GeoJsonFeature representing the track
     * @param maxDistance The maximum distance in meters from the track to include points
     * @param maxResults The maximum number of results to return, sorted by position along the track
     * @return A filtered and sorted list of points of interest
     */
    private fun filterPointsOfInterestByDistanceToTrack(
        pointsOfInterest: List<PointOfInterest>,
        feature: GeoJsonFeature,
        maxDistance: Double,
        maxResults: Int
    ): List<PointOfInterest> {
        // If the feature is not a LineString, we can't calculate distances to a track
        if (feature.geometry.type != "LineString") {
            return pointsOfInterest.take(maxResults)
        }

        // Extract track coordinates
        val trackCoordinates = extractTrackCoordinates(feature)
        if (trackCoordinates.isEmpty()) {
            return pointsOfInterest.take(maxResults)
        }

        // Calculate distance and position along track for each POI
        val poisWithDistanceAndPosition = pointsOfInterest.map { poi ->
            val distance = calculateMinDistanceToTrack(poi.lat, poi.lon, trackCoordinates)
            val position = calculatePositionAlongTrack(poi.lat, poi.lon, trackCoordinates)
            Triple(poi, distance, position)
        }

        // Filter by maximum distance and sort by position along the track
        return poisWithDistanceAndPosition
            .filter { it.second <= maxDistance }
            .sortedBy { it.third }  // Sort by position along track
            .take(maxResults)
            .map { it.first }
    }

    /**
     * Calculates the position of a point along a track.
     * The position is measured as the distance from the start of the track to the
     * closest point on the track to the given point.
     *
     * @param lat The latitude of the point
     * @param lon The longitude of the point
     * @param trackCoordinates The list of coordinate pairs representing the track
     * @return The position along the track in meters from the start
     */
    private fun calculatePositionAlongTrack(
        lat: Double,
        lon: Double,
        trackCoordinates: List<Pair<Double, Double>>
    ): Double {
        if (trackCoordinates.isEmpty()) {
            return 0.0
        }

        if (trackCoordinates.size == 1) {
            return 0.0
        }

        var minDistance = Double.MAX_VALUE
        var closestSegmentIndex = 0
        var closestRatio = 0.0

        // Find the closest segment and the projection ratio
        for (i in 0 until trackCoordinates.size - 1) {
            val segmentStart = trackCoordinates[i]
            val segmentEnd = trackCoordinates[i + 1]

            // Calculate distances to the endpoints
            val distToStart = calculateHaversineDistance(lat, lon, segmentStart.first, segmentStart.second)
            val distToEnd = calculateHaversineDistance(lat, lon, segmentEnd.first, segmentEnd.second)

            // Calculate the length of the segment
            val segmentLength = calculateHaversineDistance(
                segmentStart.first, segmentStart.second,
                segmentEnd.first, segmentEnd.second
            )

            // If the segment is very short, use distance to either endpoint
            if (segmentLength < 1.0) {
                val distance = min(distToStart, distToEnd)
                if (distance < minDistance) {
                    minDistance = distance
                    closestSegmentIndex = i
                    closestRatio = if (distToStart < distToEnd) 0.0 else 1.0
                }
                continue
            }

            // Calculate the projection of the point onto the segment
            val x = (lon - segmentStart.second) * (segmentEnd.second - segmentStart.second) + 
                    (lat - segmentStart.first) * (segmentEnd.first - segmentStart.first)
            val y = (segmentEnd.second - segmentStart.second) * (segmentEnd.second - segmentStart.second) + 
                    (segmentEnd.first - segmentStart.first) * (segmentEnd.first - segmentStart.first)
            val ratio = x / y

            // Calculate the distance to the segment
            val distance: Double
            val projRatio: Double

            if (ratio < 0) {
                distance = distToStart
                projRatio = 0.0
            } else if (ratio > 1) {
                distance = distToEnd
                projRatio = 1.0
            } else {
                // Calculate the projected point
                val projLat = segmentStart.first + ratio * (segmentEnd.first - segmentStart.first)
                val projLon = segmentStart.second + ratio * (segmentEnd.second - segmentStart.second)

                distance = calculateHaversineDistance(lat, lon, projLat, projLon)
                projRatio = ratio
            }

            if (distance < minDistance) {
                minDistance = distance
                closestSegmentIndex = i
                closestRatio = projRatio
            }
        }

        // Calculate the distance along the track to the closest point
        var distanceAlongTrack = 0.0

        // Add the lengths of all segments before the closest one
        for (i in 0 until closestSegmentIndex) {
            distanceAlongTrack += calculateHaversineDistance(
                trackCoordinates[i].first, trackCoordinates[i].second,
                trackCoordinates[i + 1].first, trackCoordinates[i + 1].second
            )
        }

        // Add the partial length of the closest segment
        distanceAlongTrack += closestRatio * calculateHaversineDistance(
            trackCoordinates[closestSegmentIndex].first, trackCoordinates[closestSegmentIndex].second,
            trackCoordinates[closestSegmentIndex + 1].first, trackCoordinates[closestSegmentIndex + 1].second
        )

        return distanceAlongTrack
    }

    /**
     * Extracts track coordinates from a GeoJsonFeature.
     *
     * @param feature The GeoJsonFeature to extract coordinates from
     * @return A list of coordinate pairs (latitude, longitude)
     */
    private fun extractTrackCoordinates(feature: GeoJsonFeature): List<Pair<Double, Double>> {
        if (feature.geometry.type != "LineString") {
            return emptyList()
        }

        val coordinates = mutableListOf<Pair<Double, Double>>()
        val lineCoords = feature.geometry.coordinates as? List<*>

        lineCoords?.forEach { point ->
            (point as? List<*>)?.let {
                if (it.size >= 2) {
                    // GeoJSON uses [longitude, latitude] order
                    coordinates.add(Pair(it[1] as Double, it[0] as Double))
                }
            }
        }

        return coordinates
    }

    /**
     * Calculates the minimum distance from a point to a track.
     *
     * @param lat The latitude of the point
     * @param lon The longitude of the point
     * @param trackCoordinates The list of coordinate pairs representing the track
     * @return The minimum distance in meters
     */
    private fun calculateMinDistanceToTrack(
        lat: Double,
        lon: Double,
        trackCoordinates: List<Pair<Double, Double>>
    ): Double {
        if (trackCoordinates.isEmpty()) {
            return Double.MAX_VALUE
        }

        if (trackCoordinates.size == 1) {
            return calculateHaversineDistance(lat, lon, trackCoordinates[0].first, trackCoordinates[0].second)
        }

        var minDistance = Double.MAX_VALUE

        // Calculate distance to each segment of the track
        for (i in 0 until trackCoordinates.size - 1) {
            val segmentStart = trackCoordinates[i]
            val segmentEnd = trackCoordinates[i + 1]

            val distance = calculateDistanceToSegment(
                lat, lon,
                segmentStart.first, segmentStart.second,
                segmentEnd.first, segmentEnd.second
            )

            minDistance = min(minDistance, distance)
        }

        return minDistance
    }

    /**
     * Calculates the distance from a point to a line segment.
     *
     * @param lat The latitude of the point
     * @param lon The longitude of the point
     * @param lat1 The latitude of the first endpoint of the segment
     * @param lon1 The longitude of the first endpoint of the segment
     * @param lat2 The latitude of the second endpoint of the segment
     * @param lon2 The longitude of the second endpoint of the segment
     * @return The distance in meters
     */
    private fun calculateDistanceToSegment(
        lat: Double, lon: Double,
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        // Calculate distances to the endpoints
        val distToStart = calculateHaversineDistance(lat, lon, lat1, lon1)
        val distToEnd = calculateHaversineDistance(lat, lon, lat2, lon2)

        // Calculate the length of the segment
        val segmentLength = calculateHaversineDistance(lat1, lon1, lat2, lon2)

        // If the segment is very short, return the distance to either endpoint
        if (segmentLength < 1.0) {
            return min(distToStart, distToEnd)
        }

        // Calculate the projection of the point onto the segment
        // This is an approximation for small distances
        val x = (lon - lon1) * (lon2 - lon1) + (lat - lat1) * (lat2 - lat1)
        val y = (lon2 - lon1) * (lon2 - lon1) + (lat2 - lat1) * (lat2 - lat1)
        val ratio = x / y

        // If the projection is outside the segment, return the distance to the closest endpoint
        if (ratio < 0) {
            return distToStart
        }
        if (ratio > 1) {
            return distToEnd
        }

        // Calculate the projected point
        val projLat = lat1 + ratio * (lat2 - lat1)
        val projLon = lon1 + ratio * (lon2 - lon1)

        // Return the distance to the projected point
        return calculateHaversineDistance(lat, lon, projLat, projLon)
    }

    /**
     * Calculates the distance between two points using the Haversine formula.
     *
     * @param lat1 The latitude of the first point
     * @param lon1 The longitude of the first point
     * @param lat2 The latitude of the second point
     * @param lon2 The longitude of the second point
     * @return The distance in meters
     */
    private fun calculateHaversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // Earth radius in meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}
