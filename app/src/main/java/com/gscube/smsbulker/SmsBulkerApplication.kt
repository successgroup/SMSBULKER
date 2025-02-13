package com.gscube.smsbulker

import android.app.Application
import com.gscube.smsbulker.di.AppComponent
import com.gscube.smsbulker.di.DaggerAppComponent
import javax.inject.Inject

class SmsBulkerApplication : Application() {
    lateinit var appComponent: AppComponent
        private set

    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.factory().create(this)
        appComponent.inject(this)
        instance = this
    }

    companion object {
        lateinit var instance: SmsBulkerApplication
            private set
    }
}
