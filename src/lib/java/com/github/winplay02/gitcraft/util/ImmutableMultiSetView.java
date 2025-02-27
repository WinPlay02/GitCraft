package com.github.winplay02.gitcraft.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An immutable view over multiple {@link Set}s
 * @param backed Collection containing sets backing this view
 * @param <E> Type of elements contained in this view
 */
public record ImmutableMultiSetView<E> (Collection<Set<E>> backed) implements Set<E> {

	/**
	 * Create an immutable {@link Set} view from multiple sets
	 * @param backed Varargs Array of sets
	 */
	@SafeVarargs
	public ImmutableMultiSetView(Set<E>... backed) {
		this(List.of(backed));
	}

	@Override
	public int size() {
		return 0;
	}

	@Override
	public boolean isEmpty() {
		return backed.stream().allMatch(Set::isEmpty);
	}

	@Override
	public boolean contains(Object o) {
		return backed.stream().anyMatch(set -> set.contains(o));
	}

	@Override
	public Iterator<E> iterator() {
		return backed.stream().flatMap(Collection::stream).distinct().iterator();
	}

	@Override
	public Object[] toArray() {
		return backed.stream().flatMap(Collection::stream).distinct().toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return backed.stream().flatMap(Collection::stream).collect(Collectors.toUnmodifiableSet()).toArray(a);
	}

	@Override
	public boolean add(E e) {
		return false;
	}

	@Override
	public boolean remove(Object o) {
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return backed.stream().allMatch(set -> set.containsAll(c));
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return false;
	}

	@Override
	public void clear() {
	}
}
