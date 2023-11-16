package com.github.winplay02.gitcraft.manifest;

import com.github.winplay02.gitcraft.GitCraftConfig;
import com.github.winplay02.gitcraft.meta.VersionMeta;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.game.minecraft.McVersion;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MinecraftLauncherManifest extends ManifestProvider {

	public MinecraftLauncherManifest() {
		super(new DescribedURL[]{new DescribedURL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", "Launcher Meta")},
				new Artifact[]{
						// 1.14.3 - Combat Test
						Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/610f5c9874ba8926d5ae1bcce647e5f0e6e7c889/1_14_combat-212796.zip", "610f5c9874ba8926d5ae1bcce647e5f0e6e7c889"),
						// Combat Test 2
						Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/d164bb6ecc5fca9ac02878c85f11befae61ac1ca/1_14_combat-0.zip", "d164bb6ecc5fca9ac02878c85f11befae61ac1ca"),
						// Combat Test 3
						Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/0f209c9c84b81c7d4c88b4632155b9ae550beb89/1_14_combat-3.zip", "0f209c9c84b81c7d4c88b4632155b9ae550beb89"),
						// Combat Test 4
						Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/ac11ea96f3bb2fa2b9b76ab1d20cacb1b1f7ef60/1_15_combat-1.zip", "ac11ea96f3bb2fa2b9b76ab1d20cacb1b1f7ef60"),
						// Combat Test 5
						Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/52263d42a626b40c947e523128f7a195ec5af76a/1_15_combat-6.zip", "52263d42a626b40c947e523128f7a195ec5af76a"),
						// Combat Test 6
						Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/5a8ceec8681ed96ab6ecb9607fb5d19c8a755559/1_16_combat-0.zip", "5a8ceec8681ed96ab6ecb9607fb5d19c8a755559"),
						// Combat Test 7 - From Minecraft Wiki
						Artifact.fromURL("https://archive.org/download/Combat_Test_7ab/1_16_combat-1.zip", "47bb5be6cb3ba215539ee97dfae66724c73c3dd5"),
						// Combat Test 7b - From Minecraft Wiki
						Artifact.fromURL("https://archive.org/download/Combat_Test_7ab/1_16_combat-2.zip", "43266ea8f2c20601d9fb264d5aa85df8052abc9e"),
						// Combat Test 7c
						Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/2557b99d95588505e988886220779087d7d6b1e9/1_16_combat-3.zip", "2557b99d95588505e988886220779087d7d6b1e9"),
						// Combat Test 8 - From Minecraft Wiki
						Artifact.fromURL("https://cdn.discordapp.com/attachments/369990015096455168/947864881028272198/1_16_combat-4.zip", "b4306b421183bd084b2831bd8d33a5db05ae9f9c"),
						// Combat Test 8b
						Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/9b2b984d635d373564b50803807225c75d7fd447/1_16_combat-5.zip", "9b2b984d635d373564b50803807225c75d7fd447"),
						// Combat Test 8c
						Artifact.fromURL("https://piston-data.mojang.com/experiments/combat/ea08f7eb1f96cdc82464e27c0f95d23965083cfb/1_16_combat-6.zip", "ea08f7eb1f96cdc82464e27c0f95d23965083cfb"),
						// 1.18 Experimental Snapshot 1
						Artifact.fromURL("https://piston-data.mojang.com/v1/objects/231bba2a21e18b8c60976e1f6110c053b7b93226/1_18_experimental-snapshot-1.zip", "231bba2a21e18b8c60976e1f6110c053b7b93226"),
						// 1.18 Experimental Snapshot 2
						Artifact.fromURL("https://piston-data.mojang.com/v1/objects/0adfe4f321aa45248fc88ac888bed5556633e7fb/1_18_experimental-snapshot-2.zip", "0adfe4f321aa45248fc88ac888bed5556633e7fb"),
						// 1.18 Experimental Snapshot 3
						Artifact.fromURL("https://piston-data.mojang.com/v1/objects/846648ff9fe60310d584061261de43010e5c722b/1_18_experimental-snapshot-3.zip", "846648ff9fe60310d584061261de43010e5c722b"),
						// 1.18 Experimental Snapshot 4
						Artifact.fromURL("https://piston-data.mojang.com/v1/objects/b92a360cbae2eb896a62964ad8c06c3493b6c390/1_18_experimental-snapshot-4.zip", "b92a360cbae2eb896a62964ad8c06c3493b6c390"),
						// 1.18 Experimental Snapshot 5
						Artifact.fromURL("https://piston-data.mojang.com/v1/objects/d9cb7f6fb4e440862adfb40a385d83e3f8d154db/1_18_experimental-snapshot-5.zip", "d9cb7f6fb4e440862adfb40a385d83e3f8d154db"),
						// 1.18 Experimental Snapshot 6
						Artifact.fromURL("https://piston-data.mojang.com/v1/objects/4697c84c6a347d0b8766759d5b00bc5a00b1b858/1_18_experimental-snapshot-6.zip", "4697c84c6a347d0b8766759d5b00bc5a00b1b858"),
						// 1.18 Experimental Snapshot 7
						Artifact.fromURL("https://piston-data.mojang.com/v1/objects/ab4ecebb133f56dd4c4c4c3257f030a947ddea84/1_18_experimental-snapshot-7.zip", "ab4ecebb133f56dd4c4c4c3257f030a947ddea84"),
						// 1.19 Deep Dark Experimental Snapshot 1
						Artifact.fromURL("https://piston-data.mojang.com/v1/objects/b1e589c1d6ed73519797214bc796e53f5429ac46/1_19_deep_dark_experimental_snapshot-1.zip", "b1e589c1d6ed73519797214bc796e53f5429ac46"),
						// Before Reupload of 23w13a_or_b: 23w13a_or_b_original
						Artifact.fromURL("https://maven.fabricmc.net/net/minecraft/23w13a_or_b_original.json", "469f0d1416f2b25a8829d7991c11be3411813bf1")
				}
		);
	}

	@Override
	public String getName() {
		return "Mojang Minecraft Launcher Main Meta";
	}

	public String getInternalName() {
		return "mojang-launcher";
	}

	@Override
	protected String lookupSemanticVersion(VersionMeta versionMeta) {
		{
			if (GitCraftConfig.minecraftVersionSemVerOverride.containsKey(versionMeta.id())) {
				return GitCraftConfig.minecraftVersionSemVerOverride.get(versionMeta.id());
			}
		}
		if (semverCache.containsKey(versionMeta.id())) {
			try {
				SemanticVersion.parse(semverCache.get(versionMeta.id()));
				return semverCache.get(versionMeta.id());
			} catch (VersionParsingException ignored) {
			}
		}

		Artifact clientJar = OrderedVersion.getClientJarFromMeta(versionMeta);
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
					clientJar.fetchArtifact(clientJarArtifactParentPath(versionMeta), "client jar");
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

	private Path clientJarArtifactParentPath(VersionMeta versionMeta) {
		return GitCraftPaths.MC_VERSION_STORE.resolve(versionMeta.id());
	}

	private String fixupSemver(String proposedSemVer) {
		if (Objects.equals(proposedSemVer, "1.19-22.w.13.oneBlockAtATime")) {
			return "1.19-alpha.22.13.oneblockatatime";
		}
		if (Objects.equals(proposedSemVer, "1.16.2-Combat.Test.8")) { // this is wrong here, fabric gets it correct
			return "1.16.3-combat.8";
		}
		if (Objects.equals(proposedSemVer, "0.30.1.c")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.30.1-c";
		}
		if (Objects.equals(proposedSemVer, "0.0.13.a")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.13-a";
		}
		if (Objects.equals(proposedSemVer, "0.0.13.a.3")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.13-a3";
		}
		if (Objects.equals(proposedSemVer, "0.0.11.a")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.11-a";
		}
		if (Objects.equals(proposedSemVer, "rd-161348")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.0.161348-rd";
		}
		if (Objects.equals(proposedSemVer, "rd-160052")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.0.160052-rd";
		}
		if (Objects.equals(proposedSemVer, "rd-20090515")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.0.132328-rd20090515";
		}
		if (Objects.equals(proposedSemVer, "rd-132328")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.0.132328-rd";
		}
		if (Objects.equals(proposedSemVer, "rd-132211")) { // this might be correct, but semver parser from fabric loader does not accept it
			return "0.0.0.132211-rd";
		}
		if (proposedSemVer.contains("-Experimental")) {
			return proposedSemVer.replace("-Experimental", "-alpha.0.0.Experimental");
		}
		return proposedSemVer;
	}

	public List<String> getParentVersion(OrderedVersion mcVersion) {
		switch (mcVersion.semanticVersion()) {
			// Combat
			case "1.14.3-rc.4.combat.1" -> {
				return List.of("1.14.3-rc.4");
			}
			case "1.14.5-combat.2" -> {
				return List.of("1.14.4", "1.14.3-rc.4.combat.1");
			}
			case "1.14.5-combat.3" -> {
				return List.of("1.14.5-combat.2");
			}
			case "1.15-rc.3.combat.4" -> {
				return List.of("1.15-rc.3", "1.14.5-combat.3");
			}
			case "1.15.2-rc.2.combat.5" -> {
				return List.of("1.15.2-rc.2", "1.15-rc.3.combat.4");
			}
			case "1.16.2-beta.3.combat.6" -> {
				return List.of("1.16.2-beta.3", "1.15.2-rc.2.combat.5");
			}
			case "1.16.3-combat.7" -> {
				return List.of("1.16.2", "1.16.2-beta.3.combat.6");
			}
			case "1.16.3-combat.7.b" -> {
				return List.of("1.16.3-combat.7");
			}
			case "1.16.3-combat.7.c" -> {
				return List.of("1.16.3-combat.7.b");
			}
			case "1.16.3-combat.8" -> {
				return List.of("1.16.3-combat.7.c");
			}
			case "1.16.3-combat.8.b" -> {
				return List.of("1.16.3-combat.8");
			}
			case "1.16.3-combat.8.c" -> {
				return List.of("1.16.3-combat.8.b");
			}
			// Experimental 1.18
			case "1.18-alpha.0.0.Experimental.1" -> {
				return List.of("1.17.1");
			}
			case "1.18-alpha.0.0.Experimental.2" -> {
				return List.of("1.18-alpha.0.0.Experimental.1");
			}
			case "1.18-alpha.0.0.Experimental.3" -> {
				return List.of("1.18-alpha.0.0.Experimental.2");
			}
			case "1.18-alpha.0.0.Experimental.4" -> {
				return List.of("1.18-alpha.0.0.Experimental.3");
			}
			case "1.18-alpha.0.0.Experimental.5" -> {
				return List.of("1.18-alpha.0.0.Experimental.4");
			}
			case "1.18-alpha.0.0.Experimental.6" -> {
				return List.of("1.18-alpha.0.0.Experimental.5");
			}
			case "1.18-alpha.0.0.Experimental.7" -> {
				return List.of("1.18-alpha.0.0.Experimental.6");
			}
			case "1.18-alpha.21.37.a" -> {
				return List.of("1.17.1", "1.18-alpha.0.0.Experimental.7");
			}
			// Experimental 1.19
			case "1.19-alpha.0.0.Experimental.1" -> {
				return List.of("1.18.1");
			}
			case "1.19-alpha.22.11.a" -> {
				return List.of("1.18.2", "1.19-alpha.0.0.Experimental.1");
			}
			// April
			case "1.8.4-alpha.15.14.a+loveandhugs" -> {
				return List.of("1.8.3");
			}
			case "1.9.2-rv+trendy" -> {
				return List.of("1.9.2");
			}
			case "1.14-alpha.19.13.shareware" -> {
				return List.of("1.14-alpha.19.13.b");
			}
			case "1.16-alpha.20.13.inf" -> {
				return List.of("1.16-alpha.20.13.b");
			}
			case "1.19-alpha.22.13.oneblockatatime" -> {
				return List.of("1.19-alpha.22.13.a");
			}
			case "1.20-alpha.23.13.ab" -> {
				return List.of("1.20-alpha.23.13.ab.original", "1.20-alpha.23.13.a");
			}
			case "1.20-alpha.23.13.ab.original" -> {
				return List.of("1.20-alpha.23.13.a");
			}
			default -> {
				return null;
			}
		}
	}
}
