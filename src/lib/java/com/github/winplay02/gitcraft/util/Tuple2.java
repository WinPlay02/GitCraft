package com.github.winplay02.gitcraft.util;

import java.util.Iterator;
import java.util.RandomAccess;
import java.util.stream.Stream;

public record Tuple2<V1, V2>(V1 v1, V2 v2) implements Iterable<Object>, RandomAccess {
	public static <V1, V2> Tuple2<V1, V2> tuple(V1 v1, V2 v2) {
		return new Tuple2<>(v1, v2);
	}

	public V1 getV1() {
		return this.v1;
	}

	public V2 getV2() {
		return this.v2;
	}

	@Override
	public Iterator<Object> iterator() {
		return Stream.of(this.v1(), this.v2()).iterator();
	}
}
