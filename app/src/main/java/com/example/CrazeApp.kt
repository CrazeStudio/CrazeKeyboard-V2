package com.example
 
import android.app.Application
import android.content.Context
 
class CrazeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
 
    companion object {
        private var instance: CrazeApp? = null
 
        fun getAppContext(): Context {
            return instance!!.applicationContext
        }
    }
}
