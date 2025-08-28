package com.github.winplay02.gitcraft.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Function;

public class SerializationHelper {
	protected static Gson gson;

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
		}).registerTypeAdapterFactory(new TypeAdapterFactory() {
			@Override
			public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
				Class<? super T> requestedType = type.getRawType();
				Optional<Class<T>> assignableClass = (Optional<Class<T>>) (Optional<?>) registeredTypeAdapters.keySet().stream().filter(classInstance -> classInstance.isAssignableFrom(requestedType)).findAny();
				if (assignableClass.isEmpty()) {
					return null;
				}
				TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
				return (TypeAdapter<T>) registeredTypeAdapters.get(assignableClass.orElseThrow()).apply(delegate);
			}
		}).setPrettyPrinting().create();
	}

	private static final Map<Class<?>, Function<TypeAdapter<?>, TypeAdapter<?>>> registeredTypeAdapters = new HashMap<>();

	public static <T> void registerTypeAdapter(Class<T> classInstance, Function<TypeAdapter<T>, TypeAdapter<T>> typeAdapter) {
		registeredTypeAdapters.put(classInstance, (Function<TypeAdapter<?>, TypeAdapter<?>>) (Function<?, ?>) typeAdapter);
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
