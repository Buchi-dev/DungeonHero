package com.dungeonhero.framework.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Small typed event bus for feature-to-feature communication. */
public final class FeatureEventBus {

  private final Map<Class<?>, CopyOnWriteArrayList<Handler<?>>> handlers =
      new ConcurrentHashMap<>();

  public <T> Subscription subscribe(Class<T> eventType, Consumer<T> handler) {
    if (eventType == null || handler == null) {
      throw new IllegalArgumentException("Event type and handler are required.");
    }
    Handler<T> registered = new Handler<>(eventType, handler);
    handlers.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(registered);
    return () -> {
      List<Handler<?>> registeredHandlers = handlers.get(eventType);
      if (registeredHandlers != null) {
        registeredHandlers.remove(registered);
      }
    };
  }

  public void publish(Object event) {
    if (event == null) {
      return;
    }
    List<Handler<?>> matching = new ArrayList<>();
    for (Map.Entry<Class<?>, CopyOnWriteArrayList<Handler<?>>> entry : handlers.entrySet()) {
      if (entry.getKey().isInstance(event)) {
        matching.addAll(entry.getValue());
      }
    }
    for (Handler<?> handler : matching) {
      handler.dispatch(event);
    }
  }

  public void clear() {
    handlers.clear();
  }

  @FunctionalInterface
  public interface Subscription extends AutoCloseable {
    @Override
    void close();
  }

  private record Handler<T>(Class<T> type, Consumer<T> consumer) {
    private void dispatch(Object event) {
      consumer.accept(type.cast(event));
    }
  }
}
