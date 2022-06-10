package com.github.kikimanjaro.intellify.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import javax.swing.JPanel


class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val webViewPanel = JPanel(BorderLayout())
        val jbcefBrowser = JBCefBrowser()

        jbcefBrowser.loadURL("https://open.spotify.com/") // TODO: don't work like this, create a curstom IHM
        webViewPanel.add(jbcefBrowser.component, BorderLayout.CENTER)

        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(webViewPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}