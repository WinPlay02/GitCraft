package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.GitCraftConfig;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.FabricLoaderImpl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MiscHelper {
	public static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void println(String formatString, Object... args) {
		System.out.println(String.format(formatString, args));
	}

	public static void panic(String formatString, Object... args) {
		throw new RuntimeException(String.format(formatString, args));
	}

	public static void panicBecause(Exception cause, String formatString, Object... args) {
		throw new RuntimeException(String.format(formatString, args), cause);
	}

	public static void copyLargeDir(Path source, Path target) {
		try (Stream<Path> walk = Files.walk(source)) {
			for (Path path : (Iterable<? extends Path>) walk::iterator) {
				Path resultPath = target.resolve(source.relativize(path).toString());
				if (Files.isDirectory(path) && Files.notExists(resultPath)) {
					Files.createDirectories(resultPath);
				} else if (Files.isRegularFile(path)) {
					Files.copy(path, resultPath, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void deleteFile(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void deleteDirectory(Path directory) throws IOException {
		if (!Files.exists(directory)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(directory)) {
			walk.sorted(Comparator.reverseOrder())
				.forEach(MiscHelper::deleteFile);
		}
	}

	public static List<Path> listDirectly(Path directory) throws IOException {
		try (Stream<Path> walk = Files.walk(directory, 1)) {
			return walk.sorted(Comparator.reverseOrder())
				.filter(path -> !path.equals(directory))
				.collect(Collectors.toList());
		}
	}

	public static List<Path> listRecursively(Path directory) throws IOException {
		try (Stream<Path> walk = Files.walk(directory)) {
			return walk.sorted(Comparator.naturalOrder())
				.collect(Collectors.toList());
		}
	}

	public static List<Path> listRecursivelyFilteredExtension(Path directory, String extension) throws IOException {
		try (Stream<Path> walk = Files.walk(directory)) {
			return walk.sorted(Comparator.naturalOrder())
				.filter(path -> path.toString().endsWith(extension))
				.collect(Collectors.toList());
		}
	}

	public interface ExceptionInsensitiveRunnable {
		void run() throws Exception;
	}

	public static void executeTimedStep(String message, ExceptionInsensitiveRunnable runnable) {
		println(message);
		long timeStart = System.nanoTime();
		try {
			runnable.run();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		long timeEnd = System.nanoTime();
		long delta = timeEnd - timeStart;
		Duration deltaDuration = Duration.ofNanos(delta);
		println("\tFinished: %dm %02ds", deltaDuration.toMinutes(), deltaDuration.toSecondsPart());
	}

	public static void checkFabricLoaderVersion() {
		try {
			SemanticVersion actualFabricLoaderVersion = SemanticVersion.parse(FabricLoaderImpl.VERSION);
			SemanticVersion minRequiredVersion = SemanticVersion.parse(GitCraftConfig.MIN_SUPPORTED_FABRIC_LOADER);
			if (actualFabricLoaderVersion.compareTo((Version) minRequiredVersion) < 0) {
				panic("Fabric loader is out of date. Min required version: %s, Actual provided version: %s", GitCraftConfig.MIN_SUPPORTED_FABRIC_LOADER, FabricLoaderImpl.VERSION);
			}
		} catch (VersionParsingException e) {
			throw new RuntimeException(e);
		}
	}

	public static void createJavaSubprocess(Path cwd, List<String> args) throws IOException, InterruptedException {
		List<String> processArgs = new ArrayList<>(List.of("java"));
		processArgs.addAll(args);
		ProcessBuilder processBuilder = new ProcessBuilder(processArgs).directory(cwd.toFile()).inheritIO();
		Process process = processBuilder.start();
		process.waitFor();
	}

	public static void createJavaCpSubprocess(Path jarFile, Path cwd, String[] jvmArgs, String[] args) throws IOException, InterruptedException {
		List<String> processArgs = new ArrayList<>(List.of(jvmArgs));
		processArgs.add("-cp");
		processArgs.add(jarFile.toFile().getAbsolutePath());
		processArgs.addAll(List.of(args));
		createJavaSubprocess(cwd, processArgs);
	}

	public static void createJavaJarSubprocess(Path jarFile, Path cwd, String[] jvmArgs, String[] args) throws IOException, InterruptedException {
		List<String> processArgs = new ArrayList<>(List.of(jvmArgs));
		processArgs.add("-jar");
		processArgs.add(jarFile.toFile().getAbsolutePath());
		processArgs.addAll(List.of(args));
		createJavaSubprocess(cwd, processArgs);
	}

	public static void tryJavaExecution() {
		try {
			ProcessBuilder processBuilder = new ProcessBuilder(List.of("java", "--version"));
			Process process = processBuilder.start();
			process.waitFor();
		} catch (IOException | InterruptedException e) {
			MiscHelper.panicBecause(e, "Could not execute java as subprocess. Make sure, java can be found in the path environment variable or current directory");
		}
	}
}
