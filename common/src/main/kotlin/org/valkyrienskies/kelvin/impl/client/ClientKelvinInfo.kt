package org.valkyrienskies.kelvin.impl.client

import org.valkyrienskies.kelvin.api.DuctNodePos
import org.valkyrienskies.kelvin.impl.DuctNodeInfo

data class ClientKelvinInfo(val nodes: HashMap<DuctNodePos, DuctNodeInfo>) {
}
