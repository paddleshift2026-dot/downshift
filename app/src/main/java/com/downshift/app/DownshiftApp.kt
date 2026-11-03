package com.downshift.app

import android.app.Application

class DownshiftApp : Application() {
    /** The one engine every screen shares, made on first use. */
    val host: EngineHost by lazy { EngineHost(this) }
}
