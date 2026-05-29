package com.github.adibfara.filerush

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.ide.util.PropertiesComponent
import java.io.File
import java.util.concurrent.Future

class QuickFileService(private val project: Project, private val view: QuickFileView) {

    private val projectBasePath = project.basePath ?: ""
    private var entries: List<QuickFileEntry> = emptyList()
    private var selectedIndex: Int = -1
    private var pendingSearch: Future<*>? = null

    companion object {
        private const val RECENT_EXTENSIONS_KEY = "filerush.recent.extensions"
        private const val MAX_RECENT_EXTENSIONS = 10
    }

    private fun getRecentExtensions(): List<String> {
        val stored = PropertiesComponent.getInstance().getValue(RECENT_EXTENSIONS_KEY, "")
        return if (stored.isBlank()) emptyList() else stored.split(",").filter { it.isNotBlank() }
    }

    private fun saveExtension(ext: String) {
        if (ext.isBlank()) return
        val current = getRecentExtensions().toMutableList()
        current.remove(ext)
        current.add(0, ext)
        if (current.size > MAX_RECENT_EXTENSIONS) current.subList(MAX_RECENT_EXTENSIONS, current.size).clear()
        PropertiesComponent.getInstance().setValue(RECENT_EXTENSIONS_KEY, current.joinToString(","))
    }

    private val selectedEntry: QuickFileEntry?
        get() = entries.getOrNull(selectedIndex)

    fun updateSuggestions(text: String) {
        pendingSearch?.cancel(true)
        if (text.isBlank()) {
            setEntries(emptyList())
            return
        }
        if (text.endsWith(".") && !text.endsWith("..")) {
            val recentExts = getRecentExtensions()
            if (recentExts.isNotEmpty()) {
                val extEntries = recentExts.map { ext ->
                    val fileType = FileTypeManager.getInstance().getFileTypeByExtension(ext)
                    val langName = fileType.name.takeIf { it != "UNKNOWN" && it != "PlainText" }
                    QuickFileEntry(
                        path = text + ext,
                        isDirectory = false,
                        existing = false,
                        isExtensionSuggestion = true,
                        languageName = langName,
                        displayName = ".$ext"
                    )
                }
                setEntries(extEntries)
                return
            }
        }
        pendingSearch = ReadAction.nonBlocking<List<QuickFileEntry>> {
            val results = getSuggestions(text)
            if (results.isEmpty()) {
                val file = File(projectBasePath, text)
                if (!file.exists() && !text.isDirectory()) {
                    buildCreateEntries(text)
                } else if (file.isDirectory) {
                    val dirEntry = QuickFileEntry(text, isDirectory = true, existing = true)
                    val children = (file.listFiles() ?: emptyArray()).map { child ->
                        val childPath = text.trimEnd('/', '\\') + "/" + child.name
                        QuickFileEntry(childPath, child.isDirectory, true)
                    }.sortedWith(compareByDescending<QuickFileEntry> { it.isDirectory }.thenBy { it.path })
                    listOf(dirEntry) + children
                } else {
                    listOf(QuickFileEntry(text, file.isDirectory, file.exists()))
                }
            } else {
                results.map { QuickFileEntry(it, File(projectBasePath, it).isDirectory, true) }
            }
        }.finishOnUiThread(com.intellij.openapi.application.ModalityState.any()) { entries ->
            setEntries(entries)
        }.submit(AppExecutorUtil.getAppExecutorService())
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
        if (selected.isExtensionSuggestion) {
            view.setInputText(selected.path)
            updateSuggestions(selected.path)
            return
        }
        if (!selected.existing) {
            if (selected.templateName != null) createOrOpenFile()
            return
        }
        var text = selected.path
        if (selected.isDirectory && !(text.endsWith("/"))) text += "/"
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
        runCatching {
            val entry = selectedEntry
            val path = (entry?.path ?: view.getInputText()).trimEnd('/')
            if (path.isBlank()) return
            val target = File(projectBasePath, path)
            if (!view.getInputText().isDirectory()) {
                target.parentFile?.mkdirs()
                val ext = path.substringAfterLast('.', "")
                if (ext.isNotBlank()) saveExtension(ext)
                val templateName = entry?.templateName
                if (templateName != null) {
                    createFromTemplate(target, templateName)
                } else {
                    if (!target.exists()) target.createNewFile()
                    openVirtualFile(target)
                }
            } else {
                target.mkdirs()
            }
        }
        view.close()
    }

    private fun createFromTemplate(target: File, templateName: String) {
        val templateManager = FileTemplateManager.getInstance(project)
        val template = templateManager.allTemplates.find { it.name == templateName }
            ?: templateManager.internalTemplates.find { it.name == templateName }
            ?: run { target.createNewFile(); openVirtualFile(target); return }

        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                runCatching {
                    val parentVf = LocalFileSystem.getInstance()
                        .refreshAndFindFileByIoFile(target.parentFile ?: return@runCatching)
                        ?: return@runCatching
                    val psiDir = PsiManager.getInstance(project).findDirectory(parentVf)
                        ?: return@runCatching
                    val props = FileTemplateManager.getInstance(project).defaultProperties
                    val psiFile = FileTemplateUtil.createFromTemplate(
                        template, target.nameWithoutExtension, props, psiDir
                    )
                    psiFile.containingFile?.virtualFile?.let {
                        FileEditorManager.getInstance(project).openFile(it, true)
                    }
                }
            }
        }
    }

    private fun openVirtualFile(target: File) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)
            if (vf != null) {
                ApplicationManager.getApplication().invokeLater {
                    FileEditorManager.getInstance(project).openFile(vf, true)
                }
            }
        }
    }

    private fun buildCreateEntries(path: String): List<QuickFileEntry> {
        val extension = path.substringAfterLast('.', "").lowercase()
        val templateManager = FileTemplateManager.getInstance(project)
        val templates = templateManager.internalTemplates
            .filter { it.extension.lowercase() == extension }
        return if (templates.isEmpty()) {
            listOf(QuickFileEntry(path, isDirectory = false, existing = false))
        } else {
            templates.map { QuickFileEntry(path, isDirectory = false, existing = false, templateName = it.name, displayName = it.name) }
        }
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
        FilenameIndex.processAllFileNames({ name ->
            if (matcher.matches(name)) {
                FilenameIndex.getVirtualFilesByName(name, scope).forEach { vf ->
                    results.add(vf.path.removePrefix(projectBasePath).trimStart('/', '\\'))
                }
            }
            results.size < 50
        }, scope, null)
        return results
    }
}
