package com.liferay.sample.activator;

import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.osgi.OSGiConstants;
import com.liferay.portal.service.UserLocalService;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;

public class Activator implements BundleActivator {

	public final void start(BundleContext bundleContext) throws Exception {
		StringBundler sb = new StringBundler(7);

		sb.append("(&(");
		sb.append(OSGiConstants.BEAN_ID);
		sb.append("=");
		sb.append(UserLocalService.class.getName());
		sb.append(")(");
		sb.append(OSGiConstants.ORIGINAL_BEAN);
		sb.append("=*))");

		Filter filter = bundleContext.createFilter(sb.toString());

		_userLocalServiceTracker = new UserLocalServiceTracker(
			bundleContext, filter);

		_userLocalServiceTracker.open();
	}

	public final void stop(BundleContext bundleContext) throws Exception {
		_userLocalServiceTracker.close();
	}

	private UserLocalServiceTracker _userLocalServiceTracker;

}