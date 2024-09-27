package com.github.winplay02.gitcraft.util;

import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.reflect.TypeToken;

public class MetaVersionsSource<V extends Comparable<V>> {

	private final String url;
	private final TypeToken<? extends List<V>> metaType;
	private final Function<V, String> classifier;

	private Map<String, V> latestVersions;

	public MetaVersionsSource(String url, TypeToken<? extends List<V>> metaType, Function<V, String> classifier) {
		this.url = url;
		this.metaType = metaType;
		this.classifier = classifier;
	}

	public V getLatest(String clas) throws IOException {
		if (latestVersions == null) {
			List<V> allVersions = SerializationHelper.deserialize(SerializationHelper.fetchAllFromURL(new URL(url)), metaType);
			Map<String, List<V>> groupedVersions = allVersions.stream().collect(Collectors.groupingBy(classifier));
	
			latestVersions = groupedVersions.values().stream().map(versions -> versions.stream().max(Comparator.naturalOrder())).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toMap(classifier, Function.identity()));
		}

		return latestVersions.get(clas);
	}
}
