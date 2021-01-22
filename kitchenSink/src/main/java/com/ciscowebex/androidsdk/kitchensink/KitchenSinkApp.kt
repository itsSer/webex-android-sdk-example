package com.ciscowebex.androidsdk.kitchensink

import android.app.Application
import android.content.Context
import com.ciscowebex.androidsdk.kitchensink.auth.LoginActivity
import com.ciscowebex.androidsdk.kitchensink.auth.loginModule
import com.ciscowebex.androidsdk.kitchensink.calling.callModule
import com.ciscowebex.androidsdk.kitchensink.messaging.messagingModule
import com.ciscowebex.androidsdk.kitchensink.messaging.search.searchPeopleModule
import com.ciscowebex.androidsdk.kitchensink.person.personModule
import com.ciscowebex.androidsdk.kitchensink.search.searchModule
import com.ciscowebex.androidsdk.kitchensink.webhooks.webhooksModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.unloadKoinModules

class KitchenSinkApp : Application() {

    companion object {
        lateinit var instance: KitchenSinkApp
            private set

        fun applicationContext(): Context {
            return instance.applicationContext
        }

        fun get(): KitchenSinkApp {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@KitchenSinkApp)
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        instance = this
    }

    fun closeApplication() {
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    fun loadKoinModules(type: LoginActivity.LoginType) {
        if (type == LoginActivity.LoginType.JWT) {
            loadKoinModules(listOf(mainAppModule, webexModule, loginModule, JWTWebexModule, searchModule, callModule, messagingModule, personModule, searchPeopleModule, webhooksModule))
        }
        else {
            loadKoinModules(listOf(mainAppModule, webexModule, loginModule, OAuthWebexModule, searchModule, callModule, messagingModule, personModule, searchPeopleModule, webhooksModule))
        }
    }

    fun unloadKoinModules() {
        unloadKoinModules(listOf(mainAppModule, webexModule, loginModule, JWTWebexModule, OAuthWebexModule, searchModule, callModule, messagingModule, personModule, searchPeopleModule, webhooksModule))
    }
}