package moe.matsuri.nb4a;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import com.google.gson.*;


/**
 * Sing-box implementation: <a href="https://github.com/SagerNet/sing-box/blob/3066dfe3b31c0d436766047ab6c363be5c60ff53/option/types.go#L120">...</a>
 * Just support gson.
 */
public class Listable<T> extends ArrayList<T> {

    public Listable() {
        super();
    }

    public Listable(Integer cap) {
        super(cap);
    }

    public Listable(Collection<? extends T> c) {
        super(c);
    }

    @SafeVarargs
    public static <T> Listable<T> fromArgs(T... elements) {
        Listable<T> listable = new Listable<>();
        Collections.addAll(listable, elements);
        return listable;
    }

    public static class ListableSerializer<T> implements JsonSerializer<Listable<T>> {
        @Override
        public JsonElement serialize(Listable<T> src, Type typeOfSrc, JsonSerializationContext context) {
            if (src.size() == 1) {
                return context.serialize(src.get(0));
            }
            return context.serialize(src);
        }
    }

    public static class ListableDeserializer<T> implements JsonDeserializer<Listable<T>> {
        private final Class<T> clazz;

        public ListableDeserializer(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Listable<T> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Listable<T> listable = new Listable<>();
            if (json.isJsonArray()) {
                JsonArray jsonArray = json.getAsJsonArray();
                for (JsonElement element : jsonArray) {
                    listable.add(context.deserialize(element, clazz));
                }
            } else {
                T value = context.deserialize(json, clazz);
                listable.add(value);
            }
            return listable;
        }
    }
}

