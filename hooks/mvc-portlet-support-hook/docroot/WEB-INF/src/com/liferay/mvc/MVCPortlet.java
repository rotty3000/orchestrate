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

package com.liferay.mvc;

import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.util.bridges.mvc.ActionCommandCache;

import java.io.IOException;

import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.WindowState;

/**
 * @author Brian Wing Shun Chan
 * @author Raymond Augé
 */
public class MVCPortlet extends com.liferay.util.bridges.mvc.MVCPortlet {

	public String getTemplateToken() {
		return _TEMPLATE_TOKEN;
	}

	@Override
	public void init() throws PortletException {
		super.init();

		jspPath = getInitParameter(getTemplateToken() + "-path");

		if (Validator.isNull(jspPath)) {
			jspPath = StringPool.SLASH;
		}
		else if (jspPath.contains(StringPool.BACK_SLASH) ||
				 jspPath.contains(StringPool.DOUBLE_SLASH) ||
				 jspPath.contains(StringPool.PERIOD) ||
				 jspPath.contains(StringPool.SPACE)) {

			throw new PortletException(
				getTemplateToken() + "-path " + jspPath +
					" has invalid characters");
		}
		else if (!jspPath.startsWith(StringPool.SLASH) ||
				 !jspPath.endsWith(StringPool.SLASH)) {

			throw new PortletException(
				getTemplateToken() + "-path " + jspPath +
					" must start and end with a /");
		}

		aboutJSP = getInitParameter("about-" + getTemplateToken());
		configJSP = getInitParameter("config-" + getTemplateToken());
		editJSP = getInitParameter("edit-" + getTemplateToken());
		editDefaultsJSP = getInitParameter(
			"edit-defaults-" + getTemplateToken());
		editGuestJSP = getInitParameter("edit-guest-" + getTemplateToken());
		helpJSP = getInitParameter("help-" + getTemplateToken());
		previewJSP = getInitParameter("preview-" + getTemplateToken());
		printJSP = getInitParameter("print-" + getTemplateToken());
		viewJSP = getInitParameter("view-" + getTemplateToken());

		clearRequestParameters = GetterUtil.getBoolean(
			getInitParameter("clear-request-parameters"));
		copyRequestParameters = GetterUtil.getBoolean(
			getInitParameter("copy-request-parameters"));

		String packagePrefix = getInitParameter(
			ActionCommandCache.ACTION_PACKAGE_NAME);

		if (Validator.isNotNull(packagePrefix)) {
			_actionCommandCache = new ActionCommandCache(packagePrefix);
		}
	}

	@Override
	public void serveResource(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws IOException, PortletException {

		String jspPage = resourceRequest.getParameter(
			getTemplateToken() + "Page");

		if (jspPage != null) {
			include(
				jspPage, resourceRequest, resourceResponse,
				PortletRequest.RESOURCE_PHASE);
		}
		else {
			super.serveResource(resourceRequest, resourceResponse);
		}
	}

	@Override
	protected void doDispatch(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		String jspPage = renderRequest.getParameter(
			getTemplateToken() + "Page");

		if (jspPage != null) {
			if (!isProcessRenderRequest(renderRequest)) {
				renderRequest.setAttribute(
					WebKeys.PORTLET_DECORATE, Boolean.FALSE);

				return;
			}

			WindowState windowState = renderRequest.getWindowState();

			if (windowState.equals(WindowState.MINIMIZED)) {
				return;
			}

			include(jspPage, renderRequest, renderResponse);
		}
		else {
			super.doDispatch(renderRequest, renderResponse);
		}
	}

	protected static String _TEMPLATE_TOKEN = "jsp";

}