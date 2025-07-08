package fr.bearit.template

import java.io.File

/**
 * Maps CSV data from a file to a list of GeoJsonFeature objects.
 */
class CsvMapper {
    /**
     * Reads a CSV file and maps each row to a GeoJsonFeature object.
     *
     * @param filePath The path to the CSV file
     * @return A list of GeoJsonFeature objects
     */
    fun mapCsvToFeatures(filePath: String): List<GeoJsonFeature> {
        val file = File(filePath)
        if (!file.exists()) {
            println("File not found: $filePath")
            return emptyList()
        }

        return file.readLines()
            .filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split(",", limit = 2)
                if (parts.size < 2) {
                    println("Invalid line format: $line")
                    null
                } else {
                    GeoJsonFeature.fromJson(
                        id = parts[0],
                        geoJsonString = parts[1]
                    )
                }
            }
    }
}
