package com.github.winplay02.gitcraft.launchagent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class GitCraftLauncherTransformer implements ClassFileTransformer {

	protected Map<String, Function<byte[], byte[]>> transformations = new HashMap<>();

	public GitCraftLauncherTransformer() {
		registerTransformer("net/minecraft/launchwrapper/Launch", this::transform_net$minecraft$launchwrapper$Launch);
	}

	@Override
	public byte[] transform(ClassLoader loader, String fullyQualifiedClassName, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		String className = fullyQualifiedClassName.replaceAll(".*/", "");
		String packageName = fullyQualifiedClassName.replaceAll("/[a-zA-Z$0-9_]*$", "");
		if (transformations.containsKey(fullyQualifiedClassName)) {
			System.out.println(String.format("Transforming %s (package %s)",  className, packageName));
			return transformations.get(fullyQualifiedClassName).apply(classfileBuffer);
		}
		return null;
	}

	protected void registerTransformer(String fullyQualifiedClassName, Function<byte[], byte[]> transformation) {
		transformations.put(fullyQualifiedClassName, transformation);
	}

	protected byte[] transform_net$minecraft$launchwrapper$Launch(byte[] classfileBuffer) {

	}
}
