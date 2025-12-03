package com.github.winplay02.gitcraft.pipeline.workers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;

import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.LibraryPaths;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.mappings.MappingUtils;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IPipeline;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepConfig;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.unpick.Unpick;
import com.github.winplay02.gitcraft.unpick.UnpickDescriptionFile;
import com.github.winplay02.gitcraft.unpick.UnpickFlavour;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import daomephsta.unpick.api.ConstantUninliner;
import daomephsta.unpick.api.classresolvers.ClassResolvers;
import daomephsta.unpick.api.classresolvers.IClassResolver;
import daomephsta.unpick.api.classresolvers.IConstantResolver;
import daomephsta.unpick.api.classresolvers.IInheritanceChecker;
import daomephsta.unpick.api.classresolvers.IMemberChecker;
import daomephsta.unpick.api.constantgroupers.ConstantGroupers;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Reader;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Remapper;
import daomephsta.unpick.constantmappers.datadriven.tree.UnpickV3Visitor;
import daomephsta.unpick.impl.constantmappers.datadriven.DataDrivenConstantGrouper;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.JarPackageIndex;
import net.fabricmc.mappingio.tree.VisitableMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public record Unpicker(GitCraftStepConfig config) implements GitCraftStepWorker<GitCraftStepWorker.JarTupleInput> {

	@Override
	public boolean shouldExecute(IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline, IStepContext.SimpleStepContext<OrderedVersion> context) {
		return this.config().unpickFlavour() != UnpickFlavour.NONE; // optimization
	}

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		GitCraftStepWorker.JarTupleInput input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> mergedStatus = unpickJar(pipeline, context, MinecraftJar.MERGED, input.mergedJar().orElse(null), GitCraftPipelineFilesystemStorage.UNPICKED_MERGED_JAR);
		if (mergedStatus.status().isSuccessful()) {
			return mergedStatus;
		}
		StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> clientStatus = unpickJar(pipeline, context, MinecraftJar.CLIENT, input.clientJar().orElse(null), GitCraftPipelineFilesystemStorage.UNPICKED_CLIENT_JAR);
		StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> serverStatus = unpickJar(pipeline, context, MinecraftJar.SERVER, input.serverJar().orElse(null), GitCraftPipelineFilesystemStorage.UNPICKED_SERVER_JAR);
		return StepOutput.merge(clientStatus, serverStatus);
	}

	private StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> unpickJar(IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
																													 IStepContext.SimpleStepContext<OrderedVersion> context, MinecraftJar type, StorageKey inputFile, StorageKey outputFile) throws IOException, URISyntaxException, InterruptedException {
		if (inputFile == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		if (!config.unpickFlavour().exists(context.targetVersion())) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Unpick.UnpickContext unpickContext = config.unpickFlavour().getContext(context.targetVersion(), type);
		if (unpickContext == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		Path jarIn = pipeline.getStoragePath(inputFile, context, this.config);
		Path jarOut = pipeline.getStoragePath(outputFile, context, this.config);
		if (Files.exists(jarOut) && !MiscHelper.isJarEmpty(jarOut)) {
			return StepOutput.ofSingle(StepStatus.UP_TO_DATE, outputFile);
		}
		Files.deleteIfExists(jarOut);
		Path librariesDir = pipeline.getStoragePath(GitCraftPipelineFilesystemStorage.LIBRARIES, context, this.config);
		if (librariesDir == null) {
			return StepOutput.ofEmptyResultSet(StepStatus.FAILED);
		}
		unpickSingleJar(
			context,
			config.mappingFlavour(),
			config.unpickFlavour(),
			type,
			jarIn,
			jarOut,
			unpickContext.unpickDefinitions(),
			unpickContext.unpickConstants(),
			context.targetVersion().libraries().stream().map(artifact -> artifact.resolve(librariesDir)).toList(),
			getUnpickDescriptionFile(unpickContext)
		);
		return StepOutput.ofSingle(StepStatus.SUCCESS, outputFile);
	}

	private static final UnpickDescriptionFile DEFAULT_LEGACY_UNPICK_DESCRIPTION = new UnpickDescriptionFile(1, MappingsNamespace.NAMED.toString());

	private static UnpickDescriptionFile getUnpickDescriptionFile(Unpick.UnpickContext unpickContext) throws IOException {
		if (unpickContext.unpickDescription() == null || !Files.exists(unpickContext.unpickDescription())) {
			return DEFAULT_LEGACY_UNPICK_DESCRIPTION;
		}
		UnpickDescriptionFile read = SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(unpickContext.unpickDescription()), UnpickDescriptionFile.class);
		return read.namespace() == null ? new UnpickDescriptionFile(read.version(), DEFAULT_LEGACY_UNPICK_DESCRIPTION.namespace()) : read;
	}

	private static Class<UnpickV3Remapper> c_UnpickRemapperService$UnpickRemapper;
	private static Constructor<UnpickV3Remapper> constructor_UnpickRemapperService$UnpickRemapper;

	private static UnpickV3Remapper construct_net_fabricmc_loom_task_service_UnpickRemapperService$UnpickRemapper(UnpickV3Visitor downstream, TinyRemapper tinyRemapper, JarPackageIndex jarPackageIndex) {
		if (constructor_UnpickRemapperService$UnpickRemapper == null) {
			try {
				c_UnpickRemapperService$UnpickRemapper = (Class<UnpickV3Remapper>) Class.forName("net.fabricmc.loom.task.service.UnpickRemapperService$UnpickRemapper");
				constructor_UnpickRemapperService$UnpickRemapper = c_UnpickRemapperService$UnpickRemapper.getDeclaredConstructor(UnpickV3Visitor.class, TinyRemapper.class, JarPackageIndex.class);
				constructor_UnpickRemapperService$UnpickRemapper.setAccessible(true);
			} catch (ClassNotFoundException | NoSuchMethodException e) {
				throw new RuntimeException(e);
			}
		}
		try {
			return constructor_UnpickRemapperService$UnpickRemapper.newInstance(downstream, tinyRemapper, jarPackageIndex);
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
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

	private static Consumer<UnpickV3Visitor> createUnpickV3VisitorRemapper(UnpickV3Reader unpickReader, TinyRemapper tinyRemapper, JarPackageIndex jarPackageIndex) {
		return targetVisitor -> {
			UnpickV3Remapper remapper = construct_net_fabricmc_loom_task_service_UnpickRemapperService$UnpickRemapper(
				targetVisitor,
				tinyRemapper,
				jarPackageIndex
			);
			try {
				unpickReader.accept(remapper);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};
	}

	private static void unpickSingleJar(IStepContext.SimpleStepContext<OrderedVersion> context, MappingFlavour mappingFlavour, UnpickFlavour unpickFlavour, MinecraftJar type, Path inputJar, Path outputJar, Path unpickDefinition, Path unpickConstants, Collection<Path> libraries, UnpickDescriptionFile unpickDescription) throws IOException, URISyntaxException, InterruptedException {
		List<FileSystemUtil.Delegate> openedLibraries = libraries.stream().map(Unpicker::openReadNoExcept).toList();
		List<Path> jarsClasspath = new ArrayList<>(libraries);
		final FileSystemUtil.Delegate unpickConstantsPath;
		if (unpickConstants != null) {
			unpickConstantsPath = FileSystemUtil.getJarFileSystem(unpickConstants);
			jarsClasspath.add(unpickConstants);
		} else {
			unpickConstantsPath = null;
		}
		try (
			FileSystemUtil.Delegate inputPath = FileSystemUtil.getJarFileSystem(inputJar);
			unpickConstantsPath;
			Reader unpickDefinitionReader = Files.newBufferedReader(unpickDefinition);
			FileSystemUtil.Delegate outputPath = FileSystemUtil.getJarFileSystem(outputJar, true);
		) {
			IClassResolver inputClassResolver = ClassResolvers.fromDirectory(inputPath.getRoot());
			jarsClasspath.add(inputJar);

			IClassResolver unpickConstantsClassResolver = unpickConstantsPath != null ? ClassResolvers.fromDirectory(unpickConstantsPath.getRoot()) : null;

			IClassResolver chainedInputClassResolver = ClassResolvers.classpath(ClassLoader.getPlatformClassLoader())
				.chain(
					openedLibraries.stream().map(library -> ClassResolvers.fromDirectory(library.getRoot())).toArray(IClassResolver[]::new)
				);

			if (unpickConstantsClassResolver != null) {
				chainedInputClassResolver = chainedInputClassResolver.chain(
					unpickConstantsClassResolver,
					inputClassResolver
				);
			} else {
				chainedInputClassResolver = chainedInputClassResolver.chain(inputClassResolver);
			}

			IConstantResolver unpickConstantResolver = chainedInputClassResolver.asConstantResolver();
			IInheritanceChecker unpickInheritanceChecker = chainedInputClassResolver.asInheritanceChecker();
			IMemberChecker unpickMemberChecker = chainedInputClassResolver.asMemberChecker();
			final ConstantUninliner unInliner;
			// Remap Unpick
			MappingFlavour applicableMappingFlavour = unpickFlavour.applicableMappingFlavour(unpickDescription);
			if (applicableMappingFlavour != mappingFlavour) {
				if (unpickFlavour.supportsRemapping(unpickDescription)) {
					UnpickV3Reader unpickReader = new UnpickV3Reader(unpickDefinitionReader);
					VisitableMappingTree applicableUnpickMappingToConfiguredMapping = MappingUtils.fuse(
						MappingUtils.renameNamespace(
							MappingUtils.invert(
								MappingUtils.prepareAndCreateTreeFromMappingFlavour(applicableMappingFlavour, context, type),
								applicableMappingFlavour.getDestinationNS()
							),
							Map.of(
								applicableMappingFlavour.getDestinationNS(), "remap_unpick_domain",
								applicableMappingFlavour.getSourceNS(), "remap_unpick_common"
							)
						),
						MappingUtils.renameNamespace(
							MappingUtils.prepareAndCreateTreeFromMappingFlavour(mappingFlavour, context, type),
							Map.of(
								mappingFlavour.getSourceNS(), "remap_unpick_common",
								mappingFlavour.getDestinationNS(), "remap_unpick_target"
							)
						)
					);
					try (
						LibraryPaths.TmpFileGuard tmpFileRemapped = LibraryPaths.getTmpFile("unpick-remapped", String.join("-", mappingFlavour.toString(), unpickFlavour.toString(), applicableMappingFlavour.toString()) + ".jar");
					) {
						// Create JAR for unpick domain
						IMappingProvider mapJarToUnpick = MappingUtils.createProvider(MappingUtils.invert(applicableUnpickMappingToConfiguredMapping, "remap_unpick_target"), "remap_unpick_target", "remap_unpick_domain");
						MappingUtils.remapJar(MappingUtils.createTinyRemapperSkipLocals(mapJarToUnpick), inputJar, tmpFileRemapped.filePath());
						// Remap unpick; Prepare remapper
						TinyRemapper remapper = MappingUtils.createTinyRemapper(
							MappingUtils.createProvider(
								applicableUnpickMappingToConfiguredMapping,
								"remap_unpick_domain",
								"remap_unpick_target"
							)
						);
						remapper.readInputs(tmpFileRemapped.filePath());
						remapper.readInputs(libraries.toArray(Path[]::new));
						// Create unpick stuff
						Consumer<UnpickV3Visitor> unpickVisitorConsumer = createUnpickV3VisitorRemapper(unpickReader, remapper, JarPackageIndex.create(jarsClasspath));
						DataDrivenConstantGrouper constantGrouper = (DataDrivenConstantGrouper) ConstantGroupers.dataDriven().lenient(true).constantResolver(unpickConstantResolver).inheritanceChecker(unpickInheritanceChecker).memberChecker(unpickMemberChecker).mappingSource(unpickVisitorConsumer).build();
						unInliner = ConstantUninliner.builder().logger(Library.getSubLogger("GitCraft/Unpicker", Level.ALL)).classResolver(chainedInputClassResolver).constantResolver(unpickConstantResolver).inheritanceChecker(unpickInheritanceChecker).grouper(constantGrouper).build();
						remapper.finish();
					}
				} else {
					// Remap Jar
					try (
						LibraryPaths.TmpFileGuard tmpFileRemapped = LibraryPaths.getTmpFile("unpick-remapped", String.join("-", mappingFlavour.toString(), unpickFlavour.toString(), applicableMappingFlavour.toString()) + ".jar");
						LibraryPaths.TmpFileGuard tmpFileRemappedAndUnpicked = LibraryPaths.getTmpFile("unpick-remapped", String.join("-", mappingFlavour.toString(), unpickFlavour.toString(), applicableMappingFlavour.toString(), "unpicked") + ".jar")
					) {
						VisitableMappingTree jarMappings = MappingUtils.prepareAndCreateTreeFromMappingFlavour(mappingFlavour, context, type);
						VisitableMappingTree unpickMappings = MappingUtils.prepareAndCreateTreeFromMappingFlavour(applicableMappingFlavour, context, type);
						// Map to unpick
						{
							VisitableMappingTree fusedMappingsBackwards = MappingUtils.fuse(
								MappingUtils.renameNamespace(
									MappingUtils.invert(jarMappings, mappingFlavour.getDestinationNS()),
									Map.of(
										mappingFlavour.getDestinationNS(), "remap_unpick_src",
										mappingFlavour.getSourceNS(), "remap_unpick_common"
									)
								),
								MappingUtils.renameNamespace(
									unpickMappings,
									Map.of(
										applicableMappingFlavour.getSourceNS(), "remap_unpick_common",
										applicableMappingFlavour.getDestinationNS(), "remap_unpick_dst"
									)
								)
							);
							IMappingProvider mapJarToUnpick = MappingUtils.createProvider(fusedMappingsBackwards, "remap_unpick_src", "remap_unpick_dst");
							MappingUtils.remapJar(MappingUtils.createTinyRemapperSkipLocals(mapJarToUnpick), inputJar, tmpFileRemapped.filePath());
						}
						// Do unpick in correct mapping space
						if (applicableMappingFlavour != unpickFlavour.applicableMappingFlavour(unpickDescription)) {
							MiscHelper.panic("Applicable mapping flavour does not match current mapping flavour after remapping");
						}
						unpickSingleJar(context, applicableMappingFlavour, unpickFlavour, type, tmpFileRemapped.filePath(), tmpFileRemappedAndUnpicked.filePath(), unpickDefinition, unpickConstants, libraries, unpickDescription);
						// Remap unpicked to named
						{
							VisitableMappingTree fusedMappingsForwards = MappingUtils.fuse(
								MappingUtils.renameNamespace(
									MappingUtils.invert(unpickMappings, applicableMappingFlavour.getDestinationNS()),
									Map.of(
										applicableMappingFlavour.getDestinationNS(), "remap_unpick_dst",
										applicableMappingFlavour.getSourceNS(), "remap_unpick_common"
									)
								),
								MappingUtils.renameNamespace(
									jarMappings,
									Map.of(
										mappingFlavour.getSourceNS(), "remap_unpick_common",
										mappingFlavour.getDestinationNS(), "remap_unpick_src"
									)
								)
							);
							IMappingProvider mapJarToUnpick = MappingUtils.createProvider(fusedMappingsForwards, "remap_unpick_dst", "remap_unpick_src");
							MappingUtils.remapJar(MappingUtils.createTinyRemapper(mapJarToUnpick), tmpFileRemappedAndUnpicked.filePath(), outputJar);
						}
						return;
					}
				}
			} else {
				DataDrivenConstantGrouper constantGrouper = (DataDrivenConstantGrouper) ConstantGroupers.dataDriven().lenient(true).constantResolver(unpickConstantResolver).inheritanceChecker(unpickInheritanceChecker).memberChecker(unpickMemberChecker).mappingSource(unpickDefinitionReader).build();
				unInliner = ConstantUninliner.builder().logger(Library.getSubLogger("GitCraft/Unpicker", Level.ALL)).classResolver(chainedInputClassResolver).constantResolver(unpickConstantResolver).inheritanceChecker(unpickInheritanceChecker).grouper(constantGrouper).build();
			}

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
