package io.github.lucf15.localai.design

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/** Call before super.onCreate. Uses the real first frame, without an artificial startup delay. */
fun Activity.installLocalAiSplash() {
    installSplashScreen().setOnExitAnimationListener { splash ->
        splash.iconView.animate().alpha(0f).setDuration(90).start()
        splash.view.animate().alpha(0f).setDuration(140)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { splash.remove() }
            }).start()
    }
}
