package com.yuracodir.common.tools.resources

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.tools.texturepacker.TexturePacker
import com.badlogic.gdx.tools.texturepacker.TexturePackerFileProcessor
import java.io.File

class TexturesPacker(private val configuration: Configuration = Configuration()) {

  class Configuration @JvmOverloads constructor(
    val sourcePath: String = "core/raw",
    val output: String = "core/res/atlas",
    val w: Int = 4096,
    val h: Int = 4096,
    val smooth: Boolean = true
  )

  fun process() {
    TexturePacker.Settings().apply {
      maxWidth = configuration.w
      maxHeight = configuration.h
      val textureFilter = if (configuration.smooth) Texture.TextureFilter.Linear else Texture.TextureFilter.Nearest
      filterMag = textureFilter
      filterMin = textureFilter
    }.let { settings ->
      val output = File(configuration.output, "atlas")
      val source = File(configuration.sourcePath)
      source
        .tree()
        .filter { it.toLowerCase().endsWith(".png") }
        .map { File(it).parentFile }
        .toSet().forEach {
          try {
            val name = it.absolutePath.removePrefix(source.absolutePath)
              .trim(File.separatorChar)
              .replace(File.separatorChar, '_')
            val processor = TexturePackerFileProcessor(settings, name, null)
            processor.setRecursive(false)
            processor.process(it, output)
          } catch (t: Throwable) {
            t.printStackTrace()
          }
        }
    }
  }
}