package com.github.kikimanjaro.intellify.services

import com.github.kikimanjaro.intellify.MyBundle
import com.intellij.openapi.diagnostic.Logger

class MyApplicationService {

    init {
        Logger.getInstance(MyApplicationService::class.java).info(MyBundle.message("applicationService"))
    }
}
