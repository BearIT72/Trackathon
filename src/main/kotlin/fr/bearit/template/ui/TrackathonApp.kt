package fr.bearit.template.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fr.bearit.template.DatabaseService
import fr.bearit.template.GeoJsonFeature
import fr.bearit.template.PointOfInterest

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Trackathon - Track and POI Viewer",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        App()
    }
}

@Composable
fun App() {
    val dbService = remember { DatabaseService() }
    var tracks by remember { mutableStateOf(emptyList<fr.bearit.template.Track>()) }
    var selectedTrackId by remember { mutableStateOf<Int?>(null) }

    // Load tracks on first composition
    LaunchedEffect(Unit) {
        tracks = dbService.getAllTracks()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                Text(
                    text = "Trackathon - Track and POI Viewer",
                    style = MaterialTheme.typography.h4
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tracks found: ${tracks.size}",
                    style = MaterialTheme.typography.h6
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (tracks.isEmpty()) {
                    Text("No tracks found in database")
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Track list
                        Column(
                            modifier = Modifier.width(300.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Select a track:",
                                style = MaterialTheme.typography.subtitle1
                            )

                            tracks.forEach { track ->
                                Button(
                                    onClick = { selectedTrackId = track.id.value },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = selectedTrackId != track.id.value
                                ) {
                                    Text("Track ${track.id.value}")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Track details
                        Column(modifier = Modifier.weight(1f)) {
                            selectedTrackId?.let { trackId ->
                                val track = tracks.find { it.id.value == trackId }
                                if (track != null) {
                                    DisplayTrackDetails(track)
                                }
                            } ?: run {
                                Text("Select a track to view details")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisplayTrackDetails(track: fr.bearit.template.Track) {
    val feature = track.getGeoJsonFeature()
    val pois = track.getPointsOfInterest()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Track Details",
            style = MaterialTheme.typography.h5
        )

        Text("ID: ${track.id.value}")
        Text("Feature ID: ${feature.id}")
        Text("Coordinates count: ${feature.geometry.coordinates.size}")

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Points of Interest (${pois.size})",
            style = MaterialTheme.typography.h6
        )

        if (pois.isEmpty()) {
            Text("No POIs found for this track")
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pois.forEach { poi ->
                    val name = poi.name ?: "Unnamed"
                    val type = poi.tags.entries.firstOrNull()?.let { "${it.key}=${it.value}" } ?: "Unknown type"

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.subtitle1
                            )
                            Text("Type: $type")
                            Text("Location: (${poi.lat}, ${poi.lon})")
                        }
                    }
                }
            }
        }
    }
}