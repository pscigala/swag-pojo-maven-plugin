package pl.forcode.swagpojo;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Przemysław Ścigała on 06.04.2020.
 */
public class SwagPojoTest {


	@Test
	void swagTest() {
		ModelConverters converter = ModelConverters.getInstance();
		Set<Class> classes = new HashSet<>();
		classes.add(TestPojo.class);
		Map<String, Schema> schemas = new HashMap<>();
		for (Class aClass : classes) {
			schemas.putAll(converter.readAll(aClass));
		}
		Yaml.prettyPrint(schemas);
		Assertions.assertTrue(true);//todo
	}



	private class TestPojo {
		private String name;

		public String getName() {
			return name;
		}
	}
}
