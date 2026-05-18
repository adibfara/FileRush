package com.github.adibfara.filerush

import com.intellij.openapi.vfs.VirtualFile

interface QuickFileView {
    fun getInputText(): String
    fun setInputText(text: String)
    fun setInputSelection(start: Int, end: Int)
    fun showEntries(entries: List<QuickFileEntry>)
    fun moveSuggestion(index: Int)
    fun openFile(vf: VirtualFile)
    fun close()
}
