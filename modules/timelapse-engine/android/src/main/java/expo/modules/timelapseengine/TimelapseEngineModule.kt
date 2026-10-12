package expo.modules.timelapseengine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record

class Plan : Record {
  @Field val host: String = ""
  @Field val port: Int = 8080
  @Field val intervalMs: Int = 5000
  @Field val totalShots: Int = 100
}

class TimelapseEngineModule : Module() {
  private val ctx: Context get() = appContext.reactContext ?: throw IllegalStateException("No context")

  override fun definition() = ModuleDefinition {
    Name("TimelapseEngine")   // doit correspondre au nom utilisé côté TS

    AsyncFunction("start") { plan: Plan ->
      val intent = Intent(ctx, TimelapseService::class.java)
        .putExtra("host", plan.host).putExtra("port", plan.port)
        .putExtra("intervalMs", plan.intervalMs).putExtra("total", plan.totalShots)
      ContextCompat.startForegroundService(ctx, intent)
    }

    AsyncFunction("stop") {
      ctx.getSharedPreferences("timelapse", Context.MODE_PRIVATE)
        .edit().putBoolean("running", false).apply()
      ctx.stopService(Intent(ctx, TimelapseService::class.java))
    }

    Function("getStatus") {
      val p = ctx.getSharedPreferences("timelapse", Context.MODE_PRIVATE)
      mapOf("running" to p.getBoolean("running", false),
            "taken" to p.getInt("taken", 0),
            "total" to p.getInt("total", 0))
    }
  }
}