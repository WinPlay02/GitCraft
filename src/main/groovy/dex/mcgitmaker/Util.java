package dex.mcgitmaker;

import com.github.winplay02.MiscHelper;
import com.github.winplay02.SerializationHelper;
import dex.mcgitmaker.data.McVersion;
import dex.mcgitmaker.data.outlet.McFabric;
import dex.mcgitmaker.data.outlet.Outlet;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Util {
	public enum MappingsNamespace {
		OFFICIAL,
		MOJMAP;

		@Override
		public String toString() {
			return name().toLowerCase(Locale.ENGLISH);
		}
	}

	public static void saveMetadata(Map<String, McVersion> data) throws IOException {
		SerializationHelper.writeAllToPath(GitCraft.METADATA_STORE, SerializationHelper.serialize(data));
	}

	static String fixupSemver(String proposedSemVer) {
		if (Objects.equals(proposedSemVer, "1.19-22.w.13.oneBlockAtATime")) {
			return "1.19-alpha.22.13.oneblockatatime";
		}
		if (Objects.equals(proposedSemVer, "1.16.2-Combat.Test.8")) { // outlet is wrong here, fabric gets it correct
			return "1.16.3-combat.8";
		}
		if (proposedSemVer.contains("-Experimental")) {
			return proposedSemVer.replace("-Experimental", "-alpha.0.0.Experimental");
		}
		return proposedSemVer;
	}

	public static void addLoaderVersion(McVersion mcVersion) {
		if (mcVersion.loaderVersion == null) {
			// Attempt lookup in Outlet database as newer MC versions require a loader update
			Optional<McFabric> v = Outlet.INSTANCE.outletDatabase.getVersion(mcVersion);
			if (v.isPresent()) {
				mcVersion.loaderVersion = fixupSemver(v.get().getNormalized());
				MiscHelper.println("Successfully looked up new semver version for: %s as %s", mcVersion.version, mcVersion.loaderVersion);
				return;
			}

			MiscHelper.println("Creating new semver version...");
			net.fabricmc.loader.impl.game.minecraft.McVersion x = null;
			Path x_path = null;
			while (x == null) {
				try {
					x_path = mcVersion.artifacts.clientJar().fetchArtifact().toPath();
					x = McVersionLookup.getVersion(List.of(x_path), mcVersion.mainClass, null);
				} catch (Exception e) {
					MiscHelper.println("Semver creation failed. Retrying... ");
					if (x_path != null) {
						x_path.toFile().delete();
					}
					MiscHelper.sleep(250);
				}
			}
			mcVersion.loaderVersion = fixupSemver(x.getNormalized());
			MiscHelper.println("Semver made for: %s as %s", x.getRaw(), mcVersion.loaderVersion);
		}
	}

	static List<McVersion> orderVersionList(List<McVersion> versions) {
		MiscHelper.println("Sorting specified versions on semver MC versions...");
		return versions.stream().sorted(Comparator.comparing((McVersion v) -> {
			try {
				return SemanticVersion.parse(v.loaderVersion);
			} catch (VersionParsingException e) {
				throw new RuntimeException(e);
			}
		})).collect(Collectors.toList());
	}

	static TreeMap<SemanticVersion, McVersion> orderVersionMap(LinkedHashMap<String, McVersion> metadata) throws VersionParsingException {
		TreeMap<SemanticVersion, McVersion> ORDERED_MAP = new TreeMap<SemanticVersion, McVersion>();
		MiscHelper.println("Sorting on semver MC versions...");
		for (McVersion version : metadata.values()) {
			if (version.hasMappings && !RepoManager.isVersionNonLinearSnapshot(version)) {
				addLoaderVersion(version);
				ORDERED_MAP.put(SemanticVersion.parse(version.loaderVersion), version);
			}
		}
		return ORDERED_MAP;
	}

	static TreeMap<SemanticVersion, McVersion> nonLinearVersionList(LinkedHashMap<String, McVersion> metadata) throws VersionParsingException {
		TreeMap<SemanticVersion, McVersion> NONLINEAR_MAP = new TreeMap<>();
		MiscHelper.println("Putting non-linear MC versions into other list...");
		for (McVersion version : metadata.values()) {
			if (version.hasMappings && RepoManager.isVersionNonLinearSnapshot(version)) {
				addLoaderVersion(version);
				NONLINEAR_MAP.put(SemanticVersion.parse(version.loaderVersion), version);
			}
		}
		MiscHelper.println("The following versions are considered non-linear: %s", NONLINEAR_MAP.values().stream().map((version) -> version.version).collect(Collectors.joining(", ")));
		return NONLINEAR_MAP;
	}

	public static void deleteDirectory(Path directory) throws IOException {
		try (Stream<Path> walk = Files.walk(directory)) {
			walk.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(java.io.File::delete);
		}
	}
}
