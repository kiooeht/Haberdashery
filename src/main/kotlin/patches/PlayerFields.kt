package haberdashery.patches

import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.esotericsoftware.spine.AnimationState
import com.esotericsoftware.spine.Skeleton
import com.evacipated.cardcrawl.modthespire.lib.SpireField
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2
import com.megacrit.cardcrawl.characters.AbstractPlayer

@SpirePatch2(
    clz = AbstractPlayer::class,
    method = SpirePatch.CLASS,
)
object PlayerFields {
    @JvmField
    val subSkeletons: SpireField<MutableList<SubSkeleton>> = SpireField { mutableListOf() }
}

val AbstractPlayer.subSkeletons: MutableList<SubSkeleton>
    get() = PlayerFields.subSkeletons.get(this)

data class SubSkeleton(
    val skeleton: Skeleton,
    val anim: AnimationState,
    val atlas: TextureAtlas,
)
