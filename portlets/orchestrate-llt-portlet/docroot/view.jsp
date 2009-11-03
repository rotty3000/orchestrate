<%
/**
 * Copyright (c) 2000-2009 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
%>

<%@ include file="/init.jsp" %>

<%@ page import="com.liferay.portal.model.Layout" %>
<%@ page import="com.liferay.portal.model.LayoutConstants" %>
<%@ page import="com.liferay.portal.model.LayoutTemplate" %>
<%@ page import="com.liferay.portal.security.permission.ActionKeys" %>
<%@ page import="com.liferay.portal.service.LayoutTemplateLocalServiceUtil" %>
<%@ page import="com.liferay.portal.service.permission.LayoutPermissionUtil" %>
<%@ page import="com.liferay.portal.theme.ThemeDisplay" %>

<span/>

<%
boolean orchestrateLayoutTemplate = themeDisplay.isSignedIn() && (layout != null) && layout.getType().equals(LayoutConstants.TYPE_PORTLET) && LayoutPermissionUtil.contains(permissionChecker, plid, ActionKeys.UPDATE);

if (orchestrateLayoutTemplate) {
	LayoutTemplate layoutTemplate = LayoutTemplateLocalServiceUtil.getLayoutTemplate(layoutTypePortlet.getLayoutTemplateId(), false, null);

	if (request.getContextPath().equals("/" + layoutTemplate.getServletContextName())) {
		orchestrateLayoutTemplate = true;
	}
	else {
		orchestrateLayoutTemplate = false;
	}
}
%>

<c:if test="<%= orchestrateLayoutTemplate %>">
	<h1><%= portletDisplay.getId() %></h1>
</c:if>
