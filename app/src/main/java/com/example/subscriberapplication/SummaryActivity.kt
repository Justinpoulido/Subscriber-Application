package com.example.subscriberapplication

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SummaryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)

        // Get the summary data from the Intent
        val summaryText = intent.getStringExtra("summary_text")

        // Find the TextView and set the summary text
        val summaryTextView: TextView = findViewById(R.id.summaryTextView)
        summaryTextView.text = summaryText
    }
}
