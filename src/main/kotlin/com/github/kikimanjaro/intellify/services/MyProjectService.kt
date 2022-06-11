package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))

    }
}
