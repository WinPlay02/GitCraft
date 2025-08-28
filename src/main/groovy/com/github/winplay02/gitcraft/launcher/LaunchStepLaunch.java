package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public record LaunchStepLaunch(StepWorker.Config config) implements StepWorker<OrderedVersion, LaunchStepLaunch.Inputs> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, LaunchStepLaunch.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		Path assetsPath = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCH_ASSETS));
		Path nativesPath = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCH_NATIVES));
		Path gamePath = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, PipelineFilesystemStorage.LAUNCH_GAME));
		Path librariesPath = pipeline.getStoragePath(PipelineFilesystemStorage.LIBRARIES, context, this.config);
		Path clientPath = pipeline.getStoragePath(input.clientJar().orElse(null), context, this.config);
		// Classpath
		List<Path> classpath = context.targetVersion().libraries().stream().map(artifact -> artifact.resolve(librariesPath)).collect(Collectors.toList());
		classpath.add(clientPath);
		// Args
		Map<String, String> args = new HashMap<>();
		// Program Args
		args.put("clientid", "none"); // TODO clientid
		args.put("auth_xuid", "none"); // TODO auth_xuid
		args.put("auth_player_name", GitCraftLauncher.getLauncherConfig().username());
		args.put("version_name", context.targetVersion().launcherFriendlyVersionName());
		args.put("game_directory", gamePath.toAbsolutePath().toString());
		args.put("assets_root", assetsPath.toAbsolutePath().toString());
		args.put("assets_index_name", context.targetVersion().assetsIndex().name());
		args.put("auth_uuid", UUID.randomUUID().toString()); // TODO UUID?
		args.put("auth_access_token", "none"); // TODO access token
		args.put("user_type", "legacy");
		args.put("version_type", context.targetVersion().versionInfo().type());
		// JVM Args
		args.put("classpath", classpath.stream().map(Path::toAbsolutePath).map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
		args.put("natives_directory", nativesPath.toAbsolutePath().toString());
		args.put("launcher_name", GitCraft.NAME);
		args.put("launcher_version", GitCraft.VERSION);
		// Launch
		String os = LauncherUtils.getOperatingSystem();
		String arch = LauncherUtils.getArch();
		// JVM Args
		List<String> jvmArgs = LauncherUtils.evaluateArgs(context.targetVersion().versionInfo().arguments().jvm(), args, os, arch);
		List<String> cmdArgs = new ArrayList<>(jvmArgs);
		// Main Class
		cmdArgs.add(context.targetVersion().mainClass());
		// Program Args
		List<String> programArgs = LauncherUtils.evaluateArgs(context.targetVersion().versionInfo().arguments().game(), args, os, arch);
		cmdArgs.addAll(programArgs);
		MiscHelper.println(String.join(" ", cmdArgs));
		MiscHelper.createJavaSubprocess(context.executorService(), String.format("Client/%s", context.targetVersion().launcherFriendlyVersionName()), pipeline.getFilesystemStorage().rootFilesystem().getRuntimeDirectory(), cmdArgs);
		return StepOutput.ofEmptyResultSet(StepStatus.SUCCESS);
	}

	public record Inputs(Optional<StorageKey> clientJar) implements StepInput {
	}
}
