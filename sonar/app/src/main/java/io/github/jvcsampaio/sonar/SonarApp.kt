package io.github.jvcsampaio.sonar

import android.app.Application
import android.content.Context

class SonarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.start()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as SonarApp).container
