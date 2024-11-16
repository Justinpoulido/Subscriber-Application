package com.example.subscriberapplication

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.nio.charset.StandardCharsets
import java.util.UUID

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var studentIdTextView: TextView
    private lateinit var mMap: GoogleMap
    private var mqttClient: Mqtt5AsyncClient? = null
    private val pointsList = mutableListOf<LatLng>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        studentIdTextView = findViewById(R.id.studentIdText)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize and connect to MQTT broker
        setupMqttClient()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val trinidadBounds = LatLngBounds(
            LatLng(10.0, -62.0),
            LatLng(11.5, -60.0)
        )
        mMap.setLatLngBoundsForCameraTarget(trinidadBounds)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(10.6918, -61.2225), 10f))
    }

    private fun setupMqttClient() {
        mqttClient = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816034633.sundaebytestt.com")
            .serverPort(1883)
            .buildAsync()

        mqttClient?.connectWith()?.send()?.whenComplete { _, throwable ->
            if (throwable != null) {
                Log.e("SubscriberApp", "Error connecting to MQTT broker", throwable)
            } else {
                Log.d("SubscriberApp", "Connected to MQTT broker")
                subscribeToLocationUpdates()
            }
        }
    }


    private fun subscribeToLocationUpdates() {
        mqttClient?.subscribeWith()
            ?.topicFilter("assignment/location")
            ?.callback { publish ->
                val message = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                Log.d("SubscriberApp", "Received message: $message")
                handleReceivedLocation(message)
            }
            ?.send()
    }
    private fun handleReceivedLocation(message: String) {
        try {
            val parts = message.split(" | ")
            val studentIdPart = parts.firstOrNull { it.startsWith("Student ID:") }?.removePrefix("Student ID: ")
            val speedPart = parts.firstOrNull { it.startsWith("Speed:") }?.removePrefix("Speed: ")
            val locationPart = parts.lastOrNull { it.startsWith("Location:") }?.removePrefix("Location: ")
            val coordinates = locationPart?.split(", ") ?: return

            val latitude = coordinates[0].toDouble()
            val longitude = coordinates[1].toDouble()
            val location = LatLng(latitude, longitude)

            // Update Student ID and Speed in the TextView
            runOnUiThread {
                studentIdTextView.text = "Student ID: $studentIdPart | Speed: $speedPart"
                addMarkerAndPath(location)
            }
        } catch (e: Exception) {
            Log.e("SubscriberApp", "Failed to parse location message", e)
        }
    }

    /*
    private fun handleReceivedLocation(message: String) {
        try {
            val parts = message.split(" | ")
            val studentIdPart = parts.firstOrNull { it.startsWith("Student ID:") }?.removePrefix("Student ID: ")
            val locationPart = parts.lastOrNull { it.startsWith("Location:") }?.removePrefix("Location: ")
            val coordinates = locationPart?.split(", ") ?: return

            val latitude = coordinates[0].toDouble()
            val longitude = coordinates[1].toDouble()
            val location = LatLng(latitude, longitude)

            // Update Student ID in the TextView
            runOnUiThread {
                studentIdTextView.text = "Student ID: $studentIdPart"
                addMarkerAndPath(location)
            }
        } catch (e: Exception) {
            Log.e("SubscriberApp", "Failed to parse location message", e)
        }
    }

    */
    private fun addMarkerAndPath(location: LatLng) {
        pointsList.add(location)

        // Clear map and redraw path with markers
        mMap.clear()
        mMap.addMarker(MarkerOptions().position(location).title("Location Update"))

        val polylineOptions = PolylineOptions()
            .addAll(pointsList)
            .width(5f)
            .color(android.graphics.Color.BLUE)

        mMap.addPolyline(polylineOptions)
        mMap.moveCamera(CameraUpdateFactory.newLatLng(location))
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient?.disconnect()?.whenComplete { _, throwable ->
            if (throwable != null) {
                Log.e("SubscriberApp", "Error disconnecting from MQTT broker", throwable)
            } else {
                Log.d("SubscriberApp", "Disconnected from MQTT broker")
            }
        }
    }
}
