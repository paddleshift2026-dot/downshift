package com.downshift.app.core.flow

enum class Screen { ONBOARDING, GARAGE, START, DRIVE, SUMMARY }

/**
 * Which screen the app shows (PRD features D and F). The safety note comes first and has to be agreed to. Leaving park
 * opens the drive screen from any parked screen; coming back to park opens the summary, or after a short hop the screen
 * the trip began on.
 */
class ScreenFlow(consented: Boolean) {
    var screen = if (consented) Screen.GARAGE else Screen.ONBOARDING
        private set
    private var tripFrom = Screen.GARAGE

    fun agree() {
        if (screen == Screen.ONBOARDING) screen = Screen.GARAGE
    }

    fun reviewSafety() {
        if (screen == Screen.GARAGE) screen = Screen.ONBOARDING
    }

    fun openStart() {
        if (screen == Screen.GARAGE) screen = Screen.START
    }

    fun back() {
        if (screen == Screen.START || screen == Screen.SUMMARY) screen = Screen.GARAGE
    }

    fun tripStarted() {
        if (screen != Screen.GARAGE && screen != Screen.START && screen != Screen.SUMMARY) return
        tripFrom = if (screen == Screen.START) Screen.START else Screen.GARAGE
        screen = Screen.DRIVE
    }

    fun tripEnded(summary: Boolean) {
        if (screen == Screen.DRIVE) screen = if (summary) Screen.SUMMARY else tripFrom
    }
}
