package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LauncherUtils {
	protected static Pattern LAUNCH_PARAMETER_REGEX = Pattern.compile("\\$\\{.+?\\}");

	public static String getOperatingSystem() {
		String osName = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
		if ((osName.contains("mac")) || (osName.contains("darwin"))) {
			return "osx";
		} else if (osName.contains("win")) {
			return "windows";
		} else if (osName.contains("nux")) {
			return "linux";
		} else {
			return "unknown";
		}
	}

	public static String getArch() {
		return System.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);
	}

	public static String getShortArch() {
		return System.getProperty("sun.arch.data.model", "").toLowerCase(Locale.ENGLISH);
	}

	public static boolean evaluateLauncherRuleFast(VersionInfo.VersionArgumentRule rule) {
		return evaluateLauncherRule(rule, getOperatingSystem(), getArch());
	}

	public static boolean evaluateLauncherRule(VersionInfo.VersionArgumentRule rule, String os, String arch) {
		if (rule.os() != null) {
			if (!rule.action().equalsIgnoreCase("allow") &&
				(rule.os().name() == null || rule.os().name().equalsIgnoreCase(os)) &&
				(rule.os().arch() == null || rule.os().arch().equalsIgnoreCase(arch))) {
				return false;
			}
			if (rule.action().equalsIgnoreCase("allow") &&
				(rule.os().name() != null && !rule.os().name().equalsIgnoreCase(os) ||
					rule.os().arch() != null && !rule.os().arch().equalsIgnoreCase(arch))) {
				return false;
			}
		}
		if (rule.features() != null) {
			if (rule.features().is_demo_user() && !GitCraftLauncher.getLauncherConfig().launchDemo()) {
				return false;
			}
			if (rule.features().has_custom_resolution() && (GitCraftLauncher.getLauncherConfig().customResolution() == null)) {
				return false;
			}
			if (rule.features().has_quick_plays_support() && (GitCraftLauncher.getLauncherConfig().quickPlayPath() == null)) {
				return false;
			}
			if (rule.features().is_quick_play_singleplayer() && (GitCraftLauncher.getLauncherConfig().quickPlaySingleplayer() == null)) {
				return false;
			}
			if (rule.features().is_quick_play_multiplayer() && (GitCraftLauncher.getLauncherConfig().quickPlayMultiplayer() == null)) {
				return false;
			}
			if (rule.features().is_quick_play_realms() && (GitCraftLauncher.getLauncherConfig().quickPlayRealms() == null)) {
				return false;
			}
		}
		return true;
	}

	public static String replaceLambda(String src, Pattern regex, Function<String, String> replacement) {
		Matcher m = regex.matcher(src);
		boolean result = m.find();
		if (result) {
			StringBuilder sb = new StringBuilder(src.length());
			int index = 0;
			do {
				sb.append(src, index, m.start());
				sb.append(replacement.apply(m.group()));
				index = m.end();
			} while (m.find());
			sb.append(src, index, src.length());
			return sb.toString();
		}
		return src;
	}

	public static List<String> evaluateArgs(List<VersionInfo.VersionArgumentWithRules> protoArgs, Map<String, String> args, String os, String arch) {
		return protoArgs.stream()
			.filter(arg -> arg.rules().isEmpty() ||
				arg.rules().stream().map(rule -> LauncherUtils.evaluateLauncherRule(rule, os, arch)).reduce(true, Boolean::logicalAnd))
			.map(arg -> arg.value().stream().map(value -> evaluateLauncherString(value, args)).collect(Collectors.toList())).reduce(new ArrayList<>(), (list, next) -> {
				list.addAll(next);
				return list;
			});
	}

	public static String evaluateLauncherString(String toEvaluate, Map<String, String> args) {
		return LauncherUtils.replaceLambda(toEvaluate, LAUNCH_PARAMETER_REGEX, (replaced) -> {
			String argsVar = replaced.substring(2, replaced.length() - 1);
			if (args.containsKey(argsVar)) {
				return args.get(argsVar);
			}
			return replaced;
		});
	}
}
