package com.github.adibfara.filerush

import com.intellij.openapi.application.*
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*
import com.intellij.openapi.vfs.*
import com.intellij.psi.codeStyle.*
import com.intellij.psi.search.*
import com.intellij.ui.components.*
import com.intellij.util.ui.*
import java.awt.*
import java.awt.event.*
import java.io.*
import java.util.concurrent.Future
import javax.swing.*
import javax.swing.event.*

private sealed class FileItem() {
    abstract val path: String
    abstract val isDirectory: Boolean

    data class Existing(override val path: String, override val isDirectory: Boolean) : FileItem()
    data class New(override val path: String, override val isDirectory: Boolean) : FileItem()
}

class QuickFileDialog(private val project: Project) : DialogWrapper(project) {

    private val inputField = JTextField()
    private val listModel = DefaultListModel<FileItem>()
    private val resultList = JBList(listModel)
    private val projectBasePath = project.basePath ?: ""
    private var searchJob: Future<*>? = null

    init {
        title = "Quick File Creator"
        init()
        updateSuggestions("")
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.preferredSize = Dimension(600, 300)
        panel.border = JBUI.Borders.empty(8)


        inputField.font = inputField.font.deriveFont(14f)
        inputField.setFocusTraversalKeysEnabled(false)
        inputField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateSuggestions(inputField.text)
            override fun removeUpdate(e: DocumentEvent) = updateSuggestions(inputField.text)
            override fun changedUpdate(e: DocumentEvent) = updateSuggestions(inputField.text)
        })


        inputField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    (e.isControlDown && e.keyCode == KeyEvent.VK_N) || e.keyCode == KeyEvent.VK_DOWN -> {
                        moveSuggestion(+1)
                        e.consume()
                    }

                    (e.isControlDown && e.keyCode == KeyEvent.VK_P) || e.keyCode == KeyEvent.VK_UP -> {
                        moveSuggestion(-1)
                        e.consume()
                    }

                    e.keyCode == KeyEvent.VK_ENTER -> {
                        when (val selected = resultList.selectedValue) {
                            is FileItem.New -> doOKAction()
                            is FileItem.Existing -> {
                                val file = File(projectBasePath, selected.path)
                                ApplicationManager.getApplication().executeOnPooledThread {
                                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
                                    SwingUtilities.invokeLater {
                                        vf?.let { openFile(it) }
                                        super@QuickFileDialog.doOKAction()
                                    }
                                }
                            }

                            null -> Unit
                        }
                        e.consume()
                    }

                    e.keyCode == KeyEvent.VK_TAB -> {
                        completePath()
                        e.consume()
                    }
                }
            }
        })


        resultList.cellRenderer = object : DefaultListCellRenderer() {
            private val badgeLabel = JLabel().apply {
                font = font.deriveFont(10f)
                isOpaque = false
            }
            private val row = JPanel(BorderLayout(6, 0)).apply { isOpaque = true }

            private fun applyBadge(text: String, fg: Color) {
                badgeLabel.text = text
                badgeLabel.foreground = fg
                badgeLabel.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(fg, 1, true),
                    BorderFactory.createEmptyBorder(1, 5, 1, 5)
                )
            }

            override fun getListCellRendererComponent(
                list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val item = value as? FileItem ?: return super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus
                )
                val label =
                    super.getListCellRendererComponent(list, item.path, index, isSelected, cellHasFocus) as JLabel
                val badgeText = when (item) {
                    is FileItem.Existing -> if (isSelected && item.path != inputField.text) "Tab" else null
                    is FileItem.New -> "Create ->"
                }
                if (badgeText == null) return label
                applyBadge(badgeText, label.foreground)
                row.background = label.background
                row.removeAll()
                row.add(label, BorderLayout.CENTER)
                row.add(badgeLabel, BorderLayout.EAST)
                return row
            }
        }
        resultList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) completePath()
            }
        })

        panel.add(inputField, BorderLayout.NORTH)
        panel.add(JBScrollPane(resultList), BorderLayout.CENTER)

        return panel
    }

    override fun getPreferredFocusedComponent() = inputField

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

    private fun updateSuggestions(text: String) {
        searchJob?.cancel(true)
        listModel.clear()
        if (text.isBlank()) return
        searchJob = ApplicationManager.getApplication().executeOnPooledThread {
            val results = getSuggestions(text)
            SwingUtilities.invokeLater {
                listModel.clear()
                if (results.isEmpty()) {
                    val file = File(projectBasePath, text)
                    if (file.exists()) listModel.addElement(FileItem.Existing(text, file.isDirectory))
                    else listModel.addElement(FileItem.New(text, file.isDirectory))
                } else {
                    results.forEach {
                        val file = File(it)
                        listModel.addElement(FileItem.Existing(it, file.isDirectory))
                    }
                }
                if (listModel.size() > 0) resultList.selectedIndex = 0
            }
        }
    }


    private fun moveSuggestion(delta: Int) {
        if (listModel.size() == 0) return
        val next = (resultList.selectedIndex + delta).coerceIn(0, listModel.size() - 1)
        resultList.selectedIndex = next
        resultList.ensureIndexIsVisible(next)
    }


    private fun completePath() {
        val selected = resultList.selectedValue ?: return
        if (selected is FileItem.Existing) {
            var enteredText = selected.path
            if (selected.isDirectory) {
                enteredText += "/"
            }
            inputField.text = enteredText
            inputField.caretPosition = inputField.text.length
            val extensionStart = enteredText.lastIndexOf(".")
            if (!(selected.isDirectory) && extensionStart >= 0) {

                inputField.selectionStart = enteredText.lastIndexOf("/").takeIf { it >= 0 }?.let { it + 1 } ?: 0
                inputField.selectionEnd = extensionStart
            }
            updateSuggestions(inputField.text)
        }
    }


    override fun doOKAction() {
        val path = (resultList.selectedValue?.path ?: inputField.text).trimEnd('/')
        if (path.isBlank()) return

        val target = File(projectBasePath, path)


        if (!inputField.text.endsWith("/")) {
            target.parentFile?.mkdirs()
            if (!target.exists()) target.createNewFile()
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)?.let { vf ->
                openFile(vf)
            }
        } else {

            target.mkdirs()
        }

        super.doOKAction()
    }

    private fun openFile(vf: VirtualFile) {
        FileEditorManager.getInstance(project).openFile(vf, true)
    }


}