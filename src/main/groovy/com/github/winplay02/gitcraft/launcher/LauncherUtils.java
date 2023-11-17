package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.meta.VersionMeta;

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
			return "macos";
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

	public static boolean evaluateLauncherRule(VersionMeta.VersionArgumentRule rule, String os, String arch) {
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
			if (rule.features().is_demo_user() && !GitCraftLauncher.launchDemo) {
				return false;
			}
			if (rule.features().has_custom_resolution() && (GitCraftLauncher.customResolution == null)) {
				return false;
			}
			if (rule.features().has_quick_plays_support() && (GitCraftLauncher.quickPlayPath == null)) {
				return false;
			}
			if (rule.features().is_quick_play_singleplayer() && (GitCraftLauncher.quickPlaySingleplayer == null)) {
				return false;
			}
			if (rule.features().is_quick_play_multiplayer() && (GitCraftLauncher.quickPlayMultiplayer == null)) {
				return false;
			}
			if (rule.features().is_quick_play_realms() && (GitCraftLauncher.quickPlayRealms == null)) {
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

	public static List<String> evaluateArgs(List<VersionMeta.VersionArgumentWithRules> protoArgs, Map<String, String> args, String os, String arch) {
		return protoArgs.stream()
			.filter(arg -> arg.rules().isEmpty() ||
				arg.rules().stream().map(rule -> LauncherUtils.evaluateLauncherRule(rule, os, arch)).reduce(true, Boolean::logicalAnd))
			.map(arg -> arg.value().stream().map(value -> LauncherUtils.replaceLambda(value, LAUNCH_PARAMETER_REGEX, (replaced) -> {
				String argsVar = replaced.substring(2, replaced.length() - 1);
				if (args.containsKey(argsVar)) {
					return args.get(argsVar);
				}
				return replaced;
			})).collect(Collectors.toList())).reduce(new ArrayList<>(), (list, next) -> {
				list.addAll(next);
				return list;
			});
	}
}
