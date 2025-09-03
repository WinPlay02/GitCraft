package com.github.winplay02.gitcraft.launcher

import com.github.winplay02.gitcraft.config.ApplicationConfiguration
import com.github.winplay02.gitcraft.config.Configuration
import com.github.winplay02.gitcraft.config.IntegrityConfiguration
import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour
import com.github.winplay02.gitcraft.manifest.ManifestSource
import com.github.winplay02.gitcraft.mappings.MappingFlavour
import com.github.winplay02.gitcraft.nests.NestsFlavour
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour
import com.github.winplay02.gitcraft.unpick.UnpickFlavour
import com.github.winplay02.gitcraft.util.MiscHelper
import groovy.cli.picocli.CliBuilder
import groovy.cli.picocli.OptionAccessor

import java.util.stream.Collectors

class GitCraftLauncherCli {
	static CliBuilder createCli() {
		CliBuilder cli_args = new CliBuilder();
		cli_args.setUsage("gradlew runLauncher --args=\"[Options]\"");
		cli_args.setHeader("Options:");
		cli_args.setFooter("If you want to launch versions which are not part of the default minecraft meta, put the JSON files of these versions (e.g. 1_16_combat-0.json) into the \"extra-versions\" directory");
		cli_args._(longOpt: 'version', args: 1, argName: 'version', 'Specify the version to launch.');
		cli_args._(longOpt: 'no-verify', 'Disables checksum verification');
		cli_args._(longOpt: 'mappings', "Specifies the mappings used to decompile the source tree. Mojmaps are selected by default. Possible values are: ${Arrays.stream(MappingFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: MappingFlavour, argName: "mapping", defaultValue: "mojmap");
		cli_args._(longOpt: 'fallback-mappings', args: -2 /*CliBuilder.COMMONS_CLI_UNLIMITED_VALUES*/, valueSeparator: ',', argName: "mapping", "If the primary mapping fails, these mappings are tried (in given order). By default none is tried as a fallback. Possible values are: ${Arrays.stream(MappingFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: MappingFlavour[]);
		cli_args._(longOpt: 'unpick', "Specifies the unpick information used to unpick constants in the source tree. None is selected by default. Possible values are: ${Arrays.stream(UnpickFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: UnpickFlavour, argName: "unpick", defaultValue: "none");
		cli_args._(longOpt: 'fallback-unpick', args: -2 /*CliBuilder.COMMONS_CLI_UNLIMITED_VALUES*/, valueSeparator: ',', argName: "mapping", "If the primary unpick information fails, these are tried (in given order). By default none is tried as a fallback. Possible values are: ${Arrays.stream(UnpickFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: UnpickFlavour[]);
		cli_args._(longOpt: 'ornithe-intermediary-generation', "Specifies which generation of Ornithe intermediary to use for Ornithe's mapping flavours", type: int, argName: "generation", defaultValue: "1")
		cli_args._(longOpt: 'patch-lvt', "Generates local variable tables of the Minecraft jars for versions where they were stripped during obfuscation.");
		cli_args._(longOpt: 'preening-enabled', "Undo merging of specialized and bridge methods.");
		cli_args._(longOpt: 'exceptions', "Specifies the exceptions patches used to patch throws clauses into method declarations. None is selected by default. Possible values are: ${Arrays.stream(ExceptionsFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: ExceptionsFlavour, argName: "exceptions", defaultValue: "none");
		cli_args._(longOpt: 'signatures', "Specifies the signatures patches used to patch generics into class, field, and method declarations. None is selected by default. Possible values are: ${Arrays.stream(SignaturesFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: SignaturesFlavour, argName: "signatures", defaultValue: "none");
		cli_args._(longOpt: 'nests', "Specifies the nests used to patch inner classes. None is selected by default. Possible values are: ${Arrays.stream(NestsFlavour.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: NestsFlavour, argName: "nests", defaultValue: "none");
		cli_args._(longOpt: 'manifest-source', "Specifies the manifest source used to fetch the available versions, the mapping to semantic versions and the dependencies between versions. The Minecraft Launcher Meta (from Mojang) is selected by default. Possible values are: ${Arrays.stream(ManifestSource.values()).map(Object::toString).collect(Collectors.joining(", "))}", type: ManifestSource, argName: "manifestsrc", defaultValue: "mojang");
		cli_args._(longOpt: 'launch-demo', "Whether the client should launch in demo mode.");
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

		// Application
		ManifestSource manifestSource = null;
		try {
			manifestSource = cli_args_parsed.'manifest-source';
		} catch (IllegalArgumentException ignored) {
			MiscHelper.println("Ignoring value for 'manifest-source': %s (not recognized)", cli_args_parsed.'manifest-source')
		}
		MappingFlavour usedMapping = null;
		try {
			usedMapping = cli_args_parsed.'mappings';
		} catch (IllegalArgumentException ignored) {
			MiscHelper.println("Ignoring value for 'mappings': %s (not recognized)", cli_args_parsed.'mappings')
		}
		MappingFlavour[] fallbackMappings = null;
		if (cli_args_parsed.hasOption("fallback-mappings")) {
			fallbackMappings = cli_args_parsed.'fallback-mappingss';
		}

		UnpickFlavour usedUnpick = null;
		try {
			usedUnpick = cli_args_parsed.'unpick';
		} catch (IllegalArgumentException ignored) {
			MiscHelper.println("Ignoring value for 'unpick': %s (not recognized)", cli_args_parsed.'unpick')
		}
		UnpickFlavour[] fallbackUnpicks = null;
		if (cli_args_parsed.hasOption("fallback-unpick")) {
			fallbackMappings = cli_args_parsed.'fallback-unpicks';
		}

		String onlyVersion = null;
		if (cli_args_parsed.hasOption("version")) {
			onlyVersion = cli_args_parsed.'version';
		} else {
			MiscHelper.panic("Version was not specified.")
		}


		// Ornithe Settings
		Integer generation = null;
		if (cli_args_parsed.hasOption("ornithe-intermediary-generation")) {
			generation = (int) cli_args_parsed.'ornithe-intermediary-generation';
		}
		boolean patchLvt = cli_args_parsed.'patch-lvt';
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
		boolean preeningEnabled = cli_args_parsed.'preening-enabled';
		Configuration.editConfiguration(ApplicationConfiguration.class, (original) -> new ApplicationConfiguration(
			manifestSource != null ? manifestSource : original.manifestSource(),
			usedMapping != null ? usedMapping : original.usedMapping(),
			fallbackMappings != null ? fallbackMappings : original.fallbackMappings(),
			usedUnpick != null ? usedUnpick : original.usedUnpickFlavour(),
			fallbackUnpicks != null ? fallbackUnpicks : original.fallbackUnpickFlavours(),
			original.onlyStableReleases(),
			original.onlySnapshots(),
			original.skipNonLinear(),
			new String[] { onlyVersion },
			null,
			null,
			null,
			generation != null ? generation : original.ornitheIntermediaryGeneration(),
			original.patchLvt() || patchLvt,
			usedExceptions != null ? usedExceptions : original.usedExceptions(),
			usedSignatures != null ? usedSignatures : original.usedSignatures(),
			usedNests != null ? usedNests : original.usedNests(),
			original.enablePreening() || preeningEnabled
		));

		// Launcher
		boolean demoMode = cli_args_parsed.'launch-demo';

		Configuration.editConfiguration(LauncherConfig.class, (original) -> new LauncherConfig(
			original.username(),
			original.uuid(),
			original.launchDemo() || demoMode,
			original.customResolution(),
			original.quickPlayPath(),
			original.quickPlaySingleplayer(),
			original.quickPlayMultiplayer(),
			original.quickPlayRealms()
		));

		return true;
	}
}
