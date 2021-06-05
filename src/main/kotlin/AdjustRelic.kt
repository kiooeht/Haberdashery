package haberdashery

import basemod.DevConsole
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.esotericsoftware.spine.BonePickerSkeletonRendererDebug
import com.esotericsoftware.spine.Skeleton
import com.esotericsoftware.spine.attachments.Attachment
import com.esotericsoftware.spine.attachments.RegionAttachment
import com.megacrit.cardcrawl.core.AbstractCreature
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.core.Settings
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import com.megacrit.cardcrawl.helpers.FontHelper
import com.megacrit.cardcrawl.helpers.input.InputHelper
import haberdashery.database.AttachDatabase
import haberdashery.database.AttachInfo
import haberdashery.extensions.flipY
import haberdashery.extensions.getPrivate
import haberdashery.extensions.scale
import kotlin.math.absoluteValue
import kotlin.math.max

object AdjustRelic {
    private val debugRenderer = ShapeRenderer()
    private val projection
        get() = Gdx.app.applicationListener.getPrivate<OrthographicCamera>("camera", clazz = CardCrawlGame::class.java).combined
    private val skeleton
        get() = AbstractDungeon.player.getPrivate<Skeleton?>("skeleton", clazz = AbstractCreature::class.java)
    private val skeletonStart by lazy {
        Skeleton(skeleton).apply {
            setToSetupPose()
            updateWorldTransform()
        }
    }
    private val srd = BonePickerSkeletonRendererDebug().apply {
        setPremultipliedAlpha(true)
        setBoundingBoxes(false)
        setMeshHull(false)
        setMeshTriangles(false)
        setRegionAttachments(false)
        setScale(Settings.scale)
    }
    private val attachment: Attachment?
        get() {
            val relicSlotName = "${HaberdasheryMod.ID}:${relicId}"
            val slotIndex = skeleton?.findSlotIndex(relicSlotName) ?: return null
            return skeleton?.getAttachment(slotIndex, relicSlotName)
        }

    private var relicId: String? = null
        set(value) {
            field = value
            if (value != null) {
                info = AttachDatabase.getInfo(AbstractDungeon.player.chosenClass, value)
            }
        }
    private var info: AttachInfo? = null

    private var positioning: Vector2? = null
    private var rotating: Vector2? = null
    private var scaling: Float? = null

    var renderBones: Boolean = false

    fun setRelic(relicId: String?) {
        if (relicId == null) {
            this.relicId = relicId
            return
        }

        val player = AbstractDungeon.player ?: return

        val relicSlotName = "${HaberdasheryMod.ID}:${relicId}"
        val skeleton = skeleton ?: return
        if (skeleton.findSlotIndex(relicSlotName) < 0) {
            return
        }

        this.relicId = relicId
    }

    fun update() {
        val relicId = relicId
        val info = info
        if (DevConsole.visible || relicId == null || info == null) {
            return
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            AttachDatabase.save(AbstractDungeon.player.chosenClass)
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) {
            renderBones = !renderBones
        }

        // Reset changes
        if (InputHelper.justClickedRight) {
            info.clean()
            if (positioning != null) {
                attachmentPosition(info)
                positioning = null
            }
            if (rotating != null) {
                attachmentRotation(info)
                rotating = null
            }
            if (scaling != null) {
                attachmentScale(info)
                scaling = null
            }
        }

        // Position
        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            positioning = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        } else if (!Gdx.input.isKeyPressed(Input.Keys.T) && positioning != null) {
            positioning = null
            info.finalize()
        }

