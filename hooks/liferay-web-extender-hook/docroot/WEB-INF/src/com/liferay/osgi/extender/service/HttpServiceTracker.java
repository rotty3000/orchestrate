/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.osgi.extender.service;

import com.liferay.osgi.extender.servlet.FilterDefinition;
import com.liferay.osgi.extender.servlet.ListenerDefinition;
import com.liferay.osgi.extender.servlet.ServletDefinition;
import com.liferay.osgi.extender.servlet.WebXML;
import com.liferay.osgi.extender.servlet.WebXMLLoader;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringPool;

import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * @author Raymond Augé
 */
public class HttpServiceTracker
	extends ServiceTracker<HttpService, HttpService> {

	public HttpServiceTracker(BundleContext bundleContext) {
		super(bundleContext, HttpService.class, null);
	}

	@Override
	public HttpService addingService(ServiceReference<HttpService> reference) {
		HttpService httpService = context.getService(reference);

		HttpContext httpContext = httpService.createDefaultHttpContext();

		readConfiguration(context.getBundle());

		initListeners((ExtendedHttpService)httpService, httpContext);
		initServlets(httpService, httpContext);
		initFilters((ExtendedHttpService)httpService, httpContext);
		initMimeResourceMappings(httpService, httpContext);

		return httpService;
	}

	@Override
	public void removedService(
		ServiceReference<HttpService> reference, HttpService httpService) {

		destroyMimeResourceMappings(httpService);
		destroyFilters((ExtendedHttpService)httpService);
		destroyServlets(httpService);
		destroyListeners((ExtendedHttpService)httpService);

		_webXML = null;
	}

	protected void destroyFilters(ExtendedHttpService httpService) {
		Map<String, FilterDefinition> filters = _webXML.getFilters();

		for (String filterMapping : filters.keySet()) {
			try {
				httpService.unregisterFilter(filterMapping);
			}
			catch (Exception e) {
				_log.error(e);
			}
		}
	}

	protected void destroyListeners(ExtendedHttpService httpService) {
		Map<String, ListenerDefinition> listeners = _webXML.getListeners();

		for (String listenerClassName : listeners.keySet()) {
			try {
				httpService.unregisterListener(listenerClassName);
			}
			catch (Exception e) {
				_log.error(e);
			}
		}
	}

	protected void destroyMimeResourceMappings(HttpService httpService) {
		for (String extension : _webXML.getMimeTypes().keySet()) {
			httpService.unregister(extension);
		}
	}

	protected void destroyServlets(HttpService httpService) {
		Map<String, ServletDefinition> servlets = _webXML.getServlets();

		for (String servletMapping : servlets.keySet()) {
			httpService.unregister(servletMapping);
		}
	}

	protected void initFilters(
		ExtendedHttpService httpService, HttpContext httpContext) {

		Map<String, FilterDefinition> filters = _webXML.getFilters();

		for (String filterMapping : filters.keySet()) {
			FilterDefinition filterDefinition = filters.get(filterMapping);

			try {
				httpService.registerFilter(
					filterMapping, filterDefinition.getFilter(),
					filterDefinition.getInitParams(), httpContext);
			}
			catch (Exception e) {
				_log.error(e);
			}
		}
	}

	protected void initListeners(
		ExtendedHttpService httpService, HttpContext httpContext) {

		Map<String, ListenerDefinition> listeners = _webXML.getListeners();

		for (String listenerClassName : listeners.keySet()) {
			ListenerDefinition listenerDefinition = listeners.get(
				listenerClassName);

			try {
				httpService.registerListener(
					listenerClassName, listenerDefinition.getListener(),
					listenerDefinition.getContextParams(), httpContext);
			}
			catch (Exception e) {
				_log.error(e);
			}
		}
	}

	protected void initMimeResourceMappings(
		HttpService httpService, HttpContext httpContext) {

		Map<String, String> mimeTypes = _webXML.getMimeTypes();

		for (String extension : mimeTypes.keySet()) {
			try {
				httpService.registerResources(
					extension, StringPool.BLANK, httpContext);
			}
			catch (Exception e) {
				_log.error(e, e);
			}
		}
	}

	protected void initServlets(
		HttpService httpService, HttpContext httpContext) {

		Map<String, ServletDefinition> servlets = _webXML.getServlets();

		for (String servletMapping : servlets.keySet()) {
			ServletDefinition servletDefinition = servlets.get(servletMapping);

			try {
				httpService.registerServlet(
					servletMapping, servletDefinition.getServlet(),
					servletDefinition.getInitParams(), httpContext);
			}
			catch (Exception e) {
				_log.error(e);
			}
		}
	}

	protected void readConfiguration(Bundle bundle) {
		_webXML = WebXMLLoader.loadWebXML(bundle);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		HttpServiceTracker.class);

	protected WebXML _webXML;

}