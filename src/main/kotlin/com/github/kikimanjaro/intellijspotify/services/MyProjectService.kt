package com.github.kikimanjaro.intellijspotify.services

import com.intellij.openapi.project.Project
import com.github.kikimanjaro.intellijspotify.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
