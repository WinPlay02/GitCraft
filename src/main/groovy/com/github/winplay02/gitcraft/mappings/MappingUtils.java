package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingNsRenamer;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MappingUtils {
	// From Fabric-loom
	private static final Pattern MC_LV_PATTERN = Pattern.compile("\\$\\$\\d+");

	public static TinyRemapper createTinyRemapper(IMappingProvider mappingProvider) {
		TinyRemapper.Builder remapperBuilder = TinyRemapper.newRemapper()
			.renameInvalidLocals(true)
			.rebuildSourceFilenames(true)
			.invalidLvNamePattern(MC_LV_PATTERN)
			.inferNameFromSameLvIndex(true)
			.withMappings(mappingProvider)
			.fixPackageAccess(true)
			.threads(Library.CONF_GLOBAL.remappingThreads());
		TinyRemapper remapper = remapperBuilder.build();
		return remapper;
	}

	public static TinyRemapper createTinyRemapperSkipLocals(IMappingProvider mappingProvider) {
		TinyRemapper.Builder remapperBuilder = TinyRemapper.newRemapper()
			.renameInvalidLocals(true)
			.rebuildSourceFilenames(true)
			.invalidLvNamePattern(MC_LV_PATTERN)
			.inferNameFromSameLvIndex(true)
			.withMappings(mappingProvider)
			.fixPackageAccess(true)
			.skipLocalVariableMapping(true)
			.threads(Library.CONF_GLOBAL.remappingThreads());
		TinyRemapper remapper = remapperBuilder.build();
		return remapper;
	}

	public static VisitableMappingTree prepareAndCreateTreeFromMappingFlavour(MappingFlavour mapping, IStepContext<?, OrderedVersion> versionContext, MinecraftJar mcJar) throws IOException, URISyntaxException, InterruptedException {
		StepStatus stepStatus = mapping.provide(versionContext, mcJar);
		if (!stepStatus.hasRun()) {
			if (mcJar == MinecraftJar.MERGED) {
				stepStatus = StepStatus.merge(
					mapping.provide(versionContext, MinecraftJar.CLIENT),
					mapping.provide(versionContext, MinecraftJar.SERVER)
				);
			} else {
				stepStatus = mapping.provide(versionContext, MinecraftJar.MERGED);
			}
		}
		if (!stepStatus.isSuccessful()) {
			MiscHelper.panic("Mappings %s could not be prepared for version %s (%s)", stepStatus, versionContext.targetVersion(), mcJar);
		}
		return createTreeFromMappingFlavour(mapping, versionContext.targetVersion(), mcJar);
	}

	public static VisitableMappingTree createTreeFromMappingFlavour(MappingFlavour mapping, OrderedVersion version, MinecraftJar mcJar) {
		return createTreeFromMappingFlavour(mapping.getImpl(), version, mcJar);
	}

	protected static VisitableMappingTree createTreeFromMappingFlavour(Mapping mapping, OrderedVersion version, MinecraftJar mcJar) {
		if (!mapping.canMappingsBeUsedOn(version, mcJar)) {
			MiscHelper.panic("Tried to use %s-mappings for version %s, %s jar. These mappings can not be used for this version.", mapping, version.launcherFriendlyVersionName(), mcJar.name().toLowerCase());
		}
		MemoryMappingTree mappings = new MemoryMappingTree();
		try {
			mapping.visit(version, mcJar, mappings);
		} catch (IOException e) {
			MiscHelper.panicBecause(e, "An error occurred while getting mapping information for %s (version %s)", mapping, version.launcherFriendlyVersionName());
		}
		return mappings;
	}

	public static Stream<String> getNamespaces(VisitableMappingTree mapping) {
		return MiscHelper.concatStreams(
			Stream.of(mapping.getSrcNamespace()),
			mapping.getDstNamespaces().stream()
		);
	}

	public static VisitableMappingTree renameSrcNamespace(VisitableMappingTree mapping, String newSrcName) throws IOException {
		return renameNamespace(mapping, Map.of(Objects.requireNonNull(mapping.getSrcNamespace()), newSrcName));
	}

	public static VisitableMappingTree renameDstNamespace(VisitableMappingTree mapping, String newDstName) throws IOException {
		return renameNamespace(mapping, Map.of(Objects.requireNonNull(mapping.getDstNamespaces().getFirst()), newDstName));
	}

	public static VisitableMappingTree renameNamespace(VisitableMappingTree mapping, Map<String, String> renames) throws IOException {
		MemoryMappingTree outMappingTree = new MemoryMappingTree();
		mapping.accept(new MappingNsRenamer(outMappingTree, renames));
		return outMappingTree;
	}

	public static VisitableMappingTree invert(VisitableMappingTree mapping) throws IOException {
		return invert(mapping, mapping.getDstNamespaces().getFirst());
	}

	public static VisitableMappingTree invert(VisitableMappingTree mapping, String newSrc) throws IOException {
		return invert(mapping, newSrc, false);
	}

	public static VisitableMappingTree invert(VisitableMappingTree mapping, String newSrc, boolean allowNamespaceMerge) throws IOException {
		MemoryMappingTree outMappingTree = new MemoryMappingTree();
		mapping.accept(new MappingSourceNsSwitch(outMappingTree, newSrc, !allowNamespaceMerge));
		return outMappingTree;
	}

	public static VisitableMappingTree fuseKeep(VisitableMappingTree mappingAtoB, VisitableMappingTree mappingBtoC, boolean allowNamespaceMerge) throws IOException {
		MemoryMappingTree intermediateMappingTree = new MemoryMappingTree();
		mappingBtoC.accept(intermediateMappingTree);
		Set<String> nsToRename = getNamespaces(mappingBtoC).collect(Collectors.toSet());
		Map<String, String> nsRename = getNamespaces(mappingAtoB).filter(ns -> !ns.equals(mappingBtoC.getSrcNamespace())).filter(nsToRename::contains).collect(Collectors.toMap(ns -> ns, ns -> String.format("second_ns_%s", ns)));
		mappingAtoB.accept(new MappingSourceNsSwitch(new MappingNsRenamer(intermediateMappingTree, nsRename), mappingBtoC.getSrcNamespace(), !allowNamespaceMerge));
		MemoryMappingTree outMappingTree = new MemoryMappingTree();
		intermediateMappingTree.accept(new MappingSourceNsSwitch(outMappingTree, mappingAtoB.getSrcNamespace(), !allowNamespaceMerge));
		return outMappingTree;
	}

	public static VisitableMappingTree fuse(VisitableMappingTree mappingAtoB, VisitableMappingTree mappingBtoC) throws IOException {
		return fuse(mappingAtoB, mappingBtoC, false);
	}

	public static VisitableMappingTree fuse(VisitableMappingTree mappingAtoB, VisitableMappingTree mappingBtoC, boolean allowNamespaceMerge) throws IOException {
		MemoryMappingTree intermediateMappingTree = new MemoryMappingTree();
		// do not create unwanted mixtures of mappings
		Map<String, String> fallbackMappingsToCommon = MiscHelper.concatStreams(
			getNamespaces(mappingAtoB),
			getNamespaces(mappingBtoC)
		).filter(ns -> !ns.equals(mappingBtoC.getSrcNamespace())).distinct().collect(Collectors.toMap(Function.identity(),$ -> mappingBtoC.getSrcNamespace()));
		//MappingVisitor intermediateMappingTreeCompleter = new MappingNsCompleter(intermediateMappingTree, fallbackMappingsToCommon);
		//
		mappingBtoC.accept(new MappingDstNsReorder(intermediateMappingTree, mappingBtoC.getDstNamespaces().stream().filter(ns -> !ns.equals(mappingAtoB.getSrcNamespace())).toList()));
		mappingAtoB.accept(new MappingSourceNsSwitch(new MappingDstNsReorder(intermediateMappingTree, mappingAtoB.getSrcNamespace()), mappingBtoC.getSrcNamespace(), !allowNamespaceMerge));
		// mappingBtoC.accept(new MappingDstNsReorder(new MappingNsCompleter(intermediateMappingTree, fallbackMappingsToCommon, true), mappingBtoC.getDstNamespaces().stream().filter(ns -> !ns.equals(mappingAtoB.getSrcNamespace())).toList()));
		MemoryMappingTree outMappingTree = new MemoryMappingTree();

		// System.out.println(fallbackMappingsToCommon);
		intermediateMappingTree.accept(new MappingSourceNsSwitch(outMappingTree, mappingAtoB.getSrcNamespace(), !allowNamespaceMerge));
		return outMappingTree;
	}

	public static VisitableMappingTree merge(String srcNs, VisitableMappingTree... mappings) throws IOException {
		int maxPasses = mappings.length;
		MemoryMappingTree outMappingTree = new MemoryMappingTree();
		Queue<VisitableMappingTree> queue = new LinkedList<>(Arrays.stream(mappings).toList());
		// Find mappings with src ns
		{
			Iterator<VisitableMappingTree> it = queue.iterator();
			while (it.hasNext()) {
				VisitableMappingTree next = it.next();
				if (getNamespaces(next).anyMatch(srcNs::equals)) {
					next.accept(outMappingTree);
					it.remove();
					break;
				}
			}
		}
		int currentPasses = 0;
		// Merge others
		while (!queue.isEmpty()) {
			if (currentPasses > maxPasses) {
				MiscHelper.panic("Cannot merge mappings with namespaces, no candidate was found: %s", Arrays.stream(mappings).toList());
			}
			Iterator<VisitableMappingTree> it = queue.iterator();
			while (it.hasNext()) {
				VisitableMappingTree next = it.next();
				if (!MiscHelper.calculateSetIntersection(getNamespaces(outMappingTree).collect(Collectors.toSet()), getNamespaces(next).collect(Collectors.toSet())).isEmpty()) {
					next.accept(outMappingTree);
					it.remove();
				}
			}
			++currentPasses;
		}
		return outMappingTree;
	}

	public static IMappingProvider createProvider(VisitableMappingTree mapping, String src, String dst) {
		return TinyUtils.createMappingProvider(mapping, src, dst);
	}

	public static void remapJar(TinyRemapper remapper, Path jarIn, Path jarOut) throws IOException {
		remapper.readInputs(jarIn);
		try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(jarOut).build()) {
			remapper.apply(consumer, remapper.createInputTag());
		}
		remapper.finish();
	}
}
