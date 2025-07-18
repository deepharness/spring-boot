/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.boot.zipkin.autoconfigure;

import java.net.http.HttpClient;

import org.junit.jupiter.api.Test;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.HttpEndpointSupplier;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ZipkinAutoConfiguration}.
 *
 * @author Moritz Halbritter
 * @author Wick Dynex
 */
class ZipkinAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ZipkinAutoConfiguration.class));

	@Test
	void shouldSupplyBeans() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(Encoding.class);
			assertThat(context).hasSingleBean(PropertiesZipkinConnectionDetails.class);
			assertThat(context).hasSingleBean(BytesMessageSender.class);
			assertThat(context).hasSingleBean(ZipkinHttpClientSender.class);
		});
	}

	@Test
	void shouldNotSupplyBeansIfZipkinReporterIsMissing() {
		this.contextRunner.withClassLoader(new FilteredClassLoader("zipkin2.reporter"))
			.run((context) -> assertThat(context).doesNotHaveBean(Encoding.class));
	}

	@Test
	void shouldNotProvideHttpClientSenderIfHttpClientIsNotAvailable() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(HttpClient.class))
			.run((context) -> assertThat(context).doesNotHaveBean(ZipkinHttpClientSender.class));
	}

	@Test
	void shouldBackOffOnCustomEncodingBeans() {
		this.contextRunner.withUserConfiguration(CustomEncodingConfiguration.class).run((context) -> {
			assertThat(context).hasBean("customEncoding");
			assertThat(context).hasSingleBean(Encoding.class);
		});
	}

	@Test
	void shouldBackOffOnCustomSenderBeans() {
		this.contextRunner.withUserConfiguration(CustomSenderConfiguration.class).run((context) -> {
			assertThat(context).hasBean("customSender");
			assertThat(context).hasSingleBean(BytesMessageSender.class);
		});
	}

	@Test
	void shouldUseCustomHttpEndpointSupplierFactory() {
		this.contextRunner.withUserConfiguration(CustomHttpEndpointSupplierFactoryConfiguration.class)
			.run((context) -> {
				ZipkinHttpClientSender httpClientSender = context.getBean(ZipkinHttpClientSender.class);
				assertThat(httpClientSender).extracting("endpointSupplier")
					.isInstanceOf(CustomHttpEndpointSupplier.class);
			});
	}

	@Test
	void definesPropertiesBasedConnectionDetailsByDefault() {
		this.contextRunner.run((context) -> assertThat(context).hasSingleBean(PropertiesZipkinConnectionDetails.class));
	}

	@Test
	void shouldUseCustomConnectionDetailsWhenDefined() {
		this.contextRunner
			.withBean(ZipkinConnectionDetails.class, () -> new FixedZipkinConnectionDetails("http://localhost"))
			.run((context) -> assertThat(context).hasSingleBean(ZipkinConnectionDetails.class)
				.doesNotHaveBean(PropertiesZipkinConnectionDetails.class));
	}

	@Test
	void shouldWorkWithoutSenders() {
		this.contextRunner
			.withClassLoader(new FilteredClassLoader("org.springframework.web.client",
					"org.springframework.web.reactive.function.client"))
			.run((context) -> assertThat(context).hasNotFailed());
	}

	private static final class FixedZipkinConnectionDetails implements ZipkinConnectionDetails {

		private final String spanEndpoint;

		private FixedZipkinConnectionDetails(String spanEndpoint) {
			this.spanEndpoint = spanEndpoint;
		}

		@Override
		public String getSpanEndpoint() {
			return this.spanEndpoint;
		}

	}

	@Configuration(proxyBeanMethods = false)
	private static final class CustomEncodingConfiguration {

		@Bean
		Encoding customEncoding() {
			return Encoding.PROTO3;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomSenderConfiguration {

		@Bean
		BytesMessageSender customSender() {
			return mock(BytesMessageSender.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomHttpEndpointSupplierFactoryConfiguration {

		@Bean
		HttpEndpointSupplier.Factory httpEndpointSupplier() {
			return new CustomHttpEndpointSupplierFactory();
		}

	}

	static class CustomHttpEndpointSupplierFactory implements HttpEndpointSupplier.Factory {

		@Override
		public HttpEndpointSupplier create(String endpoint) {
			return new CustomHttpEndpointSupplier(endpoint);
		}

	}

	static class CustomHttpEndpointSupplier implements HttpEndpointSupplier {

		private final String endpoint;

		CustomHttpEndpointSupplier(String endpoint) {
			this.endpoint = endpoint;
		}

		@Override
		public String get() {
			return this.endpoint;
		}

		@Override
		public void close() {
		}

	}

}
