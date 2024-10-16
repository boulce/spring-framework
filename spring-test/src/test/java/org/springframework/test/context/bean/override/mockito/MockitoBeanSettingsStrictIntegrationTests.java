/*
 * Copyright 2002-2024 the original author or authors.
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

package org.springframework.test.context.bean.override.mockito;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.mockito.exceptions.misusing.UnnecessaryStubbingException;

import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.quality.Strictness.LENIENT;
import static org.mockito.quality.Strictness.STRICT_STUBS;

/**
 * Integration tests ensuring unnecessary stubbing is reported in various
 * cases where a strict style is chosen or assumed.
 *
 * @author Simon Baslé
 * @author Sam Brannen
 * @since 6.2
 */
class MockitoBeanSettingsStrictIntegrationTests {

	@ParameterizedTest
	@FieldSource("strictCases")
	void unusedStubbingIsReported(Class<?> testCase, int startedCount, int failureCount) {
		Events events = EngineTestKit.engine("junit-jupiter")
				.selectors(selectClass(testCase))
				.execute()
				.testEvents()
				.assertStatistics(stats -> stats.started(startedCount).failed(failureCount));

		events.assertThatEvents().haveExactly(failureCount,
				event(test("unnecessaryStub"),
						finishedWithFailure(
								instanceOf(UnnecessaryStubbingException.class),
								message(msg -> msg.contains("Unnecessary stubbings detected.")))));
	}

	static final List<Arguments> strictCases = List.of(
			argumentSet("explicit strictness", ExplicitStrictness.class, 1, 1),
			argumentSet("explicit strictness on enclosing class", ExplicitStrictnessEnclosingTestCase.class, 1, 1),
			argumentSet("implicit strictness with @MockitoBean on field", ImplicitStrictnessWithMockitoBean.class, 1, 1),
			// 3, 1 --> The tests in LenientStubbingNestedTestCase and InheritedLenientStubbingNestedTestCase
			// should not result in an UnnecessaryStubbingException.
			argumentSet("implicit strictness overridden and inherited in @Nested test classes",
					ImplicitStrictnessWithMockitoBeanEnclosingTestCase.class, 3, 1)
		);

	abstract static class BaseCase {

		@Test
		@SuppressWarnings("rawtypes")
		void unnecessaryStub() {
			List list = mock();
			when(list.get(anyInt())).thenReturn(new Object());
		}
	}

	@SpringJUnitConfig(Config.class)
	@DirtiesContext
	@MockitoBeanSettings(STRICT_STUBS)
	static class ExplicitStrictness extends BaseCase {
	}

	@SpringJUnitConfig(Config.class)
	@DirtiesContext
	static class ImplicitStrictnessWithMockitoBean extends BaseCase {

		@MockitoBean
		Runnable ignoredMock;
	}

	@SpringJUnitConfig(Config.class)
	@DirtiesContext
	@MockitoBeanSettings(STRICT_STUBS)
	static class ExplicitStrictnessEnclosingTestCase {

		@Nested
		class NestedTestCase extends BaseCase {
		}
	}

	@SpringJUnitConfig(Config.class)
	@DirtiesContext
	static class ImplicitStrictnessWithMockitoBeanEnclosingTestCase extends BaseCase {

		@MockitoBean
		Runnable ignoredMock;

		@Nested
		// Overrides implicit STRICT_STUBS
		@MockitoBeanSettings(LENIENT)
		class LenientStubbingNestedTestCase extends BaseCase {

			@Nested
			// Inherits LENIENT
			class InheritedLenientStubbingNestedTestCase extends BaseCase {
			}
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {
		// no beans
	}

}
