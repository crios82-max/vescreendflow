package com.veplayer.app.vehicle.can

import android.content.Context
import com.veplayer.app.data.VePrefs
import com.veplayer.app.vehicle.VehicleSignals
import com.veplayer.app.vehicle.can.dbc.DbcDatabase
import com.veplayer.app.vehicle.can.dbc.DbcRepository
import com.veplayer.app.vehicle.can.dbc.DbcVehicleMapper

/**
 * Decodes CAN frames via loaded DBC (default: assets/dbc/veplayer_demo.dbc).
 * Legacy IDs 0x100–0x108 are expressed in that demo DBC.
 */
object CanSignalDecoder {
    @Volatile
    private var db: DbcDatabase? = null

    fun ensureLoaded(context: Context) {
        if (db == null) {
            db = DbcRepository.load(context)
        }
    }

    fun reload(context: Context) {
        DbcRepository.invalidate()
        db = DbcRepository.load(context)
    }

    fun database(): DbcDatabase? = db

    fun apply(
        frame: CanFrame,
        base: VehicleSignals,
        sourceTag: String,
        context: Context? = null,
    ): VehicleSignals {
        val database =
            db
                ?: context?.let {
                    ensureLoaded(it)
                    db
                }
        if (database != null) {
            return DbcVehicleMapper.apply(frame, database, base, sourceTag)
        }
        // No context yet — leave unchanged (RealCanAdapter always passes context path via ensureLoaded)
        return base
    }

    fun statusLabel(prefs: VePrefs): String {
        val d = db
        return if (d != null) {
            "DBC ${d.sourceLabel} · ${d.messageCount} msgs · src=${prefs.dbcSource}"
        } else {
            "DBC no cargado · ${prefs.dbcSource}"
        }
    }
}
