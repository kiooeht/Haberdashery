package com.evacipated.cardcrawl.mod.haberdashery.patches

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.evacipated.cardcrawl.mod.haberdashery.HaberdasheryMod
import com.evacipated.cardcrawl.mod.haberdashery.extensions.inst
import com.evacipated.cardcrawl.mod.haberdashery.ui.CustomCursor
import com.evacipated.cardcrawl.mod.haberdashery.ui.CustomizeAttachmentsScreen
import com.evacipated.cardcrawl.mod.haberdashery.util.CursorLoader
import com.evacipated.cardcrawl.modthespire.lib.*
import com.megacrit.cardcrawl.characters.AbstractPlayer.PlayerClass
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.core.GameCursor
import com.megacrit.cardcrawl.core.GameCursor.CursorType
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import com.megacrit.cardcrawl.helpers.Hitbox
import com.megacrit.cardcrawl.helpers.input.InputHelper
import com.megacrit.cardcrawl.relics.AbstractRelic
import javassist.CtBehavior
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

object NewCursors {
    object Enums {
        @SpireEnum @JvmStatic lateinit var HAND: CursorType
        @SpireEnum @JvmStatic lateinit var GRAB: CursorType
    }

    private val handGeneric = CursorLoader.load(HaberdasheryMod.assetPath("images/cursors/genericHand.json"))
    private val grabGeneric = CursorLoader.load(HaberdasheryMod.assetPath("images/cursors/genericGrab.json"))
    private val handCursors = mutableMapOf<PlayerClass, CustomCursor>()
    private val grabCursors = mutableMapOf<PlayerClass, CustomCursor>()

    fun addCursor(character: PlayerClass, type: CursorType, cursor: CustomCursor) {
        val map = when (type) {
            Enums.HAND -> handCursors
            Enums.GRAB -> grabCursors
            else -> return
        }
        map[character] = cursor
    }

    fun getGeneric(type: CursorType): CustomCursor? {
        return when (type) {
            Enums.HAND -> handGeneric
            Enums.GRAB -> grabGeneric
            else -> null
        }
    }

    @SpirePatch2(
        clz = GameCursor::class,
        method = "render"
    )
    object Render {
        @JvmStatic
        @SpireInsertPatch(
            locator = Locator::class
        )
        fun render(__instance: GameCursor, ___type: CursorType, sb: SpriteBatch) {
            val map = when (___type) {
                Enums.HAND -> handCursors
                Enums.GRAB -> grabCursors
                else -> return
            }
            val cursor = map[AbstractDungeon.player?.chosenClass] ?: getGeneric(___type)
            cursor?.render(sb, InputHelper.mX, InputHelper.mY)
        }

        private class Locator : SpireInsertLocator() {
            override fun Locate(ctBehavior: CtBehavior): IntArray {
                val finalMatcher = Matcher.FieldAccessMatcher(GameCursor::class.java, "type")
                return LineFinder.findInOrder(ctBehavior, finalMatcher)
            }
        }
    }

    @SpirePatch2(
        clz = AbstractRelic::class,
        method = "update"
    )
    object GrabCursor {
        @JvmStatic
        @SpireInstrumentPatch
        fun instrument() = object : ExprEditor() {
            override fun edit(m: MethodCall) {
                if (m.className == GameCursor::class.qualifiedName && m.methodName == "changeType") {
                    m.replace(
                        "\$_ = \$proceed(\$\$);" +
                                "${GrabCursor::changeCursorOnHover.inst}(this);"
                    )
                } else if (m.className == Hitbox::class.qualifiedName && m.methodName == "update") {
                    m.replace(
                        "if (${GrabCursor::updateRelicHitbox.inst}(this)) {" +
                                "\$_ = \$proceed(\$\$);" +
                                "}"
                    )
                }
            }
        }

        @JvmStatic
        @SpirePostfixPatch
        fun postfix() {
            if (DragRelicToAdjustExcludes.grabbedRelic != null) {
                CardCrawlGame.cursor.changeType(Enums.GRAB)
            } else if (DragRelicToAdjustExcludes.droppedTimer > 0f) {
                CardCrawlGame.cursor.changeType(Enums.HAND)
            }
        }

        @JvmStatic
        fun changeCursorOnHover(relic: AbstractRelic) {
            if (DragRelicToAdjustExcludes.canDragRelic(relic)) {
                CardCrawlGame.cursor.changeType(Enums.HAND)
            }
        }

        @JvmStatic
        fun updateRelicHitbox(relic: AbstractRelic): Boolean {
            return !CustomizeAttachmentsScreen.isOpen() || DragRelicToAdjustExcludes.canDragRelic(relic)
        }
    }
}
