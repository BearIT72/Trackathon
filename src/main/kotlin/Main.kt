package fr.bearit.template

import fr.bearit.template.CsvMapper
import fr.bearit.template.OpenStreetMapService

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
        println("Feature ${index + 1}:")
        println("  ID: ${feature.id}")
        println("  Type: ${feature.type}")
        println("  Geometry Type: ${feature.geometry.type}")

        // Fetch and display the count of points of interest
        println("  Fetching points of interest near coordinates...")
        val poiCount = osmService.getPointsOfInterestCountForFeature(feature)
        println("  Points of Interest Count: $poiCount")

        println("  GeoJSON: ${feature.toJson().take(50)}...")
        println()
    }

    println("Note: Only processed the first 5 features to avoid long processing times.")
}
