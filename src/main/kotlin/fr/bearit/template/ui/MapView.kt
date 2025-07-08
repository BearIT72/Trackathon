package fr.bearit.template.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import fr.bearit.template.GeoJsonFeature
import fr.bearit.template.PointOfInterest
import org.jxmapviewer.JXMapViewer
import org.jxmapviewer.OSMTileFactoryInfo
import org.jxmapviewer.painter.CompoundPainter
import org.jxmapviewer.painter.Painter
import org.jxmapviewer.viewer.*
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Point2D
import java.util.*
import javax.swing.SwingUtilities

/**
 * A Compose component that displays a map with a track and points of interest.
 *
 * @param feature The GeoJsonFeature representing the track to display
 * @param pointsOfInterest The list of points of interest to display on the map
 * @param modifier The modifier to be applied to the component
 */
@Composable
fun MapView(
    feature: GeoJsonFeature,
    pointsOfInterest: List<PointOfInterest>,
    modifier: Modifier = Modifier
) {
    // Create and configure the map viewer
    val mapViewer = remember { JXMapViewer() }

    // Configure the map on first composition
    LaunchedEffect(feature, pointsOfInterest) {
        SwingUtilities.invokeLater {
            configureMap(mapViewer, feature, pointsOfInterest)
        }
    }

    // Embed the Swing component in Compose
    SwingPanel(
        factory = { mapViewer },
        modifier = modifier
    )
}

/**
 * Configures the map viewer with the track and points of interest.
 *
 * @param mapViewer The JXMapViewer to configure
 * @param feature The GeoJsonFeature representing the track
 * @param pointsOfInterest The list of points of interest
 */
private fun configureMap(
    mapViewer: JXMapViewer,
    feature: GeoJsonFeature,
    pointsOfInterest: List<PointOfInterest>
) {
    // Set the tile factory (OpenStreetMap)
    val tileFactoryInfo = OSMTileFactoryInfo()
    val tileFactory = DefaultTileFactory(tileFactoryInfo)
    mapViewer.tileFactory = tileFactory

    // Set zoom level
    mapViewer.zoom = 7

    // Create track waypoints
    val trackWaypoints = createTrackWaypoints(feature)

    // Create POI waypoints
    val poiWaypoints = createPoiWaypoints(pointsOfInterest)

    // Set the map center to the first point of the track
    if (trackWaypoints.isNotEmpty()) {
        mapViewer.addressLocation = trackWaypoints.first().position
    }

    // Create a track painter
    val trackPainter = TrackPainter(trackWaypoints)

    // Create waypoint painters for POIs
    val waypointPainter = WaypointPainter<Waypoint>()
    waypointPainter.waypoints = HashSet(poiWaypoints)

    // Create a compound painter with both the track and waypoints
    val painters = listOf<Painter<JXMapViewer>>(trackPainter, waypointPainter)
    val compoundPainter = CompoundPainter(painters)
    mapViewer.overlayPainter = compoundPainter

    // Set the viewport to show the entire track
    if (trackWaypoints.isNotEmpty()) {
        // Extract GeoPosition objects from waypoints
        val geoPositions = HashSet<GeoPosition>(trackWaypoints.map { it.position })
        mapViewer.zoomToBestFit(geoPositions, 0.7)
    }
}

/**
 * Creates waypoints from the track coordinates.
 *
 * @param feature The GeoJsonFeature representing the track
 * @return A list of waypoints
 */
private fun createTrackWaypoints(feature: GeoJsonFeature): List<DefaultWaypoint> {
    val waypoints = mutableListOf<DefaultWaypoint>()

    if (feature.geometry.type == "LineString") {
        feature.geometry.coordinates.forEach { point ->
            if (point.size >= 2) {
                // GeoJSON uses [longitude, latitude] order
                val lat = point[1]
                val lon = point[0]
                val geoPosition = GeoPosition(lat, lon)
                waypoints.add(DefaultWaypoint(geoPosition))
            }
        }
    }

    return waypoints
}

/**
 * Creates waypoints from the points of interest.
 *
 * @param pointsOfInterest The list of points of interest
 * @return A list of waypoints
 */
private fun createPoiWaypoints(pointsOfInterest: List<PointOfInterest>): List<Waypoint> {
    return pointsOfInterest.map { poi ->
        val geoPosition = GeoPosition(poi.lat, poi.lon)
        DefaultWaypoint(geoPosition)
    }
}

/**
 * A painter that draws a track as a line connecting waypoints.
 *
 * @param track The list of waypoints representing the track
 */
private class TrackPainter(private val track: List<DefaultWaypoint>) : Painter<JXMapViewer> {
    override fun paint(g: Graphics2D, viewer: JXMapViewer, width: Int, height: Int) {
        g.color = Color.RED
        g.stroke = BasicStroke(2f)

        val points = track.map { waypoint ->
            val point = viewer.tileFactory.geoToPixel(waypoint.position, viewer.zoom)
            Point2D.Double(point.x, point.y)
        }

        // Draw the track as a line connecting all points
        val oldClip = g.clip
        g.clip = Rectangle(0, 0, width, height)

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            g.drawLine(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
        }

        g.clip = oldClip
    }
}

/**
 * A utility class to help with zooming to fit all waypoints.
 */
private class GeoPositionPainter(private val waypoints: Set<Waypoint>) {
    init {
        // This is just a utility class to help with the zoomToBestFit method
    }
}
