package com.github.adibfara.filerush

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager

class OpenQuickFileAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val initialPath = currentFile?.parent?.path
            ?.removePrefix(project.basePath ?: "")
            ?.trimStart('/', '\\')
            ?.let { if (it.isNotEmpty()) "$it/" else "" }
            ?: ""
        QuickFilePopup(project, initialPath).show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
