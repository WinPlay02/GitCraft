package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.meta.GameVersionBuildMeta;
import com.github.winplay02.gitcraft.meta.SimpleVersionMeta;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;

public class SerializationTypes {

	public static final TypeToken<LinkedHashMap<String, OrderedVersion>> TYPE_LINKED_HASH_MAP_STRING_VERSION = new TypeToken<LinkedHashMap<String, OrderedVersion>>() {
	};
	public static final TypeToken<TreeMap<String, String>> TYPE_TREE_MAP_STRING_STRING = new TypeToken<TreeMap<String, String>>() {
	};
	public static final TypeToken<ArrayList<SimpleVersionMeta>> TYPE_LIST_SIMPLE_VERSION_META = new TypeToken<ArrayList<SimpleVersionMeta>>() {
	};
	public static final TypeToken<ArrayList<GameVersionBuildMeta>> TYPE_LIST_GAME_VERSION_BUILD_META = new TypeToken<ArrayList<GameVersionBuildMeta>>() {
	};

	public static final class ConvertToList<V> implements TypeAdapterFactory {
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			if (!List.class.isAssignableFrom(type.getRawType())) {
				return null;
			}
			Type valueType = getTypeArgument(type.getType());
			TypeAdapter<V> elementTypeAdapter = (TypeAdapter<V>) gson.getAdapter(TypeToken.get(valueType));
			return (TypeAdapter<T>) new TypeAdapterValueToList<>(elementTypeAdapter).nullSafe();
		}

		private static Type getTypeArgument(Type type) {
			if (!(type instanceof ParameterizedType parameterizedType)) {
				return Object.class;
			}
			return parameterizedType.getActualTypeArguments()[0];
		}
	}

	public static final class TypeAdapterValueToList<V> extends TypeAdapter<List<V>> {

		private final TypeAdapter<V> valueAdapter;

		private TypeAdapterValueToList(final TypeAdapter<V> valueAdapter) {
			this.valueAdapter = valueAdapter;
		}

		@Override
		public void write(JsonWriter out, List<V> value) throws IOException {
			out.beginArray();
			for (V v : value) {
				valueAdapter.write(out, v);
			}
			out.endArray();
		}

		@Override
		public List<V> read(JsonReader in) throws IOException {
			List<V> list = new ArrayList<>();
			JsonToken token = in.peek();
			switch (token) {
				case BEGIN_ARRAY -> {
					in.beginArray();
					while (in.hasNext()) {
						list.add(this.valueAdapter.read(in));
					}
					in.endArray();
				}
				case BEGIN_OBJECT, STRING, NUMBER, BOOLEAN, NULL -> {
					list.add(this.valueAdapter.read(in));
				}
				case NAME, END_ARRAY, END_OBJECT, END_DOCUMENT -> {
					throw new MalformedJsonException(String.format("Unexpected %s", token));
				}
			}
			return list;
		}
	}

	public static final class VersionArgumentWithRulesAdapter extends TypeAdapter<VersionInfo.VersionArgumentWithRules> {

		private final TypeAdapter<VersionInfo.VersionArgumentWithRules> delegate;

		public VersionArgumentWithRulesAdapter(TypeAdapter<VersionInfo.VersionArgumentWithRules> delegate) {
			this.delegate = delegate;
		}

		@Override
		public void write(JsonWriter out, VersionInfo.VersionArgumentWithRules value) throws IOException {
			delegate.write(out, value);
		}

		@Override
		public VersionInfo.VersionArgumentWithRules read(JsonReader in) throws IOException {
			JsonToken token = in.peek();
			switch (token) {
				case STRING, NUMBER -> {
					return new VersionInfo.VersionArgumentWithRules(List.of(in.nextString()), Collections.emptyList());
				}
				default -> {
					return delegate.read(in);
				}
			}
		}
	}
}
