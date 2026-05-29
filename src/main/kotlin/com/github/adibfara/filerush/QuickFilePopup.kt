package com.github.adibfara.filerush

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class QuickFilePopup(private val project: Project, private val initialPath: String = "") : QuickFileView {

    private val inputField = GhostTextField()
    private val listModel = DefaultListModel<QuickFileEntry>()
    private val resultList = JBList(listModel)
    private val service = QuickFileService(project, this)
    private var popup: JBPopup? = null

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
                    e.keyCode == KeyEvent.VK_DOWN || (isCtrl && e.keyCode == KeyEvent.VK_N) -> {
                        service.moveSuggestion(+1); e.consume()
                    }
                    e.keyCode == KeyEvent.VK_UP || (isCtrl && e.keyCode == KeyEvent.VK_P) -> {
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

        resultList.cellRenderer = QuickFileEntryRenderer()
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
        if (frame != null) popup!!.showInCenterOf(frame.rootPane)
        else popup!!.showInFocusCenter()
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
        updateGhostText(entries.firstOrNull())
    }

    override fun moveSuggestion(index: Int) {
        resultList.selectedIndex = index
        resultList.ensureIndexIsVisible(index)
        updateGhostText(listModel.getElementAt(index))
    }

    override fun openFile(vf: VirtualFile) {
        FileEditorManager.getInstance(project).openFile(vf, true)
    }

    override fun close() {
        popup?.closeOk(null)
    }

    private fun updateGhostText(entry: QuickFileEntry?) {
        val input = inputField.text
        val ghost = if (entry == null) null else computeGhost(input, entry.path)
        inputField.ghostSuffix = ghost
        inputField.repaint()
    }

    private fun computeGhost(input: String, path: String): String? {
        if (input.isEmpty()) return null
        // Direct prefix match on full path
        if (path.startsWith(input, ignoreCase = true)) {
            return path.substring(input.length).takeIf { it.isNotEmpty() }
        }
        // Match last typed segment against the filename
        val inputSegment = input.substringAfterLast('/').substringAfterLast('\\')
        val filename = path.substringAfterLast('/').substringAfterLast('\\')
        if (inputSegment.isNotEmpty() && filename.startsWith(inputSegment, ignoreCase = true)) {
            return filename.substring(inputSegment.length).takeIf { it.isNotEmpty() }
        }
        return null
    }

    private class GhostTextField : JTextField() {
        var ghostSuffix: String? = null

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val ghost = ghostSuffix?.takeIf { it.isNotEmpty() } ?: return
            val g2 = g.create() as Graphics2D
            try {
                val caretRect = modelToView2D(document.length) ?: return
                val fm = getFontMetrics(font)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2.color = UIUtil.getContextHelpForeground()
                g2.font = font
                g2.drawString(ghost, caretRect.x.toFloat(), (caretRect.y + fm.ascent).toFloat())
            } finally {
                g2.dispose()
            }
        }
    }

    private inner class QuickFileEntryRenderer : ListCellRenderer<QuickFileEntry> {
        private val mainLabel = JLabel().apply { isOpaque = false }
        private val hintLabel = JLabel().apply {
            font = font.deriveFont(12f)
            isOpaque = false
            border = JBUI.Borders.emptyRight(4)
        }
        private val row = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = true
            border = JBUI.Borders.empty(1, 4)
            add(mainLabel, BorderLayout.CENTER)
            add(hintLabel, BorderLayout.EAST)
        }

        override fun getListCellRendererComponent(
            list: JList<out QuickFileEntry>, value: QuickFileEntry?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val item = value ?: return row
            val bg = if (isSelected) list.selectionBackground else list.background
            val fg = if (isSelected) list.selectionForeground else list.foreground

            row.background = bg
            mainLabel.text = item.displayName ?: item.path
            mainLabel.icon = item.path.pathIcon()
            mainLabel.foreground = fg

            val hintText = when {
                item.isExtensionSuggestion -> item.languageName
                item.existing -> if (isSelected && item.path != inputField.text) "Tab →" else null
                item.templateName != null -> null
                else -> "Enter to create"
            }
            hintLabel.isVisible = hintText != null
            if (hintText != null) {
                hintLabel.text = hintText
                hintLabel.foreground = if (isSelected)
                    UIUtil.getListSelectionForeground(true).let { Color(it.red, it.green, it.blue, 160) }
                else
                    UIUtil.getContextHelpForeground()
            }
            return row
        }
    }
}
