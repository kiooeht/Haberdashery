package com.evacipated.cardcrawl.mod.haberdashery.devcommands

import basemod.devcommands.ConsoleCommand
import basemod.devcommands.relic.Relic
import com.evacipated.cardcrawl.mod.haberdashery.AdjustRelic
import com.megacrit.cardcrawl.dungeons.AbstractDungeon

class EditCommand : ConsoleCommand() {
    init {
        requiresPlayer = true
        minExtraTokens = 0
        maxExtraTokens = 1
        simpleCheck = true
    }

    override fun execute(tokens: Array<out String>, depth: Int) {
        if (tokens.size <= 2) {
            AdjustRelic.setRelic(null)
            return
        }
        val relicId = Relic.getRelicName(tokens.copyOfRange(2, tokens.size))
        AdjustRelic.setRelic(relicId)
    }

    override fun extraOptions(tokens: Array<out String>?, depth: Int): ArrayList<String> {
        val ret = arrayListOf<String>()
        AbstractDungeon.player.relics
            .map { it.relicId.replace(' ', '_') }
            .toCollection(ret)
        return ret
    }
}
