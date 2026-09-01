package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.MyBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        Logger.getInstance(MyProjectService::class.java).info(MyBundle.message("projectService", project.name))
    }
}
