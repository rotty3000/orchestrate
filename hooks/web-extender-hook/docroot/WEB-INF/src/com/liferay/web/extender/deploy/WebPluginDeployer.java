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

package com.liferay.web.extender.deploy;

import com.liferay.portal.kernel.deploy.hot.HotDeployEvent;
import com.liferay.portal.kernel.deploy.hot.HotDeployUtil;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.servlet.ServletContextPool;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.osgi.OSGiConstants;
import com.liferay.web.extender.servlet.BundleServletContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * @author Raymond Augé
 */
public class WebPluginDeployer implements BundleListener {

	public WebPluginDeployer(
			BundleContext bundleContext, Servlet portletServlet)
		throws Exception {

		_bundleContext = bundleContext;
		_portletServlet = portletServlet;

		StringBundler sb = new StringBundler(7);

		sb.append("(&(");
		sb.append(OSGiConstants.BEAN_ID);
		sb.append("=");
		sb.append(ServletContext.class.getName());
		sb.append(")(");
		sb.append(OSGiConstants.ORIGINAL_BEAN);
		sb.append("=*))");

		Filter filter = bundleContext.createFilter(sb.toString());

		_servletContextTracker =
			new ServiceTracker<ServletContext, ServletContext>(
				_bundleContext, filter, null);

		_servletContextTracker.open();

		_eventAdminTracker = new ServiceTracker<EventAdmin, EventAdmin>(
			_bundleContext, EventAdmin.class.getName(), null);

		_eventAdminTracker.open();
	}

	public void bundleChanged(BundleEvent bundleEvent) {
		int type = bundleEvent.getType();

		Bundle bundle = bundleEvent.getBundle();

		String servletContextName = BundleServletContext.getServletContextName(
			bundle);

		if (Validator.isNull(servletContextName)) {
			return;
		}

		try {
			if (type == BundleEvent.STARTED) {
				doStart(bundle, servletContextName);
			}
			else if (type == BundleEvent.STOPPED) {
				doStop(bundle, servletContextName);
			}
		}
		catch (Exception e) {
			_log.error(e, e);
		}
	}

	public void doStart(Bundle bundle, String servletContextName)
		throws Exception {

		sendEvent(bundle, "org/osgi/service/web/DEPLOYING", null, false);

		ServletContext servletContext = ServletContextPool.get(servletContextName);

		if (servletContext != null) {
			sendEvent(bundle, "org/osgi/service/web/FAILED", null, true);

			_collidedWabs.add(bundle);

			return;
		}

		BundleServletContext bundleServletContext = null;

		try {
			servletContext = _servletContextTracker.getService();

			bundleServletContext = new BundleServletContext(
				servletContext, _portletServlet);

			bundleServletContext.setAttribute(
				OSGiConstants.OSGI_BUNDLE, bundle);
			bundleServletContext.setAttribute(
				OSGiConstants.OSGI_BUNDLECONTEXT, _bundleContext);

			HotDeployUtil.fireDeployEvent(
				new HotDeployEvent(
					bundleServletContext,
					bundleServletContext.getClassLoader()));

			bundleServletContext.open();

			_deployedWabs.add(bundleServletContext);

			sendEvent(bundle, "org/osgi/service/web/DEPLOYED", null, false);
		}
		catch (Exception e) {
			if (bundleServletContext != null) {
				bundleServletContext.close();
			}

			sendEvent(bundle, "org/osgi/service/web/FAILED", e, false);
		}
	}

	public void doStop(Bundle bundle, String servletContextName)
		throws Exception {

		sendEvent(bundle, "org/osgi/service/web/UNDEPLOYING", null, false);

		ServletContext servletContext = ServletContextPool.get(
			servletContextName);

		if (servletContext == null) {
			sendEvent(bundle, "org/osgi/service/web/FAILED", null, false);

			return;
		}

		BundleServletContext bundleServletContext =
			(BundleServletContext)servletContext;

		HotDeployUtil.fireUndeployEvent(
			new HotDeployEvent(
				servletContext, bundleServletContext.getClassLoader()));

		bundleServletContext.close();

		sendEvent(bundle, "org/osgi/service/web/UNDEPLOYED", null, false);

		handleCollidedWabs(servletContextName);
	}

	protected void handleCollidedWabs(String servletContextName)
		throws Exception {

		if (_collidedWabs.isEmpty()) {
			return;
		}

		Bundle candidate = null;

		for (Bundle collided : _collidedWabs) {
			String contextName = BundleServletContext.getServletContextName(
				collided);

			if (servletContextName.equals(contextName) &&
				((candidate == null) ||
				 (collided.getBundleId() < collided.getBundleId()))) {

				candidate = collided;
			}
		}

		if (candidate != null) {
			doStart(candidate, servletContextName);
		}
	}

	protected void sendEvent(
		Bundle bundle, String eventTopic, Exception exception,
		boolean collision) {

		EventAdmin eventAdmin = _eventAdminTracker.getService();

		if (eventAdmin == null) {
			return;
		}

		Bundle systemBundle = _bundleContext.getBundle();

		Dictionary<String,String> headers = bundle.getHeaders();
		String contextPath = headers.get(OSGiConstants.WEB_CONTEXTPATH);

		Map<String, Object> properties = new Hashtable<String, Object>();

		properties.put("bundle.symbolicName", bundle.getSymbolicName());
		properties.put("bundle.id", bundle.getBundleId());
		properties.put("bundle", bundle);
		properties.put("bundle.version", bundle.getVersion());
		properties.put("context.path", contextPath);
		properties.put("timestamp", System.currentTimeMillis());
		properties.put("extender.bundle", systemBundle);
		properties.put("extender.bundle.id", systemBundle.getBundleId());
		properties.put(
			"extender.bundle.symbolicName", systemBundle.getBundleId());
		properties.put("extender.bundle.version", systemBundle.getVersion());

		if (exception != null) {
			properties.put("exception", exception);
		}

		if (collision) {
			properties.put(
				"collision", headers.get(OSGiConstants.WEB_CONTEXTPATH));

			List<String> collidedIds = new ArrayList<String>();

			for (Bundle curBundle : _bundleContext.getBundles()) {
				Dictionary<String,String> curHeaders = bundle.getHeaders();

				String curContextPath = curHeaders.get(
					OSGiConstants.WEB_CONTEXTPATH);

				if ((curContextPath != null) &&
					curContextPath.equals(contextPath)) {

					collidedIds.add(String.valueOf(curBundle.getBundleId()));
				}
			}

			properties.put("collision.bundles", collidedIds);
		}

		Event event = new Event(eventTopic, properties);

		eventAdmin.sendEvent(event);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		WebPluginDeployer.class);

	private BundleContext _bundleContext;
	private List<Bundle> _collidedWabs = Collections.synchronizedList(
		new ArrayList<Bundle>());
	private List<BundleServletContext> _deployedWabs =
		Collections.synchronizedList(new ArrayList<BundleServletContext>());
	private ServiceTracker<EventAdmin, EventAdmin> _eventAdminTracker;
	private ServiceTracker<ServletContext, ServletContext> _servletContextTracker;
	private Servlet _portletServlet;

}