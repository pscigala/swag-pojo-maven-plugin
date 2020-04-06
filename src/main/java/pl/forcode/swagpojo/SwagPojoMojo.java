package pl.forcode.swagpojo;

import com.google.common.base.Strings;
import io.swagger.v3.core.converter.ModelConverters;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static java.util.Collections.singletonList;

/**
 * Created by Przemysław Ścigała on 06.04.2020.
 */
@Mojo(name = "swag-pojo", defaultPhase = LifecyclePhase.COMPILE)
public class SwagPojoMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	MavenProject project;

	@Parameter(property = "packageToSwag", required = true, readonly = true)
	String packageToSwag;

	@Parameter(defaultValue = "${project.compileClasspathElements}", readonly = true, required = true)
	private List<String> projectClasspathElements;

	public void execute() throws MojoExecutionException, MojoFailureException {

		if (!Strings.isNullOrEmpty(packageToSwag)) {
			getLog().info("Packages to swag:" + packageToSwag);
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

			swag(classesToSwag);

		} else {
			getLog().warn("I got nothing to do! Parameter packageToSwag is not set or empty." + packageToSwag);
		}

	}

	private void swag(List<Class<?>> classesToSwag) {
		ModelConverters converter = ModelConverters.getInstance();
		Map<String, Schema> schemas = new HashMap<>();
		for (Class<?> aClass : classesToSwag) {
			schemas.putAll(converter.readAll(aClass));
		}
		Yaml.prettyPrint(schemas);
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

}
