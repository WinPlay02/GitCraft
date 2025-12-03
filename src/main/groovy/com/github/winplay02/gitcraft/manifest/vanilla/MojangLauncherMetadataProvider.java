package com.github.winplay02.gitcraft.manifest.vanilla;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.github.winplay02.gitcraft.manifest.BaseMetadataProvider;
import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.game.minecraft.McVersion;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;

public class MojangLauncherMetadataProvider extends BaseMetadataProvider<MojangLauncherManifest, MojangLauncherManifest.VersionEntry> {

	public MojangLauncherMetadataProvider() {
		this("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

		// 1.14.3 - Combat Test
		this.addMetadataSource(
			"1.14_combat-212796",
			"https://piston-data.mojang.com/experiments/combat/610f5c9874ba8926d5ae1bcce647e5f0e6e7c889/1_14_combat-212796.zip",
			"610f5c9874ba8926d5ae1bcce647e5f0e6e7c889");
		// Combat Test 2
		this.addMetadataSource(
			"1.14_combat-0",
			"https://piston-data.mojang.com/experiments/combat/d164bb6ecc5fca9ac02878c85f11befae61ac1ca/1_14_combat-0.zip",
			"d164bb6ecc5fca9ac02878c85f11befae61ac1ca");
		// Combat Test 3
		this.addMetadataSource(
			"1.14_combat-3",
			"https://piston-data.mojang.com/experiments/combat/0f209c9c84b81c7d4c88b4632155b9ae550beb89/1_14_combat-3.zip",
			"0f209c9c84b81c7d4c88b4632155b9ae550beb89");
		// Combat Test 4
		this.addMetadataSource(
			"1.15_combat-1",
			"https://piston-data.mojang.com/experiments/combat/ac11ea96f3bb2fa2b9b76ab1d20cacb1b1f7ef60/1_15_combat-1.zip",
			"ac11ea96f3bb2fa2b9b76ab1d20cacb1b1f7ef60");
		// Combat Test 5
		this.addMetadataSource(
			"1.15_combat-6",
			"https://piston-data.mojang.com/experiments/combat/52263d42a626b40c947e523128f7a195ec5af76a/1_15_combat-6.zip",
			"52263d42a626b40c947e523128f7a195ec5af76a");
		// Combat Test 6
		this.addMetadataSource(
			"1.16_combat-0",
			"https://piston-data.mojang.com/experiments/combat/5a8ceec8681ed96ab6ecb9607fb5d19c8a755559/1_16_combat-0.zip",
			"5a8ceec8681ed96ab6ecb9607fb5d19c8a755559");
		// Combat Test 7 - From Minecraft Wiki
		this.addMetadataSource(
			"1.16_combat-1",
			"https://archive.org/download/Combat_Test_7ab/1_16_combat-1.zip",
			"47bb5be6cb3ba215539ee97dfae66724c73c3dd5");
		// Combat Test 7b - From Minecraft Wiki
		this.addMetadataSource(
			"1.16_combat-2",
			"https://archive.org/download/Combat_Test_7ab/1_16_combat-2.zip",
			"43266ea8f2c20601d9fb264d5aa85df8052abc9e");
		// Combat Test 7c
		this.addMetadataSource(
			"1.16_combat-3",
			"https://piston-data.mojang.com/experiments/combat/2557b99d95588505e988886220779087d7d6b1e9/1_16_combat-3.zip",
			"2557b99d95588505e988886220779087d7d6b1e9");
		// Combat Test 8 - From Minecraft Wiki
		this.addMetadataSource(
			"1.16_combat-4",
			"https://archive.org/download/1-16-combat-4/1_16_combat-4.zip",
			"b4306b421183bd084b2831bd8d33a5db05ae9f9c");
		// Combat Test 8b
		this.addMetadataSource(
			"1.16_combat-5",
			"https://piston-data.mojang.com/experiments/combat/9b2b984d635d373564b50803807225c75d7fd447/1_16_combat-5.zip",
			"9b2b984d635d373564b50803807225c75d7fd447");
		// Combat Test 8c
		this.addMetadataSource(
			"1.16_combat-6",
			"https://piston-data.mojang.com/experiments/combat/ea08f7eb1f96cdc82464e27c0f95d23965083cfb/1_16_combat-6.zip",
			"ea08f7eb1f96cdc82464e27c0f95d23965083cfb");
		// 1.18 Experimental Snapshot 1
		this.addMetadataSource(
			"1.18_experimental-snapshot-1",
			"https://piston-data.mojang.com/v1/objects/231bba2a21e18b8c60976e1f6110c053b7b93226/1_18_experimental-snapshot-1.zip",
			"231bba2a21e18b8c60976e1f6110c053b7b93226");
		// 1.18 Experimental Snapshot 2
		this.addMetadataSource(
			"1.18_experimental-snapshot-2",
			"https://piston-data.mojang.com/v1/objects/0adfe4f321aa45248fc88ac888bed5556633e7fb/1_18_experimental-snapshot-2.zip",
			"0adfe4f321aa45248fc88ac888bed5556633e7fb");
		// 1.18 Experimental Snapshot 3
		this.addMetadataSource(
			"1.18_experimental-snapshot-3",
			"https://piston-data.mojang.com/v1/objects/846648ff9fe60310d584061261de43010e5c722b/1_18_experimental-snapshot-3.zip",
			"846648ff9fe60310d584061261de43010e5c722b");
		// 1.18 Experimental Snapshot 4
		this.addMetadataSource(
			"1.18_experimental-snapshot-4",
			"https://piston-data.mojang.com/v1/objects/b92a360cbae2eb896a62964ad8c06c3493b6c390/1_18_experimental-snapshot-4.zip",
			"b92a360cbae2eb896a62964ad8c06c3493b6c390");
		// 1.18 Experimental Snapshot 5
		this.addMetadataSource(
			"1.18_experimental-snapshot-5",
			"https://piston-data.mojang.com/v1/objects/d9cb7f6fb4e440862adfb40a385d83e3f8d154db/1_18_experimental-snapshot-5.zip",
			"d9cb7f6fb4e440862adfb40a385d83e3f8d154db");
		// 1.18 Experimental Snapshot 6
		this.addMetadataSource(
			"1.18_experimental-snapshot-6",
			"https://piston-data.mojang.com/v1/objects/4697c84c6a347d0b8766759d5b00bc5a00b1b858/1_18_experimental-snapshot-6.zip",
			"4697c84c6a347d0b8766759d5b00bc5a00b1b858");
		// 1.18 Experimental Snapshot 7
		this.addMetadataSource(
			"1.18_experimental-snapshot-7",
			"https://piston-data.mojang.com/v1/objects/ab4ecebb133f56dd4c4c4c3257f030a947ddea84/1_18_experimental-snapshot-7.zip",
			"ab4ecebb133f56dd4c4c4c3257f030a947ddea84");
		// 1.19 Deep Dark Experimental Snapshot 1
		this.addMetadataSource(
			"1.19_deep_dark_experimental_snapshot-1",
			"https://piston-data.mojang.com/v1/objects/b1e589c1d6ed73519797214bc796e53f5429ac46/1_19_deep_dark_experimental_snapshot-1.zip",
			"b1e589c1d6ed73519797214bc796e53f5429ac46");
		// Before Reupload of 23w13a_or_b: 23w13a_or_b_original
		this.addMetadataSource(
			"23w13a_or_b_original",
			"https://maven.fabricmc.net/net/minecraft/23w13a_or_b_original.json",
			"469f0d1416f2b25a8829d7991c11be3411813bf1");
		// Before Reupload of 24w14potato: 24w14potato_original
		this.addMetadataSource(
			"24w14potato_original",
			"https://maven.fabricmc.net/net/minecraft/24w14potato_original.json",
			"4e54c25e6eafdf0a2f1f6e86fb1b8c1d239dd8d5");
	}

	protected MojangLauncherMetadataProvider(String manifestUrl) {
		this.addManifestSource(manifestUrl, MojangLauncherManifest.class);
	}

	private void addMetadataSource(String id, String url, String sha1) {
		this.addMetadataSource(new MojangLauncherManifest.VersionEntry(id, url, sha1));
	}

	@Override
	public ManifestSource getSource() {
		return ManifestSource.MOJANG;
	}

	@Override
	public String getName() {
		return "Mojang Launcher Metadata";
	}

	@Override
	public String getInternalName() {
		return "mojang-launcher";
	}

	@Override
	protected CompletableFuture<OrderedVersion> loadVersionFromManifest(Executor executor, MojangLauncherManifest.VersionEntry manifestEntry, Path targetDir) throws IOException {
		CompletableFuture<VersionInfo> futureInfo = this.fetchVersionMetadata(executor, manifestEntry.id(), manifestEntry.url(), manifestEntry.sha1(), targetDir, "version info", VersionInfo.class);
		return futureInfo.thenApply(info -> {
			String semanticVersion = this.lookupSemanticVersion(executor, info);
			return OrderedVersion.from(info, semanticVersion);
		});
	}

	@Override
	protected void loadVersionsFromRepository(Executor executor, Path dir, Consumer<OrderedVersion> loader) throws IOException {
		try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir, f -> Files.isRegularFile(f) && (f.endsWith(".json") || f.endsWith(".zip")))) {
			for (Path file : dirStream) {
				VersionInfo info = this.loadVersionMetadata(file, VersionInfo.class, file.getFileName().toString());

				// we could check every field but this ought to be enough
				if (info.id() != null && info.assets() != null) {
					String semanticVersion = this.lookupSemanticVersion(executor, info);
					loader.accept(OrderedVersion.from(info, semanticVersion));
				}
			}
		}
	}

	@Override
	protected boolean isExistingVersionMetadataValid(MojangLauncherManifest.VersionEntry manifestEntry, Path targetDir) throws IOException {
		return this.isExistingVersionMetadataValid(manifestEntry.id(), manifestEntry.url(), manifestEntry.sha1(), targetDir);
	}

	// Version Override
	private static final Map<String, String> minecraftVersionSemVerOverride = Map.of(
		// FIX until fabric-loader is updated
		// END FIX
	);

	public String lookupSemanticVersion(Executor executor, VersionInfo versionMeta) {
		{
			if (minecraftVersionSemVerOverride.containsKey(versionMeta.id())) {
				return minecraftVersionSemVerOverride.get(versionMeta.id());
			}
		}
		if (semverCache.containsKey(versionMeta.id())) {
			try {
				SemanticVersion.parse(semverCache.get(versionMeta.id()));
				return semverCache.get(versionMeta.id());
			} catch (VersionParsingException ignored) {
			}
		}

		Artifact clientJar = OrderedVersion.getClientJarFromInfo(versionMeta);
		McVersion lookedUpVersion = null;
		try {
			lookedUpVersion = McVersionLookup.getVersion(/*clientJarPath != null ? List.of(clientJarPath) : */Collections.emptyList(), versionMeta.mainClass(), null);
			SemanticVersion.parse(lookedUpVersion.getNormalized());
		} catch (Exception | AssertionError ignored1) {
			try {
				lookedUpVersion = McVersionLookup.getVersion(/*clientJarPath != null ? List.of(clientJarPath) : */Collections.emptyList(), null, versionMeta.id());
				SemanticVersion.parse(lookedUpVersion.getNormalized());
			} catch (Exception | AssertionError ignored2) {
				Path clientJarPath = null;
				if (clientJar != null) {
					clientJar.fetchArtifact(executor, clientJarArtifactParentPath(versionMeta), "client jar");
					clientJarPath = clientJar.resolve(clientJarArtifactParentPath(versionMeta));
				}
				lookedUpVersion = McVersionLookup.getVersion(clientJarPath != null ? List.of(clientJarPath) : Collections.emptyList(), versionMeta.mainClass(), null);
			}
		}
		String lookedUpSemver = fixupSemver(Objects.equals(lookedUpVersion.getNormalized(), "client") ? versionMeta.id() : lookedUpVersion.getNormalized());
		try {
			SemanticVersion.parse(lookedUpVersion.getNormalized());
		} catch (VersionParsingException e) {
			MiscHelper.panicBecause(e, "Exhausted every option of getting a semantic version from %s. It seems like this version needs a manual override. Please report if this issue occurrs.", versionMeta.id());
		}
		MiscHelper.println("Semver mapped for: %s as %s", lookedUpVersion.getRaw(), lookedUpSemver);
		return lookedUpSemver;
	}

	private Path clientJarArtifactParentPath(VersionInfo versionMeta) {
		return this.manifestMetadata.resolve(versionMeta.id());
	}

	private String fixupSemver(String proposedSemVer) {
		if (proposedSemVer.contains("-Experimental")) {
			return proposedSemVer.replace("-Experimental", "-alpha.0.0.Experimental");
		}
		return proposedSemVer;
	}

	@Override
	public List<OrderedVersion> getParentVersions(OrderedVersion mcVersion) {
		List<String> parentVersionIds = this.getParentVersionIds(mcVersion.friendlyVersion());
		return parentVersionIds == null ? null : parentVersionIds.stream().map(this::getVersionByVersionID).toList();
	}

	protected List<String> getParentVersionIds(String versionId) {
		switch (versionId) {
			// Combat
			case "1.14_combat-212796" -> {
				return List.of("1.14.3-pre4");
			}
			case "1.14_combat-0" -> {
				return List.of("1.14.4", "1.14_combat-212796");
			}
			case "1.14_combat-3" -> {
				return List.of("1.14_combat-0");
			}
			case "1.15_combat-1" -> {
				return List.of("1.15-pre3", "1.14_combat-3");
			}
			case "1.15_combat-6" -> {
				return List.of("1.15.2-pre2", "1.15_combat-1");
			}
			case "1.16_combat-0" -> {
				return List.of("1.16.2-pre3", "1.15_combat-6");
			}
			case "1.16_combat-1" -> {
				return List.of("1.16.2", "1.16_combat-0");
			}
			case "1.16_combat-2" -> {
				return List.of("1.16_combat-1");
			}
			case "1.16_combat-3" -> {
				return List.of("1.16_combat-2");
			}
			case "1.16_combat-4" -> {
				return List.of("1.16_combat-3");
			}
			case "1.16_combat-5" -> {
				return List.of("1.16_combat-4");
			}
			case "1.16_combat-6" -> {
				return List.of("1.16_combat-5");
			}
			// Experimental 1.18
			case "1.18_experimental-snapshot-1" -> {
				return List.of("1.17.1");
			}
			case "1.18_experimental-snapshot-2" -> {
				return List.of("1.18_experimental-snapshot-1");
			}
			case "1.18_experimental-snapshot-3" -> {
				return List.of("1.18_experimental-snapshot-2");
			}
			case "1.18_experimental-snapshot-4" -> {
				return List.of("1.18_experimental-snapshot-3");
			}
			case "1.18_experimental-snapshot-5" -> {
				return List.of("1.18_experimental-snapshot-4");
			}
			case "1.18_experimental-snapshot-6" -> {
				return List.of("1.18_experimental-snapshot-5");
			}
			case "1.18_experimental-snapshot-7" -> {
				return List.of("1.18_experimental-snapshot-6");
			}
			case "21w37a" -> {
				return List.of("1.17.1", "1.18_experimental-snapshot-7");
			}
			// Experimental 1.19
			case "1.19_deep_dark_experimental_snapshot-1" -> {
				return List.of("1.18.1");
			}
			case "22w11a" -> {
				return List.of("1.18.2", "1.19_deep_dark_experimental_snapshot-1");
			}
			// April
			case "15w14a" -> {
				return List.of("1.8.3");
			}
			case "1.RV-Pre1" -> {
				return List.of("1.9.2");
			}
			case "3D Shareware v1.34" -> {
				return List.of("19w13b");
			}
			case "20w14infinite" -> {
				return List.of("20w13b");
			}
			case "22w13oneblockatatime" -> {
				return List.of("22w13a");
			}
			case "23w13a_or_b" -> {
				return List.of("23w13a_or_b_original", "23w13a");
			}
			case "23w13a_or_b_original" -> {
				return List.of("23w13a");
			}
			case "24w14potato" -> {
				return List.of("24w14potato_original", "24w12a");
			}
			case "24w14potato_original" -> {
				return List.of("24w12a");
			}
			case "25w14craftmine" -> {
				return List.of("1.21.5");
			}
			// Special case to make version graph not contain a cycle
			case "1.9.2" -> {
				return List.of("1.9.1");
			}
			default -> {
				return null;
			}
		}
	}

	private static final Pattern NORMAL_SNAPSHOT_PATTERN = Pattern.compile("(^\\d\\dw\\d\\d[a-z]$)|(^\\d.\\d+(.\\d+)?(-(pre|rc)\\d+|_[a-z_\\-]+snapshot-\\d+| Pre-Release \\d+)?$)");

	@Override
	public boolean shouldExcludeFromMainBranch(OrderedVersion mcVersion) {
		return super.shouldExcludeFromMainBranch(mcVersion)
			// filter out april fools snapshots and experimental versions,
			// which often have typical ids that do not match normal snapshots
			|| (mcVersion.isSnapshotOrPending() && !NORMAL_SNAPSHOT_PATTERN.matcher(mcVersion.launcherFriendlyVersionName()).matches());
	}
}
