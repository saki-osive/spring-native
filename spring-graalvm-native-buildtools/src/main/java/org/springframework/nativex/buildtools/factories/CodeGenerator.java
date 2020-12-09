package org.springframework.nativex.buildtools.factories;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Generate a {@code org.springframework.nativex.buildtools.StaticSpringFactories} class
 * that will be used by a {@link org.springframework.core.io.support.SpringFactoriesLoader} override
 * shipped with this module.
 * <p>Also generates static factory classes for instantiating factories with package private constructors.
 * 
 * @author Brian Clozel
 */
class CodeGenerator {

	private final CodeBlock.Builder staticBlock = CodeBlock.builder();

	private final Map<String, TypeSpec> staticFactoryClasses = new HashMap<>();

	public void writeToStaticBlock(Consumer<CodeBlock.Builder> consumer) {
		consumer.accept(this.staticBlock);
	}

	public TypeSpec getStaticFactoryClass(String packageName) {
		return this.staticFactoryClasses.getOrDefault(packageName, createStaticFactoryClass());
	}

	public void writeToStaticFactoryClass(String packageName, Consumer<TypeSpec.Builder> consumer) {
		TypeSpec staticFactoryClass = this.staticFactoryClasses.getOrDefault(packageName, createStaticFactoryClass());
		TypeSpec.Builder builder = staticFactoryClass.toBuilder();
		consumer.accept(builder);
		this.staticFactoryClasses.put(packageName, builder.build());
	}

	public JavaFile generateStaticSpringFactories() {
		TypeSpec springFactoriesType = createSpringFactoriesType(this.staticBlock.build());
		return JavaFile.builder("org.springframework.nativex.buildtools", springFactoriesType).build();
	}

	public List<JavaFile> generateStaticFactoryClasses() {
		return this.staticFactoryClasses.entrySet().stream()
				.map((specEntry) -> JavaFile.builder(specEntry.getKey(), specEntry.getValue()).build())
				.collect(Collectors.toList());
	}

	private TypeSpec createStaticFactoryClass() {
		return TypeSpec.classBuilder("_FactoryProvider")
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.ABSTRACT)
				.build();
	}

	private TypeSpec createSpringFactoriesType(CodeBlock staticBlock) {
		ParameterizedTypeName factoriesType = ParameterizedTypeName.get(MultiValueMap.class, Class.class, Object.class);
		FieldSpec factories = FieldSpec.builder(factoriesType, "factories")
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.initializer("new $T()", LinkedMultiValueMap.class)
				.build();
		ParameterizedTypeName namesType = ParameterizedTypeName.get(MultiValueMap.class, Class.class, String.class);
		FieldSpec names = FieldSpec.builder(namesType, "names")
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.initializer("new $T()", LinkedMultiValueMap.class)
				.build();
		return TypeSpec.classBuilder("StaticSpringFactories")
				.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
				.addField(factories)
				.addField(names)
				.addStaticBlock(staticBlock)
				.addJavadoc("Class generated - do not edit this file")
				.build();
	}
}
