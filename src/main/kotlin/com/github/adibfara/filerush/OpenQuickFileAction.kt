package com.github.adibfara.filerush

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class OpenQuickFileAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        QuickFileDialog(project).show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
