package fr.bearit.template

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

/**
 * Database table for storing track data
 */
object Tracks : IntIdTable() {
    val inputGeoJson = text("input_geojson")
    val filteredPois = text("filtered_pois")
    val featureId = varchar("feature_id", 255)
}

/**
 * Entity class for Track
 */
class Track(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Track>(Tracks)

    var inputGeoJson by Tracks.inputGeoJson
    var filteredPois by Tracks.filteredPois
    var featureId by Tracks.featureId

    /**
     * Converts the stored JSON string back to a GeoJsonFeature object
     */
    fun getGeoJsonFeature(): GeoJsonFeature {
        return GeoJsonFeature.fromJson(featureId, inputGeoJson)
    }

    /**
     * Converts the stored JSON string back to a list of PointOfInterest objects
     */
    fun getPointsOfInterest(): List<PointOfInterest> {
        val mapper = jacksonObjectMapper()
        return mapper.readValue(filteredPois)
    }
}

/**
 * Service for database operations
 */
class DatabaseService {
    private val dbFile = File("trackathon.db")
    private val objectMapper = jacksonObjectMapper()

    init {
        // Initialize database
        Database.connect("jdbc:h2:file:${dbFile.absolutePath}", driver = "org.h2.Driver")
        
        // Create tables if they don't exist
        transaction {
            SchemaUtils.create(Tracks)
        }
    }

    /**
     * Saves a track with its feature and points of interest to the database
     */
    fun saveTrack(feature: GeoJsonFeature, pointsOfInterest: List<PointOfInterest>): Int {
        return transaction {
            val track = Track.new {
                featureId = feature.id
                inputGeoJson = feature.data.let { objectMapper.writeValueAsString(it) }
                filteredPois = objectMapper.writeValueAsString(pointsOfInterest)
            }
            track.id.value
        }
    }

    /**
     * Retrieves a track by its ID
     */
    fun getTrack(id: Int): Track? {
        return transaction {
            Track.findById(id)
        }
    }

    /**
     * Retrieves all tracks
     */
    fun getAllTracks(): List<Track> {
        return transaction {
            Track.all().toList()
        }
    }

    /**
     * Retrieves a track by its feature ID
     */
    fun getTrackByFeatureId(featureId: String): Track? {
        return transaction {
            Track.find { Tracks.featureId eq featureId }.firstOrNull()
        }
    }
}