package com.cliplist.scan

class FakeAudioProbe(private val results: Map<String, ProbeResult>) : AudioProbe {
    val probed = mutableListOf<String>()
    override fun probe(node: VolumeNode): ProbeResult {
        probed.add(node.name)
        return results[node.name] ?: ProbeResult(0, false)
    }
}
