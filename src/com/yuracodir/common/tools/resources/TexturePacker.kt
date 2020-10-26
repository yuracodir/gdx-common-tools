package com.yuracodir.common.tools.resources

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.tools.texturepacker.TexturePacker
import java.io.File

class TexturesPacker(private val configuration: Configuration = Configuration()) {
    class Configuration(
        val rawPath: String = "core/raw",
        val output: String = "core/res/atlas",
        val w: Int = 4096,
        val h: Int = 4096,
    )

    fun process() {
        TexturePacker.Settings().apply {
            maxWidth = configuration.w
            maxHeight = configuration.h
            filterMag = Texture.TextureFilter.Linear
            filterMin = Texture.TextureFilter.Linear
        }.let { settings ->
            File(configuration.rawPath)
                .tree()
                .filter { it.toLowerCase().endsWith(".png") }
                .map { File(it).parentFile }
                .toSet().forEach {
                    TexturePacker.process(
                        settings,
                        it.absolutePath,
                        configuration.output,
                        it.nameWithoutExtension
                    )
                }
        }
    }
}