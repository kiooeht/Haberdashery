package com.evacipated.cardcrawl.mod.haberdashery.patches

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.evacipated.cardcrawl.mod.haberdashery.AttachRelic
import com.evacipated.cardcrawl.mod.haberdashery.HaberdasheryMod
import com.evacipated.cardcrawl.mod.haberdashery.database.AttachDatabase
import com.evacipated.cardcrawl.mod.haberdashery.extensions.chosenExclusions
import com.evacipated.cardcrawl.mod.haberdashery.extensions.scale
import com.evacipated.cardcrawl.mod.haberdashery.ui.CustomizeAttachmentsScreen
import com.evacipated.cardcrawl.modthespire.lib.*
import com.megacrit.cardcrawl.core.AbstractCreature
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.core.GameCursor
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import com.megacrit.cardcrawl.helpers.ShaderHelper
import com.megacrit.cardcrawl.helpers.input.InputHelper
import com.megacrit.cardcrawl.relics.AbstractRelic
import javassist.CtBehavior
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

object DragRelicToAdjustExcludes {
    internal var grabbedRelic: AbstractRelic? = null
    internal var droppedTimer = 0f
    private var grabOffset = Vector2()

    @JvmStatic
    fun canDragRelic(relic: AbstractRelic): Boolean {
        if (!CustomizeAttachmentsScreen.isOpen()) {
            return false
        }
        val player = AbstractDungeon.player ?: return false
        val exclusionGroup = AttachDatabase.getInfo(player.chosenClass, relic.relicId)?.exclusionGroup
            ?: return false

        var count = 0
        for (r in player.relics) {
            val group = AttachDatabase.getInfo(player.chosenClass, r.relicId)?.exclusionGroup
            if (group == exclusionGroup) {
                count++
            }
        }

        return count > 1
    }

    @SpirePatch2(
        clz = CardCrawlGame::class,
        method = "update"
    )
    object DropTimer {
        @JvmStatic
        @SpirePrefixPatch
        fun prefix() {
            if (droppedTimer > 0) {
                droppedTimer -= Gdx.graphics.deltaTime
            }
        }
    }

    @SpirePatch2(
        clz = AbstractRelic::class,
        method = "update"
    )
    object Drag {
        @JvmStatic
        @SpireInsertPatch(
            locator = Locator::class
        )
        fun startDrag(__instance: AbstractRelic) {
            if (InputHelper.justClickedLeft) {
                if (canDragRelic(__instance)) {
                    grabOffset.x = __instance.currentX - InputHelper.mX
                    grabOffset.y = __instance.currentY - InputHelper.mY
                    grabbedRelic = __instance
                }
            }
        }

        @JvmStatic
        @SpirePrefixPatch
        fun stopDrag() {
            val relic = grabbedRelic
            if (relic != null) {
                relic.scale = 1.25f.scale()
                if (!InputHelper.isMouseDown) {
                    droppedTimer = 0.15f
                    if (InputHelper.justReleasedClickLeft && AbstractDungeon.player.hb.hovered) {
                        val info = AttachDatabase.getInfo(AbstractDungeon.player.chosenClass, relic.relicId)
                        if (info?.exclusionGroup != null) {
                            AbstractDungeon.player.chosenExclusions[info.exclusionGroup!!] = HaberdasheryMod.makeID(relic.relicId)
                            AttachRelic.onChange()
                        }
                    }
                    grabbedRelic = null
                }
            }
        }

        private class Locator : SpireInsertLocator() {
            override fun Locate(ctBehavior: CtBehavior): IntArray {
                val finalMatcher = Matcher.MethodCallMatcher(GameCursor::class.java, "changeType")
                return LineFinder.findInOrder(ctBehavior, finalMatcher)
            }
        }
    }

    @SpirePatch2(
        clz = AbstractRelic::class,
        method = "renderInTopPanel"
    )
    object MoveRelicOnDrag {
        private var saveX = 0f
        private var saveY = 0f

        fun setRelicPositionToCursor(__instance: AbstractRelic, sb: SpriteBatch) {
            saveX = __instance.currentX
            saveY = __instance.currentY
            if (__instance == grabbedRelic) {
                AbstractDungeon.player?.renderReticle(sb)

                __instance.currentX = InputHelper.mX.toFloat() + grabOffset.x
                __instance.currentY = InputHelper.mY.toFloat() + grabOffset.y
            }
        }

        @JvmStatic
        @SpirePostfixPatch
        fun resetRelicPosition(__instance: AbstractRelic) {
            __instance.currentX = saveX
            __instance.currentY = saveY
        }
    }

    @SpirePatch2(
        clz = AbstractCreature::class,
        method = "renderReticleCorner",
        paramtypez = [
            SpriteBatch::class,
            Float::class,
            Float::class,
            Boolean::class,
            Boolean::class,
        ]
    )
    object GreyscaleReticle {
        @JvmStatic
        @SpireInstrumentPatch
        fun instrument() = object : ExprEditor() {
            private var count = 0
            override fun edit(m: MethodCall) {
                if (m.methodName == "draw" && ++count == 2) {
                    m.replace(
                        "${GreyscaleReticle::class.qualifiedName}.before(sb);" +
                                "\$_ = \$proceed(\$\$);" +
                                "${GreyscaleReticle::class.qualifiedName}.after(sb);"
                    )
                }
            }
        }

        @JvmStatic
        fun before(sb: SpriteBatch) {
            if (grabbedRelic != null && AbstractDungeon.player?.hb?.hovered == false) {
                ShaderHelper.setShader(sb, ShaderHelper.Shader.GRAYSCALE)
            }
        }

        @JvmStatic
        fun after(sb: SpriteBatch) {
            if (grabbedRelic != null && AbstractDungeon.player?.hb?.hovered == false) {
                ShaderHelper.setShader(sb, ShaderHelper.Shader.DEFAULT)
            }
        }
    }
}
