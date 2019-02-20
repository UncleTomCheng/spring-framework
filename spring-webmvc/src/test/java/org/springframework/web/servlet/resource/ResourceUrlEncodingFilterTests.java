/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.web.servlet.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;

import static org.junit.Assert.*;

/**
 * Unit tests for {@code ResourceUrlEncodingFilter}.
 *
 * @author Brian Clozel
 */
public class ResourceUrlEncodingFilterTests {

	private ResourceUrlEncodingFilter filter;

	private ResourceUrlProvider urlProvider;


	@Before
	public void createFilter() {
		VersionResourceResolver versionResolver = new VersionResourceResolver();
		versionResolver.setStrategyMap(Collections.singletonMap("/**", new ContentVersionStrategy()));
		PathResourceResolver pathResolver = new PathResourceResolver();
		pathResolver.setAllowedLocations(new ClassPathResource("test/", getClass()));
		List<ResourceResolver> resolvers = new ArrayList<>();
		resolvers.add(versionResolver);
		resolvers.add(pathResolver);

		this.filter = new ResourceUrlEncodingFilter();
		this.urlProvider = createResourceUrlProvider(resolvers);
	}

	private ResourceUrlProvider createResourceUrlProvider(List<ResourceResolver> resolvers) {
		ResourceHttpRequestHandler handler = new ResourceHttpRequestHandler();
		handler.setLocations(Collections.singletonList(new ClassPathResource("test/", getClass())));
		handler.setResourceResolvers(resolvers);

		ResourceUrlProvider urlProvider = new ResourceUrlProvider();
		urlProvider.setHandlerMap(Collections.singletonMap("/resources/**", handler));
		return urlProvider;
	}


	@Test
	public void encodeURL() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			req.setAttribute(ResourceUrlProviderExposingInterceptor.RESOURCE_URL_PROVIDER_ATTR, this.urlProvider);
			String result = ((HttpServletResponse) res).encodeURL("/resources/bar.css");
			assertEquals("/resources/bar-11e16cf79faee7ac698c805cf28248d2.css", result);
		});
	}

	@Test
	public void encodeURLWithContext() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/context/foo");
		request.setContextPath("/context");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			req.setAttribute(ResourceUrlProviderExposingInterceptor.RESOURCE_URL_PROVIDER_ATTR, this.urlProvider);
			String result = ((HttpServletResponse) res).encodeURL("/context/resources/bar.css");
			assertEquals("/context/resources/bar-11e16cf79faee7ac698c805cf28248d2.css", result);
		});
	}


	@Test
	public void encodeUrlWithContextAndForwardedRequest() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/context/foo");
		request.setContextPath("/context");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			req.setAttribute(ResourceUrlProviderExposingInterceptor.RESOURCE_URL_PROVIDER_ATTR, this.urlProvider);
			request.setRequestURI("/forwarded");
			request.setContextPath("/");
			String result = ((HttpServletResponse) res).encodeURL("/context/resources/bar.css");
			assertEquals("/context/resources/bar-11e16cf79faee7ac698c805cf28248d2.css", result);
		});
	}

	@Test // SPR-13757
	public void encodeContextPathUrlWithoutSuffix() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/context");
		request.setContextPath("/context");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			req.setAttribute(ResourceUrlProviderExposingInterceptor.RESOURCE_URL_PROVIDER_ATTR, this.urlProvider);
			String result = ((HttpServletResponse) res).encodeURL("/context/resources/bar.css");
			assertEquals("/context/resources/bar-11e16cf79faee7ac698c805cf28248d2.css", result);
		});
	}

	@Test
	public void encodeContextPathUrlWithSuffix() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/context/");
		request.setContextPath("/context");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			req.setAttribute(ResourceUrlProviderExposingInterceptor.RESOURCE_URL_PROVIDER_ATTR, this.urlProvider);
			String result = ((HttpServletResponse) res).encodeURL("/context/resources/bar.css");
			assertEquals("/context/resources/bar-11e16cf79faee7ac698c805cf28248d2.css", result);
		});
	}

	@Test // SPR-13018
	public void encodeEmptyURLWithContext() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/context/foo");
		request.setContextPath("/context");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			req.setAttribute(ResourceUrlProviderExposingInterceptor.RESOURCE_URL_PROVIDER_ATTR, this.urlProvider);
			String result = ((HttpServletResponse) res).encodeURL("?foo=1");
			assertEquals("?foo=1", result);
		});
	}

	@Test // SPR-13374
	public void encodeURLWithRequestParams() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/foo");
		request.setContextPath("/");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			req.setAttribute(ResourceUrlProviderExposingInterceptor.RESOURCE_URL_PROVIDER_ATTR, this.urlProvider);
			String result = ((HttpServletResponse) res).encodeURL("/resources/bar.css?foo=bar&url=http://example.org");
			assertEquals("/resources/bar-11e16cf79faee7ac698c805cf28248d2.css?foo=bar&url=http://example.org", result);
		});
	}

	@Test // SPR-13847
	public void encodeUrlPreventStringOutOfBounds() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/context-path/index");
		request.setContextPath("/context-path");
		request.setServletPath("");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			req.setAttribute(ResourceUrlProviderExposingInterceptor.RESOURCE_URL_PROVIDER_ATTR, this.urlProvider);
			String result = ((HttpServletResponse) res).encodeURL("index?key=value");
			assertEquals("index?key=value", result);
		});
	}

}
