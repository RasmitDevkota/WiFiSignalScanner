package com.alientech.wifisignalscanner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PatternMatcher
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import java.io.File
import java.io.FileOutputStream
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt


class MainActivity : Activity() {
    var wifi: WifiManager? = null
    var connectivity: ConnectivityManager? = null

    val ssid = "ASUS"

    val rssiList = ArrayList<Int>()
    lateinit var rssiString: String

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI

        findViewById<TextView>(R.id.rssiText).movementMethod = ScrollingMovementMethod()

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

        val t = Timer()
        val i = 0
        val tt: TimerTask = object : TimerTask() {
            @SuppressLint("SetTextI18n")
            override fun run() {
                runOnUiThread {
                    val timestamp = Timestamp.from(Instant.now())
                    val timelist = timestamp.toString().split(" ")
                    val timestring = "${listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                        .indexOf(timelist[1]) + 1}/${timelist[2].removePrefix("0")} @ ${timelist[3]}"

                    val rssi = wifi!!.connectionInfo.rssi

                    if (rssiList.size > 9) rssiList.removeAt(0)
                    rssiList.add(rssi)

                    val average = rssiList.average().roundToInt()

                    rssiString += "($timestring) RSSI: $rssi dBm; Average RSSI: $average dBm\n"
                    (findViewById<View>(R.id.rssiText) as TextView).text = rssiString
                }
            }
        }

        t.scheduleAtFixedRate(tt, 500, 1000)

        // Settings
        val settingsButton = findViewById<ImageButton>(R.id.settings_button)

        settingsButton.setOnClickListener {
            val inflater = layoutInflater
            val settingsPopupView = inflater.inflate(R.layout.settings_popup, null)

            val width = LinearLayout.LayoutParams.WRAP_CONTENT
            val height = LinearLayout.LayoutParams.WRAP_CONTENT
            val focusable = true

            val popupWindow = PopupWindow(settingsPopupView, width, height, focusable).apply {
                showAtLocation(View(applicationContext), Gravity.CENTER, 0, 0)
                println("hi")
            }

            settingsPopupView.findViewById<Button>(R.id.buttonPopup).setOnClickListener {

            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun saveData() {
        if (Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
            return
        }

        val file = File(getExternalFilesDir(null), "")

        try {
            file.createNewFile()

            FileOutputStream(file, true).apply {
                write(rssiString.toByteArray())

                flush()

                close()
            }
        } catch (e: Exception) {
            e.printStackTrace()

            TODO("Display error to user")
        }
    }

    fun copyTXT() {
        val txtData = rssiString
        val clipboard = getSystemService(applicationContext, ClipboardManager::class.java)
        val clip = ClipData.newPlainText("", rssiString)
        clipboard!!.setPrimaryClip(clip)
    }
}

