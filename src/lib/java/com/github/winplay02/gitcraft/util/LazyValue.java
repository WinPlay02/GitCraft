package com.github.winplay02.gitcraft.util;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A value that is calculated on first access.
 *
 * @param <T> Any type
 */
public final class LazyValue<T> implements Supplier<T> {
	/**
	 * Supplier that create the value
	 */
	public Supplier<T> valueSupplier;

	/**
	 * Actual value
	 */
	public T value;

	/**
	 * Constructs a new lazy value with a supplier.
	 *
	 * @param valueSupplier Supplier that create the value. The supplied value may also be null, the supplier may not.
	 */
	private LazyValue(final Supplier<T> valueSupplier) {
		Objects.requireNonNull(valueSupplier);
		this.valueSupplier = valueSupplier;
		this.value = null;
	}

	/**
	 * Constructs a new lazy value with a supplier.
	 *
	 * @param valueSupplier Supplier that create the value. The supplied value may also be null, the supplier may not.
	 */
	public static <T> LazyValue<T> of(final Supplier<T> valueSupplier) {
		return new LazyValue<>(valueSupplier);
	}

	@Override
	public T get() {
		if (this.valueSupplier != null) {
			this.value = this.valueSupplier.get();
			this.valueSupplier = null;
		}
		return this.value;
	}
}
