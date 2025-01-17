/*
 * Copyright 2019-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aot.factories;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aot.build.context.BuildContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.classreading.TypeSystem;
import org.springframework.nativex.AotOptions;

import com.squareup.javapoet.ClassName;

// TODO [issue839] All keys whose targets are configurations should be processed - 
// this is currently a first step in that direction to see what breaks
/**
 * {@link FactoriesCodeContributor} that contributes source code for {@code EnableAutoConfiguration} factories.
 * <p>Instead of instantiating them statically, we're making sure that
 * {@link org.springframework.core.io.support.SpringFactoriesLoader#loadFactoryNames(Class, ClassLoader)}
 * will return their name and we're also adding reflection metadata for native images.
 * <p>For optimization purposes, this contributor can also ignore auto-configurations with
 * conditional annotations that will not match at runtime.
 * <p>This is similar to AutoConfigurationFactoriesCodeContributor but instead of looking for EnableAutoConfiguration
 * keys it is looking for keys starting org.springframework.boot.test.autoconfigure.
 * 
 * @author Andy Clement
 * @author Brian Clozel
 */
public class TestAutoConfigurationFactoriesCodeContributor implements FactoriesCodeContributor {

	
	private static String TEST_AUTO_CONFIGURATION_PREFIX = "org.springframework.boot.test.autoconfigure.";

	private final Log logger = LogFactory.getLog(TestAutoConfigurationFactoriesCodeContributor.class);

	private final AotOptions aotOptions;

	public TestAutoConfigurationFactoriesCodeContributor(AotOptions aotOptions) {
		this.aotOptions = aotOptions;
	}

	@Override
	public boolean canContribute(SpringFactory factory) {
		boolean b = factory.getFactoryType().getClassName().startsWith(TEST_AUTO_CONFIGURATION_PREFIX);//.equals(factory.getFactoryType().getClassName());
		return b;
	}

	@Override
	public void contribute(SpringFactory factory, CodeGenerator code, BuildContext context) {
		TypeSystem typeSystem = context.getTypeSystem();
		// Condition checks
		// TODO make into a pluggable system
		List<String> failedPropertyChecks = new ArrayList<>();
		boolean factoryOK =
				passesConditionalOnClass(typeSystem, factory) &&
				passesAnyConditionalOnSingleCandidate(typeSystem, factory) &&
				passesConditionalOnBean(typeSystem, factory) &&
				passesIgnoreJmxConstraint(typeSystem, factory) &&
				passesConditionalOnWebApplication(typeSystem, factory);
		if (!failedPropertyChecks.isEmpty()) {
			logger.debug("Following property checks failed on "+factory.getFactory().getClassName()+": "+failedPropertyChecks);
		}
		if (factoryOK) {
			ClassName factoryTypeClass = ClassName.bestGuess(factory.getFactoryType().getCanonicalClassName());
			code.writeToStaticBlock(builder -> {
				builder.addStatement("names.add($T.class, $S)", factoryTypeClass,
						factory.getFactory().getCanonicalClassName());
			});
		}
	}

	private boolean passesIgnoreJmxConstraint(TypeSystem typeSystem, SpringFactory factory) {
		String name = factory.getFactory().getCanonicalClassName();
		if (aotOptions.isRemoveJmxSupport() && name.toLowerCase().contains("jmx")) {
			return false;
		}
		return true;
	}

	private boolean passesAnyConditionalOnSingleCandidate(TypeSystem typeSystem, SpringFactory factory) {
		MergedAnnotation<Annotation> onSingleCandidate = factory.getFactory().getAnnotations()
				.get("org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate");
		if (onSingleCandidate.isPresent()) {
			String singleCandidateClass = onSingleCandidate.asAnnotationAttributes(MergedAnnotation.Adapt.CLASS_TO_STRING)
					.getString("value");
			return typeSystem.resolveClass(singleCandidateClass) != null;
		}
		return true;
	}

	private boolean passesConditionalOnBean(TypeSystem typeSystem, SpringFactory factory) {
		MergedAnnotation<Annotation> onBeanCondition = factory.getFactory().getAnnotations()
				.get("org.springframework.boot.autoconfigure.condition.ConditionalOnBean");
		if (onBeanCondition.isPresent()) {
			AnnotationAttributes attributes = onBeanCondition.asAnnotationAttributes(MergedAnnotation.Adapt.CLASS_TO_STRING);
			return Stream.concat(Arrays.stream(attributes.getStringArray("value")),
					Arrays.stream(attributes.getStringArray("type")))
					.allMatch(beanClass -> typeSystem.resolveClass(beanClass) != null);
		}
		return true;
	}

}
