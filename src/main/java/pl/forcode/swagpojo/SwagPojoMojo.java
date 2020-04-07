package pl.forcode.swagpojo;

import com.google.common.base.Strings;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static java.util.Collections.singletonList;
import static java.util.EnumSet.allOf;

/**
 * Created by Przemysław Ścigała on 06.04.2020.
 */
@Mojo(name = "swag-pojo", defaultPhase = LifecyclePhase.COMPILE)
public class SwagPojoMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	MavenProject project;

	@Parameter(property = "packageToSwag")
	String packageToSwag;

	@Parameter(property = "format", defaultValue = "yaml")
	String format;

	@Parameter(property = "output")
	String output;

	@Parameter(property = "print")
	boolean print = false;

	@Parameter(property = "onlyPrint")
	boolean onlyPrint = false;

	@Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
	private List<String> projectClasspathElements;

	public void execute() throws MojoExecutionException, MojoFailureException {

		if (!Strings.isNullOrEmpty(packageToSwag)) {
			getLog().info("Packages to swag:" + packageToSwag);

			List<Class<?>> classesToSwag = getClassesToSwag();
			swag(classesToSwag);

		} else {
			getLog().warn("I got nothing to do! Parameter packageToSwag is not set or empty." + packageToSwag);
		}

	}

	private List<Class<?>> getClassesToSwag() throws MojoExecutionException {
		Set<String> classNames = scanPackageForClasses();
		List<Class<?>> classesToSwag = new ArrayList<>();

		ClassLoader projectClassLoader = getProjectClassLoader();
		for (String className : classNames) {
			Class<?> aClass = null;
			try {
				aClass = projectClassLoader.loadClass(className);
			} catch (ClassNotFoundException e) {
				getLog().warn("Class not found: " + className);
			}
			if (aClass != null) {
				classesToSwag.add(aClass);
			}
		}
		return classesToSwag;
	}

	private void swag(List<Class<?>> classesToSwag) {
		Map<String, Schema> schemas = classesToSchemas(classesToSwag);
		SwagFormat swagFormat = getSwagFormat();

		if (swagFormat != null) {
			if (onlyPrint || print) {
				swagFormat.prettyPrint(schemas);
			}

			if (!onlyPrint) {
				String pretty = swagFormat.pretty(schemas);
				toFile(swagFormat, pretty);
			}
		}
	}

	private void toFile(SwagFormat format, String pretty) {
		String outputPath = "pojo-swag." + format.getExtension();
		try {
			writeFile(outputPath, pretty);
		} catch (IOException e) {
			getLog().error("Error writing to file: " + outputPath, e);//todo error handling
		}
	}

	private Map<String, Schema> classesToSchemas(List<Class<?>> classesToSwag) {
		ModelConverters converter = ModelConverters.getInstance();
		Map<String, Schema> schemas = new HashMap<>();
		for (Class<?> aClass : classesToSwag) {
			schemas.putAll(converter.readAll(aClass));
		}
		return schemas;
	}

	private ClassLoader getProjectClassLoader() throws MojoExecutionException {
		URL[] projectClasspath = buildMavenClasspath(this.projectClasspathElements);
		return new URLClassLoader(projectClasspath);
	}

	private Set<String> scanPackageForClasses() throws MojoExecutionException {
		List<String> outputDir = singletonList(project.getBuild().getOutputDirectory());
		URL[] sourceFiles = buildMavenClasspath(outputDir);

		Reflections reflections = new Reflections(
				new ConfigurationBuilder()
						.setUrls(sourceFiles)
						.addClassLoaders(getProjectClassLoader())
						.setScanners(new SubTypesScanner(false))
						.filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(packageToSwag)))
		);
		reflections.expandSuperTypes();
		return reflections.getAllTypes();
	}

	protected URL[] buildMavenClasspath(List<String> elements) throws MojoExecutionException {
		List<URL> projectClasspathList = new ArrayList<>();
		for (String element : elements) {
			try {
				projectClasspathList.add(new File(element).toURI().toURL());
			} catch (MalformedURLException e) {
				throw new MojoExecutionException("Classpath " + element + " is invalid", e);
			}
		}
		return projectClasspathList.toArray(new URL[projectClasspathList.size()]);
	}

	private SwagFormat getSwagFormat() throws FormatNotAllowedException {
		SwagFormat swagFormat = null;
		try {
			swagFormat = SwagFormat.valueOf(format.toUpperCase());
		} catch (IllegalArgumentException e) {
			String msg = "Swag output format:" + format + " not allowed! Allowed formats:" + allOf(SwagFormat.class);
			throw new FormatNotAllowedException(msg);
		}
		return swagFormat;
	}

	enum SwagFormat {
		JSON("json"), YAML("yml"), YML("yml");

		private String extension;

		SwagFormat(String extension) {
			this.extension = extension;
		}

		public void prettyPrint(Map<String, Schema> schemas) {
			if (this.equals(YAML) || this.equals(YML)) {
				Yaml.prettyPrint(schemas);
			}
			if (this.equals(JSON)) {
				Json.prettyPrint(schemas);
			}
		}

		public String pretty(Map<String, Schema> schemas) {
			if (this.equals(JSON)) {
				return Json.pretty(schemas);
			}
			return Yaml.pretty(schemas);
		}

		public String getExtension() {
			return this.extension;
		}
	}

	public void writeFile(String fileName, String content) throws IOException {
		FileOutputStream outputStream = new FileOutputStream(fileName);
		byte[] strToBytes = content.getBytes();
		outputStream.write(strToBytes);

		outputStream.close();
	}
}