        // Rotation
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            rotating = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
                .sub(Settings.WIDTH / 2f, Settings.HEIGHT / 2f)
                .nor()
                .scl(200.scale())
        } else if (!Gdx.input.isKeyPressed(Input.Keys.R) && rotating != null) {
            rotating = null
            info.finalize()
        }

        // Scale
        if (Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            val mouse = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()).sub(Settings.WIDTH / 2f, Settings.HEIGHT / 2f)
            scaling = if (mouse.x.absoluteValue > mouse.y.absoluteValue) {
                mouse.x.absoluteValue
            } else {
                mouse.y.absoluteValue
            }
        } else if (!Gdx.input.isKeyPressed(Input.Keys.S) && scaling != null) {
            scaling = null
            info.finalize()
        }

        // Flip
        if (Gdx.input.isKeyJustPressed(Input.Keys.X)) {
            info.flipHorizontal(!info.flipHorizontal)
            attachmentScale(info)
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
            info.flipVertical(!info.flipVertical)
            attachmentScale(info)
        }
    }

    fun render(sb: SpriteBatch) {
        val relicId = relicId
        val info = info
        if (relicId == null || info == null) {
            return
        }

        if (renderBones) {
            sb.end()
            srd.shapeRenderer.projectionMatrix = projection
            srd.draw(skeleton)
            sb.begin()
        }

        positionWidget(sb, info)
        rotationWidget(sb, info)
        scaleWidget(sb, info)

        FontHelper.renderFontLeftTopAligned(
            sb,
            FontHelper.tipBodyFont,
            "[$relicId]\n" +
                    "Bone: ${info.boneName}\n" +
                    "Draw Order: ${info.drawOrderSlotName} [${info.drawOrderZIndex}]\n" +
                    "Position: ${info.dirtyPosition.x}, ${info.dirtyPosition.y}\n" +
                    "Rotation: ${info.dirtyRotation}\n" +
                    "Scale: ${info.dirtyScaleX}, ${info.dirtyScaleY}\n" +
                    if (srd.hoveredBone != null) {
                        "\nBone Select: ${srd.hoveredBone.data.name}\n"
                    } else { "" },
            30f, Settings.HEIGHT - 300.scale(),
            Color.WHITE
        )
    }

    private fun attachmentPosition(info: AttachInfo) {
        val attachment = attachment
        if (attachment is RegionAttachment) {
            skeletonStart.findBone(info.boneName)?.let { bone ->
                val pos = info.dirtyPosition.cpy().rotate(bone.worldRotationX)
                attachment.x = pos.x
                attachment.y = pos.y
                attachment.updateOffset()
            }
        }
    }

    private fun attachmentRotation(info: AttachInfo) {
        val attachment = attachment
        if (attachment is RegionAttachment) {
            attachment.rotation = info.dirtyRotation
            attachment.updateOffset()
        }
    }

    private fun attachmentScale(info: AttachInfo) {
        val attachment = attachment
        if (attachment is RegionAttachment) {
            attachment.scaleX = info.dirtyScaleX
            if (info.flipHorizontal) {
                attachment.scaleX *= -1
            }
            attachment.scaleY = info.dirtyScaleY
            if (info.flipVertical) {
                attachment.scaleY *= -1
            }
            attachment.updateOffset()
        }
    }

    private fun positionWidget(sb: SpriteBatch, info: AttachInfo) {
        val startPosition = positioning
        if (startPosition != null) {
            sb.end()

            val mouse = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            val diff = mouse.cpy().sub(startPosition).scl(0.1f)
            skeletonStart.findBone(info.boneName)?.let { bone ->
                diff.rotate(bone.worldRotationX).scl(1f, -1f).rotate(-bone.worldRotationX)
            }

            info.relativePosition(diff.x , diff.y)

            if (info.dirtyPosition != info.position) {
                attachmentPosition(info)
            }

            Gdx.gl.glLineWidth(2f)
            debugRenderer.projectionMatrix = projection
            debugRenderer.begin(ShapeRenderer.ShapeType.Line)
            debugRenderer.color = Color.WHITE
            debugRenderer.line(startPosition.cpy().add(-20f, 0f).flipY(), startPosition.cpy().add(20f, 0f).flipY())
            debugRenderer.line(startPosition.cpy().add(0f, -20f).flipY(), startPosition.cpy().add(0f, 20f).flipY())
            debugRenderer.color = Color.RED
            debugRenderer.line(startPosition.cpy().flipY(), mouse.cpy().flipY())
            debugRenderer.line(mouse.cpy().add(-20f, 0f).flipY(), mouse.cpy().add(20f, 0f).flipY())
            debugRenderer.line(mouse.cpy().add(0f, -20f).flipY(), mouse.cpy().add(0f, 20f).flipY())
            debugRenderer.end()
            Gdx.gl.glLineWidth(1f)

            sb.begin()
        }
    }

    private fun rotationWidget(sb: SpriteBatch, info: AttachInfo) {
        val startRotation = rotating
        if (startRotation != null) {
            sb.end()

            val center = Vector2(Settings.WIDTH / 2f, Settings.HEIGHT / 2f)
            val mouse = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()).sub(center)

            val angle = mouse.angle(startRotation)
            info.relativeRotation(angle)

            if (info.dirtyRotation != info.rotation) {
                attachmentRotation(info)
            }

            Gdx.gl.glLineWidth(2f)
            debugRenderer.projectionMatrix = projection
            debugRenderer.begin(ShapeRenderer.ShapeType.Line)
            debugRenderer.color = Color.RED
            debugRenderer.arc(center.x, center.y, 100.scale(), 360f - startRotation.angle(), angle, max(1, (angle.absoluteValue / 10f).toInt()))
            debugRenderer.color = Color.WHITE
            debugRenderer.line(center, startRotation.cpy().add(center).flipY())
            debugRenderer.color = Color.RED
            debugRenderer.line(center, mouse.cpy().add(center).flipY())
            debugRenderer.end()
            Gdx.gl.glLineWidth(1f)

            sb.begin()
        }
    }

    private fun scaleWidget(sb: SpriteBatch, info: AttachInfo) {
        val startScale = scaling
        if (startScale != null) {
            sb.end()

            val center = Vector2(Settings.WIDTH / 2f, Settings.HEIGHT / 2f)
            val mouse = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()).sub(center)
            val longSide = if (mouse.x.absoluteValue > mouse.y.absoluteValue) {
                mouse.x.absoluteValue
            } else {
                mouse.y.absoluteValue
            }

            info.relativeScale(longSide / startScale)

            if (info.dirtyScaleX != info.scaleX) {
                attachmentScale(info)
            }

            Gdx.gl.glLineWidth(2f)
            debugRenderer.projectionMatrix = projection
            debugRenderer.begin(ShapeRenderer.ShapeType.Line)
            debugRenderer.color = Color.WHITE
            debugRenderer.rect(center.x - startScale, center.y - startScale, startScale * 2, startScale * 2)
            debugRenderer.color = Color.RED
            debugRenderer.rect(center.x - longSide, center.y - longSide, longSide * 2, longSide * 2)
            debugRenderer.end()
            Gdx.gl.glLineWidth(1f)

            sb.begin()
        }
    }
}
