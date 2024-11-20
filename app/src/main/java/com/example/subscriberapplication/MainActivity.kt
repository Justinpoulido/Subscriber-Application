package com.example.subscriberapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
    private lateinit var viewMoreButton: Button
    private lateinit var summaryTextView: TextView
    private lateinit var dbHelper: SubscriberDatabaseHelper

    private var minSpeed = Double.MAX_VALUE // Start with the maximum possible value
    private var maxSpeed = Double.MIN_VALUE // Start with the minimum possible value
    private var totalSpeed = 0.0 // Track total speed for average calculation
    private var speedCount = 0   // Count number of speeds for average calculation
    private lateinit var mMap: GoogleMap
    private var mqttClient: Mqtt5AsyncClient? = null
    private val pointsList = mutableListOf<LatLng>()
    private var studentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        studentIdTextView = findViewById(R.id.studentIdText)
        viewMoreButton = findViewById(R.id.viewMoreButton)
        summaryTextView = findViewById(R.id.summaryTextView)
        dbHelper = SubscriberDatabaseHelper(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize and connect to MQTT broker
        setupMqttClient()
        viewMoreButton.setOnClickListener {
            showSummaryView() // Display details when the button is clicked
        }
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
            val speedPart = parts.firstOrNull { it.startsWith("Speed:") }?.removePrefix("Speed: ")?.replace(" km/h", "")
            val locationPart = parts.lastOrNull { it.startsWith("Location:") }?.removePrefix("Location: ")
            val coordinates = locationPart?.split(", ") ?: return

            // Parse speed and update min/max values
      /*      val speed = speedPart?.toDoubleOrNull()
            if (speed != null) {
                minSpeed = minOf(minSpeed, speed)
                maxSpeed = maxOf(maxSpeed, speed)

            }*/

            val latitude = coordinates[0].toDouble()
            val longitude = coordinates[1].toDouble()
            val location = LatLng(latitude, longitude)
            val speed = speedPart?.toDoubleOrNull() ?: 0.0
            minSpeed = minOf(minSpeed, speed)
            maxSpeed = maxOf(maxSpeed, speed)
            if (speed > 0.0) {
                totalSpeed += speed
                speedCount++
            }

            val timestamp = System.currentTimeMillis().toString()
            val studentId = studentIdPart ?: "Unknown"
            // Update the studentId
         //   studentId = studentIdPart // Assigning the studentId to the class variable

            // Insert the data into the database
            dbHelper.insertLocationData(studentId, speed, latitude, longitude, timestamp)

            // Update the UI with Student ID, Min Speed, and Max Speed
            runOnUiThread {
                studentIdTextView.text = "Student ID: $studentId | Min Speed: ${"%.2f".format(minSpeed)} km/h | Max Speed: ${"%.2f".format(maxSpeed)} km/h"
                addMarkerAndPath(location)
            }
        } catch (e: Exception) {
            Log.e("SubscriberApp", "Failed to parse location message", e)
        }
    }




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

    private fun showSummaryView() {
        val allData = dbHelper.getAllLocationData()
        if (allData.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Data")
                .setMessage("No location data available to summarize.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val speeds = allData.map { it.speed }
        val maxSpeed = speeds.maxOrNull() ?: 0.0
        val minSpeed = speeds.minOrNull() ?: 0.0
        val averageSpeed = if (speeds.isNotEmpty()) speeds.average() else 0.0

        // Get the latest student ID
        val latestStudentId = allData.last().studentId

        // Create summary text
        val summaryText = """
        Summary of $latestStudentId
        
        Max Speed: ${"%.2f".format(maxSpeed)} km/h
        Min Speed: ${"%.2f".format(minSpeed)} km/h
        Average Speed: ${"%.2f".format(averageSpeed)} km/h
    """.trimIndent()

        // Launch SummaryActivity with the summary data
        val intent = Intent(this, SummaryActivity::class.java)
        intent.putExtra("summary_text", summaryText)
        startActivity(intent)
    }


    /*
    private fun showSummaryView() {
        val allData = dbHelper.getAllLocationData()
        if (allData.isEmpty()) {
            summaryTextView.text = "No data available"
            summaryTextView.visibility = View.VISIBLE
            return
        }

        val speeds = allData.map { it.speed }
        val maxSpeed = speeds.maxOrNull() ?: 0.0
        val minSpeed = speeds.minOrNull() ?: 0.0
        val averageSpeed = if (speeds.isNotEmpty()) speeds.average() else 0.0

        // Get the latest student ID
        val latestStudentId = allData.last().studentId

        // Create summary text
        val summaryText = """
        Summary of $latestStudentId
        
        Max Speed: ${"%.2f".format(maxSpeed)} km/h
        Min Speed: ${"%.2f".format(minSpeed)} km/h
        Average Speed: ${"%.2f".format(averageSpeed)} km/h
    """.trimIndent()

        summaryTextView.text = summaryText
        summaryTextView.visibility = View.VISIBLE
    }   */


/*
    private fun showSummaryView() {
        // Calculate average speed
        val averageSpeed = if (speedCount > 0) totalSpeed / speedCount else 0.0

        // Create summary text
        val summaryText = """
            Summary of ${studentId ?: "Unknown"}
            
            Max Speed: ${"%.2f".format(maxSpeed)} km/h
            Min Speed: ${"%.2f".format(minSpeed)} km/h
            Average Speed: ${"%.2f".format(averageSpeed)} km/h
        """.trimIndent()

        // Populate and show the summary text view
        summaryTextView.text = summaryText
        summaryTextView.visibility = View.VISIBLE
    }
*/
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
