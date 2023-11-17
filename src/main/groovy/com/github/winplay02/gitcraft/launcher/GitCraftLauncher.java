package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.GitCraftConfig;
import com.github.winplay02.gitcraft.manifest.MinecraftLauncherManifest;
import com.github.winplay02.gitcraft.pipeline.FetchArtifactsStep;
import com.github.winplay02.gitcraft.pipeline.FetchAssetsStep;
import com.github.winplay02.gitcraft.pipeline.FetchLibrariesStep;
import com.github.winplay02.gitcraft.pipeline.Step;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import groovy.lang.Tuple2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GitCraftLauncher {
	public static Step LAUNCH_STEP_HARDLINK_ASSETS = null;
	public static Step LAUNCH_STEP_LAUNCH = null;

	/// Default Pipeline
	public static List<Step> LAUNCH_PIPELINE = null;

	protected static Path gamePath;
	protected static Path assetsPath;
	protected static Path nativesPath;
	protected static String username = "TestingUser";
	protected static boolean launchDemo = false;
	protected static Tuple2<Integer, Integer> customResolution = null;
	protected static String quickPlayPath = null;
	protected static String quickPlaySingleplayer = null;
	protected static String quickPlayMultiplayer = null;
	protected static String quickPlayRealms = null;

	public static void main(String... args) throws IOException {
		GitCraft.config = GitCraftConfig.defaultConfig();
		GitCraftPaths.initializePaths(GitCraftPaths.lookupCurrentWorkingDirectory());
		GitCraft.manifestProvider = new MinecraftLauncherManifest();
		String version = "1.20";

		gamePath = GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve("runtime");
		assetsPath = GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve("runtime_assets");
		nativesPath = GitCraftPaths.CURRENT_WORKING_DIRECTORY.resolve("runtime_natives");

		OrderedVersion mcVersion = GitCraft.manifestProvider.getVersionMeta().get(version);
		{
			LAUNCH_PIPELINE = new ArrayList<>();
			LAUNCH_PIPELINE.add(GitCraft.STEP_FETCH_ARTIFACTS = new FetchArtifactsStep());
			LAUNCH_PIPELINE.add(GitCraft.STEP_FETCH_LIBRARIES = new FetchLibrariesStep());
			LAUNCH_PIPELINE.add(GitCraft.STEP_FETCH_ASSETS = new FetchAssetsStep());
			LAUNCH_PIPELINE.add(LAUNCH_STEP_HARDLINK_ASSETS = new LaunchStepHardlinkAssets());
			LAUNCH_PIPELINE.add(LAUNCH_STEP_LAUNCH = new LaunchStepLaunch());
		}
		Step.executePipeline(LAUNCH_PIPELINE, mcVersion, null, null, null);
		if (mcVersion == null) {
			MiscHelper.panic("Minecraft version %s was not found", version);
		}
	}
}
