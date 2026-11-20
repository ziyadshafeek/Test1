package com.codex.dashrunner

import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.view.*
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 16, 38)
        window.navigationBarColor = Color.rgb(11, 16, 38)
        setContentView(RunnerView(this))
    }
}

/** A lightweight perspective-rendered 3D-style endless runner, drawn with native Canvas. */
private class RunnerView(context: android.content.Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private data class Obstacle(var lane: Int, var distance: Float, val kind: Int)
    private val obstacles = mutableListOf<Obstacle>()
    private var lastFrame = 0L
    private var score = 0f
    private var speed = 360f
    private var lane = 0
    private var shownLane = 0f
    private var jump = 0f
    private var jumpVelocity = 0f
    private var alive = true
    private var started = false
    private var spawnDistance = 500f
    private var downX = 0f
    private var downY = 0f

    private val sky = Color.rgb(11, 16, 38)
    private fun reset() {
        score = 0f; speed = 360f; lane = 0; shownLane = 0f; jump = 0f; jumpVelocity = 0f
        obstacles.clear()
        obstacles.add(Obstacle(Random.nextInt(0, 3) - 1, 620f, Random.nextInt(3)))
        obstacles.add(Obstacle(Random.nextInt(0, 3) - 1, 1030f, Random.nextInt(3)))
        spawnDistance = 1450f
        alive = true; started = true; lastFrame = 0L
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val horizon = h * .32f
        val ground = h * .83f
        c.drawColor(sky)

        // Glowing moon, stars and distant silhouettes.
        p.color = Color.rgb(28, 38, 76); c.drawCircle(w * .78f, h * .16f, h * .105f, p)
        p.color = Color.rgb(119, 225, 255); c.drawCircle(w * .78f, h * .16f, h * .038f, p)
        p.color = Color.WHITE
        for (i in 0..25) {
            val sx = (i * 83f + w * 2 - score * 2.7f % (w * 2)) % w
            val sy = (i * 47f % (horizon * .9f)) + 8f
            c.drawCircle(sx, sy, if (i % 4 == 0) 2.2f else 1.3f, p)
        }
        p.color = Color.rgb(24, 35, 68)
        for (i in 0..8) {
            val x = i * w / 7f - 20f
            val top = horizon - (i * 19f % (h * .09f))
            c.drawRect(x, top, x + w / 9f, horizon + 2f, p)
            p.color = Color.rgb(35, 49, 85)
            for (r in 0..2) c.drawRect(x + 8f + r * 17f, top + 15f, x + 13f + r * 17f, top + 22f, p)
            p.color = Color.rgb(24, 35, 68)
        }

        // Road rendered as a narrowing perspective trapezoid.
        val road = Path().apply {
            moveTo(w * .46f, horizon); lineTo(w * .54f, horizon)
            lineTo(w * .98f, h); lineTo(w * .02f, h); close()
        }
        p.shader = LinearGradient(0f, horizon, 0f, h, Color.rgb(63, 70, 111), Color.rgb(25, 31, 57), Shader.TileMode.CLAMP)
        c.drawPath(road, p); p.shader = null
        p.color = Color.rgb(72, 229, 218); p.strokeWidth = 4f
        c.drawLine(w * .46f, horizon, w * .02f, h, p); c.drawLine(w * .54f, horizon, w * .98f, h, p)
        // Dashed lane dividers move toward the camera to create forward motion.
        for (i in 0..11) {
            val d = ((i * 125f + score * 36f) % 1400f) / 1400f
            val y = horizon + (h - horizon) * d
            val half = w * (.04f + .44f * d)
            val dashH = 3f + 22f * d
            p.color = Color.argb((110 + 120 * d).toInt().coerceIn(0, 230), 174, 204, 235)
            c.drawRoundRect(w / 2f - half / 3f - 2f, y, w / 2f - half / 3f + 2f, y + dashH, 3f, 3f, p)
            c.drawRoundRect(w / 2f + half / 3f - 2f, y, w / 2f + half / 3f + 2f, y + dashH, 3f, 3f, p)
        }

        if (started && alive) {
            val now = System.currentTimeMillis()
            val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(.04f)
            lastFrame = now
            score += dt * 10f
            speed = (360f + score * 2.2f).coerceAtMost(720f)
            shownLane += (lane - shownLane) * (dt * 12f).coerceAtMost(1f)
            if (jump > 0f) { jumpVelocity -= 1900f * dt; jump += jumpVelocity * dt; if (jump <= 0f) { jump = 0f; jumpVelocity = 0f } }
            obstacles.forEach { it.distance -= speed * dt }
            spawnDistance -= speed * dt
            if (spawnDistance <= 0f) {
                obstacles.add(Obstacle(Random.nextInt(0, 3) - 1, 1000f, Random.nextInt(3)))
                spawnDistance = Random.nextInt(440, 680).toFloat()
            }
            if (obstacles.any { it.distance in -14f..35f && it.lane == lane && jump < 35f }) alive = false
            obstacles.removeAll { it.distance < -65f }
        }

        // Distant hazards first, so near objects naturally overlap them.
        obstacles.sortedByDescending { it.distance }.forEach { drawObstacle(c, w, horizon, h, it) }
        drawRunner(c, w, h, shownLane, jump)

        p.color = Color.WHITE; p.textAlign = Paint.Align.RIGHT
        p.typeface = Typeface.create("sans-serif-black", Typeface.BOLD); p.textSize = 38f
        c.drawText(score.toInt().toString(), w - 24f, 58f, p)
        p.textSize = 12f; p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        c.drawText("DISTANCE", w - 24f, 78f, p); p.textAlign = Paint.Align.LEFT

        if (!started || !alive) {
            p.color = Color.argb(205, 8, 13, 32)
            c.drawRoundRect(w * .08f, h * .34f, w * .92f, h * .63f, 28f, 28f, p)
            p.color = Color.rgb(90, 242, 225); p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.create("sans-serif-black", Typeface.BOLD); p.textSize = 31f
            c.drawText(if (!started) "NEON RUNNER" else "WIPEOUT!", w / 2f, h * .425f, p)
            p.color = Color.WHITE; p.typeface = Typeface.create("sans-serif", Typeface.NORMAL); p.textSize = 16f
            c.drawText(if (!started) "Dodge, switch lanes & jump" else "Distance  ${score.toInt()}", w / 2f, h * .49f, p)
            p.typeface = Typeface.create("sans-serif", Typeface.BOLD); p.textSize = 15f
            c.drawText(if (!started) "SWIPE  •  TAP TO JUMP" else "TAP TO RUN AGAIN", w / 2f, h * .555f, p)
            p.textAlign = Paint.Align.LEFT
        } else {
            p.color = Color.argb(190, 230, 243, 255); p.textSize = 13f
            c.drawText("←  SWIPE TO DODGE     TAP TO JUMP  ↑", 18f, h - 24f, p)
            postInvalidateOnAnimation()
        }
    }

    private fun drawObstacle(c: Canvas, w: Float, horizon: Float, h: Float, ob: Obstacle) {
        val t = (1f - (ob.distance / 1050f)).coerceIn(.04f, 1.16f)
        val y = horizon + (h - horizon) * t.coerceAtMost(1f)
        val roadHalf = w * (.04f + .44f * t.coerceAtMost(1f))
        val x = w / 2f + ob.lane * roadHalf * .56f
        val s = .22f + .95f * t
        val bw = 48f * s; val bh = (if (ob.kind == 1) 48f else 62f) * s
        val depth = 13f * s
        val col = when (ob.kind) { 0 -> Color.rgb(255, 74, 111); 1 -> Color.rgb(255, 167, 65); else -> Color.rgb(157, 104, 255) }
        // Top, front and side faces give each hazard a chunky 3D block silhouette.
        val front = Path().apply { moveTo(x - bw / 2, y - bh); lineTo(x + bw / 2, y - bh); lineTo(x + bw / 2, y); lineTo(x - bw / 2, y); close() }
        val top = Path().apply { moveTo(x - bw / 2, y - bh); lineTo(x - bw / 2 + depth, y - bh - depth * .55f); lineTo(x + bw / 2 + depth, y - bh - depth * .55f); lineTo(x + bw / 2, y - bh); close() }
        val side = Path().apply { moveTo(x + bw / 2, y - bh); lineTo(x + bw / 2 + depth, y - bh - depth * .55f); lineTo(x + bw / 2 + depth, y - depth * .55f); lineTo(x + bw / 2, y); close() }
        p.color = col; c.drawPath(front, p)
        p.color = Color.rgb(255, 205, 224); c.drawPath(top, p)
        p.color = Color.rgb(165, 43, 100); c.drawPath(side, p)
        p.color = Color.argb(65, 0, 0, 0); c.drawOval(x - bw * .65f, y - 3f, x + bw * .7f, y + 8f * s, p)
    }

    private fun drawRunner(c: Canvas, w: Float, h: Float, visualLane: Float, jumpHeight: Float) {
        val baseY = h * .84f - jumpHeight.coerceAtLeast(0f)
        val x = w / 2f + visualLane * w * .25f
        val scale = (w / 420f).coerceIn(.78f, 1.22f)
        p.color = Color.argb((100 - jumpHeight / 5f).toInt().coerceIn(15, 100), 0, 0, 0)
        c.drawOval(x - 31f * scale, h * .835f, x + 31f * scale, h * .855f, p)
        c.save(); c.translate(x, baseY); c.scale(scale, scale)
        // Legs, body, arm and helmet highlight, with small motion swing.
        p.color = Color.rgb(36, 48, 83); c.drawRoundRect(-20f, -34f, -7f, 3f, 6f, 6f, p)
        c.drawRoundRect(7f, -34f, 20f, 3f, 6f, 6f, p)
        p.color = Color.rgb(71, 246, 222); c.drawRoundRect(-23f, -75f, 23f, -29f, 14f, 14f, p)
        p.color = Color.rgb(42, 192, 195); c.drawRoundRect(8f, -69f, 23f, -36f, 9f, 9f, p)
        p.color = Color.rgb(255, 192, 117); c.drawCircle(0f, -91f, 18f, p)
        p.color = Color.rgb(28, 38, 70); c.drawRoundRect(1f, -99f, 17f, -88f, 5f, 5f, p)
        p.color = Color.WHITE; c.drawCircle(12f, -94f, 2f, p)
        p.color = Color.rgb(255, 192, 117); c.drawRoundRect(-30f, -69f, -20f, -37f, 5f, 5f, p)
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; return true }
            MotionEvent.ACTION_UP -> {
                if (!started || !alive) { reset(); return true }
                val dx = e.x - downX; val dy = e.y - downY
                if (abs(dx) > 42f && abs(dx) > abs(dy)) lane = (lane + if (dx > 0) 1 else -1).coerceIn(-1, 1)
                else if (dy < -35f || abs(dx) < 30f) { if (jump <= 0f) jumpVelocity = 760f }
                invalidate(); return true
            }
        }
        return true
    }
}
