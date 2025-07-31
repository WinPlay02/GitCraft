package com.github.winplay02.gitcraft.util;

import com.github.winplay02.gitcraft.mappings.yarn.FabricYarnVersionMeta;
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
	public static final TypeToken<ArrayList<FabricYarnVersionMeta>> TYPE_LIST_FABRIC_YARN_VERSION_META = new TypeToken<ArrayList<FabricYarnVersionMeta>>() {
	};
}
