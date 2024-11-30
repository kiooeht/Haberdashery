package haberdashery.util

import com.badlogic.gdx.graphics.g2d.TextureAtlas
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

object Assets {
    val vfx: TextureAtlas by preload("images/vfx/vfx.atlas")

    internal fun preload() {
        Assets::class.declaredMemberProperties.forEach {
            it.isAccessible = true
            val delegate = it.getDelegate(this)
            if (delegate is AssetDelegate<*> && delegate.preload) {
                delegate.preload()
            }
        }
    }
}