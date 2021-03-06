package de.qabel.desktop.inject;

import de.qabel.desktop.ServiceFactory;

import java.util.HashMap;
import java.util.Map;

public class DefaultServiceFactory implements ServiceFactory {
    protected Map<String, Object> cache = new HashMap<>();

    @Override
    public Object get(String key) {
        return cache.get(key);
    }

    @Override
    public <T> T getByType(Class<T> type) {
        for (Object value : cache.values()) {
            if (type.isInstance(value)) {
                return (T)value;
            }
        }
        return null;
    }

    @Override
    public void put(String key, Object instance) {
        cache.put(key, instance);
    }
}
