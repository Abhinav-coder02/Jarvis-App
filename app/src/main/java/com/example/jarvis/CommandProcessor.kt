package com.example.jarvis

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log

class CommandProcessor(private val context: Context) {

    fun processCommand(commandText: String): String {
        val command = commandText.lowercase().trim()
        Log.d("CommandProcessor", "Processing command: $command")

        return when {
            command.startsWith("open ") -> handleOpenApp(command.removePrefix("open "))
            command.startsWith("call ") -> handleMakeCall(command.removePrefix("call "))
            command.contains("turn on flashlight") || command.contains("flashlight on") -> handleFlashlight(true)
            command.contains("turn off flashlight") || command.contains("flashlight off") -> handleFlashlight(false)
            command.contains("open wifi") || command.contains("wifi settings") -> openSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings")
            command.contains("open bluetooth") || command.contains("bluetooth settings") -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")
            command.startsWith("search for ") -> handleWebSearch(command.removePrefix("search for "))
            command.startsWith("search ") -> handleWebSearch(command.removePrefix("search "))
            else -> handleWebSearch(command)
        }
    }

    private fun handleOpenApp(appName: String): String {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        var matchedPackage: String? = null
        var matchedLabel: String? = null

        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label == appName.lowercase() || label.contains(appName.lowercase())) {
                matchedPackage = app.packageName
                matchedLabel = pm.getApplicationLabel(app).toString()
                break
            }
        }

        return if (matchedPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matchedPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                "Opening $matchedLabel"
            } else {
                "Unable to open $appName"
            }
        } else {
            "Application $appName was not found on this device."
        }
    }

    private fun handleMakeCall(query: String): String {
        val trimmedQuery = query.trim()
        val phoneNumber = if (trimmedQuery.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
            trimmedQuery
        } else {
            getPhoneNumberFromContacts(trimmedQuery)
        }

        return if (!phoneNumber.isNullOrEmpty()) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                "Calling $trimmedQuery"
            } catch (e: SecurityException) {
                "Call permission was denied."
            }
        } else {
            "Could not find contact details for $trimmedQuery"
        }
    }

    private fun getPhoneNumberFromContacts(name: String): String? {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    return it.getString(numberIndex)
                }
            }
        }
        return null
    }

    private fun handleFlashlight(enable: Boolean): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enable)
            if (enable) "Flashlight turned on" else "Flashlight turned off"
        } catch (e: CameraAccessException) {
            Log.e("CommandProcessor", "Error toggling flashlight", e)
            "Failed to toggle flashlight"
        } catch (e: Exception) {
            "Flashlight is unavailable on this device"
        }
    }

    private fun openSettings(action: String, name: String): String {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opening $name"
    }

    private fun handleWebSearch(query: String): String {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Searching the web for $query"
    }
}