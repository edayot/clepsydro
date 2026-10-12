package expo.modules.timelapseengine

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class TimelapseService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var job: Job? = null
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val prefs = getSharedPreferences("timelapse", Context.MODE_PRIVATE)

    if (intent?.hasExtra("host") == true) {            // nouveau lancement
      prefs.edit().clear()
        .putString("host", intent.getStringExtra("host"))
        .putInt("port", intent.getIntExtra("port", 8080))
        .putInt("intervalMs", intent.getIntExtra("intervalMs", 5000))
        .putInt("total", intent.getIntExtra("total", 100))
        .putLong("startAt", System.currentTimeMillis())
        .putInt("taken", 0)
        .putBoolean("running", true)
        .apply()
    } else if (!prefs.getBoolean("running", false)) {  // redémarrage système sans plan actif
      stopSelf(); return START_NOT_STICKY
    }

    startInForeground()
    wakeLock = getSystemService(PowerManager::class.java)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "timelapse:loop")
      .apply { acquire(12 * 60 * 60 * 1000L) }

    job?.cancel()
    job = scope.launch { runLoop(prefs) }
    return START_STICKY                                // le système relance le service s'il le tue
  }

  private suspend fun runLoop(prefs: android.content.SharedPreferences) {
    val host = prefs.getString("host", "")!!
    val port = prefs.getInt("port", 8080)
    val interval = prefs.getInt("intervalMs", 5000)
    val total = prefs.getInt("total", 100)
    val startAt = prefs.getLong("startAt", 0)
    var i = prefs.getInt("taken", 0)

    while (currentCoroutineContext().isActive && i < total) {
      val wait = startAt + i.toLong() * interval - System.currentTimeMillis()
      if (wait > 0) delay(wait)
      // En retard de plus d'un intervalle (service tué un moment) : on saute les photos manquées
      else if (wait < -interval) { i = ((System.currentTimeMillis() - startAt) / interval).toInt() + 1; continue }

      fireShutter(host, port)                          // une erreur ponctuelle n'arrête pas la boucle
      i++
      prefs.edit().putInt("taken", i).apply()
    }
    prefs.edit().putBoolean("running", false).apply()
    stopSelf()
  }

  private fun fireShutter(host: String, port: Int): Boolean {
    return try {
      val url = URL("http://$host:$port/ccapi/ver100/shooting/control/shutterbutton")
      val conn = (wifiNetwork()?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
      conn.requestMethod = "POST"
      conn.connectTimeout = 3000
      conn.readTimeout = 10000
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "application/json")
      conn.outputStream.use { it.write("""{"af":false}""".toByteArray()) }
      val ok = conn.responseCode in 200..299
      conn.disconnect()
      ok
    } catch (e: IOException) { false }
  }

  // Force le trafic sur le Wi-Fi de l'appareil, même s'il n'a pas Internet
  // (sinon Android peut router via la 4G/5G et la caméra devient injoignable)
  @Suppress("DEPRECATION")
  private fun wifiNetwork(): Network? {
    val cm = getSystemService(ConnectivityManager::class.java)
    return cm.allNetworks.firstOrNull {
      cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
  }

  private fun startInForeground() {
    getSystemService(NotificationManager::class.java).createNotificationChannel(
      NotificationChannel("timelapse", "Timelapse", NotificationManager.IMPORTANCE_LOW))
    val notif = NotificationCompat.Builder(this, "timelapse")
      .setContentTitle("Timelapse en cours")
      .setSmallIcon(android.R.drawable.ic_menu_camera)
      .setOngoing(true).build()
    ServiceCompat.startForeground(this, 1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
  }

  override fun onDestroy() {
    job?.cancel(); scope.cancel()
    wakeLock?.let { if (it.isHeld) it.release() }
    super.onDestroy()
  }
}