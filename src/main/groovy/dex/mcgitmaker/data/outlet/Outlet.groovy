package dex.mcgitmaker.data.outlet

import groovy.json.JsonParserType
import groovy.json.JsonSlurper

import java.time.ZoneId
import java.time.ZonedDateTime

enum Outlet {
	INSTANCE();

	public McOutletMeta outletDatabase = new McOutletMeta(lastChanged: Date.from(ZonedDateTime.of(2012, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()), versions: [])
	private final def OUTLET_DATABASE = 'https://raw.githubusercontent.com/dexman545/outlet-database/master/mc2fabric.json'

	Outlet() {
		outletDatabase = new JsonSlurper(type: JsonParserType.INDEX_OVERLAY).parse(new URL(OUTLET_DATABASE)) as McOutletMeta
	}
}
