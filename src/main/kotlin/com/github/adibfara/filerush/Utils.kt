package com.github.adibfara.filerush

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileTypeManager
import javax.swing.Icon

fun String.isDirectory(): Boolean {
    val lastPathPart = this.substringAfterLast('/', this).substringAfterLast('\\', this)
    if(lastPathPart.contains(".")) return false
    return true
}

fun String.pathIcon(): Icon {
    if (isDirectory()) return AllIcons.Nodes.Folder
    val fileName = substringAfterLast('/', this).substringAfterLast('\\', this)
    return FileTypeManager.getInstance().getFileTypeByFileName(fileName).icon
        ?: AllIcons.FileTypes.Unknown
}