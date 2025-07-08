package fr.bearit.template

/**
 * Main function that reads the CSV file and displays information about the GeoJSON features.
 */
fun main() {
    val csvFilePath = "input/flat/id_geojson.csv"
    val mapper = CsvMapper()
    val osmService = OpenStreetMapService()

    println("Reading GeoJSON features from CSV file: $csvFilePath")
    val features = mapper.mapCsvToFeatures(csvFilePath)

    println("Found ${features.size} features")

    // Display information about each feature and fetch points of interest
    // Limit to first 5 features to avoid long processing times
    features.take(5).forEachIndexed { index, feature ->
        println("ID: ${feature.id}")
        println("  Coordinates count: ${feature.geometry.coordinates.size}")

        // Fetch and display points of interest closest to the track
        println("  Fetching points of interest closest to the track...")
        // Get points of interest within 2000 meters of the track, limited to 5 results
        val pointsOfInterest = osmService.getPointsOfInterestForFeature(feature, 500.0, 5)
        println("  Points of Interest Count (filtered by distance to track): ${pointsOfInterest.size}")

        // Display information about each point of interest
        if (pointsOfInterest.isNotEmpty()) {
            println("  Points of Interest (sorted by position along the track):")
            pointsOfInterest.forEach { poi ->
                val name = poi.name ?: "Unnamed"
                val type = poi.tags.entries.firstOrNull()?.let { "${it.key}=${it.value}" } ?: "Unknown type"
                println("    - $name (${poi.lat}, ${poi.lon}) [$type]")
            }
        }

        println()
    }

    println("Note: Only processed the first 5 features to avoid long processing times.")
}
