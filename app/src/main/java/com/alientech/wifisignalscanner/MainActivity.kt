package com.alientech.wifisignalscanner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.*
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import kotlinx.coroutines.*
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt


class MainActivity : Activity() {
    var wifi: WifiManager? = null
    var connectivity: ConnectivityManager? = null

    var ssid = "ASUS"
    var scanInterval = 1L
    var showTimestamp = false
    var scanOn = true

    var rssiList = ArrayList<Int>()
    var rssiString = ""

    val MODE_CSV = 1122
    val MODE_TXT = 519

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putSerializable("rssiList", rssiList)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        rssiList = savedInstanceState.getSerializable("rssiList") as ArrayList<Int>
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun runRSSIProtocol() {
        val timestring = if (showTimestamp) {
            val timestamp = Timestamp.from(Instant.now())
            val timelist = timestamp.toString().split(" ")
            "(${
                listOf(
                    "Jan",
                    "Feb",
                    "Mar",
                    "Apr",
                    "May",
                    "Jun",
                    "Jul",
                    "Aug",
                    "Sep",
                    "Oct",
                    "Nov",
                    "Dec"
                ).indexOf(timelist[1]) + 1
            }/${timelist[2].removePrefix("0")} @ ${timelist[3]}) "
        } else {
            ""
        }

        val rssi = wifi!!.connectionInfo.rssi

        if (rssiList.size > 999) rssiList.removeAt(0)
        rssiList.add(rssi)

        val average = rssiList.subList(max(rssiList.size - 10, 0), rssiList.size).average().roundToInt()

        rssiString += "${timestring}RSSI: $rssi dBm; Average RSSI: $average dBm\n"

        runOnUiThread {
            (findViewById<View>(R.id.rssiText) as TextView).text = rssiString
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI
        findViewById<TextView>(R.id.rssiText).apply {
            movementMethod = ScrollingMovementMethod()
        }

        // WiFi
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivity = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsidPattern(PatternMatcher(ssid, PatternMatcher.PATTERN_PREFIX))
            .build()

        val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

        val networkCallback: ConnectivityManager.NetworkCallback = ConnectivityManager.NetworkCallback()

        connectivity!!.requestNetwork(request, networkCallback)

        val rssiDataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        rssiDataScope.launch {
            while (scanOn) {
                runRSSIProtocol()

                delay(scanInterval * 1000)
            }
        }

        // Settings
        val settingsButton = findViewById<ImageButton>(R.id.showSettingsPopupButton)

        settingsButton.setOnClickListener {
            val inflater = layoutInflater
            val settingsPopupView = inflater.inflate(R.layout.settings_popup, null)

            val width = ceil(Resources.getSystem().displayMetrics.widthPixels * 0.9).toInt()
            val height = ceil(Resources.getSystem().displayMetrics.heightPixels * 0.9).toInt()
            val focusable = true

            val popupWindow = PopupWindow(settingsPopupView, width, height, focusable).apply {
                showAtLocation(View(applicationContext), Gravity.CENTER, 0, 0)

                with (settingsPopupView) {
                    findViewById<EditText>(R.id.routerSSIDInput).setText(ssid, TextView.BufferType.EDITABLE)

                    findViewById<EditText>(R.id.scanIntervalInput).setText(scanInterval.toString(), TextView.BufferType.EDITABLE)

                    findViewById<CheckBox>(R.id.showTimestampCheckBox).isChecked = showTimestamp
                }
            }

            settingsPopupView.findViewById<Button>(R.id.saveDataButton).setOnClickListener {
                Log.d("MainActivity", "Save data requested")

                if (settingsPopupView.findViewById<CheckBox>(R.id.saveCSVCheckbox).isChecked) {
                    saveData(MODE_CSV)

                    Log.d("MainActivity", "Saving .csv of data...")
                }

                if (settingsPopupView.findViewById<CheckBox>(R.id.saveTXTCheckbox).isChecked) {
                    saveData(MODE_TXT)

                    Log.d("MainActivity", "Saving .txt of data...")
                }
            }

            settingsPopupView.findViewById<Button>(R.id.closeSettingsPopupButton).setOnClickListener {
                popupWindow.dismiss()
            }

            settingsPopupView.findViewById<Button>(R.id.saveSettingsPopupButton).setOnClickListener {
                with (settingsPopupView) {
                    ssid = findViewById<EditText>(R.id.routerSSIDInput).text.toString()

                    scanInterval = max(findViewById<EditText>(R.id.scanIntervalInput).text.toString().toLong(), 1)

                    showTimestamp = findViewById<CheckBox>(R.id.showTimestampCheckBox).isChecked
                }

                popupWindow.dismiss()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if ((requestCode == MODE_CSV || requestCode == MODE_TXT) && resultCode == RESULT_OK) {
            resultData?.data?.also { uri ->
                try {
                    contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { fos ->
                            fos.write(
                                (when (requestCode) {
                                    MODE_CSV -> {
                                        "RSSI (dBm)\n" + rssiList.joinToString("\n")
                                    }
                                    MODE_TXT -> {
                                        rssiString
                                    }
                                    else -> { // Default to MODE_TXT
                                        rssiString
                                    }
                                }).toByteArray()
                            )
                        }
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun saveData(mode: Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/${modeString(mode)}"
            putExtra(Intent.EXTRA_TITLE, "rssi_data.${modeString(mode)}")
        }

        startActivityForResult(intent, mode)
    }

    fun copyTXT() {
        val txtData = rssiString
        val clipboard = getSystemService(applicationContext, ClipboardManager::class.java)
        val clip = ClipData.newPlainText("WiFiSignalScanner RSSI Data", rssiString)
        clipboard!!.setPrimaryClip(clip)
    }

    fun modeString(mode: Int): String {
        return when (mode) {
            MODE_CSV -> "csv"
            MODE_TXT -> "txt"
            else -> "txt" // Default to MODE_TXT
        }
    }
}

