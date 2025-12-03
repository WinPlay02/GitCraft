package com.github.winplay02.gitcraft.config;

import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.NestsFlavour;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.unpick.UnpickFlavour;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.github.winplay02.gitcraft.config.Configuration.Utils._null;
import static com.github.winplay02.gitcraft.config.Configuration.Utils.array;
import static com.github.winplay02.gitcraft.config.Configuration.Utils.prim;

/**
 * GitCraft Application Configuration
 *
 * @param manifestSource Manifest source for versions
 * @param usedMapping Mappings used to make versions comparable and readable
 * @param fallbackMappings Mappings used if primary mappings are not available (they're tried in the order provided)
 * @param usedUnpickFlavour Unpick information used to make constants more useful
 * @param fallbackUnpickFlavours Unpick information used if primary unpick information is not available (tried in the order provided)
 * @param onlyStableReleases Whether only stable releases should be handled
 * @param onlySnapshots Whether only snapshots should be handled
 * @param skipNonLinear Whether non-linear versions are not handled
 * @param onlyVersion Set of versions that are handled; Order is not important here as there exists a defined partial order on the versions
 * @param minVersion The min version that is handled; Every version greater than this version is also included, as there exists a defined partial order on the versions
 * @param maxVersion The max version that is handled; Every version less than this version is also included, as there exists a defined partial order on the versions
 * @param excludedVersion Versions that should be excluded
 */
