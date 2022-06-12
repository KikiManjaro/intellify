package com.github.kikimanjaro.intellify

import com.intellij.openapi.actionSystem.AnAction

abstract class AbstractSpotifyCommandAction : AnAction() {
    protected var isActionActive = false

    fun setActive(active: Boolean) {
        this.isActionActive = active
    }
}