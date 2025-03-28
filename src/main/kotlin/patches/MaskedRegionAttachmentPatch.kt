package com.evacipated.cardcrawl.mod.haberdashery.patches

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.esotericsoftware.spine.Skeleton
import com.esotericsoftware.spine.SkeletonMeshRenderer
import com.esotericsoftware.spine.attachments.Attachment
import com.evacipated.cardcrawl.mod.haberdashery.HaberdasheryMod
import com.evacipated.cardcrawl.mod.haberdashery.extensions.bind
import com.evacipated.cardcrawl.mod.haberdashery.spine.attachments.MaskedRegionAttachment
import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

@SpirePatch2(
    clz = SkeletonMeshRenderer::class,
    method = "draw",
    paramtypez = [
        PolygonSpriteBatch::class,
        Skeleton::class,
    ]
)
object MaskedRegionAttachmentPatch {
    private val shader: ShaderProgram by lazy {
        ShaderProgram(
            Gdx.files.internal(HaberdasheryMod.assetPath("shaders/mask.vert")),
            Gdx.files.internal(HaberdasheryMod.assetPath("shaders/mask.frag"))
        ).apply {
            if (!isCompiled) {
                throw RuntimeException(log)
            }
        }
    }

    @Suppress("unused")
    @JvmStatic
    fun drawMaskStart(batch: PolygonSpriteBatch, attachment: Attachment) {
        if (attachment is MaskedRegionAttachment && attachment.hasMask()) {
            batch.flush()
            batch.shader = shader
            val mask = attachment.getMask()
            shader.bind("u_mask", 1, mask.texture)
        }
    }

    @Suppress("unused")
    @JvmStatic
    fun drawMaskEnd(batch: PolygonSpriteBatch, attachment: Attachment) {
        if (attachment is MaskedRegionAttachment && attachment.hasMask()) {
            batch.flush()
            batch.shader = null
        }
    }

    @JvmStatic
    @SpireInstrumentPatch
    fun instrument(): ExprEditor = object : ExprEditor() {
        override fun edit(m: MethodCall) {
            if (m.className == PolygonSpriteBatch::class.qualifiedName && m.methodName == "draw") {
                m.replace(
                    "${MaskedRegionAttachmentPatch::class.qualifiedName}.drawMaskStart(batch, attachment);" +
                            "\$_ = \$proceed(\$\$);" +
                            "${MaskedRegionAttachmentPatch::class.qualifiedName}.drawMaskEnd(batch, attachment);"
                )
            }
        }
    }
}
