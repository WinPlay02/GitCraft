package com.github.winplay02.gitcraft.nests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.util.MiscHelper;

import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;

import net.ornithemc.nester.nest.Nest;
import net.ornithemc.nester.nest.NestType;
import net.ornithemc.nester.nest.NesterIo;
import net.ornithemc.nester.nest.Nests;

/**
 * Nests mapper based on https://github.com/OrnitheMC/mapping-utils/blob/main/src/main/java/net/ornithemc/mappingutils/NestsMapper.java
 */
class NestsMapper {

	static StepStatus mapNests(Path srcPath, Path dstPath, MappingTree mappings, String targetNamespace) {
		return new NestsMapper(srcPath, dstPath, mappings, targetNamespace).run();
	}

	private final Path srcPath;
	private final Path dstPath;
	private final MappingTree mappings;
	private final int dstNs;

	private NestsMapper(Path srcPath, Path dstPath, MappingTree mappings, String targetNamespace) {
		this.srcPath = srcPath;
		this.dstPath = dstPath;
		this.mappings = mappings;
		this.dstNs = this.mappings.getNamespaceId(targetNamespace);
	}

	private StepStatus run() {
		Nests src = Nests.of(srcPath);
		Nests dst = Nests.empty();

		for (Nest nest : src) {
			dst.add(map(nest));
		}

		try {
			NesterIo.write(dst, dstPath);
			return StepStatus.SUCCESS;
		} catch (IOException e) {
			try {
				Files.deleteIfExists(dstPath);
			} catch (IOException e1) {
				MiscHelper.panicBecause(e1, "unable to delete corrupted nests file!");
			}
			return StepStatus.FAILED;
		}
	}

	private Nest map(Nest nest) {
		NestType type = nest.type;
		String className = mapClassName(nest.className);
		String enclClassName = mapOuterName(nest.className, nest.enclClassName);
		String enclMethodName = (nest.enclMethodName == null) ? null : mapMethodName(nest.enclClassName, nest.enclMethodName, nest.enclMethodDesc);
		String enclMethodDesc = (nest.enclMethodDesc == null) ? null : mapMethodDesc(nest.enclClassName, nest.enclMethodName, nest.enclMethodDesc);
		String innerName = mapInnerName(nest.className, nest.innerName);
		int access = nest.access;

		return new Nest(type, className, enclClassName, enclMethodName, enclMethodDesc, innerName, access);
	}

	private String mapClassName(String name) {
		return mappings.mapClassName(name, dstNs);
	}

	private String mapMethodName(String className, String name, String desc) {
		MethodMapping method = mappings.getMethod(className, name, desc);
		return method == null ? name : method.getName(dstNs);
	}

	private String mapMethodDesc(String className, String name, String desc) {
		return mappings.mapDesc(desc, dstNs);
	}

	private String mapOuterName(String name, String enclClassName) {
		String mappedName = mapClassName(name);
		int idx = mappedName.lastIndexOf("__");

		if (idx > 0) {
			// provided mappings already apply nesting
			return mappedName.substring(0, idx);
		}

		return mapClassName(enclClassName);
	}

	private String mapInnerName(String name, String innerName) {
		String mappedName = mapClassName(name);
		int idx = mappedName.lastIndexOf("__");

		if (idx > 0) {
			// provided mappings already apply nesting
			return mappedName.substring(idx + 2);
		}

		int i = 0;
		while (i < innerName.length() && Character.isDigit(innerName.charAt(i))) {
			i++;
		}
		if (i < innerName.length()) {
			// local classes have a number prefix
			String prefix = innerName.substring(0, i);
			String simpleName = innerName.substring(i);

			// make sure the class does not have custom inner name
			if (name.endsWith(simpleName)) {
				// inner name is full name with package stripped
				// so translate that
				innerName = prefix + mappedName.substring(mappedName.lastIndexOf('/') + 1);
			}
		} else {
			// anonymous class
			String simpleName = mappedName.substring(mappedName.lastIndexOf('/') + 1);

			if (simpleName.startsWith("class_") || simpleName.startsWith("C_")) {
				// mapped name is Calamus or Fabric intermediary format C_<number>
				// we strip the C_ prefix and keep the number as the inner name
				return simpleName.substring(simpleName.lastIndexOf('_') + 1);
			} else {
				// keep the inner name given by the nests file
				return innerName;
			}
		}

		return innerName;
	}
}
