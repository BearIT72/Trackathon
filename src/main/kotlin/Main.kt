package fr.bearit.template

/**
 * Main function that reads the CSV file and displays information about the GeoJSON features.
 */
fun mainT() {
    val csvFilePath = "input/flat/id_geojson.csv"
    val mapper = CsvMapper()
    val osmService = OpenStreetMapService()
    val dbService = DatabaseService()

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
        // Get points of interest within 500 meters of the track, limited to 5 results
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

            // Create and display the route URL
            val routeUrl = osmService.createRouteUrl(feature, pointsOfInterest)
            println("  Route URL:")
            println("    $routeUrl")

            // Save to database
            val trackId = dbService.saveTrack(feature, pointsOfInterest)
            println("  Saved to database with ID: $trackId")
        }

        println()
    }

    // Demonstrate retrieving data from the database
    println("Retrieving data from database...")
    val allTracks = dbService.getAllTracks()
    println("Found ${allTracks.size} tracks in the database")

    if (allTracks.isNotEmpty()) {
        val firstTrack = allTracks.first()
        println("First track in database:")
        println("  ID: ${firstTrack.id}")
        println("  Feature ID: ${firstTrack.featureId}")

        val feature = firstTrack.getGeoJsonFeature()
        println("  Coordinates count: ${feature.geometry.coordinates.size}")

        val pois = firstTrack.getPointsOfInterest()
        println("  Points of Interest Count: ${pois.size}")

        if (pois.isNotEmpty()) {
            println("  First POI: ${pois.first().name ?: "Unnamed"} (${pois.first().lat}, ${pois.first().lon})")
        }
    }

    println("Note: Only processed the first 5 features to avoid long processing times.")
}
