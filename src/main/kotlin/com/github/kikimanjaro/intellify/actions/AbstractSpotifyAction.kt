package com.github.kikimanjaro.intellify.actions

import com.intellij.openapi.actionSystem.AnAction

abstract class AbstractSpotifyAction : AnAction() {
    protected var isActionActive = false

    fun setActive(active: Boolean) {
        this.isActionActive = active
    }
}