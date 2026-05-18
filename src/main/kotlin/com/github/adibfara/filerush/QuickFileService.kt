package com.github.adibfara.filerush

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

class QuickFileService(private val project: Project, private val view: QuickFileView) {

    private val projectBasePath = project.basePath ?: ""
    private var entries: List<QuickFileEntry> = emptyList()
    private var selectedIndex: Int = -1

    private val selectedEntry: QuickFileEntry?
        get() = entries.getOrNull(selectedIndex)

    fun updateSuggestions(text: String) {
        if (text.isBlank()) {
            setEntries(emptyList())
            return
        }
        val results = getSuggestions(text)
        setEntries(if (results.isEmpty()) {
            val file = File(projectBasePath, text)
            listOf(QuickFileEntry(text, file.isDirectory, file.exists()))
        } else {
            results.map { QuickFileEntry(it, File(it).isDirectory, true) }
        })
    }

    fun moveSuggestion(delta: Int) {
        if (entries.isEmpty()) return
        selectedIndex = (selectedIndex + delta).coerceIn(0, entries.size - 1)
        view.moveSuggestion(selectedIndex)
    }

    fun handleEnter() {
        val selected = selectedEntry
        if (selected == null || !selected.existing) {
            createOrOpenFile()
        } else {
            val file = File(projectBasePath, selected.path)
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
            vf?.let { view.openFile(it) }
            view.close()
        }
    }

    fun completePath() {
        val selected = selectedEntry ?: return
        if (!selected.existing) return
        var text = selected.path
        if (selected.isDirectory) text += "/"
        view.setInputText(text)
        if (!selected.isDirectory) {
            val extensionStart = text.lastIndexOf(".")
            if (extensionStart >= 0) {
                val nameStart = text.lastIndexOf("/").takeIf { it >= 0 }?.let { it + 1 } ?: 0
                view.setInputSelection(nameStart, extensionStart)
            }
        }
        updateSuggestions(text)
    }

    fun createOrOpenFile() {
        val path = (selectedEntry?.path ?: view.getInputText()).trimEnd('/')
        if (path.isBlank()) return
        val target = File(projectBasePath, path)
        if (!view.getInputText().endsWith("/")) {
            target.parentFile?.mkdirs()
            if (!target.exists()) target.createNewFile()
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)?.let { vf ->
                FileEditorManager.getInstance(project).openFile(vf, true)
            }
        } else {
            target.mkdirs()
        }
        view.close()
    }

    private fun setEntries(list: List<QuickFileEntry>) {
        entries = list
        selectedIndex = if (list.isNotEmpty()) 0 else -1
        view.showEntries(list)
        if (selectedIndex >= 0) view.moveSuggestion(selectedIndex)
    }

    private fun getSuggestions(pattern: String): List<String> {
        val matcher = NameUtil.buildMatcher("*$pattern").build()
        val scope = GlobalSearchScope.projectScope(project)
        val results = mutableListOf<String>()
        ReadAction.run<Exception> {
            FilenameIndex.processAllFileNames({ name ->
                if (matcher.matches(name)) {
                    FilenameIndex.getVirtualFilesByName(name, scope).forEach { vf ->
                        results.add(vf.path.removePrefix(projectBasePath).trimStart('/', '\\'))
                    }
                }
                results.size < 50
            }, scope, null)
        }
        return results
    }
}
