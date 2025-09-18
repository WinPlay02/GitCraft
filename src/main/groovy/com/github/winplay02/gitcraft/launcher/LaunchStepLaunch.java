package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.manifest.metadata.ArtifactMetadata;
import com.github.winplay02.gitcraft.manifest.metadata.LibraryMetadata;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IPipeline;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepConfig;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.unpick.Unpick;
import com.github.winplay02.gitcraft.util.MiscHelper;
import groovy.lang.Tuple2;
import net.fabricmc.loom.util.FileSystemUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record LaunchStepLaunch(GitCraftStepConfig config) implements GitCraftStepWorker<LaunchStepLaunch.Inputs> {

	protected static final Artifact launchWrapper = new Artifact("https://libraries.minecraft.net/net/minecraft/launchwrapper/1.5/launchwrapper-1.5.jar", "launchwrapper-1.5.jar", "5150b9c2951f0fde987ce9c33496e26add1de224");

	protected static final String LEGACY_MAIN_CLASS = "net.minecraft.launchwrapper.Launch";

	protected static List<VersionInfo.VersionArgumentWithRules> DEFAULT_JVM_ARGS = List.of(
		new VersionInfo.VersionArgumentWithRules(
			List.of("-XstartOnFirstThread"),
			List.of(
				new VersionInfo.VersionArgumentRule(
					"allow",
					VersionInfo.VersionArgumentRuleFeatures.EMPTY,
					new VersionInfo.VersionArgumentOS(
						"osx",
						null,
						null
					)
				)
			)
		),
		new VersionInfo.VersionArgumentWithRules(
			List.of("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump"),
			List.of(
				new VersionInfo.VersionArgumentRule(
					"allow",
					VersionInfo.VersionArgumentRuleFeatures.EMPTY,
					new VersionInfo.VersionArgumentOS(
						"windows",
						null,
						null
					)
				)
			)
		),
		new VersionInfo.VersionArgumentWithRules(
			List.of(
				"-Dos.name=Windows 10",
				"-Dos.version=10.0"
			),
			List.of(
				new VersionInfo.VersionArgumentRule(
					"allow",
					VersionInfo.VersionArgumentRuleFeatures.EMPTY,
					new VersionInfo.VersionArgumentOS(
						"windows",
						"^10\\\\.",
						null
					)
				)
			)
		),
		new VersionInfo.VersionArgumentWithRules(
			List.of("-Xss1M"),
			List.of(
				new VersionInfo.VersionArgumentRule(
					"allow",
					VersionInfo.VersionArgumentRuleFeatures.EMPTY,
					new VersionInfo.VersionArgumentOS(
						null,
						null,
						"x86"
					)
				)
			)
		),
		new VersionInfo.VersionArgumentWithRules(
			List.of(
				"-Djava.library.path=${natives_directory}",
				"-Dminecraft.launcher.brand=${launcher_name}",
				"-Dminecraft.launcher.version=${launcher_version}",
				"-cp",
				"${classpath}"
			),
			List.of()
		)
	);

	private Tuple2<List<Artifact>, Map<Artifact, LibraryMetadata.Extract>> getLibrariesNeededForLaunch(VersionInfo versionInfo, Map<String, String> args) {
		List<Artifact> libs = new ArrayList<>();
		Map<Artifact, LibraryMetadata.Extract> extractions = new HashMap<>();
		for (LibraryMetadata library : versionInfo.libraries()) {
			if (library.downloads() == null) {
				continue;
			}
			if (library.downloads().artifact() != null) {
				ArtifactMetadata artifactMeta = library.downloads().artifact();
				Artifact artifact = Artifact.fromURL(artifactMeta.url(), artifactMeta.sha1());
				libs.add(artifact);
				if (library.extract() != null) {
					extractions.put(artifact, library.extract());
				}
			}
			if (library.downloads().classifiers() != null && library.natives() != null) {
				boolean applicable = library.rules() != null ? library.rules().stream().map(LauncherUtils::evaluateLauncherRuleFast).reduce(Boolean::logicalAnd).orElse(true) : true;
				if (applicable) {
					ArtifactMetadata artifactMeta = library.downloads().classifiers().get(LauncherUtils.evaluateLauncherString(library.natives().get(LauncherUtils.getOperatingSystem()), args));
					Artifact artifact = Artifact.fromURL(artifactMeta.url(), artifactMeta.sha1());
					libs.add(artifact);
					if (library.extract() != null) {
						extractions.put(artifact, library.extract());
					}
				}
			}
		}
		return Tuple2.tuple(libs, extractions);
	}

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		LaunchStepLaunch.Inputs input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		Path assetsPath = results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCH_ASSETS);
		Path assetsPathVirtualFs = results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCH_ASSETS_VIRTUALFS);
		Path nativesPath = results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCH_NATIVES);
		{
			// Clean natives path
			MiscHelper.deleteDirectory(nativesPath);
			Files.createDirectories(nativesPath);
		}
		Path gamePath = Files.createDirectories(results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCH_GAME));
		// Options fixing; otherwise legacy versions may crash because said languages cannot be found
		{
			Path optionsPath = gamePath.resolve("options.txt");
			Pattern languagePattern = Pattern.compile("^lang:[a-z]{2}_(?<country>[A-Za-z]{2})$");
			if (Files.exists(optionsPath)) {
				List<String> options = Files.readAllLines(optionsPath).stream()
					.map(option -> {
						if (option.startsWith("lang:")) {
							Matcher matcher = languagePattern.matcher(option);
							if (matcher.find()) {
								return option.substring(0, matcher.start("country")) + matcher.group("country").toUpperCase(Locale.ROOT) + option.substring(matcher.end("country"));
							}
							return "lang:en_US";
						} else {
							return option;
						}
					}).toList();
				Files.writeString(optionsPath, String.join("\n", options), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			}
		}
		Path librariesPath = pipeline.getStoragePath(GitCraftPipelineFilesystemStorage.LIBRARIES, context, this.config);
		Path clientPath = pipeline.getStoragePath(input.clientJar().orElse(null), context, this.config);
		// Classpath
		Tuple2<List<Artifact>, Map<Artifact, LibraryMetadata.Extract>> libraries = getLibrariesNeededForLaunch(context.targetVersion().versionInfo(), Map.of("arch", LauncherUtils.getShortArch()));
		List<Path> classpath = libraries.getV1().stream().map(artifact -> artifact.resolve(librariesPath)).collect(Collectors.toList());
		Map<Path, LibraryMetadata.Extract> extractInfo = libraries.getV2().entrySet().stream().map(entry -> Map.entry(entry.getKey().resolve(librariesPath), entry.getValue())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		// Unpick
		{
			Unpick.UnpickContext unpickCtx = config.unpickFlavour().getContext(context.targetVersion(), MinecraftJar.CLIENT);
			if (unpickCtx != null && unpickCtx.unpickConstants() != null && Files.exists(unpickCtx.unpickConstants())) {
				classpath.add(unpickCtx.unpickConstants());
			}
		}
		// LaunchWrapper
		{
			if (LEGACY_MAIN_CLASS.equals(context.targetVersion().mainClass()) && classpath.stream().map(Path::getFileName).map(Object::toString).filter(s -> s.endsWith("launchwrapper-1.5.jar")).findAny().isEmpty()) {
				launchWrapper.fetchArtifact(context.executorService(), librariesPath);
				classpath.add(launchWrapper.resolve(librariesPath));
			}
		}
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
		args.put("auth_uuid", GitCraftLauncher.getLauncherConfig().uuid().toString()); // TODO UUID?
		args.put("auth_access_token", "none"); // TODO access token
		args.put("user_type", "legacy");
		args.put("version_type", context.targetVersion().versionInfo().type());
		args.put("user_properties", "{}");
		args.put("game_assets", assetsPathVirtualFs.toAbsolutePath().toString());
		args.put("auth_session", "none");
		// JVM Args
		args.put("classpath", classpath.stream().map(Path::toAbsolutePath).map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
		args.put("natives_directory", nativesPath.toAbsolutePath().toString());
		args.put("launcher_name", GitCraft.NAME);
		args.put("launcher_version", GitCraft.VERSION);
		// Other used args
		args.put("arch", LauncherUtils.getArch());
		// Extract
		for (Map.Entry<Path, LibraryMetadata.Extract> entry : extractInfo.entrySet()) {
			try (
				FileSystemUtil.Delegate toExtractJar = FileSystemUtil.getJarFileSystem(entry.getKey());
				Stream<Path> extractStream = Files.list(toExtractJar.getRoot());
			) {
				Set<Path> exclusions = entry.getValue().exclude().stream().map(exclusion -> toExtractJar.getRoot().resolve(exclusion)).collect(Collectors.toSet());
				for (Path toExtractPath : extractStream.toList()) {
					Path outputPath = MiscHelper.crossResolvePath(nativesPath, toExtractJar.getRoot().relativize(toExtractPath));
					if (exclusions.contains(toExtractPath)) {
						continue;
					}
					if (Files.isRegularFile(toExtractPath)) {
						Files.copy(toExtractPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
					} else if (Files.isDirectory(toExtractPath)) {
						MiscHelper.copyLargeDirExcept(toExtractPath, outputPath, exclusions.stream().toList());
					}
				}
			}
		}
		// Launch
		String os = LauncherUtils.getOperatingSystem();
		String arch = LauncherUtils.getArch();
		// JVM Args
		List<String> jvmArgs = LauncherUtils.evaluateArgs(
			context.targetVersion().versionInfo().arguments() != null && context.targetVersion().versionInfo().arguments().jvm() != null ?
				context.targetVersion().versionInfo().arguments().jvm() : DEFAULT_JVM_ARGS,
			args, os, arch);
		List<String> cmdArgs = new ArrayList<>(jvmArgs);
		// cmdArgs.addAll(List.of("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:50056"));
		if (LEGACY_MAIN_CLASS.equals(context.targetVersion().mainClass())) {
			String agentJar = System.getenv("GITCRAFT_LAUNCH_AGENT");
			if (agentJar == null || agentJar.isEmpty() || !Files.exists(Path.of(agentJar)) || !Files.isRegularFile(Path.of(agentJar))) {
				MiscHelper.panic("Cannot launch legacy version (that needs the launch wrapper) without the gitcraft agent. Please set the environment variable 'GITCRAFT_LAUNCH_AGENT' to point to the gitcraft agent.");
			}
			MiscHelper.println("Using gitcraft launch agent for legacy launching: %s", agentJar);
			cmdArgs.addAll(List.of(String.format("-javaagent:%s", agentJar)));
			cmdArgs.addAll(List.of("--add-opens", "java.base/jdk.internal.loader=ALL-UNNAMED"));
		}
		cmdArgs.addAll(List.of("--enable-native-access=ALL-UNNAMED"));
		// Main Class
		cmdArgs.add(context.targetVersion().mainClass());
		// Program Args
		List<String> programArgs = LauncherUtils.evaluateArgs(
			context.targetVersion().versionInfo().arguments() != null && context.targetVersion().versionInfo().arguments().game() != null ?
				context.targetVersion().versionInfo().arguments().game() : List.of(new VersionInfo.VersionArgumentWithRules(Arrays.stream(context.targetVersion().versionInfo().minecraftArguments().split("\\s")).toList(), List.of())), args, os, arch);
		cmdArgs.addAll(programArgs);
		MiscHelper.println(String.join(" ", cmdArgs));
		MiscHelper.createJavaSubprocess(context.executorService(), String.format("Client/%s", context.targetVersion().launcherFriendlyVersionName()), GitCraftPipelineFilesystemRoot.getRuntimeDirectory().apply(pipeline.getFilesystemStorage().rootFilesystem()), cmdArgs);
		return StepOutput.ofEmptyResultSet(StepStatus.SUCCESS);
	}

	public record Inputs(Optional<StorageKey> clientJar) implements StepInput {
	}
}
