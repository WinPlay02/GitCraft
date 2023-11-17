package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.meta.FabricYarnVersionMeta;
import com.github.winplay02.gitcraft.meta.VersionMeta;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;

public class SerializationHelper {

	protected static Gson gson;

	public static final TypeToken<LinkedHashMap<String, OrderedVersion>> TYPE_LINKED_HASH_MAP_STRING_VERSION = new TypeToken<LinkedHashMap<String, OrderedVersion>>() {
	};
	public static final TypeToken<TreeMap<String, String>> TYPE_TREE_MAP_STRING_STRING = new TypeToken<TreeMap<String, String>>() {
	};
	public static final TypeToken<ArrayList<FabricYarnVersionMeta>> TYPE_LIST_FABRIC_YARN_VERSION_META = new TypeToken<ArrayList<FabricYarnVersionMeta>>() {
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

	public static final class VersionArgumentWithRulesAdapter extends TypeAdapter<VersionMeta.VersionArgumentWithRules> {

		private final TypeAdapter<VersionMeta.VersionArgumentWithRules> delegate;

		public VersionArgumentWithRulesAdapter(TypeAdapter<VersionMeta.VersionArgumentWithRules> delegate) {
			this.delegate = delegate;
		}

		@Override
		public void write(JsonWriter out, VersionMeta.VersionArgumentWithRules value) throws IOException {
			delegate.write(out, value);
		}

		@Override
		public VersionMeta.VersionArgumentWithRules read(JsonReader in) throws IOException {
			JsonToken token = in.peek();
			switch (token) {
				case STRING, NUMBER -> {
					return new VersionMeta.VersionArgumentWithRules(List.of(in.nextString()), Collections.emptyList());
				}
				default -> {
					return delegate.read(in);
				}
			}
		}
	}

	static {
		gson = new GsonBuilder().registerTypeHierarchyAdapter(Path.class, new TypeAdapter<Path>() {
			@Override
			public void write(JsonWriter out, Path value) throws IOException {
				if (value == null) {
					out.nullValue();
				} else {
					out.value(value.toFile().getCanonicalPath());
				}
			}

			@Override
			public Path read(JsonReader in) throws IOException {
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
					return null;
				} else {
					return Path.of(in.nextString());
				}
			}
		}).registerTypeAdapterFactory(new TypeAdapterFactory() {
			@Override
			public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
				Class<? super T> requestedType = type.getRawType();
				if (!VersionMeta.VersionArgumentWithRules.class.isAssignableFrom(requestedType)) {
					return null;
				}
				TypeAdapter<VersionMeta.VersionArgumentWithRules> delegate = (TypeAdapter<VersionMeta.VersionArgumentWithRules>) gson.getDelegateAdapter(this, type);
				return (TypeAdapter<T>) new VersionArgumentWithRulesAdapter(delegate);
			}
		}).setPrettyPrinting().create();
	}

	public static <T> String serialize(T objectToSerialize) {
		StringWriter writer = new StringWriter();
		JsonWriter jsonWriter = new JsonWriter(writer);
		jsonWriter.setIndent("\t");
		gson.toJson(objectToSerialize, objectToSerialize.getClass(), jsonWriter);
		return writer.toString();
	}

	public static void writeAllToPath(Path path, String json) throws IOException {
		Files.writeString(path, json, StandardCharsets.UTF_8);
	}

	public static <T> T deserialize(String json, Class<T> type) {
		return gson.fromJson(json, type);
	}

	public static <T> T deserialize(String json, TypeToken<T> type) {
		return gson.fromJson(json, type);
	}

	public static String fetchAllFromURL(URL url) throws IOException {
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		try (InputStream inStream = new BufferedInputStream(url.openConnection().getInputStream())) {
			inStream.transferTo(outStream);
		}
		return outStream.toString(StandardCharsets.UTF_8);
	}

	public static String fetchAllFromPath(Path path) throws IOException {
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		try (InputStream inStream = new BufferedInputStream(Files.newInputStream(path, StandardOpenOption.READ))) {
			inStream.transferTo(outStream);
		}
		return outStream.toString(StandardCharsets.UTF_8);
	}
}
