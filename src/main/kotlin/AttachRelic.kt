package haberdashery

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.esotericsoftware.spine.*
import com.esotericsoftware.spine.attachments.Attachment
import com.megacrit.cardcrawl.characters.AbstractPlayer
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import com.megacrit.cardcrawl.helpers.ImageMaster
import com.megacrit.cardcrawl.relics.AbstractRelic
import haberdashery.database.AttachDatabase
import haberdashery.database.AttachInfo
import haberdashery.database.MySlotData
import haberdashery.extensions.*
import haberdashery.patches.LoseRelic
import haberdashery.patches.SubSkeleton
import haberdashery.spine.AnimationEventListener
import haberdashery.spine.FSFileHandle
import haberdashery.spine.attachments.MaskedRegionAttachment
import haberdashery.spine.attachments.OffsetSkeletonAttachment
import haberdashery.spine.attachments.RelicAttachmentLoader
import kotlin.io.path.exists

object AttachRelic {
    fun receive(relic: AbstractRelic) {
        if (LoseRelic.losingRelic) return

        val player = AbstractDungeon.player ?: return
        val info = AttachDatabase.getInfo(player.chosenClass, relic.relicId) ?: return

        val relicSlotName = HaberdasheryMod.makeID(relic.relicId)
        // Always wear newly picked up exclusion relics
        info.exclusionGroup?.let { group ->
            player.chosenExclusions[group] = relicSlotName
        }
        val skeleton = player.skeleton ?: return
        skeleton.setFlip(false, false)
        if (skeleton.findSlotIndex(relicSlotName) >= 0) {
            val slot = skeleton.findSlot(relicSlotName)
            (slot.data as MySlotData).visible = true
            updateSlotVisibilities(player, skeleton)
            skeleton.setFlip(player.flipHorizontal, player.flipVertical)
            return
        }

        val bone = skeleton.findBone(info.boneName) ?: return
        val slotClone = Slot(
            MySlotData(
                skeleton.slots.size,
                relicSlotName,
                bone.data,
                info.drawOrderZIndex,
                info.hideSlotNames,
                info.requiredSlotNames,
                info.exclusionGroup,
            ),
            bone
        )
        slotClone.data.blendMode = BlendMode.normal
        skeleton.slots.add(slotClone)

        val drawOrder = skeleton.drawOrder
        var insertIndex = startingDrawOrder(relic.relicId, info, drawOrder, bone)
        if (info.drawOrderSlotName == null) {
            info.drawOrder(drawOrder[insertIndex].data.name, info.drawOrderZIndex)
        }
        ++insertIndex
        for (i in insertIndex until drawOrder.size) {
            val slot = drawOrder[i]
            val data = slot.data
            if (data.name.startsWith(HaberdasheryMod.makeID("")) && data is MySlotData) {
                if (info.drawOrderZIndex >= data.zIndex) {
                    insertIndex = i + 1
                }
            } else {
                break
            }
        }
        drawOrder.insert(insertIndex, slotClone)
        skeleton.drawOrder = drawOrder

        val attachment = makeAttachment(relicSlotName, relic, skeleton, info)
        slotClone.data.attachmentName = attachment.name
        val skin = skeleton.data.defaultSkin
        skin.addAttachment(slotClone.data.index, attachment.name, attachment)

        updateSlotVisibilities(player, skeleton)
        skeleton.setFlip(player.flipHorizontal, player.flipVertical)
    }

    fun lose(relicId: String) {
        val player = AbstractDungeon.player ?: return
        // Don't remove attachment if player still has relic (i.e. they had multiple copies of the relic)
        if (player.relics.any { it.relicId == relicId }) return

        val relicSlotName = HaberdasheryMod.makeID(relicId)
        val skeleton = player.skeleton ?: return
        val slot = skeleton.findSlot(relicSlotName)

        if (slot != null) {
            val data = slot.data
            if (data is MySlotData) {
                data.exclusionGroup?.let { group ->
                    if (player.chosenExclusions[group] == relicSlotName) {
                        player.chosenExclusions.remove(group)
                    }
                }
                data.visible = false
                updateSlotVisibilities(player, skeleton)
            }
        }
    }

    private fun makeAttachment(relicSlotName: String, relic: AbstractRelic, skeleton: Skeleton, info: AttachInfo): Attachment {
        val skeletonStart = Skeleton(skeleton).apply {
            setToSetupPose()
            updateWorldTransform()
        }
        val bone = skeletonStart.findBone(info.boneName)

        info.skeletonInfo?.let { skeletonInfo ->
            val subSkeleton = loadSubSkeleton(relic, info, skeletonInfo, skeleton)
            AbstractDungeon.player.subSkeletons[relic.relicId] = subSkeleton

            return OffsetSkeletonAttachment(relicSlotName).apply {
                this.skeleton = subSkeleton.skeleton
                val pos = info.dirtyPosition.cpy()
                    .scl(1f, -1f)
                    .add(bone.worldX, bone.worldY)
                bone.worldToLocal(pos)
                position.set(pos)
                scaleX = info.scaleX.scale()
                scaleY = info.scaleY.scale()
                rotation = info.rotation
                boneTransforms = skeletonInfo.boneTransforms
            }
        }

        val tex = getTexture(relic, info).asRegion()
        val maskRegion = AttachDatabase.getMaskImg(relic, info)

        return MaskedRegionAttachment(relicSlotName).apply {
            if (maskRegion != null) {
                setMask(maskRegion)
            } else {
                info.mask(null)
            }
            region = tex
            width = tex.regionWidth.toFloat()
            height = tex.regionHeight.toFloat()
            val pos = info.position.cpy().rotate(bone.worldRotationX)
            x = pos.x.scale()
            y = pos.y.scale()
            scaleX = info.scaleX.scale()
            scaleY = info.scaleY.scale()
            rotation = info.rotation
            shearFactor.set(info.shearFactor)
            updateOffset()
        }
    }

