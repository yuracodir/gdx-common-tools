package com.yuracodir.common.tools.resources

import java.io.File

fun File.tree(): List<String> {
    val items = mutableListOf<String>()
    if (isDirectory) {
        listFiles()?.forEach {
            items += it.tree()
        }
    } else {
        items.add(absolutePath)
    }
    return items
}

fun String.escape(): String {
    return replace("\\W".toRegex(), "_")
}

fun String.fixPath(): String {
    return  this.replace("\\", "/")
}