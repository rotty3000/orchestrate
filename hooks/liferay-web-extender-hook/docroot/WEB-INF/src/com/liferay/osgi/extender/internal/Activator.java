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

package com.liferay.osgi.extender.internal;

import com.liferay.osgi.extender.deploy.WebPluginDeployer;
import com.liferay.osgi.extender.servlet.BundleServletConfig;
import com.liferay.osgi.extender.servlet.OSGiServlet;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.ReleaseInfo;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.osgi.OSGiConstants;
import com.liferay.portal.struts.StrutsActionRegistry;

import java.util.Hashtable;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * @author Raymond Augé
 */
public class Activator
	implements BundleActivator,
		ServiceTrackerCustomizer<ServletContext, ServletContext> {

	public ServletContext addingService(
		ServiceReference<ServletContext> serviceReference) {

		ServletContext servletContext = _bundleContext.getService(
			serviceReference);

		Hashtable<String, String> properties = new Hashtable<String, String>();

		properties.put(OSGiConstants.BEAN_ID, OSGiServlet.class.getName());
		properties.put(OSGiConstants.ORIGINAL_BEAN, Boolean.TRUE.toString());
		properties.put(OSGiConstants.SERVICE_VENDOR, ReleaseInfo.getVendor());

		ServletConfig servletConfig = new BundleServletConfig(
			servletContext, "OSGi Servlet", properties,
			new PortalHttpContext(servletContext));

		try {
			_osgiServlet = new OSGiServlet(_bundleContext);

			_osgiServlet.init(servletConfig);

			StrutsActionRegistry.register(
				OSGiServlet.SERVLET_MAPPING, _osgiServlet);

			_webPluginDeployer = new WebPluginDeployer(
				_bundleContext, _osgiServlet);

			_bundleContext.addBundleListener(_webPluginDeployer);
		}
		catch (Exception e) {
			_log.error(e, e);
		}

		return servletContext;
	}

	public void modifiedService(
		ServiceReference<ServletContext> serviceReference,
		ServletContext servletContext) {
	}

	public void removedService(
		ServiceReference<ServletContext> serviceReference,
		ServletContext servletContext) {

		_bundleContext.removeBundleListener(_webPluginDeployer);

		StrutsActionRegistry.unregister(OSGiServlet.SERVLET_MAPPING);

		_osgiServlet.destroy();
	}

	public void start(BundleContext bundleContext) throws Exception {
		_bundleContext = bundleContext;

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
				bundleContext, filter, this);

		_servletContextTracker.open();
	}

	public void stop(BundleContext bundleContext) throws Exception {
		_servletContextTracker.close();
	}

	private static Log _log = LogFactoryUtil.getLog(Activator.class);

	private BundleContext _bundleContext;
	private OSGiServlet _osgiServlet;
	private ServiceTracker<ServletContext, ServletContext> _servletContextTracker;
	private WebPluginDeployer _webPluginDeployer;

}