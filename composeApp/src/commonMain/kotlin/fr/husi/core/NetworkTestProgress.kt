package fr.husi.core

import fr.husi.proto.daemon.NetworkQualityTestProgress
import fr.husi.proto.daemon.STUNTestProgress

enum class StunPhase {
    Binding,
    NatMapping,
    NatFiltering,
    Done,
    ;

    companion object {
        fun ofWire(wireValue: Int): StunPhase = entries.getOrElse(wireValue) { Binding }
    }
}

enum class NatBehaviour {
    Unknown,
    EndpointIndependent,
    AddressDependent,
    AddressAndPortDependent,
    ;

    companion object {
        private const val MAPPING_ENDPOINT_INDEPENDENT = 2
        private const val MAPPING_ADDRESS_DEPENDENT = 3
        private const val MAPPING_ADDRESS_AND_PORT_DEPENDENT = 4

        private const val FILTERING_ENDPOINT_INDEPENDENT = 1
        private const val FILTERING_ADDRESS_DEPENDENT = 2
        private const val FILTERING_ADDRESS_AND_PORT_DEPENDENT = 3

        fun ofMapping(wireValue: Int): NatBehaviour = when (wireValue) {
            MAPPING_ENDPOINT_INDEPENDENT -> EndpointIndependent
            MAPPING_ADDRESS_DEPENDENT -> AddressDependent
            MAPPING_ADDRESS_AND_PORT_DEPENDENT -> AddressAndPortDependent
            else -> Unknown
        }

        fun ofFiltering(wireValue: Int): NatBehaviour = when (wireValue) {
            FILTERING_ENDPOINT_INDEPENDENT -> EndpointIndependent
            FILTERING_ADDRESS_DEPENDENT -> AddressDependent
            FILTERING_ADDRESS_AND_PORT_DEPENDENT -> AddressAndPortDependent
            else -> Unknown
        }
    }
}

enum class NetworkQualityPhase {
    Idle,
    Download,
    Upload,
    Done,
    ;

    companion object {
        fun ofWire(wireValue: Int): NetworkQualityPhase = entries.getOrElse(wireValue) { Idle }
    }
}

val STUNTestProgress.failure: String?
    get() = error.takeIf { isFinal && it.isNotEmpty() }

val NetworkQualityTestProgress.failure: String?
    get() = error.takeIf { isFinal && it.isNotEmpty() }