    private fun getTexture(relic: AbstractRelic, info: AttachInfo): Texture {
        return if (info.large) {
            ImageMaster.loadImage("images/largeRelics/${relic.imgUrl}")
                ?.asPremultiplyAlpha(true)
        } else {
            null
        } ?: ImageMaster.getRelicImg(relic.relicId).asPremultiplyAlpha(false)
    }

    private fun loadSubSkeleton(relic: AbstractRelic, info: AttachInfo, skeletonInfo: AttachInfo.SkeletonInfo, parentSkeleton: Skeleton): SubSkeleton {
        for (path in AttachDatabase.getPaths(info)) {
            val skeletonDir = path.resolve("skeletons").resolve(skeletonInfo.name)
            val skeletonJsonPath = skeletonDir.resolve("skeleton.json")
            if (!skeletonDir.exists()) continue
            if (!skeletonJsonPath.exists()) continue

            val toDispose: Disposable
            val json = if (skeletonInfo.useRelicAsAtlas) {
                toDispose = getTexture(relic, info)
                SkeletonJson(RelicAttachmentLoader(toDispose))
            } else {
                val atlasFile = FSFileHandle(skeletonDir.resolve("skeleton.atlas"))
                if (!atlasFile.exists()) continue
                toDispose = TextureAtlas(atlasFile).apply {
                    premultiplyAlpha()
                }
                SkeletonJson(toDispose)
            }
            val skeletonData = json.readSkeletonData(FSFileHandle(skeletonJsonPath))
            val subSkeleton = Skeleton(skeletonData)
            subSkeleton.color = Color.WHITE
            val stateData = AnimationStateData(skeletonData)
            val state = AnimationState(stateData)
            skeletonInfo.speed?.let { state.timeScale = it }
            state.addListener(AnimationEventListener(state, parentSkeleton, HaberdasheryMod.makeID(relic.relicId)))
            for ((i, animationName) in skeletonInfo.animations.withIndex()) {
                skeletonData.findAnimation(animationName)?.let { animation ->
                    val e = state.setAnimation(i, animation, true)
                    if (skeletonInfo.randomStartTime) {
                        e.time = e.endTime * MathUtils.random()
                    }
                }
            }

            return SubSkeleton(subSkeleton, state, toDispose)
        }
        throw RuntimeException("Failed to find skeleton: ${skeletonInfo.name}")
    }

    private fun startingDrawOrder(relicId: String, info: AttachInfo, drawOrder: Array<Slot>, bone: Bone): Int {
        val ret = if (info.drawOrderSlotName != null) {
            drawOrder.indexOfFirst { it.data.name == info.drawOrderSlotName }
        } else {
            drawOrder.indexOfLast { it.bone == bone && it.data !is MySlotData }
        }
        if (ret < 0) {
            return 0
        }
        return ret
    }

    private fun updateSlotVisibilities(player: AbstractPlayer, skeleton: Skeleton) {
        val exclusionCount = mutableMapOf<String, MutableList<String>>()
        for (slot in skeleton.slots) {
            val data = slot.data
            // Make all slots visible
            skeleton.setAttachment(data.name, data.attachmentName)
            if (data !is MySlotData) continue

            // Hide non-visible relics (relics player doesn't currently have)
            if (!data.visible) {
                skeleton.setAttachment(data.name, null)
            }

            // Collate exclusion groups
            if (data.exclusionGroup != null && data.visible) {
                exclusionCount.compute(data.exclusionGroup) { _, set ->
                    (set ?: mutableListOf()).apply {
                        add(data.name)
                    }
                }
            }
        }

        // Hide relics based on exclusion group
        for ((exclusionGroup, set) in exclusionCount) {
            val chosen = player.chosenExclusions[exclusionGroup]
            if (chosen != null) {
                for (i in 0 until set.size) {
                    if (set[i] == chosen) continue
                    skeleton.setAttachment(set[i], null)
                }
            } else {
                // Show latest relic if none were specifically chosen
                for (i in 0 until set.size-1) {
                    skeleton.setAttachment(set[i], null)
                }
            }
        }

        // Hide slots requested by visible relics
        for (slot in skeleton.slots) {
            val data = slot.data
            if (data !is MySlotData) continue
            if (!data.visible || slot.attachment == null) continue

            for (slotName in data.hideSlotNames) {
                skeleton.setAttachment(slotName, null)
            }
        }

        // Hide relics if required slot(s) aren't visible
        for (slot in skeleton.slots) {
            val data = slot.data
            if (data !is MySlotData) continue
            if (!data.visible || slot.attachment == null) continue

            var hide = false
            for (slotName in data.requiredSlotNames) {
                if (skeleton.findSlot(slotName)?.attachment == null) {
                    hide = true
                    break
                }
            }
            if (hide) {
                skeleton.setAttachment(data.name, null)
            } else if (data.visible) {
                skeleton.setAttachment(data.name, data.name)
            }
        }
    }

    fun onChange() {
        val player = AbstractDungeon.player ?: return
        val skeleton = player.skeleton ?: return
        updateSlotVisibilities(player, skeleton)
    }
}
