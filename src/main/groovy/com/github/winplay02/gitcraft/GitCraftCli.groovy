package com.github.winplay02.gitcraft

import com.github.winplay02.gitcraft.mappings.MappingFlavour
import com.github.winplay02.gitcraft.util.MiscHelper
import groovy.cli.picocli.CliBuilder
import groovy.cli.picocli.OptionAccessor

import java.nio.file.Path
import java.util.stream.Collectors

class GitCraftCli {
	static CliBuilder createCli() {
		CliBuilder cli_args = new CliBuilder();
		cli_args.setUsage("gradlew run --args=\"[Options]\"");
		cli_args.setHeader("Options:");
		cli_args.setFooter("If you want to decompile versions which are not part of the default minecraft meta, put the JSON files of these versions (e.g. 1_16_combat-0.json) into the \"extra-versions\" directory");
		cli_args._(longOpt: 'only-version', args: -2 /*CliBuilder.COMMONS_CLI_UNLIMITED_VALUES*/, valueSeparator: ',', argName:
			'version', 'Specify the only version(s) to decompile. The repository be stored in minecraft-repo-<version>-<version>-.... The normal repository will not be touched. --only-version will take precedence over --min-version.');
		cli_args._(longOpt: 'min-version', args: 1, argName:
			'version', 'Specify the min. version to decompile. Each following version will be decompiled afterwards, non-linear versions are still committed to separate branches. The repository will be stored in minecraft-repo-min-<version>. The normal repository will not be touched.');
		cli_args._(longOpt: 'max-version', args: 1, argName:
			'version', 'Specify the max. version to decompile. Every version before (and including) the specified will be decompiled afterwards, non-linear versions are still committed to separate branches. The repository will be stored in minecraft-repo-max-<version>. The normal repository will not be touched.');
		cli_args._(longOpt: 'exclude-version', args: -2 /*CliBuilder.COMMONS_CLI_UNLIMITED_VALUES*/, valueSeparator: ',', argName:
			'version', 'Specify version(s) to exclude from decompilation. The exclusion info will be added to the repository name. The normal repository will not be touched.');
		cli_args._(longOpt: 'no-verify', 'Disables checksum verification');
		cli_args._(longOpt: 'no-datapack', 'Disables data (integrated datapack) versioning');
		cli_args._(longOpt: 'no-datagen-snbt', 'Disables datagen for converting NBT files (like structures) to SNBT files. If "no-datapack" is set, this flag is automatically set.');
		cli_args._(longOpt: 'no-datagen-report', 'Disables datagen for versioning reports (like blocks, and other registries)');
		cli_args._(longOpt: 'no-assets', 'Disables assets versioning (includes external assets)');
		cli_args._(longOpt:
			'no-external-assets', 'Disables assets versioning for assets not included inside "minecraft".jar (e.g. other languages). Has no effect if --no-assets is specified');
		cli_args._(longOpt: 'skip-nonlinear', 'Skips non-linear (e.g. April Fools, Combat Snapshots, ...) versions completely');
		cli_args._(longOpt:
			'no-repo', 'Prevents the creation/modification of a repository for versioning, only decompiles the provided (or all) version(s)');
		cli_args._(longOpt:
			'refresh', 'Refreshes the decompilation by deleting old decompiled artifacts and restarting. This may be useful, if the decompiler has been updated or new mappings exist. The repository will get updated (existing commits will get deleted, and new ones will be inserted). This will cause local branches to diverge from remote branches, if any exist.');
		cli_args._(longOpt:
			'refresh-only-version', args: -2 /*CliBuilder.COMMONS_CLI_UNLIMITED_VALUES*/, valueSeparator: ',', argName:
				'version', 'Restricts the refreshed versions to the ones provided. This options will cause the git repository to refresh.');
		cli_args._(longOpt:
			'refresh-min-version', args: 1, argName:
				'version', 'Restricts the min. refreshed version to the one provided. This options will cause the git repository to refresh.');
		cli_args._(longOpt:
			'refresh-max-version', args: 1, argName:
				'version', 'Restricts the max. refreshed version to the one provided. This options will cause the git repository to refresh.');
		cli_args._(longOpt: 'mappings', "Specifies the mappings used to decompile the source tree. Mojmaps are selected by default. Possible values are: ${Arrays.stream(MappingFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: MappingFlavour, argName: "mapping", defaultValue: "mojmap");
		cli_args._(longOpt: 'fallback-mappings', args: -2 /*CliBuilder.COMMONS_CLI_UNLIMITED_VALUES*/, valueSeparator: ',', argName: "mapping", "If the primary mapping fails, these mappings are tried (in given order). By default none is tried as a fallback. Possible values are: ${Arrays.stream(MappingFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: MappingFlavour[]);
		cli_args._(longOpt: 'only-stable', 'Only decompiles stable releases.');
		cli_args._(longOpt: 'only-snapshot', 'Only decompiles snapshots (includes pending and non-linear, if not otherwise specified).');
		cli_args._(longOpt: 'override-repo-target', args: 1, argName: 'path', type: Path,
			'Changes the location of the target repository, as repo names may get quite long and unintuitive. If not used carefully, this can lead to repositories with unwanted mixed mappings or straight up refuse to work as some versions in the target repository may be missing.');
        cli_args._(longOpt: 'create-version-branches', 'Creates a separate branch for each version, including linear versions. This may be useful for quickly switching between multiple versions.')
        cli_args._(longOpt: 'create-stable-version-branches', 'Creates a separate branch for each stable linear version. This may be useful for quickly switching between multiple versions.')
		cli_args._(longOpt: 'sort-json', 'Sorts JSON objects contained in JSON files (e.g. models, language files, ...) in natural order. This is disabled by default as it modifies original data.')
		cli_args.h(longOpt: 'help', 'Displays this help screen');
		return cli_args;
	}

	static GitCraftConfig handleCliArgs(String[] args) {
		GitCraftConfig config = GitCraftConfig.defaultConfig();
		CliBuilder cli_args = createCli();
		OptionAccessor cli_args_parsed = cli_args.parse(args)
		config.loadAssets = !cli_args_parsed.hasOption("no-assets");
		config.loadAssetsExtern = !cli_args_parsed.hasOption("no-external-assets");
		config.verifyChecksums = !cli_args_parsed.hasOption("no-verify");
		config.skipNonLinear = cli_args_parsed.hasOption("skip-nonlinear");
		config.noRepo = cli_args_parsed.hasOption("no-repo");
		config.loadIntegratedDatapack = !cli_args_parsed.hasOption("no-datapack");
		config.readableNbt = !cli_args_parsed.hasOption("no-datagen-snbt");
		config.loadDatagenRegistry = !cli_args_parsed.hasOption("no-datagen-report");
		config.refreshDecompilation = cli_args_parsed.hasOption("refresh");
        config.createVersionBranches = cli_args_parsed.hasOption("create-version-branches");
        config.createStableVersionBranches = cli_args_parsed.hasOption("create-stable-version-branches");
		config.sortJsonObjects = cli_args_parsed.hasOption("sort-json");
		if (cli_args_parsed.hasOption("help")) {
			cli_args.usage();
			return null;
		}
		if (cli_args_parsed.hasOption("refresh-only-version")) {
			String[] subjectVersion = cli_args_parsed.'refresh-only-versions';
			config.refreshOnlyVersion = subjectVersion;
		}
		if (cli_args_parsed.hasOption("refresh-min-version")) {
			String subjectVersion = cli_args_parsed.'refresh-min-version';
			config.refreshMinVersion = subjectVersion;
		}
		if (cli_args_parsed.hasOption("refresh-max-version")) {
			String subjectVersion = cli_args_parsed.'refresh-max-version';
			config.refreshMaxVersion = subjectVersion;
		}
		if (cli_args_parsed.hasOption("refresh-only-version") || cli_args_parsed.hasOption("refresh-min-version") || cli_args_parsed.hasOption("refresh-max-version")) {
			config.refreshDecompilation = true;
		}
		if (cli_args_parsed.hasOption("only-version")) {
			String[] subjectVersion = cli_args_parsed.'only-versions';
			config.onlyVersion = subjectVersion;
		}
		if (cli_args_parsed.hasOption("min-version")) {
			String subjectVersion = cli_args_parsed.'min-version';
			config.minVersion = subjectVersion;
		}
		if (cli_args_parsed.hasOption("max-version")) {
			String subjectVersion = cli_args_parsed.'max-version';
			config.maxVersion = subjectVersion;
		}
		if (cli_args_parsed.hasOption("exclude-version")) {
			String[] subjectVersion = cli_args_parsed.'exclude-versions';
			config.excludedVersion = subjectVersion;
		}
		config.usedMapping = cli_args_parsed.'mappings';
		if (cli_args_parsed.hasOption("fallback-mappings")) {
			MappingFlavour[] fallbackMappings = cli_args_parsed.'fallback-mappingss';
			config.fallbackMappings = fallbackMappings;
		}
		config.onlyStableReleases = cli_args_parsed.hasOption("only-stable");
		config.onlySnapshots = cli_args_parsed.hasOption("only-snapshot");
		if (cli_args_parsed.hasOption("override-repo-target")) {
			Path repositoryPath = cli_args_parsed.'override-repo-target';
			config.overrideRepositoryPath = repositoryPath.toAbsolutePath();
		}
		/// Validate parameters (and combinations), which are wrong without looking at any context
		if (config.onlyStableReleases && config.onlySnapshots) {
			MiscHelper.panic("ERROR: Excluding both stable releases and snapshots would lead to doing nothing");
		}
		/// Print Info
		config.printConfigInformation();
		return config;
	}
}
