package org.valkyrienskies.kelvin.api.edges

import org.valkyrienskies.kelvin.api.simulation.EdgeFlowContext
/**
 * Represents an aperture connection in the graph. Aperture connections constrict or expand to control or lock the flow of gas through them.
 *
 * An aperture's radius modifier cannot cause the final radius to be greater than the radius of the edge. If the final radius is 0 or smaller, flow through this connection is blocked.
 */
interface ApertureEdge : IEdgeFlowRadiusModifier {

    override fun modifyFlowRadius(context: EdgeFlowContext, radius: Double): Double =
        (radius + aperture.coerceAtMost(0.0)).coerceAtLeast(0.0)

    var aperture: Double

    fun setTargetAperture(newAperture: Double) {
        if (newAperture > 0) {
            this.aperture = 0.0
        } else {
            this.aperture = newAperture
        }
    }
}