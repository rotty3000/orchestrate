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

package com.liferay.sample.activator;

import com.liferay.portal.osgi.OSGiConstants;
import com.liferay.portal.service.UserLocalService;

import java.util.Hashtable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * <a href="UserLocalServiceTracker.java.html"><b><i>View Source</i></b></a>
 *
 * @author Raymond Augé
 */
public class UserLocalServiceTracker
	extends ServiceTracker<UserLocalService, UserLocalService> {

	public UserLocalServiceTracker(
		BundleContext bundleContext, Filter filter) {

		super(bundleContext, filter, null);

		_logServiceTracker = new ServiceTracker<LogService, LogService>(
			bundleContext, LogService.class, null);

		_logServiceTracker.open();
	}

	@Override
	public UserLocalService addingService(
		ServiceReference<UserLocalService> reference) {

		UserLocalService userLocalService = context.getService(reference);

		Hashtable<String, Object> properties = new Hashtable<String,Object>();

		properties.put(OSGiConstants.BEAN_ID, UserLocalService.class.getName());

		_serviceRegistration = context.registerService(
			UserLocalService.class, new CustomUserLocalService(
				userLocalService, _logServiceTracker.getService()), properties);

		return userLocalService;
	}

	@Override
	public void close() {
		super.close();

		_logServiceTracker.close();
	}

	@Override
	public void removedService(
		ServiceReference<UserLocalService> reference, UserLocalService service) {

		_serviceRegistration.unregister();

		context.ungetService(reference);
	}

	private ServiceTracker<LogService, LogService> _logServiceTracker;
	private ServiceRegistration<UserLocalService> _serviceRegistration;

}