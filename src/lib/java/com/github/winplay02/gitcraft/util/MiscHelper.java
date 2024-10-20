package com.github.winplay02.gitcraft.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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

	public static void copyLargeDirExcept(Path source, Path target, List<Path> exceptions) {
		try (Stream<Path> walk = Files.walk(source)) {
			for (Path path : (Iterable<? extends Path>) walk::iterator) {
				Path resultPath = target.resolve(source.relativize(path).toString());
				if (exceptions.contains(resultPath)) {
					continue;
				}
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

	public static Optional<Path> findRecursivelyByName(Path directory, String name) throws IOException {
		try (Stream<Path> walk = Files.walk(directory)) {
			return walk.sorted(Comparator.naturalOrder())
				.filter(path -> path.getFileName().toString().equals(name))
				.findFirst();
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

	public static void createJavaCpSubprocess(Path jarFile, Path cwd, String[] jvmArgs, String[] args) throws IOException, InterruptedException {
		List<String> processArgs = new ArrayList<>(List.of("java"));
		processArgs.addAll(List.of(jvmArgs));
		processArgs.add("-cp");
		processArgs.add(jarFile.toFile().getAbsolutePath());
		processArgs.addAll(List.of(args));
		ProcessBuilder processBuilder = new ProcessBuilder(processArgs).directory(cwd.toFile()).inheritIO();
		Process process = processBuilder.start();
		process.waitFor();
	}

	public static void createJavaJarSubprocess(Path jarFile, Path cwd, String[] jvmArgs, String[] args) throws IOException, InterruptedException {
		List<String> processArgs = new ArrayList<>(List.of("java"));
		processArgs.addAll(List.of(jvmArgs));
		processArgs.add("-jar");
		processArgs.add(jarFile.toFile().getAbsolutePath());
		processArgs.addAll(List.of(args));
		ProcessBuilder processBuilder = new ProcessBuilder(processArgs).directory(cwd.toFile()).inheritIO();
		Process process = processBuilder.start();
		process.waitFor();
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

	public static <T> Set<T> calculateSetIntersection(Set<T> set1, Set<T> set2) {
		if (set1 == null || set2 == null) {
			return Set.of();
		}
		HashSet<T> intersection = new HashSet<>(set1);
		intersection.retainAll(set2);
		return intersection;
	}

	public static boolean isJarEmpty(Path jarFile) throws IOException {
		return Files.exists(jarFile) && Files.size(jarFile) <= 22 /* empty jar */;
	}

	public static void deleteJarIfEmpty(Path jarFile) throws IOException {
		if (isJarEmpty(jarFile)) {
			Files.delete(jarFile);
		}
	}

	@SafeVarargs
	public static <K, V> Map<K, V> mergeMaps(Map<K, V> target, Map<K, V>... maps) {
		Arrays.stream(maps).forEach(target::putAll);
		return target;
	}

	@SafeVarargs
	public static <E> Set<E> mergeSets(Set<E> target, Set<E>... sets) {
		Arrays.stream(sets).forEach(target::addAll);
		return target;
	}

	public static <E> Set<E> mergeSets(Set<E> target, Collection<Set<E>> sets) {
		sets.forEach(target::addAll);
		return target;
	}

	public static <Source, T> T mergeEqualOrNull(Collection<Source> sourceCollection, Function<Source, T> valueProducer) {
		List<T> values = sourceCollection.stream().map(valueProducer).filter(Objects::nonNull).distinct().toList();
		if (values.size() > 1) {
			MiscHelper.panic("Cannot merge values as there is more than one distinct and non-null value");
		}
		return values.size() == 1 ? values.getFirst() : null;
	}

	@Deprecated
	public static <Source, T> T mergeEqualOrFallbackToFirst(Collection<Source> sourceCollection, Function<Source, T> valueProducer) {
		// TODO remove this and add versions that need this to quirks
		List<T> values = sourceCollection.stream().map(valueProducer).filter(Objects::nonNull).distinct().toList();
		//if (values.size() > 1) {
		//	   MiscHelper.panic("Cannot merge values as there is more than one distinct and non-null value");
		//}
		return !values.isEmpty() ? values.getFirst() : null;
	}

	public static <Source, T extends Comparable<T>> T mergeMaxOrNull(Collection<Source> sourceCollection, Function<Source, T> valueProducer) {
		return sourceCollection.stream().map(valueProducer).filter(Objects::nonNull).distinct().max(Comparator.naturalOrder()).orElse(null);
	}

	public static <Source, T> List<T> mergeListDistinctValues(Collection<Source> sourceCollection, Function<Source, List<T>> valueProducer) {
		return sourceCollection.stream().map(valueProducer).flatMap(List::stream).filter(Objects::nonNull).distinct().toList();
	}

	public static <S, I, T> Function<S, T> chain(Function<S, I> sourceIntermediaryFunction, Function<I, T> intermediaryTargetFunction) {
		return sourceIntermediaryFunction.andThen(intermediaryTargetFunction);
	}
}
