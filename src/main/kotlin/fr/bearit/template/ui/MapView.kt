package fr.bearit.template.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import java.awt.event.MouseAdapter
import java.awt.event.MouseWheelEvent
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

    // State to track the current zoom level
    var zoomLevel by remember { mutableStateOf(7) } // Default zoom level

    // Configure the map on first composition
    LaunchedEffect(feature, pointsOfInterest) {
        SwingUtilities.invokeLater {
            configureMap(mapViewer, feature, pointsOfInterest)
        }
    }

    // Update map zoom level when zoomLevel state changes
    LaunchedEffect(zoomLevel) {
        SwingUtilities.invokeLater {
            mapViewer.zoom = zoomLevel
        }
    }

    Box(modifier = modifier) {
        // Embed the Swing component in Compose
        SwingPanel(
            factory = { mapViewer },
            modifier = Modifier.fillMaxSize().padding(end = 56.dp, top = 84.dp) // Leave space for zoom buttons
        )

        // Zoom controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .zIndex(1f),  // Ensure zoom controls appear above the map
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Zoom in button
            Button(
                onClick = {
                    zoomLevel = maxOf(1, zoomLevel - 1) // Zoom in (decrease zoom level)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Text("+")
            }

            // Zoom out button
            Button(
                onClick = {
                    zoomLevel = minOf(15, zoomLevel + 1) // Zoom out (increase zoom level)
                },
                modifier = Modifier.size(40.dp)
            ) {
                Text("-")
            }
        }
    }
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
        mapViewer.zoomToBestFit(geoPositions, 0.9)
    }

    // Add mouse wheel zoom functionality
    mapViewer.addMouseWheelListener { e ->
        if (e is MouseWheelEvent) {
            // Get the current zoom level
            var zoom = mapViewer.zoom

            // Adjust zoom level based on wheel rotation
            // Negative rotation means zoom in, positive means zoom out
            if (e.wheelRotation < 0) {
                zoom = maxOf(1, zoom - 1) // Zoom in (decrease zoom level)
            } else {
                zoom = minOf(15, zoom + 1) // Zoom out (increase zoom level)
            }

            // Set the new zoom level
            mapViewer.zoom = zoom
        }
    }

    // Add mouse drag functionality for panning
    val mouseAdapter = object : MouseAdapter() {
        private var lastX = 0
        private var lastY = 0
        private var isDragging = false

        override fun mousePressed(e: java.awt.event.MouseEvent) {
            // Record the starting point of the drag
            lastX = e.x
            lastY = e.y
            isDragging = true
        }

        override fun mouseReleased(e: java.awt.event.MouseEvent) {
            isDragging = false
        }

        override fun mouseDragged(e: java.awt.event.MouseEvent) {
            if (isDragging) {
                // Calculate the distance moved
                val dx = lastX - e.x
                val dy = lastY - e.y

                // Update the last position
                lastX = e.x
                lastY = e.y

                // Get the current center point
                val center = mapViewer.centerPosition

                // Convert pixel movement to geo-position movement
                val pixelWidth = mapViewer.width
                val pixelHeight = mapViewer.height
                val geoWidth = mapViewer.tileFactory.getTileSize(mapViewer.zoom) * 
                               (mapViewer.tileFactory.getInfo().getMaximumZoomLevel() - mapViewer.zoom + 1)
                val geoHeight = geoWidth

                // Calculate the new center position
                val newLat = center.latitude + (dy * geoHeight / pixelHeight)
                val newLon = center.longitude - (dx * geoWidth / pixelWidth)

                // Set the new center position
                mapViewer.centerPosition = GeoPosition(newLat, newLon)
            }
        }
    }

    // Add the mouse listeners
    mapViewer.addMouseListener(mouseAdapter)
    mapViewer.addMouseMotionListener(mouseAdapter)
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
        g.color = Color.BLUE
        g.stroke = BasicStroke(4f)

        val points = track.map { waypoint ->
            val point = viewer.tileFactory.geoToPixel(waypoint.position, viewer.zoom)
            Point2D.Double(point.x, point.y)
        }

        // Draw the track as a continuous line connecting all points
        val oldClip = g.clip
        g.clip = Rectangle(0, 0, width, height)

        // Create a path for the track
        val path = java.awt.geom.Path2D.Double()

        if (points.isNotEmpty()) {
            // Move to the first point
            path.moveTo(points[0].x, points[0].y)

            // Add line segments to all other points
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }

            // Draw the path
            g.draw(path)
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
