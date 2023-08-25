package dex.mcgitmaker.data.outlet

import dex.mcgitmaker.data.McVersion
import groovy.transform.ToString

@ToString
class McOutletMeta {
	Date lastChanged
	ArrayList<McFabric> versions

	Optional<McFabric> getVersion(McVersion version) {
		return Optional.empty();
	}
}
