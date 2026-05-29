package com.github.adibfara.filerush

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class QuickFilePopup(private val project: Project, private val initialPath: String = "") : QuickFileView {

    private val inputField = JTextField().apply {
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                SwingUtilities.invokeLater {
                    caretPosition = text.length
                    selectionStart = caretPosition
                    selectionEnd = caretPosition
                }
            }
        })
    }
    private val listModel = DefaultListModel<QuickFileEntry>()
    private val resultList = JBList(listModel)
    private val service = QuickFileService(project, this)
    private var popup: JBPopup? = null

    private fun matchesAction(e: KeyEvent, actionId: String): Boolean {
        val stroke = KeyStroke.getKeyStrokeForEvent(e)
        return KeymapManager.getInstance()?.activeKeymap?.getShortcuts(actionId)
            ?.filterIsInstance<KeyboardShortcut>()
            ?.any { it.firstKeyStroke == stroke && it.secondKeyStroke == null }
            ?: false
    }

    fun show() {
        inputField.font = inputField.font.deriveFont(14f)
        inputField.setFocusTraversalKeysEnabled(false)
        inputField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = service.updateSuggestions(inputField.text)
            override fun removeUpdate(e: DocumentEvent) = service.updateSuggestions(inputField.text)
            override fun changedUpdate(e: DocumentEvent) = service.updateSuggestions(inputField.text)
        })

        inputField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val isCtrl = e.isControlDown
                when {
                    matchesAction(e, IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN) ||
                            (isCtrl && e.keyCode == KeyEvent.VK_N) -> {
                        service.moveSuggestion(+1); e.consume()
                    }

                    matchesAction(e, IdeActions.ACTION_EDITOR_MOVE_CARET_UP) ||
                            (isCtrl && e.keyCode == KeyEvent.VK_P) -> {
                        service.moveSuggestion(-1); e.consume()
                    }

                    e.keyCode == KeyEvent.VK_ENTER -> {
                        service.handleEnter(); e.consume()
                    }

                    e.keyCode == KeyEvent.VK_TAB -> {
                        service.completePath(); e.consume()
                    }

                    e.keyCode == KeyEvent.VK_ESCAPE -> {
                        popup?.cancel(); e.consume()
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
                val displayText = item.displayName ?: item.path
                val label =
                    super.getListCellRendererComponent(list, displayText, index, isSelected, cellHasFocus) as JLabel
                label.icon = item.path.pathIcon()
                val badgeText = when {
                    item.existing -> if (isSelected && item.path != inputField.text) "Tab →" else null
                    item.templateName != null -> null
                    else -> "Enter to create"
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

        val panel = JPanel(BorderLayout(0, 8)).apply {
            preferredSize = Dimension(600, 300)
            border = JBUI.Borders.empty(8)
            add(inputField, BorderLayout.NORTH)
            add(JBScrollPane(resultList), BorderLayout.CENTER)
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, inputField)
            .setTitle("FileRush")
            .setFocusable(true)
            .setRequestFocus(true)
            .setMovable(true)
            .setResizable(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .createPopup()

        if (initialPath.isNotEmpty()) inputField.text = initialPath

        val frame = WindowManager.getInstance().getFrame(project)
        if (frame != null) {
            popup!!.showInCenterOf(frame.rootPane)
        } else {
            popup!!.showInFocusCenter()
        }
    }

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

    override fun close() {
        popup?.closeOk(null)
    }
}
