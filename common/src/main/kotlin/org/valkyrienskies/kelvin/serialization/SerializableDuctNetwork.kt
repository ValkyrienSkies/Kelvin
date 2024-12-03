package org.valkyrienskies.kelvin.serialization

import org.valkyrienskies.kelvin.api.DuctEdge
import org.valkyrienskies.kelvin.api.DuctNode
import org.valkyrienskies.kelvin.api.DuctNodePos

data class SerializableDuctNetwork(val nodes: HashMap<DuctNodePos, DuctNode>, val edges: HashSet<DuctEdge>)
