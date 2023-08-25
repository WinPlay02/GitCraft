package com.github.winplay02;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dex.mcgitmaker.data.McVersion;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;

public class SerializationHelper {

	protected static Gson gson;

	public static final TypeToken<LinkedHashMap<String, McVersion>> TYPE_LINKED_HASH_MAP_STRING_VERSION = new TypeToken<LinkedHashMap<String, McVersion>>() {
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
		}).create();
	}

	public static <T> String serialize(T objectToSerialize) {
		return gson.toJson(objectToSerialize);
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
