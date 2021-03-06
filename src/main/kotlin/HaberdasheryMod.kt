package haberdashery

import basemod.BaseMod
import basemod.ModPanel
import basemod.devcommands.ConsoleCommand
import basemod.interfaces.PostInitializeSubscriber
import basemod.interfaces.PostRenderSubscriber
import basemod.interfaces.PreUpdateSubscriber
import basemod.interfaces.RelicGetSubscriber
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer
import com.megacrit.cardcrawl.helpers.ImageMaster
import com.megacrit.cardcrawl.relics.AbstractRelic
import haberdashery.devcommands.HaberdasheryCommand

@SpireInitializer
class HaberdasheryMod :
    PostInitializeSubscriber,
    RelicGetSubscriber,
    PreUpdateSubscriber,
    PostRenderSubscriber
{
    companion object Statics {
        val ID: String = "haberdashery"
        val NAME: String = "Haberdashery"

        @Suppress("unused")
        @JvmStatic
        fun initialize() {
            BaseMod.subscribe(HaberdasheryMod())
        }

        fun makeID(id: String) = "$ID:$id"
        fun assetPath(path: String) = "${ID}Assets/$path"
    }

    override fun receivePostInitialize() {
        val settingsPanel = ModPanel()

        BaseMod.registerModBadge(
            ImageMaster.loadImage(assetPath("images/modBadge.png")),
            NAME,
            "kiooeht",
            "TODO",
            settingsPanel
        )

        ConsoleCommand.addCommand("haberdashery", HaberdasheryCommand::class.java)
    }

    override fun receiveRelicGet(relic: AbstractRelic?) {
        if (relic != null) {
            AttachRelic.receive(relic)
        }
    }

    override fun receivePreUpdate() {
        AdjustRelic.update()
    }

    override fun receivePostRender(sb: SpriteBatch) {
        AdjustRelic.render(sb)
    }
}
