package haberdashery.database

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.google.gson.annotations.SerializedName
import haberdashery.AdjustRelic
import java.nio.file.Path
import kotlin.math.absoluteValue

class AttachInfo(
    val boneName: String
) {
    // for gson initializing
    private constructor() : this("") {}

    @Transient
    var path: Path? = null

    @SerializedName("skeleton")
    val skeletonInfo: SkeletonInfo? = null
    var large: Boolean = false
        private set
    var drawOrderSlotName: String? = null
        private set
    var drawOrderZIndex: Int = 0
        private set
    var hideSlotNames: Array<out String> = emptyArray()
        private set
    var requiredSlotNames: Array<out String> = emptyArray()
        private set
    var exclusionGroup: String? = null
        private set
    var mask: String? = null
        private set
    @Transient
    internal var maskChanged: Boolean = false
        private set
    @Transient
    internal var maskRequiresSave: Boolean = false
        private set

    @Transient
    var scaleX: Float = 1f
        private set
    @Transient
    var scaleY: Float = 1f
        private set
    var flipHorizontal: Boolean = false
        private set
    var flipVertical: Boolean = false
        private set
    @Transient
    var rotation: Float = 0f
        private set
    @Transient
    var position: Vector2 = Vector2()
        private set
    @Transient
    var shear: Vector2 = Vector2()
        private set
    val shearFactor: Vector2
        get() = Vector2(calcShearFactor(shear.x), calcShearFactor(shear.y))

    @SerializedName("scaleX")
    internal var dirtyScaleX: Float = scaleX
        private set
    @SerializedName("scaleY")
    internal var dirtyScaleY: Float = scaleY
        private set
    @SerializedName("rotation")
    internal var dirtyRotation: Float = rotation
        private set(value) {
            field = value % 360f
        }
    @SerializedName("position")
    internal var dirtyPosition: Vector2 = Vector2()
        private set
    @SerializedName("shear")
    internal var dirtyShear: Vector2 = Vector2()
        private set
    internal val dirtyShearFactor: Vector2
        get() = Vector2(calcShearFactor(dirtyShear.x), calcShearFactor(dirtyShear.y))

    internal fun finalize() = apply {
        scaleX = dirtyScaleX
        if (flipHorizontal) {
            scaleX *= -1
        }
        scaleY = dirtyScaleY
        if (flipVertical) {
            scaleY *= -1
        }
        rotation = dirtyRotation
        position.set(dirtyPosition)
        shear.set(dirtyShear)
    }
    internal fun clean() = apply {
        dirtyScaleX = scaleX.absoluteValue
        dirtyScaleY = scaleY.absoluteValue
        dirtyRotation = rotation
        dirtyPosition.set(position)
        dirtyShear.set(shear)
    }

    fun large(large: Boolean) = apply { this.large = large }
    fun hideSlots(vararg names: String) = apply { this.hideSlotNames = names }
    fun mask(mask: String?) = apply {
        this.mask = mask
    }
    fun maskChanged(changed: Boolean) = apply {
        this.maskChanged = changed
    }
    fun maskRequiresSave() = apply { this.maskRequiresSave = true }
    fun maskSaved() = apply {
        this.maskRequiresSave = false
        this.maskChanged = false
    }
    fun drawOrder(slotName: String, zIndex: Int = 0) = apply {
        this.drawOrderSlotName = slotName
        this.drawOrderZIndex = zIndex
    }
    fun scale(scale: Float) = apply { scaleX(scale).scaleY(scale) }
    internal fun relativeScale(scale: Float) = apply { scaleX(this.scaleX * scale).scaleY(this.scaleY * scale) }
    fun scaleX(scale: Float) = apply {
        this.dirtyScaleX = scale
        if (scale < 0) {
            this.dirtyScaleX *= -1
            flipHorizontal(true)
        }
    }
    fun scaleY(scale: Float) = apply {
        this.dirtyScaleY = scale
        if (scale < 0) {
            this.dirtyScaleY *= -1
            flipVertical(true)
        }
    }
    fun flipHorizontal(flip: Boolean) = apply { this.flipHorizontal = flip }
    fun flipVertical(flip: Boolean) = apply { this.flipVertical = flip }
    fun rotation(degrees: Float) = apply { this.dirtyRotation = degrees }
    internal fun relativeRotation(degrees: Float) = apply { this.dirtyRotation = this.rotation + degrees }
    fun position(x: Float, y: Float) = apply {
        this.dirtyPosition.set(x, y)
    }
    internal fun relativePosition(x: Float, y: Float) = apply {
        this.dirtyPosition.set(position.x + x, position.y + y)
    }
    internal fun relativeShear(axis: AdjustRelic.Axis, v: Float) = apply {
        when (axis) {
            AdjustRelic.Axis.X -> dirtyShear.x = shear.x + v
            AdjustRelic.Axis.Y -> dirtyShear.y = shear.y + v
        }
    }

    companion object {
        private fun calcShearFactor(shearDeg: Float): Float {
            val tmp = 90f + shearDeg
            return if (tmp == 0f) {
                0f
            } else {
                MathUtils.cosDeg(tmp) / MathUtils.sinDeg(tmp)
            }
        }
    }

    data class SkeletonInfo(
        val name: String,
        val useRelicAsAtlas: Boolean,
        val animations: Array<String>,
        val onFlash: OnFlashInfo?,
        val speed: Float?,
        val randomStartTime: Boolean,
        val boneTransforms: Array<BoneTransform>?,
    )

    data class BoneTransform(
        val name: String,
        val rotation: Float?,
        val scaleX: Float?,
        val scaleY: Float?,
    )

    data class OnFlashInfo(
        val animation: String,
        val beforeAction: String?,
    )
}