public record ApplicationConfiguration(ManifestSource manifestSource,
									   MappingFlavour usedMapping,
									   MappingFlavour[] fallbackMappings,
									   UnpickFlavour usedUnpickFlavour,
									   UnpickFlavour[] fallbackUnpickFlavours,
									   boolean singleSideVersionsOnMainBranch,
									   boolean onlyStableReleases,
									   boolean onlySnapshots,
									   boolean skipNonLinear,
									   String[] onlyVersion,
									   String minVersion,
									   String maxVersion,
									   String[] excludedVersion,
									   int ornitheIntermediaryGeneration,
									   boolean patchLvt,
									   ExceptionsFlavour usedExceptions,
									   SignaturesFlavour usedSignatures,
									   NestsFlavour usedNests,
									   boolean enablePreening) implements Configuration {

	public ApplicationConfiguration {
		/// Validate parameters (and combinations), which are wrong without looking at any context
		if (onlyStableReleases && onlySnapshots) {
			MiscHelper.panic("ERROR: Excluding both stable releases and snapshots would lead to doing nothing");
		}
		if (ornitheIntermediaryGeneration < 1) {
			MiscHelper.panic("ERROR: Ornithe intermediary generation cannot be less than 1");
		}
	}

	public static final ApplicationConfiguration DEFAULT = new ApplicationConfiguration(
		ManifestSource.MOJANG,
		MappingFlavour.MOJMAP,
		new MappingFlavour[0],
		UnpickFlavour.NONE,
		new UnpickFlavour[0],
		true,
		false,
		false,
		false,
		null,
		null,
		null,
		null,
		1,
		false,
		ExceptionsFlavour.NONE,
		SignaturesFlavour.NONE,
		NestsFlavour.NONE,
		false
	);

	@Override
	public Map<String, JsonElement> serialize() {
		return MiscHelper.mergeMaps(
			new HashMap<>(),
			Map.of(
				"manifestSource", prim(this.manifestSource().toString()),
				"usedMapping", prim(this.usedMapping().toString()),
				"fallbackMappings", array(Arrays.stream(this.fallbackMappings()).map(MappingFlavour::toString).toList()),
				"usedUnpickFlavour", prim(this.usedUnpickFlavour().toString()),
				"fallbackUnpickFlavours", array(Arrays.stream(this.fallbackUnpickFlavours()).map(UnpickFlavour::toString).toList()),
				"singleSideVersionsOnMainBranch", prim(this.singleSideVersionsOnMainBranch()),
				"onlyStableReleases", prim(this.onlyStableReleases()),
				"onlySnapshots", prim(this.onlySnapshots()),
				"skipNonLinear", prim(this.skipNonLinear())
			),
			Map.of(
				"onlyVersion", this.onlyVersion() == null ? _null() : array(Arrays.stream(this.onlyVersion()).toList()),
				"minVersion", this.minVersion() == null ? _null() : prim(this.minVersion()),
				"maxVersion", this.maxVersion() == null ? _null() : prim(this.maxVersion()),
				"excludedVersion", this.excludedVersion() == null ? _null() : array(Arrays.stream(this.excludedVersion()).toList()),
				"ornitheIntermediaryGeneration", prim(this.ornitheIntermediaryGeneration()),
				"patchLvt", prim(this.patchLvt()),
				"usedExceptions", prim(this.usedExceptions().toString()),
				"usedSignatures", prim(this.usedSignatures().toString()),
				"usedNests", prim(this.usedNests().toString()),
				"enablePreening", prim(this.enablePreening())
			)
		);
	}

	@Override
	public List<String> generateInfo() {
		List<String> info = new ArrayList<>();
		info.add(String.format("Manifest source used: %s", this.manifestSource()));
		info.add(String.format("Mappings used: %s", this.usedMapping()));
		if (this.fallbackMappings() != null && this.fallbackMappings().length > 0) {
			info.add(String.format("Mappings used as fallback: %s", Arrays.stream(this.fallbackMappings()).map(Object::toString).collect(Collectors.joining(", "))));
		}
		info.add(String.format("Unpick information used: %s (fallback: %s)", this.usedUnpickFlavour(), this.fallbackUnpickFlavours() != null ? Arrays.stream(this.fallbackUnpickFlavours()).map(Object::toString).collect(Collectors.joining(", ")) : "<null>"));
		info.add(String.format("Versions with a missing side (like Beta 1.2_02 or 1.0.1) are %s the main branch.", this.singleSideVersionsOnMainBranch() ? "included on" : "excluded from"));
		String excludedBranches = this.onlyStableReleases() ? " (only stable releases)" : (this.onlySnapshots() ? " (only snapshots)" : "");
		String excludedVersions = this.isAnyVersionExcluded() && this.excludedVersion().length > 0 ? String.format(" (excluding: %s)", String.join(", ", this.excludedVersion())) : "";
		if (this.isOnlyVersion()) {
			info.add(String.format("Versions to decompile: %s%s%s", String.join(", ", this.onlyVersion()), excludedBranches, excludedVersions));
		} else if (this.isMinVersion() && this.isMaxVersion()) {
			info.add(String.format("Versions to decompile: Starting with %s up to %s%s%s", this.minVersion(), this.maxVersion(), excludedBranches, excludedVersions));
		} else if (this.isMinVersion()) {
			info.add(String.format("Versions to decompile: Starting with %s%s%s", this.minVersion(), excludedBranches, excludedVersions));
		} else if (this.isMaxVersion()) {
			info.add(String.format("Versions to decompile: Up to %s%s%s", this.maxVersion(), excludedBranches, excludedVersions));
		} else {
			info.add(String.format("Versions to decompile: all%s%s", excludedBranches, excludedVersions));
		}
		info.add(String.format("Non-Linear version are: %s", this.skipNonLinear() ? "skipped" : "included"));
		info.add(String.format("LVT patching is: %s", this.patchLvt() ? "enabled" : "disabled"));
		info.add(String.format("Exceptions used: %s", this.usedExceptions()));
		info.add(String.format("Signatures used: %s", this.usedSignatures()));
		info.add(String.format("Nests used: %s", this.usedNests()));
		info.add(String.format("Preening is: %s", this.enablePreening() ? "enabled" : "disabled"));
		return info;
	}

	public boolean isOnlyVersion() {
		return this.onlyVersion() != null;
	}

	public boolean isMinVersion() {
		return this.minVersion() != null;
	}

	public boolean isMaxVersion() {
		return this.maxVersion() != null;
	}

	public boolean isAnyVersionExcluded() {
		return this.excludedVersion() != null;
	}

	public Optional<MappingFlavour> getMappingsForMinecraftVersion(OrderedVersion mcVersion) {
		if (this.usedMapping().exists(mcVersion)) {
			return Optional.of(this.usedMapping());
		}
		if (this.fallbackMappings() != null && this.fallbackMappings().length != 0) {
			for (MappingFlavour nextBestFallbackMapping : this.fallbackMappings()) {
				if (nextBestFallbackMapping.exists(mcVersion)) {
					MiscHelper.println("WARNING: %s mappings do not exist for %s. Falling back to %s", this.usedMapping(), mcVersion.launcherFriendlyVersionName(), nextBestFallbackMapping);
					return Optional.of(nextBestFallbackMapping);
				}
			}
			MiscHelper.panic("ERROR: %s mappings do not exist for %s. All fallback options (%s) have been exhausted", this.usedMapping(), mcVersion.launcherFriendlyVersionName(), Arrays.stream(this.fallbackMappings()).map(Object::toString).collect(Collectors.joining(", ")));
		} else {
			MiscHelper.panic("ERROR: %s mappings do not exist for %s. No fallback options were specified", this.usedMapping(), mcVersion.launcherFriendlyVersionName());
		}
		return Optional.empty();
	}

	public Optional<UnpickFlavour> getUnpickForMinecraftVersion(OrderedVersion mcVersion) {
		if (this.usedUnpickFlavour().exists(mcVersion)) {
			return Optional.of(this.usedUnpickFlavour());
		}
		if (this.fallbackUnpickFlavours() != null && this.fallbackUnpickFlavours().length != 0) {
			for (UnpickFlavour nextBestFallbackUnpick : this.fallbackUnpickFlavours()) {
				if (nextBestFallbackUnpick.exists(mcVersion)) {
					MiscHelper.println("WARNING: %s unpick information does not exist for %s. Falling back to %s", this.usedUnpickFlavour(), mcVersion.launcherFriendlyVersionName(), nextBestFallbackUnpick);
					return Optional.of(nextBestFallbackUnpick);
				}
			}
			MiscHelper.panic("ERROR: %s unpick information does not exist for %s. All fallback options (%s) have been exhausted", this.usedUnpickFlavour(), mcVersion.launcherFriendlyVersionName(), Arrays.stream(this.fallbackMappings()).map(Object::toString).collect(Collectors.joining(", ")));
		} else {
			MiscHelper.panic("ERROR: %s unpick information does not exist for %s. No fallback options were specified", this.usedUnpickFlavour(), mcVersion.launcherFriendlyVersionName());
		}
		return Optional.empty();
	}

	public Optional<ExceptionsFlavour> getExceptionsForMinecraftVersion(OrderedVersion mcVersion) {
		if (this.usedExceptions.exists(mcVersion)) {
			return Optional.of(this.usedExceptions);
		} else {
			return Optional.empty();
		}
	}

	public Optional<SignaturesFlavour> getSignaturesForMinecraftVersion(OrderedVersion mcVersion) {
		if (this.usedSignatures.exists(mcVersion)) {
			return Optional.of(this.usedSignatures);
		} else {
			return Optional.empty();
		}
	}

	public Optional<NestsFlavour> getNestsForMinecraftVersion(OrderedVersion mcVersion) {
		if (this.usedNests.exists(mcVersion)) {
			return Optional.of(this.usedNests);
		} else {
			return Optional.empty();
		}
	}

	public static ApplicationConfiguration deserialize(Map<String, JsonElement> map) {
		List<String> onlyVersion = Utils.getStringArray(map, "onlyVersion", null);
		List<String> excludedVersion = Utils.getStringArray(map, "excludedVersion", null);
		return new ApplicationConfiguration(
			ManifestSource.valueOf(Utils.getString(map, "manifestSource", DEFAULT.manifestSource().toString()).toUpperCase(Locale.ROOT)),
			MappingFlavour.valueOf(Utils.getString(map, "usedMapping", DEFAULT.usedMapping().toString()).toUpperCase(Locale.ROOT)),
			Utils.getStringArray(map, "fallbackMappings", List.of()).stream().map(m -> MappingFlavour.valueOf(m.toUpperCase(Locale.ROOT))).toArray(MappingFlavour[]::new),
			UnpickFlavour.valueOf(Utils.getString(map, "usedUnpickFlavour", DEFAULT.usedUnpickFlavour().toString()).toUpperCase(Locale.ROOT)),
			Utils.getStringArray(map, "fallbackUnpickFlavours", List.of()).stream().map(u -> UnpickFlavour.valueOf(u.toUpperCase(Locale.ROOT))).toArray(UnpickFlavour[]::new),
			Utils.getBoolean(map, "singleSideVersionsOnMainBranch", DEFAULT.singleSideVersionsOnMainBranch),
			Utils.getBoolean(map, "onlyStableReleases", DEFAULT.onlyStableReleases()),
			Utils.getBoolean(map, "onlySnapshots", DEFAULT.onlySnapshots()),
			Utils.getBoolean(map, "skipNonLinear", DEFAULT.skipNonLinear()),
			onlyVersion != null ? onlyVersion.toArray(String[]::new) : null,
			Utils.getString(map, "minVersion", DEFAULT.minVersion()),
			Utils.getString(map, "maxVersion", DEFAULT.maxVersion()),
			excludedVersion != null ? excludedVersion.toArray(String[]::new) : null,
			Utils.getInt(map, "ornitheIntermediaryGeneration", DEFAULT.ornitheIntermediaryGeneration()),
			Utils.getBoolean(map, "patchLvt", DEFAULT.patchLvt()),
			ExceptionsFlavour.valueOf(Utils.getString(map, "usedExceptions", DEFAULT.usedExceptions().toString()).toUpperCase(Locale.ROOT)),
			SignaturesFlavour.valueOf(Utils.getString(map, "usedSignatures", DEFAULT.usedSignatures().toString()).toUpperCase(Locale.ROOT)),
			NestsFlavour.valueOf(Utils.getString(map, "usedNests", DEFAULT.usedNests().toString()).toUpperCase(Locale.ROOT)),
			Utils.getBoolean(map, "enablePreening", DEFAULT.enablePreening())
		);
	}
}
