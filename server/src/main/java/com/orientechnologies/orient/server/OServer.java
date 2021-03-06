/*
 * Copyright 1999-2010 Luca Garulli (l.garulli--at--orientechnologies.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.server;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.common.parser.OSystemVariableResolver;
import com.orientechnologies.common.profiler.OProfiler;
import com.orientechnologies.orient.core.OConstants;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.command.OCommandManager;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.record.ODatabaseRecord;
import com.orientechnologies.orient.core.exception.OConfigurationException;
import com.orientechnologies.orient.core.security.OSecurityManager;
import com.orientechnologies.orient.enterprise.command.script.OCommandScript;
import com.orientechnologies.orient.server.command.script.OCommandExecutorScript;
import com.orientechnologies.orient.server.config.OServerConfiguration;
import com.orientechnologies.orient.server.config.OServerConfigurationLoaderXml;
import com.orientechnologies.orient.server.config.OServerHandlerConfiguration;
import com.orientechnologies.orient.server.config.OServerNetworkListenerConfiguration;
import com.orientechnologies.orient.server.config.OServerNetworkProtocolConfiguration;
import com.orientechnologies.orient.server.config.OServerStorageConfiguration;
import com.orientechnologies.orient.server.config.OServerUserConfiguration;
import com.orientechnologies.orient.server.handler.OServerHandler;
import com.orientechnologies.orient.server.managed.OrientServer;
import com.orientechnologies.orient.server.network.OServerNetworkListener;
import com.orientechnologies.orient.server.network.protocol.ONetworkProtocol;

public class OServer {
	protected ReentrantReadWriteLock													lock						= new ReentrantReadWriteLock();

	protected volatile boolean																running					= true;
	protected OServerConfigurationLoaderXml										configurationLoader;
	protected OServerConfiguration														configuration;
	protected OServerShutdownHook															shutdownHook;
	protected List<OServerHandler>														handlers				= new ArrayList<OServerHandler>();
	protected Map<String, Class<? extends ONetworkProtocol>>	protocols				= new HashMap<String, Class<? extends ONetworkProtocol>>();
	protected List<OServerNetworkListener>										listeners				= new ArrayList<OServerNetworkListener>();
	protected Map<String, ODatabaseRecord<?>>									memoryDatabases	= new HashMap<String, ODatabaseRecord<?>>();
	protected static ThreadGroup															threadGroup;

	private OrientServer																			managedServer;

	public OServer() throws ClassNotFoundException, MalformedObjectNameException, NullPointerException,
			InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
		threadGroup = new ThreadGroup("OrientDB Server");

		// REGISTER THE COMMAND SCRIPT
		OCommandManager.instance().register(OCommandScript.class, OCommandExecutorScript.class);

		OGlobalConfiguration.STORAGE_KEEP_OPEN.setValue(true);
		System.setProperty("com.sun.management.jmxremote", "true");

		MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();

		// REGISTER PROFILER
		mBeanServer.registerMBean(OProfiler.getInstance().startRecording(), new ObjectName("OrientDB:type=Profiler"));

		// REGISTER SERVER
		managedServer = new OrientServer();
		mBeanServer.registerMBean(managedServer, new ObjectName("OrientDB:type=Server"));

		shutdownHook = new OServerShutdownHook();
	}

	@SuppressWarnings("unchecked")
	public void startup() throws InstantiationException, IllegalAccessException, ClassNotFoundException, IllegalArgumentException,
			SecurityException, InvocationTargetException, NoSuchMethodException {
		OLogManager.instance().info(this, "OrientDB Server v" + OConstants.ORIENT_VERSION + " is starting up...");

		loadConfiguration();

		Orient.instance();
		Orient.instance().removeShutdownHook();

		// REGISTER PROTOCOLS
		for (OServerNetworkProtocolConfiguration p : configuration.network.protocols)
			protocols.put(p.name, (Class<? extends ONetworkProtocol>) Class.forName(p.implementation));

		// STARTUP LISTENERS
		for (OServerNetworkListenerConfiguration l : configuration.network.listeners)
			listeners.add(new OServerNetworkListener(l.ipAddress, l.portRange, l.protocol, protocols.get(l.protocol), l.parameters,
					l.commands));

		registerHandlers();

		OLogManager.instance().info(this, "OrientDB Server v" + OConstants.ORIENT_VERSION + " is active.");
	}

	public void shutdown() {
		if (!running)
			return;

		running = false;

		OLogManager.instance().info(this, "OrientDB Server is shutdowning...");

		try {
			lock.writeLock().lock();

			// SHUTDOWN LISTENERS
			for (OServerNetworkListener l : listeners) {
				OLogManager.instance().info(this, "Shutdowning connection listener '" + l + "'...");
				l.shutdown();
			}

			// SHUTDOWN HANDLERS
			for (OServerHandler h : handlers) {
				OLogManager.instance().info(this, "Shutdowning handler %s...", h.getName());
				try {
					h.shutdown();
				} catch (Throwable t) {
				}
			}

			Orient.instance().shutdown();

		} finally {
			lock.writeLock().unlock();
		}

		OLogManager.instance().info(this, "OrientDB Server shutdown complete");
		System.out.println();
	}

	public String getStoragePath(final String iName) {
		// SEARCH IN CONFIGURED PATHS
		String dbPath = configuration.getStoragePath(iName);

		if (dbPath == null) {
			// SEARCH IN DEFAULT DATABASE DIRECTORY
			dbPath = OSystemVariableResolver.resolveSystemVariables("${ORIENTDB_HOME}/databases/" + iName + "/");
			File f = new File(dbPath + "default.odh");
			if (!f.exists())
				throw new OConfigurationException("Database '" + iName + "' is not configured on server");

			dbPath = "local:${ORIENTDB_HOME}/databases/" + iName;
		}

		return dbPath;
	}

	public ThreadGroup getServerThreadGroup() {
		return threadGroup;
	}

	/**
	 * Authenticate a server user.
	 * 
	 * @param iUserName
	 *          Username to authenticate
	 * @param iPassword
	 *          Password in clear
	 * @return true if authentication is ok, otherwise false
	 */
	public boolean authenticate(final String iUserName, final String iPassword, final String iResourceToCheck) {
		final OServerUserConfiguration user = getUser(iUserName);

		if (user != null && (iPassword == null || user.password.equals(iPassword))) {
			if (user.resources.equals("*"))
				// ACCESS TO ALL
				return true;

			String[] resourceParts = user.resources.split(",");
			for (String r : resourceParts)
				if (r.equals(iResourceToCheck))
					return true;
		}

		// WRONG PASSWORD OR NO AUTHORIZATION
		return false;
	}

	public OServerUserConfiguration getUser(final String iUserName) {
		return configuration.getUser(iUserName);
	}

	public boolean existsStoragePath(final String iURL) {
		return configuration.getStoragePath(iURL) != null;
	}

	public OServerConfiguration getConfiguration() {
		return configuration;
	}

	public void saveConfiguration() throws IOException {
		configurationLoader.save(configuration);
	}

	public Map<String, ODatabaseRecord<?>> getMemoryDatabases() {
		return memoryDatabases;
	}

	public Map<String, Class<? extends ONetworkProtocol>> getProtocols() {
		return protocols;
	}

	public List<OServerNetworkListener> getListeners() {
		return listeners;
	}

	@SuppressWarnings("unchecked")
	public <RET extends OServerNetworkListener> RET getListenerByProtocol(final Class<? extends ONetworkProtocol> iProtocolClass) {
		for (OServerNetworkListener l : listeners)
			if (l.getProtocolType().equals(iProtocolClass))
				return (RET) l;

		return null;
	}

	public OrientServer getManagedServer() {
		return managedServer;
	}

	public static String getOrientHome() {
		String v = System.getenv("ORIENTDB_HOME");

		if (v == null)
			v = System.getProperty("orient.home");

		return v;
	}

	public List<OServerHandler> getHandlers() {
		return handlers;
	}

	@SuppressWarnings("unchecked")
	public <RET extends OServerHandler> RET getHandler(final Class<RET> iHandlerClass) {
		for (OServerHandler h : handlers)
			if (h.getClass().equals(iHandlerClass))
				return (RET) h;

		return null;
	}

	protected void loadConfiguration() {
		try {
			String config = OServerConfiguration.DEFAULT_CONFIG_FILE;
			if (System.getProperty(OServerConfiguration.PROPERTY_CONFIG_FILE) != null)
				config = System.getProperty(OServerConfiguration.PROPERTY_CONFIG_FILE);

			configurationLoader = new OServerConfigurationLoaderXml(OServerConfiguration.class, config);
			configuration = configurationLoader.load();

			loadStorages();
			loadUsers();

		} catch (IOException e) {
			OLogManager.instance().error(this, "Error on reading server configuration.", OConfigurationException.class);
		}
	}

	private void loadUsers() throws IOException {
		if (configuration.users != null && configuration.users.length > 0) {
			for (OServerUserConfiguration u : configuration.users) {
				if (u.name.equals(OServerConfiguration.SRV_ROOT_ADMIN))
					// FOUND
					return;
			}
		}

		createAdminUser();
	}

	private void loadStorages() {
		String type;
		for (OServerStorageConfiguration stg : OServerMain.server().getConfiguration().storages)
			if (stg.loadOnStartup) {
				Orient.instance().loadStorage(stg.path);

				type = stg.path.substring(0, stg.path.indexOf(":"));
				OLogManager.instance().info(this, "-> Loaded " + type + " database '" + stg.name + "'");
			}
	}

	private void createAdminUser() throws IOException {
		configuration.users = new OServerUserConfiguration[1];

		final long generatedPassword = new Random(System.currentTimeMillis()).nextLong();
		String encodedPassword = OSecurityManager.instance().digest2String(String.valueOf(generatedPassword));

		configuration.users[0] = new OServerUserConfiguration(OServerConfiguration.SRV_ROOT_ADMIN, encodedPassword, "*");
		saveConfiguration();
	}

	private void registerHandlers() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		if (configuration.handlers != null) {
			// ACTIVATE HANDLERS
			OServerHandler handler;
			for (OServerHandlerConfiguration h : configuration.handlers) {
				handler = (OServerHandler) Class.forName(h.clazz).newInstance();
				handlers.add(handler);

				handler.config(this, h.parameters);
				handler.startup();
			}
		}
	}
}
