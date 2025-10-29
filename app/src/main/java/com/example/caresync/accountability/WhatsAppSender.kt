package com.example.caresync.accountability

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object WhatsAppSender {

    private const val TAG = "WhatsAppSender"

    fun sendReport(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            // Format phone number (remove spaces, add country code if missing)
            val formattedNumber = formatPhoneNumber(phoneNumber)

            // Create WhatsApp intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$formattedNumber?text=${Uri.encode(message)}")
                setPackage("com.whatsapp")
            }

            // Check if WhatsApp is installed
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "✅ WhatsApp message sent to $formattedNumber")
                true
            } else {
                Log.e(TAG, "❌ WhatsApp not installed")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending WhatsApp message", e)
            false
        }
    }

    private fun formatPhoneNumber(phone: String): String {
        // Remove all non-digit characters
        val digits = phone.filter { it.isDigit() }

        // Add +91 if it's a 10-digit Indian number
        return if (digits.length == 10) {
            "91$digits"
        } else {
            digits
        }
    }
}
