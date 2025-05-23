package com.evacipated.cardcrawl.mod.haberdashery.ui

import basemod.BaseMod
import basemod.ReflectionHacks
import basemod.abstracts.CustomScreen
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.evacipated.cardcrawl.mod.haberdashery.HaberdasheryMod
import com.evacipated.cardcrawl.mod.haberdashery.extensions.scale
import com.evacipated.cardcrawl.mod.haberdashery.patches.DragRelicToAdjustExcludes
import com.evacipated.cardcrawl.mod.haberdashery.patches.Ftue
import com.evacipated.cardcrawl.mod.haberdashery.patches.NewCursors
import com.evacipated.cardcrawl.mod.haberdashery.util.L10nStrings
import com.evacipated.cardcrawl.modthespire.lib.*
import com.megacrit.cardcrawl.core.AbstractCreature
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import com.megacrit.cardcrawl.dungeons.AbstractDungeon.CurrentScreen
import com.megacrit.cardcrawl.helpers.FontHelper
import com.megacrit.cardcrawl.helpers.ImageMaster
import com.megacrit.cardcrawl.helpers.input.InputHelper
import com.megacrit.cardcrawl.relics.AbstractRelic

class CustomizeAttachmentsScreen : CustomScreen() {
    private var isOpen = false
    private val blackScreenColor = Color(0f, 0f, 0f, 0f)
    private var blackScreenTarget = 0f

    override fun curScreen() = Enum.CUSTOMIZE_ATTACHMENTS

    @Suppress("unused")
    private fun open() {
        if (AbstractDungeon.screen != CurrentScreen.NONE) {
            AbstractDungeon.closeCurrentScreen()
        }
        reopen()
    }

    override fun reopen() {
        isOpen = true
        AbstractDungeon.screen = curScreen()
        AbstractDungeon.isScreenUp = true
        blackScreenTarget = 0.85f
        AbstractDungeon.overlayMenu.cancelButton.show(strings["return"])
    }

    override fun close() {
        isOpen = false
        blackScreenTarget = 0f
        blackScreenColor.a = 0f
        ReflectionHacks.privateStaticMethod(AbstractDungeon::class.java, "genericScreenOverlayReset").invoke<Unit>()
    }

    override fun update() {
        if (blackScreenColor.a != blackScreenTarget) {
            if (blackScreenTarget > blackScreenColor.a) {
                blackScreenColor.a += Gdx.graphics.deltaTime * 2f
                if (blackScreenColor.a > blackScreenTarget) {
                    blackScreenColor.a = blackScreenTarget
                }
            } else {
                blackScreenColor.a -= Gdx.graphics.deltaTime * 2f
                if (blackScreenColor.a < blackScreenTarget) {
                    blackScreenColor.a = blackScreenTarget
                }
            }
        }

        AbstractDungeon.player?.let { player ->
            player.hb.update()
            ReflectionHacks.privateMethod(AbstractCreature::class.java, "updateReticle")
                .invoke<Unit>(player)
        }

        if (InputHelper.mY < (Settings.HEIGHT - 64.scale())
            && !AbstractDungeon.overlayMenu.cancelButton.hovered()) {
            if (InputHelper.isMouseDown) {
                CardCrawlGame.cursor.changeType(NewCursors.Enums.GRAB)
            } else {
                CardCrawlGame.cursor.changeType(NewCursors.Enums.HAND)
            }
        }
    }

    override fun render(sb: SpriteBatch) {
        DisableRenderRelicsPatch.showAll = true
        AbstractDungeon.player?.renderRelics(sb)
        DisableRenderRelicsPatch.showAll = false

        if (blackScreenColor.a != 0f) {
            sb.color = blackScreenColor
            sb.draw(ImageMaster.WHITE_SQUARE_IMG, 0f, 0f, Settings.WIDTH.toFloat(), Settings.HEIGHT.toFloat())
        }
        sb.color = Color.WHITE

        Ftue.fixDoubleAnimation = true
        AbstractDungeon.player?.renderPlayerImage(sb)
        Ftue.fixDoubleAnimation = false

        DisableRenderRelicsPatch.showDraggable = true
        AbstractDungeon.player?.renderRelics(sb)
        DisableRenderRelicsPatch.showDraggable = false

        FontHelper.renderDeckViewTip(sb, strings["tip"], 96.scale(), Settings.CREAM_COLOR)
    }

    override fun openingSettings() {
        isOpen = false
    }

    companion object {
        val ID = HaberdasheryMod.makeID("CustomizeAttachmentsScreen")
        private val strings by lazy { L10nStrings(ID) }

        fun isOpen(): Boolean {
            return (BaseMod.getCustomScreen(Enum.CUSTOMIZE_ATTACHMENTS) as? CustomizeAttachmentsScreen)?.isOpen == true
        }
    }

    object Enum {
        @SpireEnum @JvmStatic lateinit var CUSTOMIZE_ATTACHMENTS: CurrentScreen
    }

    @SpirePatch2(
        clz = AbstractRelic::class,
        method = "renderInTopPanel"
    )
    private object DisableRenderRelicsPatch {
        var showAll = false
        var showDraggable = false

        @JvmStatic
        @SpirePrefixPatch
        fun disableWhenScreenOpen(__instance: AbstractRelic, sb: SpriteBatch): SpireReturn<Void> {
            DragRelicToAdjustExcludes.MoveRelicOnDrag.setRelicPositionToCursor(__instance, sb)

            if (isOpen()) {
                if (showAll) {
                    return SpireReturn.Continue()
                }
                if (showDraggable && DragRelicToAdjustExcludes.canDragRelic(__instance)) {
                    return SpireReturn.Continue()
                }
                return SpireReturn.Return()
            }
            return SpireReturn.Continue()
        }
    }

    @SpirePatches2(
        SpirePatch2(
            clz = AbstractRelic::class,
            method = "updateRelicPopupClick"
        ),
        SpirePatch2(
            clz = AbstractRelic::class,
            method = "renderTip"
        )
    )
    object DisableInspectAndTooltip {
        @JvmStatic
        @SpirePrefixPatch
        fun prefix(): SpireReturn<Void> {
            if (isOpen()) {
                return SpireReturn.Return()
            }
            return SpireReturn.Continue()
        }
    }
}
