/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
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

package com.liferay.web.extender.internal.webbundle;

import com.liferay.portal.deploy.DeployUtil;
import com.liferay.portal.deploy.auto.OSGiAutoDeployListener;
import com.liferay.portal.events.GlobalStartupAction;
import com.liferay.portal.kernel.deploy.auto.AutoDeployException;
import com.liferay.portal.kernel.deploy.auto.AutoDeployListener;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.Node;
import com.liferay.portal.kernel.xml.QName;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.kernel.xml.XPath;
import com.liferay.portal.kernel.zip.ZipWriter;
import com.liferay.portal.kernel.zip.ZipWriterFactoryUtil;
import com.liferay.portal.osgi.OSGiConstants;
import com.liferay.portal.util.Portal;
import com.liferay.portlet.dynamicdatamapping.util.DDMXMLUtil;
import com.liferay.util.UniqueList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.URI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.osgi.framework.Constants;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.depend.DependencyVisitor;

/**
 * @author Raymond Augé
 */
public class WebBundleProcessor {

	public WebBundleProcessor(
		java.io.File file, Map<String, String[]> parameterMap) {

		_file = file;

		_parameterMap = parameterMap;
	}

	public void process() throws IOException {
		String webContextpath = MapUtil.getString(
			_parameterMap, OSGiConstants.WEB_CONTEXTPATH);

		if (!webContextpath.startsWith(StringPool.SLASH)) {
			webContextpath = StringPool.SLASH.concat(webContextpath);
		}

		List<AutoDeployListener> autoDeployListeners =
			GlobalStartupAction.getAutoDeployListeners();

		for (AutoDeployListener autoDeployListener : autoDeployListeners) {
			if (autoDeployListener instanceof OSGiAutoDeployListener) {
				continue;
			}

			try {
				autoDeployListener.deploy(_file, webContextpath);
			}
			catch (AutoDeployException e) {
				e.printStackTrace();
			}
		}

		String deployDir = null;

		try {
			deployDir = DeployUtil.getAutoDeployDestDir();
		}
		catch (Exception e) {
			throw new IOException(e);
		}

		_deployedAppFolder = new File(deployDir, webContextpath);

		if (!_deployedAppFolder.exists() || !_deployedAppFolder.isDirectory()) {
			return;
		}

		File manifestFile = new File(
			_deployedAppFolder, "META-INF/MANIFEST.MF");

		if (!manifestFile.exists()) {
			FileUtil.mkdirs(manifestFile.getParent());

			manifestFile.createNewFile();
		}

		Manifest manifest = new Manifest();

		FileInputStream fis = new FileInputStream(manifestFile);

		try {
			manifest.read(fis);
		}
		finally {
			fis.close();
		}

		_resourcePaths = new ArrayList<String>();

		processPaths(_deployedAppFolder, _deployedAppFolder.toURI());

		Attributes attributes = manifest.getMainAttributes();

		attributes.putValue(OSGiConstants.WEB_CONTEXTPATH, webContextpath);

		// If it's not a bundle, then we need to manipulate it into one. The
		// spec states that this is only true when the Manifest does not contain
		// a Bundle_SymbolicName header.

		if (!attributes.containsKey(Constants.BUNDLE_SYMBOLICNAME)) {
			processBundleSymbolicName(attributes, webContextpath);
			processBundleVersion(attributes);
			processBundleManifestVersion(attributes);
			processPortletXML(webContextpath);
			processLiferayPortletXML(webContextpath);

			// The order of these operations is important

			processBundleClassPath(attributes);
			processDeclarativeReferences(attributes);

			processExportImportPackage(attributes);
		}

		if (!attributes.containsKey(
				Attributes.Name.MANIFEST_VERSION.toString())) {

			attributes.putValue(
				Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
		}

		FileOutputStream fos = new FileOutputStream(manifestFile);

		try {
			manifest.write(fos);
		}
		finally {
			fos.close();
		}
	}

	public java.io.InputStream getInputStream() throws IOException {
		if (!_deployedAppFolder.exists() || !_deployedAppFolder.isDirectory()) {
			return null;
		}

		ZipWriter zipWriter = ZipWriterFactoryUtil.getZipWriter();

		try {
			writeJarPaths(
				_deployedAppFolder, _deployedAppFolder.toURI(), zipWriter);
		}
		finally {
			//_deployedAppFolder.delete();
		}

		zipWriter.getFile();

		return new FileInputStream(zipWriter.getFile());
	}

	protected void processBundleClassPath(Attributes attributes) {
		StringBundler sb = new StringBundler();

		sb.append("WEB-INF/classes/");

		if (_resourcePaths.contains("WEB-INF/lib/")) {
			for (String path : _resourcePaths) {
				if (!path.startsWith("WEB-INF/lib/") ||
					!path.endsWith(".jar") ||
					ArrayUtil.contains(_EXCLUDED_CLASS_PATHS, path)) {

					continue;
				}

				sb.append(", ");
				sb.append(path);
			}
		}

		attributes.putValue(Constants.BUNDLE_CLASSPATH, sb.toString());
	}

	protected void processBundleManifestVersion(Attributes attributes) {
		String bundleManifestVersion = MapUtil.getString(
			_parameterMap, Constants.BUNDLE_MANIFESTVERSION);

		if (Validator.isNull(bundleManifestVersion)) {
			bundleManifestVersion = "2";
		}

		attributes.putValue(
			Constants.BUNDLE_MANIFESTVERSION, bundleManifestVersion);
	}

	protected void processBundleSymbolicName(
		Attributes attributes, String webContextpath) {

		String bundleSymbolicName = MapUtil.getString(
			_parameterMap, Constants.BUNDLE_SYMBOLICNAME);

		if (Validator.isNull(bundleSymbolicName)) {
			bundleSymbolicName = webContextpath.substring(1);
		}

		attributes.putValue(Constants.BUNDLE_SYMBOLICNAME, bundleSymbolicName);
	}

	protected void processBundleVersion(Attributes attributes) {
		_version = MapUtil.getString(_parameterMap, Constants.BUNDLE_VERSION);

		if (Validator.isNull(_version)) {
			_version = "0.0.1";
		}

		attributes.putValue(Constants.BUNDLE_VERSION, _version);
	}

	protected void processClass(
			DependencyVisitor dependencyVisitor, String className,
			InputStream inputStream, List<String> packageList)
		throws IOException {

		try {
			ClassReader classReader = new ClassReader(inputStream);

			classReader.accept(dependencyVisitor, 0);

			Set<String> packages = dependencyVisitor.getPackages();

			for (String packageName : packages) {
				packageList.add(
					packageName.replaceAll(
						StringPool.SLASH, StringPool.PERIOD));
			}
		}
		catch (NullPointerException npe) {
			//npe.printStackTrace();

		}
	}

	protected void processClassDependencies(File classFile) throws IOException {
		DependencyVisitor dependencyVisitor = new DependencyVisitor();

		FileInputStream fis = new FileInputStream(classFile);

		processClass(
			dependencyVisitor, classFile.getName(), fis, _referencedPackages);

		Set<String> jarPackages = dependencyVisitor.getGlobals().keySet();

		for (String jarPackage : jarPackages) {
			_classProvidedPackages.add(
				jarPackage.replaceAll(StringPool.SLASH, StringPool.PERIOD));
		}
	}

	protected void processDeclarativeReferences(Attributes attributes)
		throws IOException {

		// References from web.xml

		File xml = new File(_deployedAppFolder, "WEB-INF/web.xml");

		if (xml.exists()) {
			String content = FileUtil.read(xml);

			Document document = null;

			try {
				document = SAXReaderUtil.read(content, false);
			}
			catch (DocumentException de) {
				throw new IOException(de);
			}

			Element rootElement = document.getRootElement();

			for (String classReference : _WEBXML_CLASSREFERENCE_ELEMENTS) {
				XPath xPath = SAXReaderUtil.createXPath(
					classReference, "x", "http://java.sun.com/xml/ns/j2ee");

				List<Node> selectNodes = xPath.selectNodes(rootElement);

				for (Node node : selectNodes) {
					String value = node.getText().trim();

					int pos = value.lastIndexOf(StringPool.PERIOD);

					_referencedPackages.add(value.substring(0, pos));
				}
			}
		}

		// References from portlet.xml

		xml = new File(_deployedAppFolder, "WEB-INF/portlet.xml");

		if (xml.exists()) {
			String content = FileUtil.read(xml);

			Document document = null;

			try {
				document = SAXReaderUtil.read(content);
			}
			catch (DocumentException de) {
				throw new IOException(de);
			}

			Element rootElement = document.getRootElement();

			for (String classReference : _PORTLETXML_CLASSREFERENCE_ELEMENTS) {
				XPath xPath = SAXReaderUtil.createXPath(
					classReference, "x",
					"http://java.sun.com/xml/ns/portlet/portlet-app_2_0.xsd");

				List<Node> selectNodes = xPath.selectNodes(rootElement);

				for (Node node : selectNodes) {
					String value = node.getText().trim();

					int pos = value.lastIndexOf(StringPool.PERIOD);

					_referencedPackages.add(value.substring(0, pos));
				}
			}
		}

		// References from liferay-web.xml

		// TODO do we really need this?

		// References from liferay-portlet.xml

		xml = new File(_deployedAppFolder, "WEB-INF/liferay-portlet.xml");

		if (xml.exists()) {
			String content = FileUtil.read(xml);

			Document document = null;

			try {
				document = SAXReaderUtil.read(content);
			}
			catch (DocumentException de) {
				throw new IOException(de);
			}

			Element rootElement = document.getRootElement();

			for (String classReference :
					_LIFERAYPORTLETXML_CLASSREFERENCE_ELEMENTS) {
				XPath xPath = SAXReaderUtil.createXPath(classReference);

				List<Node> selectNodes = xPath.selectNodes(rootElement);

				for (Node node : selectNodes) {
					String value = node.getText().trim();

					int pos = value.lastIndexOf(StringPool.PERIOD);

					_referencedPackages.add(value.substring(0, pos));
				}
			}
		}
	}

	protected void processExportImportPackage(Attributes attributes)
		throws IOException {

		for (String packageName : _referencedPackages) {
			if (packageName.startsWith("java.") ||
				_jarProvidedPackages.contains(packageName)) {

				continue;
			}

			if (_classProvidedPackages.contains(packageName)) {
				_exportPackages.add(packageName);
			}
			else {
				_importPackages.add(packageName);
			}
		}

		for (String packageName : _classProvidedPackages) {
			_exportPackages.add(packageName);
		}

		if (!_exportPackages.isEmpty()) {
			Collections.sort(_exportPackages);

			String exportPackages = StringUtil.merge(
				_exportPackages, ";version=\"".concat(_version).concat("\","));

			attributes.putValue(
				Constants.EXPORT_PACKAGE,
				exportPackages.concat(";version=\"").concat(_version).concat("\""));
		}
		String importPackage = MapUtil.getString(
			_parameterMap, Constants.IMPORT_PACKAGE);

		if (Validator.isNotNull(importPackage)) {
			attributes.putValue(Constants.IMPORT_PACKAGE, importPackage);
		}
		else if (!_importPackages.isEmpty()) {
			Collections.sort(_importPackages);

			String importPackages = StringUtil.merge(
				_importPackages, StringPool.COMMA);

			attributes.putValue(Constants.IMPORT_PACKAGE, importPackages);
		}
	}

	protected void processJarDependencies(File jarFile) throws IOException {
		DependencyVisitor dependencyVisitor = new DependencyVisitor();

		ZipFile zipFile = new ZipFile(jarFile);

		Enumeration<? extends ZipEntry> en = zipFile.entries();

		while (en.hasMoreElements()) {
			ZipEntry zipEntry = en.nextElement();

			String name = zipEntry.getName();

			if (name.endsWith(".class")) {
				InputStream inputStream = zipFile.getInputStream(zipEntry);

				processClass(
					dependencyVisitor, name, inputStream, _referencedPackages);
			}
		}

		Set<String> jarPackages = dependencyVisitor.getGlobals().keySet();


		for (String jarPackage : jarPackages) {
			_jarProvidedPackages.add(
				jarPackage.replaceAll(StringPool.SLASH, StringPool.PERIOD));
		}
	}

	protected void processLiferayPortletXML(String webContextpath)
		throws IOException {

		File liferayPortletXMLFile = new File(
			_deployedAppFolder, "WEB-INF/liferay-portlet.xml");

		if (!liferayPortletXMLFile.exists()) {
			return;
		}

		String content = FileUtil.read(liferayPortletXMLFile);

		Document liferayPortletXMLDoc = null;

		try {
			liferayPortletXMLDoc = SAXReaderUtil.read(content);
		}
		catch (DocumentException de) {
			throw new IOException(de);
		}

		Element rootEl = liferayPortletXMLDoc.getRootElement();

		List<Element> portletElements = rootEl.elements("portlet");

		for (Element portletElement : portletElements) {
			Element previousChild = portletElement.element("virtual-path");

			if (previousChild == null) {
				previousChild = portletElement.element("icon");
			}

			if (previousChild == null) {
				previousChild = portletElement.element("portlet-name");
			}

			Element strutsPathElement = portletElement.element("struts-path");

			if (strutsPathElement == null) {
				List<Node> children = portletElement.content();

				int pos = children.indexOf(previousChild);

				strutsPathElement = SAXReaderUtil.createElement(
					"struts-path");

				strutsPathElement.setText("osgi".concat(webContextpath));

				children.add(pos + 1, strutsPathElement);
			}
			else {
				String strutsPath = strutsPathElement.getTextTrim();

				if (!strutsPath.startsWith(StringPool.SLASH)) {
					strutsPath = StringPool.SLASH.concat(strutsPath);
				}

				strutsPath = "osgi".concat(webContextpath).concat(strutsPath);

				strutsPathElement.setText(strutsPath);
			}
		}

		content = DDMXMLUtil.formatXML(liferayPortletXMLDoc);

		FileUtil.write(liferayPortletXMLFile, content);
	}

	protected void processPaths(File directory, URI baseURI) throws IOException {
		File[] files = directory.listFiles();

		for (File file : files) {
			String relativePath = baseURI.relativize(file.toURI()).getPath();

			_resourcePaths.add(relativePath);

			if (relativePath.startsWith("WEB-INF/classes/") &&
				relativePath.endsWith(".class")) {

				processClassDependencies(file);
			}
			else if (relativePath.startsWith("WEB-INF/lib/") &&
					 relativePath.endsWith(".jar") &&
					 !ArrayUtil.contains(_EXCLUDED_CLASS_PATHS, relativePath)) {

				processJarDependencies(file);
			}

			if (file.isDirectory()) {
				processPaths(file, baseURI);
			}
		}
	}

	protected void processPortletXML(String webContextpath)
		throws IOException {
		File portletXMLFile = new File(
			_deployedAppFolder, "WEB-INF/" +
			Portal.PORTLET_XML_FILE_NAME_STANDARD);

		if (!portletXMLFile.exists()) {
			return;
		}

		String content = FileUtil.read(portletXMLFile);

		Document document = null;

		try {
			document = SAXReaderUtil.read(content);
		}
		catch (DocumentException de) {
			throw new IOException(de);
		}

		Element rootElement = document.getRootElement();

		List<Element> portletElements = rootElement.elements("portlet");

		for (Element portletElement : portletElements) {
			String portletName = portletElement.elementText("portlet-name");

			String invokerPortletName = "osgi".concat(webContextpath).concat(
				StringPool.SLASH).concat(portletName);

			XPath xPath = SAXReaderUtil.createXPath(
				_INVOKER_PORTLET_NAME_XPATH);

			Element invokerPortletNameEl = (Element)xPath.selectSingleNode(
				portletElement);

			if (invokerPortletNameEl == null) {
				Element portletClassElement = portletElement.element(
					"portlet-class");

				List<Node> children = portletElement.content();

				int pos = children.indexOf(portletClassElement);

				QName qName = rootElement.getQName();

				Element initParamElement = SAXReaderUtil.createElement(
					SAXReaderUtil.createQName(
						"init-param", qName.getNamespace()));

				initParamElement.addElement("name").setText(
					"com.liferay.portal.invokerPortletName");
				initParamElement.addElement("value").setText(
					invokerPortletName);

				children.add(pos + 1, initParamElement);
			}
			else {
				Element valueElement = invokerPortletNameEl.element("value");

				invokerPortletName = valueElement.getTextTrim();

				if (!invokerPortletName.startsWith(StringPool.SLASH)) {
					invokerPortletName = StringPool.SLASH.concat(
						invokerPortletName);
				}

				invokerPortletName = "osgi".concat(webContextpath).concat(
					invokerPortletName);

				valueElement.setText(invokerPortletName);
			}
		}

		content = DDMXMLUtil.formatXML(document);

		FileUtil.write(portletXMLFile, content);
	}

	protected void writeJarPaths(
		File directory, URI baseURI, ZipWriter zipWriter) {

		File[] files = directory.listFiles();

		FileInputStream fis = null;

		for (File file : files) {
			String path = baseURI.relativize(file.toURI()).getPath();

			if (!file.isDirectory()) {
				if (ArrayUtil.contains(_EXCLUDED_CLASS_PATHS, path)) {
					continue;
				}

				try {
					fis = new FileInputStream(file);

					zipWriter.addEntry(path, fis);
				}
				catch (IOException ioe) {
					ioe.printStackTrace();
				}
				finally {
					if (fis != null) {
						try {
							fis.close();
						}
						catch (IOException ioe) {
							ioe.printStackTrace();
						}
					}

					fis = null;
				}
			}
			else {
				writeJarPaths(file, baseURI, zipWriter);
			}
		}
	}

	private static final String[] _EXCLUDED_CLASS_PATHS = new String[] {
		"WEB-INF/lib/commons-codec.jar",
		"WEB-INF/lib/commons-fileupload.jar",
		"WEB-INF/lib/commons-io.jar",
		"WEB-INF/lib/commons-lang.jar",
		"WEB-INF/lib/commons-logging.jar",
		"WEB-INF/lib/log4j.jar",
		"WEB-INF/lib/util-bridges.jar",
		"WEB-INF/lib/util-java.jar",
		"WEB-INF/lib/util-taglib.jar"
	};

	private static final String _INVOKER_PORTLET_NAME_XPATH =
		"init-param[name/text()='com.liferay.portal.invokerPortletName']";

	private static final String[] _LIFERAYPORTLETXML_CLASSREFERENCE_ELEMENTS =
		new String[] {
			"//configuration-action-class", "//indexer-class",
			"//open-search-class", "//portlet-url-class",
			"//friendly-url-mapper-class", "//url-encoder-class",
			"//portlet-data-handler-class", "//portlet-layout-listener-class",
			"//poller-processor-class", "//pop-message-listener-class",
			"//social-activity-interpreter-class",
			"//social-request-interpreter-class", "//webdav-storage-class",
			"//xml-rpc-method-class", "//control-panel-entry-class",
			"//asset-renderer-factory", "//atom-collection-adapter",
			"//custom-attributes-display", "//permission-propagator",
			"//workflow-handler"
		};

	private static final String[] _PORTLETXML_CLASSREFERENCE_ELEMENTS =
		new String[] {
			"//x:filter-class", "//x:listener-class", "//x:portlet-class",
			"//x:resource-bundle"
		};

	private static final String[] _WEBXML_CLASSREFERENCE_ELEMENTS =
		new String[] {
			"//x:filter-class", "//x:listener-class", "//x:servlet-class"
		};

	private List<String> _classProvidedPackages = new UniqueList<String>();
	private File _deployedAppFolder;
	private List<String> _exportPackages = new UniqueList<String>();
	private File _file;
	private List<String> _importPackages = new UniqueList<String>();
	private List<String> _jarProvidedPackages = new UniqueList<String>();
	private List<String> _referencedPackages = new UniqueList<String>();
	private List<String> _resourcePaths;
	private Map<String, String[]> _parameterMap;
	private String _version;

}