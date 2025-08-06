package com.github.winplay02.gitcraft.meta;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.winplay02.gitcraft.util.FileSystemNetworkManager;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.google.gson.reflect.TypeToken;

public class RemoteVersionMetaSource<M extends VersionMeta<M>> implements VersionMetaSource<M> {

	private final String url;
	private final TypeToken<? extends List<M>> metaType;
	private final Function<M, String> classifier;

	private Map<String, M> latestVersions;

	public RemoteVersionMetaSource(String url, TypeToken<? extends List<M>> metaType, Function<M, String> classifier) {
		this.url = url;
		this.metaType = metaType;
		this.classifier = classifier;
	}

	public M getLatest(String clas) throws IOException, URISyntaxException, InterruptedException {
		if (latestVersions == null) {
			List<M> allVersions = SerializationHelper.deserialize(FileSystemNetworkManager.fetchAllFromURLSync(new URL(url)), metaType);
			Map<String, List<M>> groupedVersions = allVersions.stream().collect(Collectors.groupingBy(classifier));

			latestVersions = groupedVersions.values().stream().map(versions -> versions.stream().max(Comparator.naturalOrder())).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(classifier, Function.identity()));
		}

		return latestVersions.get(clas);
	}
}
