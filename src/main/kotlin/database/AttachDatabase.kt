package com.evacipated.cardcrawl.mod.haberdashery.database

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Gdx2DPixmap
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.esotericsoftware.spine.Skeleton
import com.evacipated.cardcrawl.mod.haberdashery.HaberdasheryMod
import com.evacipated.cardcrawl.mod.haberdashery.database.adapters.AnimationInfoTypeAdapterFactory
import com.evacipated.cardcrawl.mod.haberdashery.extensions.asRegion
import com.evacipated.cardcrawl.mod.haberdashery.extensions.getPrivate
import com.evacipated.cardcrawl.mod.haberdashery.extensions.skeleton
import com.evacipated.cardcrawl.mod.haberdashery.spine.attachments.MaskedRegionAttachment
import com.evacipated.cardcrawl.modthespire.Loader
import com.evacipated.cardcrawl.modthespire.ModInfo
import com.evacipated.cardcrawl.modthespire.lib.SpireEnum
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.megacrit.cardcrawl.characters.AbstractPlayer
import com.megacrit.cardcrawl.core.AbstractCreature
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import com.megacrit.cardcrawl.helpers.RelicLibrary
import com.megacrit.cardcrawl.relics.AbstractRelic
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.Raster
import java.io.FileNotFoundException
import java.io.Reader
import java.net.URI
import java.nio.file.*
import java.util.*
import javax.imageio.ImageIO
import kotlin.io.path.*
import kotlin.streams.asSequence

object AttachDatabase {
    private val logger: Logger = LogManager.getLogger(AttachDatabase::class.java)
    private val database: MutableMap<AbstractPlayer.PlayerClass, MutableMap<String, AttachInfo>> = mutableMapOf()
    private val maskTextureCache = mutableMapOf<String, TextureRegion>()
    private val paths: LinkedHashSet<Path> = linkedSetOf()

    private val gsonLoad = GsonBuilder()
        .registerTypeAdapterFactory(AnimationInfoTypeAdapterFactory())
        .create()
    private val gsonSave = GsonBuilder()
        .registerTypeAdapterFactory(AnimationInfoTypeAdapterFactory())
        .setPrettyPrinting()
        .create()

    init {
        load()
    }

    fun test(type: String) {
        val character = AbstractDungeon.player?.chosenClass ?: return
        val ids = mutableSetOf<String>()
        when (type) {
            "all" -> listOf(Enums.SHARED, character)
            "character" -> listOf(character)
            "shared" -> listOf(Enums.SHARED)
            else -> emptyList()
        }.forEach {
            ids.addAll(database.getOrDefault(it, mutableMapOf()).keys)
        }
        ids.forEach { id ->
            RelicLibrary.getRelic(id)
                ?.makeCopy()
                ?.instantObtain(AbstractDungeon.player, AbstractDungeon.player.relics.size, false)
        }
    }

    fun load() {
        database.clear()

        // Load local json
        val localFS = FileSystems.getDefault()
        val localPath = localFS.getPath(HaberdasheryMod.ID)
        if (localPath.exists()) {
            Files.walk(localPath, 1)
                .filter(Files::isRegularFile)
                .forEach {
                    logger.info("Loading ${it.fileName} (LOCAL)")
                    load(it)
                }
        }

        // Load mod jsons
        for (modInfo in Loader.MODINFOS) {
            val fs = getModFileSystem(modInfo) ?: continue
            val path = fs.getPath("/${HaberdasheryMod.ID}")
            if (path.notExists()) continue
            Files.walk(path, 1)
                .filter(Files::isRegularFile)
                .filter { it.fileName?.toString()?.substringAfterLast(".", "") == "json" }
                .forEach {
                    logger.info("Loading ${it.fileName} (MOD:${modInfo.ID})")
                    load(it)
                }
        }
    }

    fun saveAll() {
        save(Enums.SHARED, AbstractDungeon.player?.getPrivate("skeleton", clazz = AbstractCreature::class.java))
        for (player in CardCrawlGame.characterManager.allCharacters) {
            val skeleton = player.skeleton ?: continue
            save(player.chosenClass, skeleton)
        }
    }

    fun save(character: AbstractPlayer.PlayerClass, skeleton: Skeleton?) {
        logger.info("Saving attach info...")
        database[character]?.let {
            val json = gsonSave.toJson(mapOf(character to it))
            val filename = if (character == Enums.SHARED) {
                "shared.json"
            } else {
                "${character.name.lowercase()}.json"
            }
            logger.info(filename)
            val path = Paths.get(HaberdasheryMod.ID, filename)
            Gdx.files.local(path.toString()).writeString(json, false)

            logger.info("Saving masks...")
            it.forEach { (relicId, info) ->
                info.path = path
                if (skeleton == null) return@forEach
                if (info.mask != null && info.maskRequiresSave) {
                    val relicSlotName = HaberdasheryMod.makeID(relicId)
                    val slot = skeleton.findSlot(relicSlotName)
                    (slot.attachment as? MaskedRegionAttachment)?.also { attachment ->
                        val pixmap = attachment.getMask().texture.textureData.consumePixmap()
                        try {
                            val img = BufferedImage(pixmap.width, pixmap.height, BufferedImage.TYPE_BYTE_GRAY)
                            val pixels = pixmap.pixels
                            val bytes = ByteArray(pixels.limit())
                            pixels.position(0)
                            pixels.get(bytes)
                            img.data = Raster.createRaster(img.sampleModel, DataBufferByte(bytes, bytes.size), null)
                            val imgUrl = info.mask!!
                            val filepath = Paths.get(HaberdasheryMod.ID, "masks", imgUrl)
                            filepath.parent.createDirectories()
                            if (ImageIO.write(img, "png", filepath.toFile())) {
                                logger.info("  $relicId: $imgUrl")
                                info.maskSaved()
                                pixels.position(0)
                            } else {
                                logger.warn("  $relicId: Couldn't write png")
                            }
                        } catch (e: Exception) {
                            logger.error("  $relicId: Failed to save mask", e)
                        }
                    }
                } else {
                    // Reset maskChanged and maskRequiresSave
                    info.maskSaved()
                }
            }
        }
    }

