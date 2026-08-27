package com.veplayer.app.vehicle

import android.util.Log
import android.content.Context
import com.veplayer.app.data.VePrefs
import com.veplayer.app.vehicle.can.RealCanAdapter
import kotlinx.coroutines.CoroutineScope

/**
 * @deprecated Prefer [RealCanAdapter]. Kept as thin alias for older call sites.
 */
class CanBusStubAdapter(
    context: Context,
    prefs: VePrefs,
    scope: CoroutineScope,
) : VehicleSignalAdapter by RealCanAdapter(context, prefs, scope) {
    init {
        Log.i("CanBusStub", "delegating to RealCanAdapter")
    }
}
