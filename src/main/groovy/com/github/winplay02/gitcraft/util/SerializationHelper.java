package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.mappings.yarn.FabricYarnVersionMeta;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

public class SerializationHelper {

	protected static Gson gson;

	public static final TypeToken<LinkedHashMap<String, OrderedVersion>> TYPE_LINKED_HASH_MAP_STRING_VERSION = new TypeToken<LinkedHashMap<String, OrderedVersion>>() {
	};
	public static final TypeToken<TreeMap<String, String>> TYPE_TREE_MAP_STRING_STRING = new TypeToken<TreeMap<String, String>>() {
	};
	public static final TypeToken<ArrayList<SimpleVersionMeta>> TYPE_LIST_SIMPLE_VERSION_META = new TypeToken<ArrayList<SimpleVersionMeta>>() {
	};
	public static final TypeToken<ArrayList<GameVersionBuildMeta>> TYPE_LIST_GAME_VERSION_BUILD_META = new TypeToken<ArrayList<GameVersionBuildMeta>>() {
	};
	public static final TypeToken<ArrayList<FabricYarnVersionMeta>> TYPE_LIST_FABRIC_YARN_VERSION_META = new TypeToken<ArrayList<FabricYarnVersionMeta>>() {
	};

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
		}).registerTypeHierarchyAdapter(ZonedDateTime.class, new TypeAdapter<ZonedDateTime>() {
			static final DateTimeFormatter RFC_3339_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
				.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
				.optionalStart()
				.appendOffset("+HH:MM", "Z")
				.optionalEnd()
				.toFormatter();

			@Override
			public void write(JsonWriter out, ZonedDateTime value) throws IOException {
				if (value == null) {
					out.nullValue();
				} else {
					out.value(RFC_3339_DATE_TIME_FORMATTER.format(value));
				}
			}

			@Override
			public ZonedDateTime read(JsonReader in) throws IOException {
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
					return null;
				} else {
					return ZonedDateTime.parse(in.nextString(), RFC_3339_DATE_TIME_FORMATTER);
				}
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

	private static JsonElement sortJsonElement(JsonElement element) {
		Queue<JsonElement> queue = new LinkedList<>();
		queue.add(element);
		while (!queue.isEmpty()) {
			JsonElement subject = queue.poll();
			if (subject instanceof final JsonObject object) {
				Map<String, JsonElement> mappings = new HashMap<>(object.asMap());
				object.asMap().clear();
				mappings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach((entry) -> object.add(entry.getKey(), entry.getValue()));
				mappings.values().stream().filter(entry -> entry instanceof JsonObject || entry instanceof JsonArray).forEach(queue::add);
			}
			if (subject instanceof final JsonArray array) {
				array.asList().stream().filter(entry -> entry instanceof JsonObject || entry instanceof JsonArray).forEach(queue::add);
			}
		}
		return element;
	}

	public static void sortJSONFile(Path path) throws IOException {
		writeAllToPath(path, serialize(sortJsonElement(gson.fromJson(fetchAllFromPath(path), JsonElement.class))));
	}
}
