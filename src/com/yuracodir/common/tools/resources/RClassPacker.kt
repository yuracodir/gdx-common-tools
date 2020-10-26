package com.yuracodir.common.tools.resources

import java.io.File

class RClassPacker(private val configuration: Configuration = Configuration()) {
    class Configuration(
      val packageName: String? = null,
      val sourceResourcesDir: String = "core/res",
      val outputDir: String = "$sourceResourcesDir/../build/generated/resources",
      val providers: List<ResourceProvider> = listOf(
        ResourceLabelSkinIdProvider(),
        ResourceSpriteIdProvider(),
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
        File(configuration.sourceResourcesDir).tree()
            .map {
                it.replace(
                  configuration.sourceResourcesDir.trimEnd(File.pathSeparatorChar) + File.pathSeparator,
                  ""
                )
            }
            .map { File(it) }
            .let { file ->
                providers.map { provider ->
                    provider.getType() to (file.filter { provider.forFile(it) }.let {
                        val values = mutableMapOf<String, String>()
                        it.forEach { file -> values += provider.get(file) }
                        values
                    })
                }.toMap()
            }.map {
                generateResourceClass(it.key, it.value)
            }.joinToString("\n")
            .let {
                val classContent = generateClass(configuration.packageName, it)
                File(configuration.outputDir, "resources.kt")
                    .apply { parentFile.mkdirs() }
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
    fun get(file: File): Map<String, String>
}

class ResourceSpriteIdProvider : ResourceProvider {
    override fun forFile(file: File) = file.extension == "atlas"

    override fun getType() = "sprite"

    override fun get(file: File): Map<String, String> {
        val readLines = file.readLines()
        return readLines.dropLast(1).mapIndexedNotNull { i, text ->
            if (!text.startsWith(" ") && readLines[i + 1].startsWith(" ")) {
                text to text
            } else {
                null
            }
        }.toMap()
    }
}

class ResourceLabelSkinIdProvider : ResourceProvider {
    override fun forFile(file: File) = file.extension == "json"

    override fun getType() = "skin"

    override fun get(file: File): Map<String, String> {
        var labelBegin = false
        return file.readLines().mapNotNull {
            if (!labelBegin) {
                labelBegin = it.startsWith("com.badlogic.gdx.scenes.scene2d.ui.Label\$LabelStyle:")
                return@mapNotNull null
            } else if (it.startsWith("}")) {
                labelBegin = false
            }
            if (labelBegin) {
                "([A-z]+): \\{".toRegex().find(it)?.let {
                  val (_, group) = it.groupValues
                    group to group
                }
            } else {
                null
            }
        }.toMap()
    }
}

class ResourceFileIdProvider(
  private val directory: String,
  private val type: String = directory,
  private val mask: String = ".*"
) : ResourceProvider {
    override fun forFile(file: File) = file.name.matches(".*$mask.*".toRegex())

    override fun getType() = type

    override fun get(file: File): Map<String, String> {
        if (file.parentFile.name == directory) {
            val key = file.nameWithoutExtension.replace("\\W".toRegex(), "_")
            val value = directory + "/" + file.name
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

    override fun get(file: File): Map<String, String> {
        val content = file.readText()
        return "(${type}.*):(.*)".toRegex()
            .findAll(content)
            .mapNotNull { result ->
                result.value.split(":").takeIf { it.size == 2 }?.let {
                  val (key, value) = it
                    key.replace("$type.", "") to if (keyToKey) key else value
                }
            }.toMap()
    }
}