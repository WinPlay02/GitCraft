package com.github.winplay02.gitcraft.util;

import java.util.Objects;

public record CachedHashKeyWrapper<T>(T inner, int innerHashCode) {

	public CachedHashKeyWrapper(T inner) {
		this(inner, Objects.hashCode(inner));
	}

	public static <T> CachedHashKeyWrapper<T> of(T inner) {
		return new CachedHashKeyWrapper<>(inner);
	}

	@Override
	public int hashCode() {
		return innerHashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof CachedHashKeyWrapper<?> wrapper)) {
			return false;
		}
		return Objects.equals(inner, wrapper.inner);
	}
}
