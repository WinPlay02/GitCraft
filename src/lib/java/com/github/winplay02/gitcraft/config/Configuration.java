package com.github.winplay02.gitcraft.config;

import com.github.winplay02.gitcraft.LibraryPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface Configuration {
	final class Holder {
		record ConfigurationUtils<T extends Configuration>(String name, Function<Map<String, JsonElement>, T> deserializer) {
		}

		private static final String CONFIGURATION_FILE = "config.json";
		private static final Map<Class<? extends Configuration>, Configuration> CONFIGURATION_MAP = new HashMap<>();
		private static final Map<Class<? extends Configuration>, ConfigurationUtils<?>> CONFIGURATION_UTILS = new HashMap<>();
		private static final List<Runnable> configurationUpdaters = new ArrayList<>();
	}

	final class Utils {
		public static boolean getBoolean(Map<String, JsonElement> config, String key, boolean defaultValue) {
			if (!config.containsKey(key)) {
				return defaultValue;
			}
			return config.get(key).getAsBoolean() || (config.get(key).getAsJsonPrimitive().isNumber() && config.get(key).getAsNumber().longValue() != 0L);
		}

		public static int getInt(Map<String, JsonElement> config, String key, int defaultValue) {
			if (!config.containsKey(key)) {
				return defaultValue;
			}
			if (!config.get(key).getAsJsonPrimitive().isNumber()) {
				return defaultValue;
			}
			return config.get(key).getAsNumber().intValue();
		}

		public static String getString(Map<String, JsonElement> config, String key, String defaultValue) {
			if (!config.containsKey(key)) {
				return defaultValue;
			}
			return config.get(key).getAsString();
		}

		public static List<String> getStringArray(Map<String, JsonElement> config, String key, List<String> defaultValue) {
			if (!config.containsKey(key) || !config.get(key).isJsonArray()) {
				return defaultValue;
			}
			return config.get(key).getAsJsonArray().asList().stream().map(JsonElement::getAsString).collect(Collectors.toList());
		}

		public static JsonPrimitive prim(boolean value) {
			return new JsonPrimitive(value);
		}

		public static JsonPrimitive prim(int value) {
			return new JsonPrimitive(value);
		}

		public static JsonPrimitive prim(String value) {
			return new JsonPrimitive(value);
		}

		public static JsonNull _null() {
			return JsonNull.INSTANCE;
		}

		public static JsonArray array(Collection<String> values) {
			JsonArray array = new JsonArray();
			for (String value : values) {
				array.add(prim(value));
			}
			return array;
		}
	}

	Map<String, JsonElement> serialize();

	List<String> generateInfo();

	static void reset() {
		Holder.CONFIGURATION_UTILS.clear();
		Holder.CONFIGURATION_MAP.clear();
	}

	static <T extends Configuration> void register(String name, Class<T> configurationClass, Function<Map<String, JsonElement>, T> deserializer) {
		if (Holder.CONFIGURATION_UTILS.containsKey(configurationClass) || Holder.CONFIGURATION_UTILS.values().stream().anyMatch((tuple) -> tuple.name().equalsIgnoreCase(name))) {
			MiscHelper.panic("Configuration with class %s, name '%s' is already registered", configurationClass.getName(), name);
		}
		Holder.CONFIGURATION_UTILS.put(configurationClass, new Holder.ConfigurationUtils<>(name, deserializer));
	}

	static void loadConfiguration() throws IOException {
		Map<String, Function<Map<String, JsonElement>, ? extends Configuration>> configLoader = Holder.CONFIGURATION_UTILS.values().stream().collect(Collectors.toMap(Holder.ConfigurationUtils::name, Holder.ConfigurationUtils::deserializer));
		MiscHelper.println("Loading configuration from %s, registered configurations: %s", Holder.CONFIGURATION_FILE, String.join(", ", configLoader.keySet()));
		Path currentDirectory = LibraryPaths.lookupCurrentWorkingDirectory();
		Path configPath = currentDirectory.resolve(Holder.CONFIGURATION_FILE);
		JsonObject configurationObject = null;
		try {
			configurationObject = JsonParser.parseReader(Files.newBufferedReader(configPath, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			configurationObject = new JsonObject();
		}
		for (Map.Entry<String, Function<Map<String, JsonElement>, ? extends Configuration>> configurationEntry : configLoader.entrySet()) {
			JsonObject configurationSection = configurationObject.getAsJsonObject(configurationEntry.getKey());
			// Map<String, String> convertedSection = configurationSection.asMap().entrySet().stream().map(entry -> Map.entry(entry.getKey(), entry.getValue().getAsString())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			Configuration c = configurationEntry.getValue().apply(configurationSection != null ? configurationSection.asMap() : Map.of());
			Holder.CONFIGURATION_MAP.put(c.getClass(), c);
		}
		// Store configuration
		if (!Files.exists(configPath)) {
			JsonObject serializedConfigurationObject = new JsonObject();
			for (Map.Entry<Class<? extends Configuration>, Holder.ConfigurationUtils<?>> configurationEntry : Holder.CONFIGURATION_UTILS.entrySet()) {
				Configuration c = Holder.CONFIGURATION_MAP.get(configurationEntry.getKey());
				Map<String, JsonElement> serializedConfiguration = c.serialize();
				if (!serializedConfiguration.isEmpty()) {
					JsonObject serializedConfigurationSection = new JsonObject();
					serializedConfigurationSection.asMap().putAll(serializedConfiguration);
					serializedConfigurationObject.add(configurationEntry.getValue().name(), serializedConfigurationSection);
				}
			}
			SerializationHelper.writeAllToPath(configPath, SerializationHelper.serialize(serializedConfigurationObject));
		}
		// Update configuration
		for (Runnable r : Holder.configurationUpdaters) {
			r.run();
		}
		Holder.configurationUpdaters.clear();
		// Print information
		for (Class<?> configuration : Holder.CONFIGURATION_UTILS.keySet()) {
			List<String> configurationInfo = Holder.CONFIGURATION_MAP.get(configuration).generateInfo();
			if (!configurationInfo.isEmpty()) {
				MiscHelper.println("----- Configuration: %s -----", Holder.CONFIGURATION_UTILS.get(configuration).name());
				MiscHelper.println(String.join("\n", configurationInfo));
			}
		}
		MiscHelper.println("----- End Configuration -----");
	}

	static <T extends Configuration> T getConfiguration(Class<T> configurationClass) {
		return (T) Holder.CONFIGURATION_MAP.get(configurationClass);
	}

	static <T extends Configuration> void editConfiguration(Class<T> configurationClass, Function<T, T> functionMapper) {
		Holder.configurationUpdaters.add(() -> {
			if (!Holder.CONFIGURATION_MAP.containsKey(configurationClass)) {
				return;
			}
			T configuration = (T) Holder.CONFIGURATION_MAP.get(configurationClass);
			T newConfiguration = functionMapper.apply(configuration);
			Holder.CONFIGURATION_MAP.put(configurationClass, newConfiguration);
		});
	}
}
