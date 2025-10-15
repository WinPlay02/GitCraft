package com.github.winplay02.gitcraft

import com.github.winplay02.gitcraft.config.ApplicationConfiguration
import com.github.winplay02.gitcraft.config.Configuration
import com.github.winplay02.gitcraft.config.DataConfiguration
import com.github.winplay02.gitcraft.config.IntegrityConfiguration
import com.github.winplay02.gitcraft.config.RepositoryConfiguration
import com.github.winplay02.gitcraft.config.TransientApplicationConfiguration
import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour
import com.github.winplay02.gitcraft.manifest.ManifestSource
import com.github.winplay02.gitcraft.mappings.MappingFlavour
import com.github.winplay02.gitcraft.nests.NestsFlavour
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour
import com.github.winplay02.gitcraft.unpick.UnpickFlavour
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
		cli_args._(longOpt: 'no-single-side-versions-on-main-branch', 'Forces versions with missing sides (like Beta 1.2_02 or 1.0.1) onto side branches instead of the main branch');
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
		cli_args._(longOpt: 'unpick', "Specifies the unpick information used to unpick constants in the source tree. None is selected by default. Possible values are: ${Arrays.stream(UnpickFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: UnpickFlavour, argName: "unpick", defaultValue: "none");
		cli_args._(longOpt: 'fallback-unpick', args: -2 /*CliBuilder.COMMONS_CLI_UNLIMITED_VALUES*/, valueSeparator: ',', argName: "mapping", "If the primary unpick information fails, these are tried (in given order). By default this list is empty. Possible values are: ${Arrays.stream(UnpickFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: UnpickFlavour[]);
		cli_args._(longOpt: 'ornithe-intermediary-generation', "Specifies which generation of Ornithe intermediary to use for Ornithe's mapping flavours", type: int, argName: "generation", defaultValue: "1")
		cli_args._(longOpt: 'patch-lvt', "Generates local variable tables of the Minecraft jars for versions where they were stripped during obfuscation.");
		cli_args._(longOpt: 'preening-enabled', "Undo merging of specialized and bridge methods.");
		cli_args._(longOpt: 'exceptions', "Specifies the exceptions patches used to patch throws clauses into method declarations. None is selected by default. Possible values are: ${Arrays.stream(ExceptionsFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: ExceptionsFlavour, argName: "exceptions", defaultValue: "none");
		cli_args._(longOpt: 'signatures', "Specifies the signatures patches used to patch generics into class, field, and method declarations. None is selected by default. Possible values are: ${Arrays.stream(SignaturesFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: SignaturesFlavour, argName: "signatures", defaultValue: "none");
		cli_args._(longOpt: 'nests', "Specifies the nests used to patch inner classes. None is selected by default. Possible values are: ${Arrays.stream(NestsFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: NestsFlavour, argName: "nests", defaultValue: "none");
		cli_args._(longOpt: 'only-stable', 'Only decompiles stable releases.');
		cli_args._(longOpt: 'only-snapshot', 'Only decompiles snapshots (includes pending and non-linear, if not otherwise specified).');
		cli_args._(longOpt: 'override-repo-target', args: 1, argName: 'path', type: Path,
			'Changes the location of the target repository, as repo names may get quite long and unintuitive. If not used carefully, this can lead to repositories with unwanted mixed mappings or straight up refuse to work as some versions in the target repository may be missing.');
		cli_args._(longOpt: 'create-version-branches', 'Creates a separate branch for each version, including linear versions. This may be useful for quickly switching between multiple versions.')
		cli_args._(longOpt: 'create-stable-version-branches', 'Creates a separate branch for each stable linear version. This may be useful for quickly switching between multiple versions.')
		cli_args._(longOpt: 'sort-json', 'Sorts JSON objects contained in JSON files (e.g. models, language files, ...) in natural order. This is disabled by default as it modifies original data.')
		cli_args._(longOpt: 'manifest-source', "Specifies the manifest source used to fetch the available versions, the mapping to semantic versions and the dependencies between versions. The Minecraft Launcher Meta (from Mojang) is selected by default. Possible values are: ${Arrays.stream(ManifestSource.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: ManifestSource, argName: "manifestsrc", defaultValue: "mojang");
		cli_args._(longOpt: 'repo-gc', 'Perform a garbage collection pass on the repository after the run. This will probably speed up any subsequent operation on the repo (e.g. viewing diffs).')
		cli_args.h(longOpt: 'help', 'Displays this help screen');
		return cli_args;
	}

	static boolean handleCliArgs(String[] args) {
		CliBuilder cli_args = createCli();
		OptionAccessor cli_args_parsed = cli_args.parse(args);

		if (cli_args_parsed == null) {
			return false;
		}

		// Help
		if (cli_args_parsed.hasOption("help")) {
			cli_args.usage();
			return false;
		}

		// Integrity
		boolean verifyChecksums = !cli_args_parsed.hasOption("no-verify");
		Configuration.editConfiguration(IntegrityConfiguration.class, (original) -> new IntegrityConfiguration(
			original.verifyChecksums() && verifyChecksums,
			original.cacheChecksums())
		);

		// Data
		boolean loadIntegratedDatapack = !cli_args_parsed.hasOption("no-datapack");
		boolean loadAssets = !cli_args_parsed.hasOption("no-assets");
		boolean loadAssetsExtern = !cli_args_parsed.hasOption("no-external-assets");
		boolean readableNbt = !cli_args_parsed.hasOption("no-datagen-snbt");
		boolean loadDatagenRegistry = !cli_args_parsed.hasOption("no-datagen-report");
		boolean sortJsonObjects = cli_args_parsed.hasOption("sort-json");
		Configuration.editConfiguration(DataConfiguration.class, (original) -> new DataConfiguration(
			original.loadIntegratedDatapack() && loadIntegratedDatapack,
			original.loadAssets() && loadAssets,
			original.loadAssetsExtern() && loadAssetsExtern,
			original.readableNbt() && readableNbt,
			original.loadDatagenRegistry() && loadDatagenRegistry,
			original.sortJsonObjects() || sortJsonObjects,
		));

		// Repository
		boolean createVersionBranches = cli_args_parsed.hasOption("create-version-branches");
		boolean createStableVersionBranches = cli_args_parsed.hasOption("create-stable-version-branches");
		boolean repoGc = cli_args_parsed.hasOption("repo-gc");
		Configuration.editConfiguration(RepositoryConfiguration.class, (original) -> new RepositoryConfiguration(
			original.gitUser(),
			original.gitMail(),
			original.gitMainlineLinearBranch(),
			original.createVersionBranches() || createVersionBranches,
			original.createStableVersionBranches() || createStableVersionBranches,
			original.gcAfterRun() || repoGc
		));

		// Application
		ManifestSource manifestSource = null;
		if (cli_args_parsed.hasOption("manifest-source")) {
			try {
				manifestSource = cli_args_parsed.'manifest-source';
			} catch (IllegalArgumentException ignored) {
				MiscHelper.println("Ignoring value for 'manifest-source': %s (not recognized)", cli_args_parsed.'manifest-source')
			}
		}
		MappingFlavour usedMapping = null;
		if (cli_args_parsed.hasOption("mappings")) {
			try {
				usedMapping = cli_args_parsed.'mappings';
			} catch (IllegalArgumentException ignored) {
				MiscHelper.println("Ignoring value for 'mappings': %s (not recognized)", cli_args_parsed.'mappings')
			}
		}
		MappingFlavour[] fallbackMappings = null;
		if (cli_args_parsed.hasOption("fallback-mappings")) {
			fallbackMappings = cli_args_parsed.'fallback-mappings';
		}

		UnpickFlavour usedUnpick = null;
		if (cli_args_parsed.hasOption("unpick")) {
			try {
				usedUnpick = cli_args_parsed.'unpick';
			} catch (IllegalArgumentException ignored) {
				MiscHelper.println("Ignoring value for 'unpick': %s (not recognized)", cli_args_parsed.'unpick')
			}
		}
		UnpickFlavour[] fallbackUnpicks = null;
		if (cli_args_parsed.hasOption("fallback-unpick")) {
			fallbackUnpicks = cli_args_parsed.'fallback-unpick';
		}

		boolean noSingleSideVersionsOnMainBranch = cli_args_parsed.hasOption("no-single-side-versions-on-main-branch");
		boolean onlyStableReleases = cli_args_parsed.hasOption("only-stable");
		boolean onlySnapshots = cli_args_parsed.hasOption("only-snapshot");
		boolean skipNonLinear = cli_args_parsed.hasOption("skip-nonlinear");
		String[] onlyVersion = null;
		if (cli_args_parsed.hasOption("only-version")) {
			onlyVersion = cli_args_parsed.'only-versions';
		}
		String minVersion = null;
		if (cli_args_parsed.hasOption("min-version")) {
			minVersion = cli_args_parsed.'min-version';
		}
		String maxVersion = null;
		if (cli_args_parsed.hasOption("max-version")) {
			maxVersion = cli_args_parsed.'max-version';
		}
		String[] excludedVersion = null;
		if (cli_args_parsed.hasOption("exclude-version")) {
			excludedVersion = cli_args_parsed.'exclude-versions';
		}
		// Ornithe Settings
		Integer generation = null;
		if (cli_args_parsed.hasOption("ornithe-intermediary-generation")) {
			generation = (int) cli_args_parsed.'ornithe-intermediary-generation';
		}
		boolean patchLvt = cli_args_parsed.hasOption("patch-lvt");
		ExceptionsFlavour usedExceptions = null;
		if (cli_args_parsed.hasOption("exceptions")) {
			usedExceptions = cli_args_parsed.'exceptions';
		}
		SignaturesFlavour usedSignatures = null;
		if (cli_args_parsed.hasOption("signatures")) {
			usedSignatures = cli_args_parsed.'signatures';
		}
		NestsFlavour usedNests = null;
		if (cli_args_parsed.hasOption("nests")) {
			usedNests = cli_args_parsed.'nests';
		}
		boolean preeningEnabled = cli_args_parsed.hasOption("preening-enabled");
		Configuration.editConfiguration(ApplicationConfiguration.class, (original) -> new ApplicationConfiguration(
			manifestSource != null ? manifestSource : original.manifestSource(),
			usedMapping != null ? usedMapping : original.usedMapping(),
			fallbackMappings != null ? fallbackMappings : original.fallbackMappings(),
			usedUnpick != null ? usedUnpick : original.usedUnpickFlavour(),
			fallbackUnpicks != null ? fallbackUnpicks : original.fallbackUnpickFlavours(),
			original.singleSideVersionsOnMainBranch() && !noSingleSideVersionsOnMainBranch,
			original.onlyStableReleases() || onlyStableReleases,
			original.onlySnapshots() || onlySnapshots,
			original.skipNonLinear() || skipNonLinear,
			onlyVersion,
			minVersion,
			maxVersion,
			excludedVersion,
			generation != null ? generation : original.ornitheIntermediaryGeneration(),
			original.patchLvt() || patchLvt,
			usedExceptions != null ? usedExceptions : original.usedExceptions(),
			usedSignatures != null ? usedSignatures : original.usedSignatures(),
			usedNests != null ? usedNests : original.usedNests(),
			original.enablePreening() || preeningEnabled
		));

		// Transient Application
		boolean noRepo = cli_args_parsed.hasOption("no-repo");
		Path overrideRepositoryPath = null;
		if (cli_args_parsed.hasOption("override-repo-target")) {
			Path repositoryPath = cli_args_parsed.'override-repo-target';
			overrideRepositoryPath = repositoryPath.toAbsolutePath();
		}
		boolean refreshDecompilation = cli_args_parsed.hasOption("refresh");
		String[] refreshOnlyVersion = null;
		if (cli_args_parsed.hasOption("refresh-only-version")) {
			refreshOnlyVersion = cli_args_parsed.'refresh-only-versions';
		}
		String refreshMinVersion = null;
		if (cli_args_parsed.hasOption("refresh-min-version")) {
			refreshMinVersion = cli_args_parsed.'refresh-min-version';
		}
		String refreshMaxVersion = null;
		if (cli_args_parsed.hasOption("refresh-max-version")) {
			refreshMaxVersion = cli_args_parsed.'refresh-max-version';
		}
		if (cli_args_parsed.hasOption("refresh-only-version") || cli_args_parsed.hasOption("refresh-min-version") || cli_args_parsed.hasOption("refresh-max-version")) {
			refreshDecompilation = true;
		}
		Configuration.editConfiguration(TransientApplicationConfiguration.class, (original) -> new TransientApplicationConfiguration(
			original.noRepo() || noRepo,
			overrideRepositoryPath,
			original.refreshDecompilation() || refreshDecompilation,
			refreshOnlyVersion,
			refreshMinVersion,
			refreshMaxVersion
		));
		return true;
	}
}
