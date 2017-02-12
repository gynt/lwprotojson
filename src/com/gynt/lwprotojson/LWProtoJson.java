package com.gynt.lwprotojson;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.json.JSONArray;
import org.json.JSONObject;

public abstract class LWProtoJson {

	@Retention(RetentionPolicy.RUNTIME)
	public static @interface lwproto {
		public int from() default 0;

		public int until() default Integer.MAX_VALUE;
	}

	public static abstract class AbstractSerializer<T> {

		protected Class<?> type;

		public AbstractSerializer(Class<?> c) {
			type = c;
		}

		public abstract T deserialize(String data) throws InstantiationException, IllegalAccessException;

		public abstract String serialize(T obj)
				throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException, InstantiationException;

	}

	@SuppressWarnings("rawtypes")
	private static final HashMap<Class<?>, AbstractSerializer> map = new HashMap<>();

	static {
		map.put(String.class, new AbstractSerializer<String>(String.class) {

			@Override
			public String deserialize(String data) {
				return data;
			}

			@Override
			public String serialize(String obj) {
				return obj;
			}
		});
		map.put(int.class, new AbstractSerializer<Integer>(int.class) {

			@Override
			public Integer deserialize(String data) {
				return Integer.parseInt(data);
			}

			@Override
			public String serialize(Integer obj) {
				return obj.toString();
			}

		});
		map.put(Integer.class, map.get(int.class));
		LWProtoJson.register(long.class, new AbstractSerializer<Long>(long.class) {

			@Override
			public Long deserialize(String data) {
				return Long.parseLong(data);
			}

			@Override
			public String serialize(Long obj) {
				return obj.toString();
			}

		});
		LWProtoJson.register(Long.class, LWProtoJson.retrieve(long.class));
		LWProtoJson.register(double.class, new AbstractSerializer<Double>(double.class) {

			@Override
			public Double deserialize(String data) throws InstantiationException, IllegalAccessException {
				return Double.parseDouble(data);
			}

			@Override
			public String serialize(Double obj)
					throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
				return obj.toString();
			}

		});
		LWProtoJson.register(Double.class, LWProtoJson.retrieve(double.class));
		LWProtoJson.register(float.class, new AbstractSerializer<Float>(float.class) {

			@Override
			public Float deserialize(String data) throws InstantiationException, IllegalAccessException {
				return Float.parseFloat(data);
			}

			@Override
			public String serialize(Float obj)
					throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
				return obj.toString();
			}

		});
		LWProtoJson.register(Float.class, LWProtoJson.retrieve(float.class));
		LWProtoJson.register(byte.class, new AbstractSerializer<Byte>(byte.class) {

			@Override
			public Byte deserialize(String data) throws InstantiationException, IllegalAccessException {
				return Byte.parseByte(data);
			}

			@Override
			public String serialize(Byte obj)
					throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
				return obj.toString();
			}

		});
		LWProtoJson.register(Float.class, LWProtoJson.retrieve(float.class));
	}

	@SuppressWarnings("unchecked")
	public static <T> AbstractSerializer<T> register(Class<T> type, AbstractSerializer<T> a) {
		return map.put(type, a);
	}

	@SuppressWarnings("unchecked")
	public static <T> AbstractSerializer<T> remove(Class<T> type) {
		return map.remove(type);
	}

	@SuppressWarnings("unchecked")
	public static <T> AbstractSerializer<T> retrieve(Class<T> type) {
		return map.get(type);
	}

	public static class Serializer<T> extends AbstractSerializer<T> {

		private boolean hasversion;
		private Field[] fields;

		public Serializer(Class<?> c) {
			super(c);

			hasversion = false;
			try {
				c.getDeclaredField("VERSION");
				hasversion = true;
			} catch (NoSuchFieldException e) {
				// e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			}

			ArrayList<Field> temp = new ArrayList<>();
			for (Field f : c.getDeclaredFields()) {
				if (f.getDeclaredAnnotation(lwproto.class) == null)
					continue;
				f.setAccessible(true);
				temp.add(f);
			}
			fields = temp.toArray(new Field[0]);
		}

		public static <T> T[] toArray(Collection<T> list, Class<?> innertype) {
			@SuppressWarnings("unchecked")
			T[] toR = (T[]) java.lang.reflect.Array.newInstance(innertype, list.size());
			int i = 0;
			for (T o : list) {
				toR[i] = o;
				i++;
			}
			return toR;
		}

		public JSONObject serializeField(Field f, T obj)
				throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException, InstantiationException {
			JSONObject result = new JSONObject();
			if (Collection.class.isAssignableFrom(f.getType())) {
				ParameterizedType p = (ParameterizedType) f.getGenericType();
				Class<?> innertype = (Class<?>) p.getActualTypeArguments()[0];
				result.put(f.getName(), serializeArray(toArray((Collection) f.get(obj), innertype), innertype));
			} else if(Map.class.isAssignableFrom(f.getType())) {
				ParameterizedType p = (ParameterizedType) f.getGenericType();
				result.put(f.getName(), serializeMap((Map) f.get(obj), (Class<?>) p.getActualTypeArguments()[0], (Class<?>) p.getActualTypeArguments()[1]));
			} else if (f.getType().isArray()) {
				result.put(f.getName(), serializeArray(f.get(obj), f.getType().getComponentType()));
			} else if (map.containsKey(f.getType())) {
				result.put(f.getName(), map.get(f.getType()).serialize(f.get(obj)));
			} else {
				throw new RuntimeException("Unsupported class: " + f.getType().getName());
			}
			return result;
		}

		public void deserializeField(Field f, T obj, JSONObject data)
				throws InstantiationException, IllegalAccessException {
			if (Collection.class.isAssignableFrom(f.getType())) {
				ParameterizedType p = (ParameterizedType) f.getGenericType();
				Class<?> innertype = (Class<?>) p.getActualTypeArguments()[0];
				Collection list = (Collection) f.getType().newInstance();
				for(Object o: (Object[]) deserializeArray(data.getJSONArray(f.getName()), innertype)) {
					list.add(o);
				}
				f.set(obj, list);
			} else if(Map.class.isAssignableFrom(f.getType())) {
				ParameterizedType p = (ParameterizedType) f.getGenericType();
				f.set(obj, deserializeMap(data.getJSONObject(f.getName()), (Class<Map>) f.getType(), (Class<?>) p.getActualTypeArguments()[0], (Class<?>) p.getActualTypeArguments()[1]));
			} else if (f.getType().isArray()) {
				f.set(obj, deserializeArray(data.getJSONArray(f.getName()), f.getType().getComponentType()));
			} else if (map.containsKey(f.getType())) {

				f.set(obj, map.get(f.getType()).deserialize(data.getString(f.getName())));
				return;

			} else {
				throw new RuntimeException("Unsupported class: " + f.getType().getName());
			}
		}

		public String serialize(T obj) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException, InstantiationException {
			StringWriter sw = new StringWriter();
			if (type.isArray()) {
				serializeArray(obj, type.getComponentType()).write(sw);
				return sw.toString();
			}

			int version = 0;
			if (hasversion) {
				version = type.getDeclaredField("VERSION").getInt(obj);
			}

			JSONObject result = new JSONObject();
			result.put("version", version);

			for (Field f : fields) {
				lwproto anno = f.getDeclaredAnnotation(lwproto.class);
				if (version < anno.from() || version > anno.until())
					continue;

				JSONObject data = serializeField(f, obj);

				result.put(f.getName(), data.get(f.getName()));
			}

			if (result.length() == 0)
				throw new RuntimeException("Nothing has been serialized");

			result.write(sw);
			return sw.toString();

		}

		public T deserialize(String data) throws InstantiationException, IllegalAccessException {

			if (type.isArray())
				return (T) deserializeArray(new JSONArray(data), type.getComponentType());
			@SuppressWarnings("unchecked")
			T t = (T) type.newInstance();

			Queue<byte[]> datas = new LinkedList<>();

			JSONObject j = new JSONObject(data);

			int version = j.getInt("version");

			for (Field f : fields) {
				lwproto anno = f.getDeclaredAnnotation(lwproto.class);
				if (version < anno.from() || version > anno.until())
					continue;
				deserializeField(f, t, j);
			}

			return t;

		}

		public static <K,V> JSONObject serializeMap(Map<K,V> obj, Class<K> componenttype1, Class<V> componenttype2) throws InstantiationException, IllegalAccessException, IllegalArgumentException, NoSuchFieldException, SecurityException {
			if (!map.containsKey(componenttype1)) {
				throw new RuntimeException("Unsupported class: " + componenttype1.getName());
			} else if(!map.containsKey(componenttype2)) {
				throw new RuntimeException("Unsupported class: " + componenttype2.getName());
			}
			JSONObject result = new JSONObject();
			ArrayList<byte[]> datas = new ArrayList<byte[]>();
			for(Entry<K,V> entry : obj.entrySet()) {
				@SuppressWarnings("unchecked")
				String data1 = map.get(componenttype1).serialize(entry.getKey());
				@SuppressWarnings("unchecked")
				String data2 = map.get(componenttype2).serialize(entry.getValue());
				result.put(data1, data2);
			}
			return result;
		}

		public static <K,V> Map<K, V> deserializeMap(JSONObject data, Class<Map> class1, Class<?> class2, Class<?> class3) throws InstantiationException, IllegalAccessException {
			if (!map.containsKey(class2)) {
				throw new RuntimeException("Unsupported class: " + class2.getName());
			} else if(!map.containsKey(class3)) {
				throw new RuntimeException("Unsupported class: " + class3.getName());
			}
			ArrayList<Entry<K,V>> entries = new ArrayList<>();
			int i = 0;
			for(String k : data.keySet()) {
				entries.add(new Entry<K, V>() {

					@SuppressWarnings("unchecked")
					K key = (K) map.get(class2).deserialize(k);
					@SuppressWarnings("unchecked")
					V value = (V) map.get(class3).deserialize(data.getString(k));

					@Override
					public K getKey() {
						return key;
					}

					@Override
					public V getValue() {
						return value;
					}

					@Override
					public V setValue(V value) {
						return null;
					}
				});

				i++;
			}
			@SuppressWarnings("unchecked")
			Map<K,V> m = class1.newInstance();
			for(Entry<K,V> entry : entries) {
				m.put(entry.getKey(), entry.getValue());
			}
			return m;
		}

		public static <T> JSONArray serializeArray(T obj, Class<?> componenttype)
				throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException, InstantiationException {

			int length = Array.getLength(obj);

			JSONArray array = new JSONArray();

			for (int i = 0; i < length; i++) {
				Object arrayElement = Array.get(obj, i);
				if (map.containsKey(componenttype)) {
					@SuppressWarnings("unchecked")
					String data = map.get(componenttype).serialize(arrayElement);
					array.put(data);
				} else {
					throw new RuntimeException("Unsupported class: " + componenttype.getName());
				}
			}

			return array;
		}

//		protected byte[] serializeArray(T obj)
//				throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException, InstantiationException {
//
//			return serializeArray(obj, type.getComponentType());
//
//		}

		public static <T> T deserializeArray(JSONArray data, Class<T> componenttype) throws ArrayIndexOutOfBoundsException,
				IllegalArgumentException, InstantiationException, IllegalAccessException {
			@SuppressWarnings("unchecked")
			T t = (T) Array.newInstance(componenttype, data.length());

			int i = 0;
			while (i < data.length()) {
				if (map.containsKey(componenttype)) {
					Array.set(t, i, map.get(componenttype).deserialize(data.getString(i)));
				} else {
					throw new RuntimeException("Unsupported class: " + componenttype.getName());
				}
				i++;
			}

			return t;
		}
//
//		@SuppressWarnings("unchecked")
//		protected T deserializeArray(byte[] data)
//				throws ArrayIndexOutOfBoundsException, InstantiationException, IllegalAccessException {
//
//			return (T) deserializeArray(data, type.getComponentType());
//
//		}

	}

//	public static class JSONWriter {
//
//		public static byte[] toBytes(Object jsonobject) {
//			if(jsonobject instanceof JSONObject) {
//				StringWriter sw = new StringWriter();
//				((JSONObject) jsonobject).write(sw);
//				return sw.toString().getBytes(Charset.forName("UTF-8"));
//			} else if(jsonobject instanceof JSONArray) {
//				StringWriter sw = new StringWriter();
//				((JSONArray) jsonobject).write(sw);
//				return sw.toString().getBytes(Charset.forName("UTF-8"));
//			} else {
//				return null;
//			}
//		}
//
//		public static Object fromBytes(byte[] data) {
//			String sdata = new String(data, Charset.forName("UTF-8"));
//			if(sdata.charAt(0)=='[') {
//				return new JSONArray(sdata);
//			} else if(sdata.charAt(0)=='{') {
//				return new JSONObject(sdata);
//			} else {
//				return null;
//			}
//		}
//
//	}

	public static final String base64(byte[] data) {
		return Base64.getEncoder().encodeToString(data);
	}

	public static final byte[] base64(String s) {
		return Base64.getDecoder().decode(s);
	}

}