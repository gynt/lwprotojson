package com.gynt.lwprotojson.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import com.gynt.lwprotojson.LWProtoJson;
import com.gynt.lwprotojson.LWProtoJson.Serializer;
import com.gynt.lwprotojson.LWProtoJson.Lwprotojson;

public class Example {

	@Lwprotojson
	private String name = "Hello world!";

	@Lwprotojson
	public int age = 100;

	@Lwprotojson
	public ArrayList<String> list = new ArrayList<>(Arrays.asList("a","b","c"));

	@Lwprotojson
	public HashMap<String, Integer> map = new HashMap<String, Integer>();
	{
		map.put("example1", 1);
		map.put("example2", 2);
	}

	public static class AnotherExample {
		public int VERSION = 4;

		@Lwprotojson(from = 1, until = 4)
		public String name = "Bye world!";

		@Lwprotojson(from = 0)
		public int age = 90;
	}

	public static void main(String[] args) throws InstantiationException, IllegalAccessException, IllegalArgumentException, NoSuchFieldException, SecurityException {

		Serializer<Example> s = new Serializer<Example>(Example.class);

		Example e = new Example();
		System.out.println(s.serialize(e));
		System.out.println(s.deserialize(s.serialize(e)).name);
		System.out.println(s.deserialize(s.serialize(e)).list.toString());
		System.out.println(s.deserialize(s.serialize(e)).map.toString());

		LWProtoJson.register(Example.class, s);
		Serializer<Example[]> q = new Serializer<Example[]>(Example[].class);
		Example e1 = new Example();
		Example e2 = new Example();
		e1.name = "foo";
		e2.name = "bar";
		Example[] es = new Example[] { e1, e2 };
		System.out.println(q.deserialize(q.serialize(es)).length);
	}

}
