package com.github.winplay02.gitcraft.launchagent;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class GitCraftResourceDownloaderTransformer implements ClassFileTransformer {
	public static final String DEAD_RESOURCE_URL = "http://s3.amazonaws.com/MinecraftResources/";
	public static final String REPLACEMENT_RESOURCE_URL = "https://web.archive.org/web/20220916212449if_/https://s3.amazonaws.com/MinecraftResources";

	@Override
	public byte[] transform(ClassLoader loader, String fullyQualifiedClassName, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		ClassFile classFile = ClassFile.of();
		ClassModel classModel = classFile.parse(classfileBuffer);
		if (!isClassRelevantToTransform(classModel)) {
			return null;
		}
		System.out.printf("[GitCraft Agent]: Transforming %s (by ResourceDownloaderTransformer)%n", fullyQualifiedClassName);
		return transform_class_with_resource_downloader(classFile, classModel);
	}

	private boolean isClassRelevantToTransform(ClassModel classModel) {
		for (PoolEntry poolEntry : classModel.constantPool()) {
			if (poolEntry instanceof Utf8Entry utf8Entry) {
				if (utf8Entry.equalsString(DEAD_RESOURCE_URL)) {
					return true;
				}
			}
		}
		return false;
	}

	private byte[] transform_class_with_resource_downloader(ClassFile classFile, ClassModel classModel) {
		return classFile.transformClass(classModel, ClassTransform.transformingMethodBodies((codeBuilder, codeElement) -> {
			if (codeElement instanceof ConstantInstruction.LoadConstantInstruction ldc) {
				if (ldc.constantValue().equals(DEAD_RESOURCE_URL)) {
					codeBuilder.loadConstant(REPLACEMENT_RESOURCE_URL);
					return;
				}
			}
			codeBuilder.accept(codeElement);
		}));
	}
}
