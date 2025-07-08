package fr.bearit.template

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeoJsonFeatureTest {
    @Test
    fun testFromJson() {
        // Sample GeoJSON string
        val geoJsonString = """
            {
              "type": "Feature",
              "properties": {},
              "geometry": {
                "type": "LineString",
                "coordinates": [
                  [5.700719, 44.872651],
                  [5.700673, 44.872703]
                ]
              }
            }
        """.trimIndent()

        // Create a GeoJsonFeature from the JSON string
        val feature = GeoJsonFeature.fromJson("test-id", geoJsonString)

        // Verify the feature properties
        assertEquals("test-id", feature.id)
        assertEquals("Feature", feature.type)
        assertNotNull(feature.properties)
        assertEquals("LineString", feature.geometry.type)
        
        // Verify that the coordinates are correctly mapped
        val coordinates = feature.geometry.coordinates as List<*>
        assertEquals(2, coordinates.size)
        
        // Convert back to JSON and verify it contains the expected data
        val json = feature.toJson()
        assert(json.contains("LineString"))
        assert(json.contains("5.700719"))
        assert(json.contains("44.872651"))
    }
}