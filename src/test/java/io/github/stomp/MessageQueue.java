package io.github.stomp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.SequencedCollection;

public final class MessageQueue<T> {

	private final List<T> all = new ArrayList<>();
	private final List<T> view = Collections.unmodifiableList(this.all);

	private final Queue<T> unviewed = new ArrayDeque<>();

	public void add(final T t) {
		this.all.add(t);
		this.unviewed.add(t);
	}

	public SequencedCollection<T> all() {
		return this.view;
	}

	public Queue<T> unviewed() {
		return this.unviewed;
	}

}
