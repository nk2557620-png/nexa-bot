package kz.nexa.assistant

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.*
import kotlin.random.Random

/** Женский силуэт из плазмы пурпурного/фиалетового цвета (как Siri). При вызове волны плазмы конденсируются в силуэт. */
class NexaView(c: Context) : View(c) {
    var level = 0f
    var paused = false

    private class Particle(
        val x: Float, val y: Float,
        val vx: Float, val vy: Float,
        val life: Float, val maxLife: Float,
        val size: Float
    )

    private val d = resources.displayMetrics.density
    private var summonT = -1L
    private var dismissT = -1L
    private var particles = mutableListOf<Particle>()
    private var lvl = 0f

    private val unit = Path()
    private var sil = Path()
    private var silX = 0f
    private var silY = 0f
    private var sl = 0f
    private var st = 0f
    private var sw = 0f
    private var sh = 0f
    private val random = Random(System.currentTimeMillis())

    // Bitmap для мягкого свечения частиц
    private val particleBitmap: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(32f, 32f, 32f, 
            intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF), 
            null, Shader.TileMode.CLAMP)
        Canvas(it).drawCircle(32f, 32f, 32f, p)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    
    private val plasmaColors = intArrayOf(
        0xFF9D4EDD.toInt(),  // фиолетовый основной
        0xFFC77DFF.toInt(),  // светлый фиолетовый
        0xFF5A189A.toInt(),  // тёмный фиолетовый
        0xFFE0AAFF.toInt(),  // очень светлый фиолетовый
        0xFF3C096C.toInt()   // очень тёмный фиолетовый
    )

    private val colorFilter1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(0xFF9D4EDD.toInt(), PorterDuff.Mode.SRC_IN)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    
    private val colorFilter2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(0xFFC77DFF.toInt(), PorterDuff.Mode.SRC_IN)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    init {
        // Женский силуэт: голова, плечи, грудь, талия, ноги
        val head = Path().apply {
            addOval(RectF(0.42f, 0.05f, 0.58f, 0.18f), Path.Direction.CW)
        }

        val neck = Path().apply {
            moveTo(0.47f, 0.18f)
            lineTo(0.47f, 0.22f)
            lineTo(0.53f, 0.22f)
            lineTo(0.53f, 0.18f)
            close()
        }

        val body = Path().apply {
            moveTo(0.35f, 0.22f)
            quadTo(0.30f, 0.30f, 0.28f, 0.45f)  // левое плечо скругленное
            lineTo(0.30f, 0.65f)  // левый бок
            quadTo(0.32f, 0.70f, 0.40f, 0.72f)  // левая талия
            lineTo(0.40f, 0.98f)  // левая нога
            lineTo(0.45f, 0.98f)
            lineTo(0.45f, 0.72f)
            quadTo(0.48f, 0.70f, 0.50f, 0.70f)  // центр талии
            quadTo(0.52f, 0.70f, 0.55f, 0.72f)
            lineTo(0.55f, 0.98f)  // правая нога
            lineTo(0.60f, 0.98f)
            lineTo(0.60f, 0.72f)
            quadTo(0.68f, 0.70f, 0.70f, 0.65f)  // правый бок
            lineTo(0.72f, 0.45f)
            quadTo(0.70f, 0.30f, 0.65f, 0.22f)  // правое плечо скругленное
            quadTo(0.60f, 0.20f, 0.53f, 0.20f)
            lineTo(0.53f, 0.22f)
            lineTo(0.47f, 0.22f)
            lineTo(0.47f, 0.20f)
            quadTo(0.40f, 0.20f, 0.35f, 0.22f)
            close()
        }

        unit.op(head, body, Path.Op.UNION)
        unit.op(unit, neck, Path.Op.UNION)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        silX = w / 2f
        silY = h - 120 * d

        sh = max(silY - 140 * d, 280 * d)
        sw = sh * 0.48f
        sl = silX - sw / 2
        st = silY - sh

        val m = Matrix().apply { setScale(sw, sh); postTranslate(sl, st) }
        sil = Path()
        unit.transform(m, sil)

        fillPaint.shader = LinearGradient(
            0f, st, 0f, silY,
            intArrayOf(0xFFC77DFF.toInt(), 0xFF9D4EDD.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
    }

    fun summon() {
        particles.clear()
        val startRadius = 80 * d
        val particleCount = 320

        repeat(particleCount) {
            val angle = random.nextFloat() * 2 * PI.toFloat()
            val r = random.nextFloat() * startRadius
            val sx = silX + cos(angle) * r + random.nextFloat() * 30 * d - 15 * d
            val sy = silY + sin(angle) * r + random.nextFloat() * 30 * d - 15 * d

            val targetPoints = listOf(
                Pair(sl + 0.5f * sw, st + 0.15f * sh),  // голова
                Pair(sl + 0.3f * sw, st + 0.35f * sh),  // левое плечо
                Pair(sl + 0.7f * sw, st + 0.35f * sh),  // правое плечо
                Pair(sl + 0.4f * sw, st + 0.65f * sh),  // левый бок
                Pair(sl + 0.6f * sw, st + 0.65f * sh),  // правый бок
                Pair(sl + 0.42f * sw, st + 0.90f * sh), // левая нога
                Pair(sl + 0.58f * sw, st + 0.90f * sh)  // правая нога
            )

            val target = targetPoints[random.nextInt(targetPoints.size)]
            val vx = (target.first - sx) / (800 + random.nextFloat() * 400)
            val vy = (target.second - sy) / (800 + random.nextFloat() * 400)

            particles.add(Particle(sx, sy, vx, vy, 0f, 1.2f, (8 + random.nextFloat() * 16) * d))
        }

        summonT = SystemClock.uptimeMillis()
        dismissT = -1L
    }

    fun dismiss() {
        if (summonT >= 0 && dismissT < 0) dismissT = SystemClock.uptimeMillis()
    }

    private fun ease(x: Float) = x * x * (3 - 2 * x)

    override fun onDraw(cv: Canvas) {
        val now = SystemClock.uptimeMillis()
        lvl += (level - lvl) * 0.15f

        var dismissProgress = 1f
        if (dismissT >= 0) {
            dismissProgress = 1f - ((now - dismissT) / 500f).coerceIn(0f, 1f)
            if (dismissProgress <= 0f) {
                summonT = -1L
                dismissT = -1L
            }
        }

        val t = if (summonT >= 0) (now - summonT) / 1000f else -1f

        // === Обновление и рисование частиц плазмы ===
        if (t >= 0 && t < 2.5f) {
            val activeParticles = particles.filter { it.life < it.maxLife }

            for (particle in activeParticles) {
                particle as Particle
                val newLife = (now - summonT) / 1000f - (random.nextFloat() * 0.3f)
                if (newLife > particle.maxLife) continue

                val progress = newLife / particle.maxLife
                val px = particle.x + particle.vx * (now - summonT)
                val py = particle.y + particle.vy * (now - summonT)

                val alpha = if (progress < 0.7f) {
                    ease(progress / 0.7f) * 200
                } else {
                    (1f - (progress - 0.7f) / 0.3f) * 200
                } * dismissProgress

                val size = particle.size * (1f - 0.3f * progress)

                val colorIdx = random.nextInt(plasmaColors.size)
                val color = plasmaColors[colorIdx]

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                    this.alpha = alpha.toInt().coerceIn(0, 255)
                }

                cv.drawBitmap(
                    particleBitmap,
                    null,
                    RectF(px - size, py - size, px + size, py + size),
                    paint
                )
            }
        }

        // === Постоянрный силуэт в режиме прослушивания ===
        val pulse = 1f + 0.06f * sin(now / 400f) + 0.3f * lvl
        val k = if (paused) 0.4f else 1f

        cv.save()
        cv.translate(silX, silY)
        cv.scale(pulse * 0.95f, pulse * 0.95f)
        cv.translate(-silX, -silY)

        // Основная заливка с градиентом
        fillPaint.alpha = (240 * k * dismissProgress).toInt()
        cv.drawPath(sil, fillPaint)

        // Внешний контур с свечением
        linePaint.strokeWidth = 2.5f * d
        linePaint.color = 0xFFC77DFF.toInt()
        linePaint.alpha = (180 * k * dismissProgress).toInt()
        cv.drawPath(sil, linePaint)

        // Внутренний тонкий контур
        linePaint.strokeWidth = 0.8f * d
        linePaint.color = 0xFFE0AAFF.toInt()
        linePaint.alpha = (120 * k * dismissProgress).toInt()
        cv.drawPath(sil, linePaint)

        cv.restore()

        // === Волнистые полосы плазмы вокруг силуэта ===
        if (k > 0.5f) {
            val waveAmplitude = 15 * d * lvl
            val waveFreq = now / 600f
            
            for (i in 0..4) {
                val offset = i * 1.2f * d
                val alpha = (60 * k * (1f - i * 0.15f)).toInt()
                linePaint.strokeWidth = 1.5f * d
                linePaint.color = plasmaColors[i % plasmaColors.size]
                linePaint.alpha = alpha
                
                cv.save()
                cv.translate(0f, sin(waveFreq + i) * waveAmplitude * 0.5f)
                cv.drawPath(sil, linePaint)
                cv.restore()
            }
        }

        postInvalidateOnAnimation()
    }
}
