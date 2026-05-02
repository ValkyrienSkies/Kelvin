package org.valkyrienskies.kelvin.impl.client.particle

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.particle.TextureSheetParticle
import net.minecraft.core.particles.SimpleParticleType
import org.valkyrienskies.kelvin.KelvinMod
import kotlin.math.max
import kotlin.math.sqrt

class DefaultGasParticle(
    level: ClientLevel,
    x: Double,
    y: Double,
    z: Double,
    xSpeed: Double,
    ySpeed: Double,
    zSpeed: Double,
    private val spriteSet: SpriteSet,
    gasDensity: Float
) : TextureSheetParticle(level, x, y, z, xSpeed, ySpeed, zSpeed) {

    private val baseSize: Float
    private val buoyancyDelta: Double

    init {
        gravity = 0f
        friction = 0.94f

        val speedMag = sqrt(xSpeed * xSpeed + ySpeed * ySpeed + zSpeed * zSpeed)
        val jitter = max(speedMag * VELOCITY_JITTER, MIN_JITTER)
        xd = xSpeed + (random.nextDouble() - 0.5) * 2.0 * jitter
        yd = ySpeed + (random.nextDouble() - 0.5) * 2.0 * jitter
        zd = zSpeed + (random.nextDouble() - 0.5) * 2.0 * jitter

        baseSize = quadSize * START_SIZE_FACTOR
        quadSize = baseSize
        // Dimensionless: 0 for air, positive for lighter-than-air, negative for heavier.
        buoyancyDelta = (AIR_DENSITY - gasDensity).toDouble() / AIR_DENSITY
    }

    override fun tick() {
        super.tick()
        try {
            // Buoyancy is gated by current speed: at high velocity inertia dominates,
            // so a thruster jet doesn't visibly U-turn upward when it stalls.
            val speedSq = xd * xd + yd * yd + zd * zd
            val buoyancyFactor = 1.0 / (1.0 + BUOYANCY_GATING_K * speedSq)
            yd += (buoyancyDelta * BUOYANCY_BASE + THERMAL_LIFT) * buoyancyFactor

            val lifeFrac = age.toFloat() / lifetime.toFloat()
            quadSize = baseSize * (1f + lifeFrac * SIZE_GROWTH)
            alpha = if (lifeFrac < FADE_OUT_START) 1f
            else ((1f - lifeFrac) / (1f - FADE_OUT_START)).coerceAtLeast(0f)

            setSpriteFromAge(spriteSet)
        } catch (e: Exception) {
            KelvinMod.KELVINLOGGER.error("Error in tick in DefaultGasParticle. Is a gas particle missing assets? Error: $e")
        }
    }

    override fun getRenderType(): ParticleRenderType {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
    }

    class DefaultGasParticleType(val density: Float = AIR_DENSITY) : SimpleParticleType(false)

    companion object {
        // STP density of air in kg/m^3; matches Kelvin's "air" GasType.
        const val AIR_DENSITY = 1.293f
        private const val BUOYANCY_BASE = 0.02
        // Constant upward bias applied to all gases — represents thermal lift / turbulence
        // so even neutral- or heavy-density exhausts visually rise a little.
        private const val THERMAL_LIFT = 0.0015
        private const val BUOYANCY_GATING_K = 200.0
        private const val START_SIZE_FACTOR = 0.4f
        private const val SIZE_GROWTH = 4f
        private const val FADE_OUT_START = 0.60f
        private const val VELOCITY_JITTER = 0.15
        private const val MIN_JITTER = 0.005
    }
}
