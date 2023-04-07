package dex.mcgitmaker.data.outlet

import groovy.transform.ToString

@ToString
class McOutletMeta {
    Date lastChanged
    ArrayList<McFabric> versions
}
