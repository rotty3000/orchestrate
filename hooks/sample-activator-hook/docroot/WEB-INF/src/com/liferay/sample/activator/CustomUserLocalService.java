/**
 * Copyright (c) 2000-2010 Liferay, Inc. All rights reserved.
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

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.model.User;
import com.liferay.portal.service.UserLocalService;
import com.liferay.portal.service.UserLocalServiceWrapper;

import org.osgi.service.log.LogService;

/**
 * @author Raymond Augé
 */
public class CustomUserLocalService extends UserLocalServiceWrapper {

	public CustomUserLocalService(
		UserLocalService userLocalService, LogService logger) {

		super(userLocalService);

		_logger = logger;
	}

	@Override
	public User getUserById(long userId)
		throws PortalException, SystemException {

		System.out.println(
			"com.liferay.test.activator.CustomUserLocalService.getUserById(" +
				userId + ")");

		if (_logger != null) {
			_logger.log(
				LogService.LOG_INFO,
				"com.liferay.test.activator.CustomUserLocalService." +
					"getUserById(" + userId + ")");
		}

		return super.getUserById(userId);
	}

	private LogService _logger;

}