package com.github.adibfara.filerush

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class QuickFileDialog(private val project: Project) : DialogWrapper(project), QuickFileView {

    private val inputField = JTextField()
    private val listModel = DefaultListModel<QuickFileEntry>()
    private val resultList = JBList(listModel)
    private val service = QuickFileService(project, this)

    init {
        title = "Quick File Creator"
        init()
        service.updateSuggestions("")
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        panel.preferredSize = Dimension(600, 300)
        panel.border = JBUI.Borders.empty(8)

        inputField.font = inputField.font.deriveFont(14f)
        inputField.setFocusTraversalKeysEnabled(false)
        inputField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = service.updateSuggestions(inputField.text)
            override fun removeUpdate(e: DocumentEvent) = service.updateSuggestions(inputField.text)
            override fun changedUpdate(e: DocumentEvent) = service.updateSuggestions(inputField.text)
        })

        inputField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    (e.isControlDown && e.keyCode == KeyEvent.VK_N) || e.keyCode == KeyEvent.VK_DOWN -> {
                        service.moveSuggestion(+1); e.consume()
                    }
                    (e.isControlDown && e.keyCode == KeyEvent.VK_P) || e.keyCode == KeyEvent.VK_UP -> {
                        service.moveSuggestion(-1); e.consume()
                    }
                    e.keyCode == KeyEvent.VK_ENTER -> {
                        service.handleEnter(); e.consume()
                    }
                    e.keyCode == KeyEvent.VK_TAB -> {
                        service.completePath(); e.consume()
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
                val item = value as? QuickFileEntry ?: return super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus
                )
                val label =
                    super.getListCellRendererComponent(list, item.path, index, isSelected, cellHasFocus) as JLabel
                val badgeText = if (item.existing) {
                    if (isSelected && item.path != inputField.text) "Tab" else null
                } else {
                    "Create ->"
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
                if (e.clickCount == 2) service.completePath()
            }
        })

        panel.add(inputField, BorderLayout.NORTH)
        panel.add(JBScrollPane(resultList), BorderLayout.CENTER)

        return panel
    }

    override fun getPreferredFocusedComponent() = inputField

    override fun doOKAction() = service.createOrOpenFile()

    // QuickFileView

    override fun getInputText(): String = inputField.text

    override fun setInputText(text: String) {
        inputField.text = text
        inputField.caretPosition = text.length
    }

    override fun setInputSelection(start: Int, end: Int) {
        inputField.selectionStart = start
        inputField.selectionEnd = end
    }

    override fun showEntries(entries: List<QuickFileEntry>) {
        listModel.clear()
        entries.forEach { listModel.addElement(it) }
    }

    override fun moveSuggestion(index: Int) {
        resultList.selectedIndex = index
        resultList.ensureIndexIsVisible(index)
    }

    override fun openFile(vf: VirtualFile) {
        FileEditorManager.getInstance(project).openFile(vf, true)
    }

    override fun close() = super.doOKAction()

}
