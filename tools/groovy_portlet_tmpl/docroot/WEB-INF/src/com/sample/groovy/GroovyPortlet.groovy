/**
 * Copyright (c) 2000-2008 Liferay, Inc. All rights reserved.
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

package com.sample.groovy

import javax.portlet.ActionRequest
import javax.portlet.ActionResponse
import javax.portlet.GenericPortlet
import javax.portlet.PortletException
import javax.portlet.RenderRequest
import javax.portlet.RenderResponse
import javax.portlet.ResourceRequest
import javax.portlet.ResourceResponse

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

/**
 * <a href="GroovyPortlet.groovy.html"><b><i>View Source</i></b></a>
 *
 * @author Raymond Augé
 *
 */
class GroovyPortlet extends GenericPortlet {

	void init() throws PortletException {
		editGSP = getInitParameter("edit-gsp")
		helpGSP = getInitParameter("help-gsp")
		viewGSP = getInitParameter("view-gsp")
	}

	void doDispatch(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		String gspPage = renderRequest.getParameter("gspPage")

		if (gspPage != null) {
			include(gspPage, renderRequest, renderResponse)
		}
		else {
			super.doDispatch(renderRequest, renderResponse)
		}
	}

	void doEdit(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		if (renderRequest.getPreferences() == null) {
			super.doEdit(renderRequest, renderResponse)
		}
		else {
			include(editGSP, renderRequest, renderResponse)
		}
	}

	void doHelp(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		include(helpGSP, renderRequest, renderResponse)
	}

	void doView(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		include(viewGSP, renderRequest, renderResponse)
	}

	void processAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws IOException, PortletException {
	}

	void include(
			String path, RenderRequest renderRequest,
			RenderResponse renderResponse)
		throws IOException, PortletException {

		try {
			def resource = portletContext.getResourceAsStream(path)

			def binding = new Binding()
			binding.setProperty("renderRequest", renderRequest)
			binding.setProperty("renderResponse", renderResponse)

			def shell = new GroovyShell(binding)
			shell.evaluate(resource)
		}
		catch (e) {
			_log.error(path + " is not a valid include")
		}
	}

	def String editGSP
	def String helpGSP
	def String viewGSP

	def static Log _log = LogFactory.getLog(GroovyPortlet.class)

}