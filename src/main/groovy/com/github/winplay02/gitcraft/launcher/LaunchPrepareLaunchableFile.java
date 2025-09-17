package com.github.winplay02.gitcraft.launcher;

import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IPipeline;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepConfig;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.GitCraftStepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.loom.util.FileSystemUtil;

import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.FieldBuilder;
import java.lang.classfile.FieldElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.FieldTransform;
import java.lang.classfile.MethodBuilder;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

public record LaunchPrepareLaunchableFile(GitCraftStepConfig config) implements GitCraftStepWorker<LaunchPrepareLaunchableFile.Inputs> {

	private class ClassAccessWidenFileTransformer implements MiscHelper.PathContentTransformer {

		@Override
		public boolean shouldTransform(Path path) {
			return path.getFileName().toString().endsWith(".class") && LaunchPrepareLaunchableFile.this.config().mappingFlavour().needsPackageFixingForLaunch();
		}

		@Override
		public byte[] transform(Path path, byte[] content) {
			return transformClassToPublicAccess(content);
		}
	}

	@Override
	public StepOutput<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> run(
		IPipeline<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> pipeline,
		IStepContext.SimpleStepContext<OrderedVersion> context,
		LaunchPrepareLaunchableFile.Inputs input,
		StepResults<OrderedVersion, IStepContext.SimpleStepContext<OrderedVersion>, GitCraftStepConfig> results
	) throws Exception {
		Path clientOriginalPath = pipeline.getStoragePath(GitCraftPipelineFilesystemStorage.ARTIFACTS_CLIENT_JAR, context, this.config);
		Path clientModifiedPath = pipeline.getStoragePath(input.clientJar().orElse(null), context, this.config);
		Path clientOutputPath = results.getPathForKeyAndAdd(pipeline, context, this.config, GitCraftPipelineFilesystemStorage.LAUNCHABLE_CLIENT_JAR);
		Files.createDirectories(clientOutputPath.getParent());
		if (Files.exists(clientOutputPath) && !MiscHelper.isJarEmpty(clientOutputPath)) {
			return new StepOutput<>(StepStatus.UP_TO_DATE, results);
		}
		MiscHelper.PathContentTransformer classFileAccessWidenerTransform = new ClassAccessWidenFileTransformer();
		try (
			FileSystemUtil.Delegate originalClient = FileSystemUtil.getJarFileSystem(clientOriginalPath);
			FileSystemUtil.Delegate modifiedClient = FileSystemUtil.getJarFileSystem(clientModifiedPath);
			FileSystemUtil.Delegate outputClient = FileSystemUtil.getJarFileSystem(clientOutputPath, true);
			Stream<Path> modifiedClientFileStream = Files.list(modifiedClient.getRoot());
			Stream<Path> originalClientFileStream = Files.list(originalClient.getRoot());
		) {
			for (Path modifiedPath : modifiedClientFileStream.toList()) {
				Path outputPath = outputClient.getRoot().resolve(modifiedClient.getRoot().relativize(modifiedPath));
				if (modifiedPath.getFileName().toString().equals("META-INF") && Files.exists(modifiedPath.resolve("MANIFEST.MF"))) {
					continue;
				}
				if (Files.isRegularFile(modifiedPath)) {
					if (classFileAccessWidenerTransform.shouldTransform(modifiedPath)) {
						Files.write(outputPath, classFileAccessWidenerTransform.transform(modifiedPath, Files.readAllBytes(modifiedPath)), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
					} else {
						Files.copy(modifiedPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
					}
				} else {
					MiscHelper.copyLargeDir(modifiedPath, outputPath, classFileAccessWidenerTransform);
				}
			}
			for (Path modifiedPath : originalClientFileStream.toList()) {
				Path outputPath = outputClient.getRoot().resolve(originalClient.getRoot().relativize(modifiedPath));
				if (modifiedPath.getFileName().toString().equals("META-INF")) {
					Files.createDirectories(outputPath);
					Path manifestFile = outputPath.resolve("MANIFEST.MF");
					Files.writeString(manifestFile, "Manifest-Version: 1.0\r\n", StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
					continue;
				}
				if (Files.isRegularFile(modifiedPath) && !modifiedPath.getFileName().toString().endsWith(".class")) {
					Files.copy(modifiedPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
				} else if (Files.isDirectory(modifiedPath) && !Files.exists(outputPath)) {
					MiscHelper.copyLargeDirExceptNoFileExt(modifiedPath, outputPath, List.of(), Set.of("class"));
				}
			}
		} catch (Exception e) {
			Files.deleteIfExists(clientOutputPath);
			throw e;
		}
		return new StepOutput<>(StepStatus.SUCCESS, results);
	}

	private static class AccessFlagTransform implements Function<Set<AccessFlag>, Set<AccessFlag>> {
		private static final Set<AccessFlag> VISIBILITY_MODIFIER_TO_REMOVE = Set.of(AccessFlag.PROTECTED);
		private static final Set<AccessFlag> PUBLIC_MODIFIER_SET = Set.of(AccessFlag.PUBLIC);
		@Override
		public Set<AccessFlag> apply(Set<AccessFlag> accessFlags) {
			if (accessFlags.contains(AccessFlag.PUBLIC) || accessFlags.contains(AccessFlag.PRIVATE)) { // if private is included, this might mess up inheritance
				return accessFlags;
			}
			if (accessFlags.isEmpty()) {
				return PUBLIC_MODIFIER_SET;
			}
			EnumSet<AccessFlag> accessFlagsSet = EnumSet.copyOf(accessFlags);
			accessFlagsSet.removeAll(VISIBILITY_MODIFIER_TO_REMOVE);
			accessFlagsSet.add(AccessFlag.PUBLIC);
			return accessFlagsSet;
		}
	}

	private static final AccessFlagTransform accessFlagTransform = new AccessFlagTransform();

	private static final Function<Set<AccessFlag>, Integer> accessFlagTransformToMask = accessFlagTransform.andThen(flags -> flags.stream().map(AccessFlag::mask).reduce(0, (a, b) -> a | b));

	private static class AccessWidenMethodTransform implements MethodTransform {

		@Override
		public void accept(MethodBuilder builder, MethodElement element) {
			switch (element) {
				case AccessFlags flags -> builder.withFlags(accessFlagTransformToMask.apply(flags.flags()));
				default -> builder.with(element);
			}
		}
	}

	private static final AccessWidenMethodTransform accessWidenMethodTransform = new AccessWidenMethodTransform();

	private static class AccessWidenFieldTransform implements FieldTransform {

		@Override
		public void accept(FieldBuilder builder, FieldElement element) {
			switch (element) {
				case AccessFlags flags -> builder.withFlags(accessFlagTransformToMask.apply(flags.flags()));
				default -> builder.with(element);
			}
		}
	}

	private static final AccessWidenFieldTransform accessWidenFieldTransform = new AccessWidenFieldTransform();

	private static class AccessWidenClassTransform implements ClassTransform {

		@Override
		public void accept(ClassBuilder builder, ClassElement element) {
			switch (element) {
				case AccessFlags flags -> builder.withFlags(accessFlagTransformToMask.apply(flags.flags()));
				case FieldModel fieldModel -> builder.transformField(fieldModel, accessWidenFieldTransform);
				case MethodModel methodModel -> builder.transformMethod(methodModel, accessWidenMethodTransform);
				case InnerClassesAttribute innerClasses -> builder.accept(InnerClassesAttribute.of(
					innerClasses.classes().stream().map(
						innerClassInfo -> InnerClassInfo.of(innerClassInfo.innerClass(), innerClassInfo.outerClass(), innerClassInfo.innerName(), accessFlagTransformToMask.apply(innerClassInfo.flags()))
					).toList()
				));
				default -> builder.accept(element);
			}
		}
	}

	private static final AccessWidenClassTransform accessWidenClassTransform = new AccessWidenClassTransform();

	private byte[] transformClassToPublicAccess(byte[] classFileContent) {
		ClassFile cf = ClassFile.of();
		ClassModel classModel = cf.parse(classFileContent);
		return cf.transformClass(classModel, accessWidenClassTransform);
	}

	public record Inputs(Optional<StorageKey> clientJar) implements StepInput {
	}
}