    private inline fun <reified T> Gson.fromJson(reader: Reader) =
        fromJson<T>(reader, object : TypeToken<T>() {}.type)

    private fun load(path: Path) {
        val reader = path.reader()
        val data = gsonLoad.fromJson<LinkedHashMap<AbstractPlayer.PlayerClass?, LinkedHashMap<String, AttachInfo>>>(reader)
        reader.close()

        paths.add(path.parent)

        data.forEach { (character, relics) ->
            if (character != null) {
                val state = character(character)
                relics.forEach { (relicId, info) ->
                    info.path = path
                    state.relic(relicId, info)
                }
            }
        }
    }

    fun getInfo(character: AbstractPlayer.PlayerClass, relicID: String): AttachInfo? {
        return database[character]?.get(relicID)
            ?: database[Enums.SHARED]?.get(relicID)
    }

    fun relic(character: AbstractPlayer.PlayerClass, relicID: String, info: AttachInfo) {
        val map = database.getOrPut(character) { mutableMapOf() }
        map.putIfAbsent(relicID, info.finalize())
    }

    fun character(character: AbstractPlayer.PlayerClass): CharacterState {
        return CharacterState(character)
    }

    data class CharacterState(val character: AbstractPlayer.PlayerClass) {
        fun relic(relicID: String, info: AttachInfo): CharacterState {
            relic(character, relicID, info)
            return this
        }
    }

    fun getRelicsDevCommand(character: AbstractPlayer.PlayerClass): Set<String> {
        val ret = (database[Enums.SHARED]?.keys ?: emptySet()) +
                (database[character]?.keys ?: emptySet())
        return ret.map { it.replace(' ', '_') }.toSet()
    }

    fun getPaths(info: AttachInfo?): Iterable<Path> {
        return info?.path?.let { listOf(it.parent) + paths } ?: paths
    }

    fun getMaskImg(relic: AbstractRelic, info: AttachInfo): TextureRegion? {
        val filename = info.mask ?: return null

        if (maskTextureCache.contains(relic.relicId)) {
            return maskTextureCache[relic.relicId]
        }

        try {
            for (path in getPaths(info)) {
                val internal = path.fileSystem.getPath(HaberdasheryMod.ID, "masks", filename)
                val local = Paths.get(HaberdasheryMod.ID, "masks", filename)
                val file = newestFile(internal, local)
                if (!file.exists()) continue

                logger.info("Loading mask $filename (${if (file == local) "LOCAL" else "INTERNAL"})")

                val bytes = file.readBytes()
                val pix2d = Gdx2DPixmap(bytes, 0, bytes.size, 0)
                // Use Texture(Pixmap(...)) instead of Texture(String) because the latter
                // uses FileTextureData, which doesn't let us make changes to the texture/pixmap later
                return Texture(Pixmap(pix2d)).asRegion().also {
                    maskTextureCache[relic.relicId] = it
                }
            }
            throw FileNotFoundException()
        } catch (e: Exception) {
            logger.warn("Failed to load mask", e)
            return null
        }
    }

    fun getModFileSystem(modInfo: ModInfo): FileSystem? {
        val uri = modInfo.jarURL?.toURI()?.let { URI.create("jar:$it") } ?: return null
        return try {
            FileSystems.newFileSystem(uri, emptyMap<String, Any?>())
        } catch (e: FileSystemAlreadyExistsException) {
            FileSystems.getFileSystem(uri)
        } catch (e: Exception) {
            logger.error("Failed to make FileSystem: $uri", e)
            return null
        }
    }

    private fun newestFile(internal: Path, local: Path): Path {
        if (local.notExists()) {
            return internal
        }
        if (internal.notExists()) {
            return local
        }

        val internalTime = internal.getLastModifiedTime().toInstant()
        val localTime = local.getLastModifiedTime().toInstant()

        return if (localTime.isAfter(internalTime)) local else internal
    }

    fun makeRelicMaskFilename(id: String): String {
        // Generate random suffix to reduce possible filename conflicts
        val randChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val suffix = Random().ints(4, 0, randChars.size)
            .asSequence()
            .map(randChars::get)
            .joinToString("")
        // Remove illegal characters from filename
        return id.replace(Regex("""[<>:"/\\|?*]"""), "_") + "_$suffix.png"
    }

    internal object Enums {
        @JvmStatic
        @SpireEnum(name = "HABERDASHERY_SHARED")
        lateinit var SHARED: AbstractPlayer.PlayerClass
    }
}
