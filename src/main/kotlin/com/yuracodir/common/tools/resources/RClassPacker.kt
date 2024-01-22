package com.yuracodir.common.tools.resources

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.ObjectMap
import java.io.File

class RClassPacker(private val configuration: Configuration = Configuration()) {

  class Configuration @JvmOverloads constructor(
    val packageName: String? = null,
    val sourceResourcesDir: String = "core/res",
    val outputDir: String = "$sourceResourcesDir/../build/generated/resources",
    val providers: MutableList<ResourceProvider> = mutableListOf(
      ResourceLabelSkinIdProvider("skin"),
      ResourceSpriteIdProvider("atlas"),
      ResourceFileIdProvider("atlas", mask = ".atlas"),
      ResourceFileIdProvider("sounds", mask = ".ogg"),
      ResourceFileIdProvider("particle", mask = ".part"),
      ResourceFileIdProvider("font", mask = ".fnt"),
      ResourceFileIdProvider("skin", mask = ".json"),
      ResourceFileIdProvider("values", "locale", "^strings"),
      ResourceIdProvider("color"),
      ResourceIdProvider("string", true)
    )
  )

  private val providers = configuration.providers


  fun process() {
    val sourceDirectory = File(configuration.sourceResourcesDir)
    sourceDirectory.tree()
      .map {
        it.replace(
          configuration.sourceResourcesDir.trimEnd(File.pathSeparatorChar) + File.pathSeparator,
          ""
        )
      }
      .map { File(it) }
      .let { file ->
        providers.associate { provider ->
          provider.getType() to (file.filter { provider.forFile(it) }.let {
            val values = mutableMapOf<String, String>()
            it.forEach { file -> values += provider.get(sourceDirectory, file) }
            values
          })
        }
      }.map {
        generateResourceClass(it.key, it.value)
      }.joinToString("\n")
      .let {
        val classContent = generateClass(configuration.packageName, it)
        val file = File(configuration.outputDir, "resources.kt")
        file.apply { parentFile.mkdirs() }
          .writeText(classContent)
      }
  }

  private fun generateClass(packageName: String?, content: String): String {
    var output = if (packageName.isNullOrEmpty()) "" else "package $packageName\n\n"
    output += "class R { \n"
    output += "$content\n"
    output += "}"
    return output
  }

  private fun generateResourceClass(classname: String, values: Map<String, String>): String {
    var output = ""
    output += "  class $classname {\n"
    output += "    companion object {\n"
    values.forEach {
      output += "      const val ${it.key} = \"${it.value.trim()}\"\n"
    }
    output += "    }\n"
    output += "  }\n"
    return output
  }
}

interface ResourceProvider {
  fun forFile(file: File): Boolean
  fun getType(): String
  fun get(source: File, file: File): Map<String, String>
}

class ResourceSpriteIdProvider(private val directory: String) : ResourceProvider {
  override fun forFile(file: File) = file.parentFile.name == directory && file.extension == "atlas"

  override fun getType() = "sprite"

  override fun get(source: File, file: File): Map<String, String> {
    val readLines = file.readLines()
    return readLines.dropLast(1).mapIndexedNotNull { i, text ->
      if (!text.startsWith(" ") && readLines[i + 1].startsWith(" ")) {
        text.escape() to text
      } else {
        null
      }
    }.toMap()
  }
}

class ResourceLabelSkinIdProvider(private val directory: String) : ResourceProvider {
  override fun forFile(file: File) = file.parentFile.name.startsWith(directory) && file.extension == "json"

  override fun getType() = "style"

  @Suppress("UNCHECKED_CAST")
  override fun get(source: File, file: File): Map<String, String> {
    val jsonMap = Json().fromJson(ObjectMap::class.java, file.readText())
    val map = ObjectMap(jsonMap as ObjectMap<String, JsonValue>)

    return jsonMap.keys()
      .filter { it.contains("style", true) }
      .flatMap { map[it] }
      .map { it.name }
      .associateBy { it.escape() }
  }
}

class ResourceFileIdProvider(
  private val directory: String,
  private val type: String = directory,
  private val mask: String = ".*"
) : ResourceProvider {
  override fun forFile(file: File) = file.name.matches(".*$mask.*".toRegex())

  override fun getType() = type

  override fun get(source: File, file: File): Map<String, String> {
    val dir = file.parentFile.absolutePath.removePrefix(source.absolutePath + File.separatorChar)
    if (dir.startsWith(directory)) {
      val key = file.nameWithoutExtension.escape()
      val value = dir.fixPath() + "/" + file.name
      return mapOf(key to value)
    }
    return emptyMap()
  }
}

class ResourceIdProvider(
  private val type: String,
  private val keyToKey: Boolean = false
) : ResourceProvider {
  override fun forFile(file: File) = file.extension == "txt"

  override fun getType() = type

  override fun get(source: File, file: File): Map<String, String> {
    val content = file.readText()
    return "(${type}.*):(.*)".toRegex()
      .findAll(content)
      .mapNotNull { result ->
        result.value.split(":").takeIf { it.size == 2 }?.let {
          val (key, value) = it
          key.replace("$type.", "").escape() to if (keyToKey) key else value
        }
      }.toMap()
  }
}