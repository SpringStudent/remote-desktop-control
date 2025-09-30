package io.github.springstudent.dekstop.client.monitor;

public interface CounterListener<T> {
	void onInstantValueUpdated(Counter<T> counter, T value);
}
