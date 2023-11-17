package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class LaunchStepLaunch extends Step {
	@Override
	public String getName() {
		return STEP_LAUNCH;
	}

	@Override
	public boolean ignoresMappings() {
		return true;
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		Files.createDirectories(GitCraftLauncher.nativesPath);
		// Classpath
		List<Path> classpath = mcVersion.libraries().stream().map(artifact -> artifact.resolve(GitCraftPaths.LIBRARY_STORE)).collect(Collectors.toList());
		classpath.add(mcVersion.clientJar().resolve(GitCraftPaths.MC_VERSION_STORE.resolve(mcVersion.launcherFriendlyVersionName())));
		// Args
		Map<String, String> args = new HashMap<>();
		// Program Args
		args.put("clientid", "none"); // TODO clientid
		args.put("auth_xuid", "none"); // TODO auth_xuid
		args.put("auth_player_name", GitCraftLauncher.username);
		args.put("version_name", mcVersion.launcherFriendlyVersionName());
		args.put("game_directory", GitCraftLauncher.gamePath.toAbsolutePath().toString());
		args.put("assets_root", GitCraftLauncher.assetsPath.toAbsolutePath().toString());
		args.put("assets_index_name", mcVersion.assetsIndex().name());
		args.put("auth_uuid", UUID.randomUUID().toString()); // TODO UUID?
		args.put("auth_access_token", "none"); // TODO access token
		args.put("user_type", "legacy");
		args.put("version_type", mcVersion.versionMeta().type());
		// JVM Args
		args.put("classpath", classpath.stream().map(Path::toAbsolutePath).map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
		args.put("natives_directory", GitCraftLauncher.nativesPath.toAbsolutePath().toString());
		args.put("launcher_name", GitCraft.NAME);
		args.put("launcher_version", GitCraft.VERSION);
		// Launch
		String os = LauncherUtils.getOperatingSystem();
		String arch = LauncherUtils.getArch();
		// JVM Args
		List<String> jvmArgs = LauncherUtils.evaluateArgs(mcVersion.versionMeta().arguments().jvm(), args, os, arch);
		List<String> cmdArgs = new ArrayList<>(jvmArgs);
		// Main Class
		cmdArgs.add(mcVersion.mainClass());
		// Program Args
		List<String> programArgs = LauncherUtils.evaluateArgs(mcVersion.versionMeta().arguments().game(), args, os, arch);
		cmdArgs.addAll(programArgs);
		MiscHelper.println(String.join(" ", cmdArgs));
		MiscHelper.createJavaSubprocess(GitCraftPaths.CURRENT_WORKING_DIRECTORY, cmdArgs);
		return StepResult.SUCCESS;
	}
}
