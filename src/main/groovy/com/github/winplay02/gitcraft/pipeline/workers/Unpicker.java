package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.mappings.Mapping;
import com.github.winplay02.gitcraft.pipeline.PipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import daomephsta.unpick.api.ConstantUninliner;
import daomephsta.unpick.api.classresolvers.ClassResolvers;
import daomephsta.unpick.api.classresolvers.IClassResolver;
import daomephsta.unpick.api.classresolvers.IConstantResolver;
import daomephsta.unpick.api.classresolvers.IInheritanceChecker;
import daomephsta.unpick.api.constantgroupers.ConstantGroupers;
import daomephsta.unpick.api.constantgroupers.IConstantGrouper;
import net.fabricmc.loom.util.FileSystemUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public record Unpicker(StepWorker.Config config) implements StepWorker<OrderedVersion, Unpicker.Inputs> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, Unpicker.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		StepOutput<OrderedVersion> mergedStatus = unpickJar(pipeline, context, MinecraftJar.MERGED, input.mergedJar().orElse(null), PipelineFilesystemStorage.UNPICKED_MERGED_JAR);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion> clientStatus = unpickJar(pipeline, context, MinecraftJar.CLIENT, input.clientJar().orElse(null), PipelineFilesystemStorage.UNPICKED_CLIENT_JAR);
		StepOutput<OrderedVersion> serverStatus = unpickJar(pipeline, context, MinecraftJar.SERVER, input.serverJar().orElse(null), PipelineFilesystemStorage.UNPICKED_SERVER_JAR);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	public record Inputs(Optional<StorageKey> mergedJar, Optional<StorageKey> clientJar, Optional<StorageKey> serverJar) implements StepInput {
	}

	private StepOutput<OrderedVersion> unpickJar(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, MinecraftJar type, StorageKey inputFile, StorageKey outputFile) throws IOException {
		if (inputFile == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		if (!config.mappingFlavour().canBeUsedOn(context.targetVersion(), type) || !config.mappingFlavour().supportsConstantUnpicking()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Map<String, Path> additionalMappingPaths = config.mappingFlavour().getAdditionalInformation(context.targetVersion(), type);
		if (!additionalMappingPaths.containsKey(Mapping.KEY_UNPICK_CONSTANTS) || !additionalMappingPaths.containsKey(Mapping.KEY_UNPICK_DEFINITIONS)) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path jarIn = pipeline.getStoragePath(inputFile, context);
		Path jarOut = pipeline.getStoragePath(outputFile, context);
		if (Files.exists(jarOut) && !MiscHelper.isJarEmpty(jarOut)) {
			return StepOutput.ofSingle(StepStatus.UP_TO_DATE, outputFile);
		}
		Files.deleteIfExists(jarOut);
		Path librariesDir = pipeline.getStoragePath(PipelineFilesystemStorage.LIBRARIES, context);
		if (librariesDir == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.FAILED);
		}
		unpickSingleJar(
			jarIn,
			jarOut,
			additionalMappingPaths.get(Mapping.KEY_UNPICK_DEFINITIONS),
			additionalMappingPaths.get(Mapping.KEY_UNPICK_CONSTANTS),
			context.targetVersion().libraries().stream().map(artifact -> artifact.resolve(librariesDir)).toList()
		);
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}

	private static FileSystemUtil.Delegate openReadNoExcept(Path zipPath) {
		try {
			return FileSystemUtil.getJarFileSystem(zipPath);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void closeNoExcept(FileSystemUtil.Delegate zipFs) {
		try {
			zipFs.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void unpickSingleJar(Path inputJar, Path outputJar, Path unpickDefinition, Path unpickConstants, Collection<Path> libraries) throws IOException {
		List<FileSystemUtil.Delegate> openedLibraries = libraries.stream().map(Unpicker::openReadNoExcept).toList();
		try (
			FileSystemUtil.Delegate inputPath = FileSystemUtil.getJarFileSystem(inputJar);
			FileSystemUtil.Delegate unpickConstantsPath = FileSystemUtil.getJarFileSystem(unpickConstants);
			Reader unpickDefinitionReader = Files.newBufferedReader(unpickDefinition);
			FileSystemUtil.Delegate outputPath = FileSystemUtil.getJarFileSystem(outputJar, true);
		) {
			IClassResolver inputClassResolver = ClassResolvers.fromDirectory(inputPath.getRoot());
			IClassResolver unpickConstantsClassResolver = ClassResolvers.fromDirectory(unpickConstantsPath.getRoot());

			IClassResolver chainedInputClassResolver = ClassResolvers.classpath(ClassLoader.getPlatformClassLoader())
				.chain(
					openedLibraries.stream().map(library -> ClassResolvers.fromDirectory(library.getRoot())).toArray(IClassResolver[]::new)
				)
				.chain(
					unpickConstantsClassResolver,
					inputClassResolver
				);

			IConstantResolver unpickConstantResolver = chainedInputClassResolver.asConstantResolver();
			IInheritanceChecker unpickInheritanceChecker = chainedInputClassResolver.asInheritanceChecker();
			IConstantGrouper constantGrouper = ConstantGroupers.dataDriven().lenient(true).constantResolver(unpickConstantResolver).inheritanceChecker(unpickInheritanceChecker).mappingSource(unpickDefinitionReader).build();
			ConstantUninliner unInliner = ConstantUninliner.builder().logger(Library.getSubLogger("GitCraft/Unpicker", Level.ALL)).classResolver(chainedInputClassResolver).constantResolver(unpickConstantResolver).inheritanceChecker(unpickInheritanceChecker).grouper(constantGrouper).build();

			try (Stream<Path> walk = Files.walk(inputPath.getRoot())) {
				for (Path path : (Iterable<? extends Path>) walk::iterator) {
					if (!Files.isRegularFile(path)) {
						continue;
					}
					Path resultPath = outputPath.getRoot().resolve(inputPath.getRoot().relativize(path).toString());
					Files.createDirectories(resultPath.getParent());
					if (!path.toString().endsWith(".class")) {
						Files.copy(path, resultPath);
						continue;
					}
					try (
						InputStream classInputStream = Files.newInputStream(path);
						OutputStream classOutputStream = Files.newOutputStream(resultPath)
					) {
						ClassReader classReader = new ClassReader(classInputStream);
						ClassNode classNode = new ClassNode();
						classReader.accept(classNode, 0);

						try {
							unInliner.transform(classNode);
						} catch (Exception e) {
							MiscHelper.println("Only partially transformed class: %s; An exception occurred while unpicking: %s", inputPath.getRoot().relativize(path).toString(), e);
						}

						ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
						classNode.accept(classWriter);
						classOutputStream.write(classWriter.toByteArray());
					}
				}
			} finally {
				openedLibraries.forEach(Unpicker::closeNoExcept);
			}
		}
	}
}
