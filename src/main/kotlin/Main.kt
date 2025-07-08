package fr.bearit.template

import fr.bearit.template.CsvMapper

/**
 * Main function that reads the CSV file and displays information about the GeoJSON features.
 */
fun main() {
    val csvFilePath = "input/flat/id_geojson.csv"
    val mapper = CsvMapper()

    println("Reading GeoJSON features from CSV file: $csvFilePath")
    val features = mapper.mapCsvToFeatures(csvFilePath)

    println("Found ${features.size} features")

    // Display some information about the first few features
    features.take(5).forEachIndexed { index, feature ->
        println("Feature ${index + 1}:")
        println("  ID: ${feature.id}")
        println("  GeoJSON length: ${feature.geoJson.length} characters")
    }
}
