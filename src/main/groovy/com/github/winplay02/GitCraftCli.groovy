package com.github.winplay02;

import groovy.cli.picocli.CliBuilder
import groovy.cli.picocli.OptionAccessor;

class GitCraftCli {
	static CliBuilder createCli() {
		CliBuilder cli_args = new CliBuilder();
		cli_args.setUsage("gradlew run --args=\"[Options]\"");
		cli_args.setHeader("Options:");
		cli_args.setFooter("If you want to decompile versions which are not part of the default minecraft meta, put the JSON files of these versions (e.g. 1_16_combat-0.json) into the \"extra-versions\" directory");
		cli_args._(longOpt: 'only-version', args: -2 /*Option.UNLIMITED_VALUES*/, valueSeparator: ',', argName:
				'version', 'Specify the only version(s) to decompile. The repository be stored in minecraft-repo-<version>-<version>-.... The normal repository (minecraft-repo) will not be touched. --only-version will take precedence over --min-version.');
		cli_args._(longOpt: 'min-version', args: 1, argName:
				'version', 'Specify the min. version to decompile. Each following version will be decompiled afterwards, non-linear versions are still committed to separate branches. The repository will be stored in minecraft-repo-min-<version>. The normal repository (minecraft-repo) will not be touched.');
		cli_args._(longOpt: 'no-verify', 'Disables checksum verification');
		cli_args._(longOpt: 'no-datapack', 'Disables data (integrated datapack) versioning');
		cli_args._(longOpt: 'no-assets', 'Disables assets versioning (includes external assets)');
		cli_args._(longOpt:
				'no-external-assets', 'Disables assets versioning for assets not included inside "minecraft".jar (e.g. other languages). Has no effect if --no-assets is specified');
		cli_args._(longOpt: 'skip-nonlinear', 'Skips non-linear (e.g. April Fools, Combat Snapshots, ...) versions completely');
		cli_args._(longOpt:
				'no-repo', 'Prevents the creation/modification of a repository for versioning, only decompiles the provided (or all) version(s)');
		cli_args._(longOpt:
				'refresh', 'Refreshes the decompilation by deleting old decompiled artifacts and restarting. This will not be useful, if the decompiler has not been updated. The repository has to be deleted manually.');
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
		config.refreshDecompilation = cli_args_parsed.hasOption("refresh");
		if (cli_args_parsed.hasOption("help")) {
			cli_args.usage();
			return null;
		}
		if (cli_args_parsed.hasOption("only-version")) {
			String[] subjectVersion = cli_args_parsed.'only-versions';
			config.onlyVersion = subjectVersion;
		}
		if (cli_args_parsed.hasOption("min-version")) {
			String subjectVersion = cli_args_parsed.'min-version';
			config.minVersion = subjectVersion;
		}
		config.printConfigInformation();
		return config;
	}
}
