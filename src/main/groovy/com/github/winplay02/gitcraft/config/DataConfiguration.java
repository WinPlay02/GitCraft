package com.github.winplay02.gitcraft.config;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.winplay02.gitcraft.config.Configuration.Utils.prim;

public record DataConfiguration(boolean loadIntegratedDatapack,
								boolean loadAssets,
								boolean loadAssetsExtern,
								boolean readableNbt,
								boolean loadDatagenRegistry,
								boolean sortJsonObjects)
	implements Configuration {

	public static final DataConfiguration DEFAULT = new DataConfiguration(
		true,
		true,
		true,
		true,
		true,
		false
	);

	@Override
	public Map<String, JsonElement> serialize() {
		return Map.of(
			"loadIntegratedDatapack", prim(this.loadIntegratedDatapack()),
			"loadAssets", prim(this.loadAssets()),
			"loadAssetsExtern", prim(this.loadAssetsExtern()),
			"readableNbt", prim(this.readableNbt()),
			"loadDatagenRegistry", prim(this.loadDatagenRegistry()),
			"sortJsonObjects", prim(this.sortJsonObjects())
		);
	}

	@Override
	public List<String> generateInfo() {
		List<String> info = new ArrayList<>(List.of(
			String.format("Integrated datapack versioning is: %s", this.loadIntegratedDatapack() ? "enabled" : "disabled"),
			String.format("Asset versioning is: %s", this.loadAssets() ? "enabled" : "disabled"),
			String.format("External asset versioning is: %s", this.loadAssetsExtern() ? (this.loadAssets() ? "enabled" : "implicitely disabled") : "disabled"),
			String.format("Conversion of NBT data is: %s", this.readableNbt() ? "enabled" : "disabled"),
			String.format("Data-generation from registries is: %s", this.loadDatagenRegistry() ? "enabled" : "disabled")
		));
		if (this.sortJsonObjects()) {
			info.add("JSON files (JSON objects) will be sorted in natural order.");
		}
		return info;
	}

	public static DataConfiguration deserialize(Map<String, JsonElement> map) {
		return new DataConfiguration(
			Utils.getBoolean(map, "loadIntegratedDatapack", DEFAULT.loadIntegratedDatapack()),
			Utils.getBoolean(map, "loadAssets", DEFAULT.loadAssets()),
			Utils.getBoolean(map, "loadAssetsExtern", DEFAULT.loadAssetsExtern()),
			Utils.getBoolean(map, "readableNbt", DEFAULT.readableNbt()),
			Utils.getBoolean(map, "loadDatagenRegistry", DEFAULT.loadDatagenRegistry()),
			Utils.getBoolean(map, "sortJsonObjects", DEFAULT.sortJsonObjects())
		);
	}
}
