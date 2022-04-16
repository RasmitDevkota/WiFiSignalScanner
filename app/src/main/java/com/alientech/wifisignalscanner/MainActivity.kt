package com.alientech.wifisignalscanner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.PatternMatcher
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt


class MainActivity : Activity() {
    var wifi: WifiManager? = null
    var connectivity: ConnectivityManager? = null

    val ssid = "ASUS"

    val rssiList = ArrayList<Int>()

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.rssiText).movementMethod = ScrollingMovementMethod()

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

                    val rssiText = findViewById<View>(R.id.rssiText) as TextView
                    rssiText.text = "${rssiText.text}($timestring) RSSI: $rssi dBm; Average RSSI: $average dBm\n"
                }
            }
        }

        t.scheduleAtFixedRate(tt, 500, 1000)
    }
}

