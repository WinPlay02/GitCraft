package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.meta.GameVersionBuildMeta;
import com.github.winplay02.gitcraft.meta.SimpleVersionMeta;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
}
