package com.github.winplay02.gitcraft.launchagent;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
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
		ClassFile classFile = ClassFile.of();
		ClassModel classModel = classFile.parse(classfileBuffer);
		return classFile.transformClass(classModel, ClassTransform.transformingMethodBodies(methodModel -> methodModel.methodType().toString().equalsIgnoreCase("()V"), CodeTransform.ofStateful(() -> (builder, element) -> {
			// constructor
			builder.aload(0);
			builder.invokespecial(classModel.superclass().orElseThrow().asSymbol(), "<init>", MethodTypeDesc.ofDescriptor("()V"));
			// get class loader
			builder.aload(0);
			builder.invokevirtual(ClassDesc.ofInternalName("java/lang/Object"), "getClass", MethodTypeDesc.ofDescriptor("()Ljava/lang/Class;"));
			builder.invokevirtual(ClassDesc.ofInternalName("java/lang/Class"), "getClassLoader", MethodTypeDesc.ofDescriptor("()Ljava/lang/ClassLoader;"));
			builder.checkcast(ClassDesc.ofInternalName("jdk/internal/loader/BuiltinClassLoader"));
			builder.astore(1);
			// find field with reflection
			builder.aload(1);
			builder.invokevirtual(ClassDesc.ofInternalName("java/lang/Object"), "getClass", MethodTypeDesc.ofDescriptor("()Ljava/lang/Class;"));
			builder.ldc("ucp");
			builder.invokevirtual(ClassDesc.ofInternalName("java/lang/Class"), "getDeclaredField", MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Ljava/lang/reflect/Field;"));
			builder.astore(2);
			// make field accessible
			builder.aload(2);
			builder.loadConstant(1);
			builder.invokevirtual(ClassDesc.ofInternalName("java/lang/reflect/Field"), "setAccessible", MethodTypeDesc.ofDescriptor("(Z)V"));
			// access field
			builder.aload(2);
			builder.aload(1);
			builder.invokevirtual(ClassDesc.ofInternalName("java/lang/reflect/Field"), "get", MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Ljava/lang/Object;"));
			builder.checkcast(ClassDesc.ofInternalName("jdk/internal/loader/URLClassPath"));
			builder.astore(3);
			// access URLs
			builder.aload(3);
			builder.invokevirtual(ClassDesc.ofInternalName("jdk/internal/loader/URLClassPath"), "getURLs", MethodTypeDesc.ofDescriptor("()[Ljava/net/URL;"));
			builder.astore(4);
			builder.new_(ClassDesc.ofInternalName("net/minecraft/launchwrapper/LaunchClassLoader"));
			builder.dup();
			builder.aload(4);
			builder.invokespecial(ClassDesc.ofInternalName("net/minecraft/launchwrapper/LaunchClassLoader"), "<init>", MethodTypeDesc.ofDescriptor("([Ljava/net/URL;)V"));
			builder.putstatic(ClassDesc.ofInternalName("net/minecraft/launchwrapper/Launch"), "classLoader", ClassDesc.ofInternalName("net/minecraft/launchwrapper/LaunchClassLoader"));
			builder.return_();
		})));
	}
}
