package com.almende.eve.context;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import com.almende.eve.agent.AgentFactory;

public class FileContextFactory extends ContextFactory {
	public FileContextFactory (AgentFactory agentFactory, Map<String, Object> params) {
		super(agentFactory, params);
		
		// built the path where the agents will be stored
		String newPath = (params != null) ? (String) params.get("path") : null;
		setPath(newPath);
	}
	
	public FileContextFactory (AgentFactory agentFactory, String path) {
		super(agentFactory, null);
		setPath(path);
	}
	
	/**
	 * Set the path where the agents data will be stored
	 * @param path
	 */
	private void setPath(String path) {
		if (path == null) {
			path = ".eveagents";
			logger.warning(
				"Config parameter 'context.path' missing in Eve " +
				"configuration. Using the default path '" + path + "'");
		}
		if (!path.endsWith("/")) path += "/";
		this.path = path;
		
		// make the directory
		File file = new File(path);
		file.mkdir();
        
        // log info
        String info = "Agents will be stored in ";
        try {
			info += file.getCanonicalPath();
		} catch (IOException e) {
			info += path;
		}
        logger.info(info);
	}
	
	/**
	 * Get context with given id. Will return null if not found
	 * @param agentId
	 * @return context
	 */
	@Override
	public FileContext get(String agentId) {
		if (exists(agentId)) {
			return new FileContext(agentFactory, agentId, getFilename(agentId));
		}
		return null;
	}

	/**
	 * Create a context with given id. Will throw an exception when already.
	 * existing.
	 * @param agentId
	 * @return context
	 */
	@Override
	public synchronized FileContext create(String agentId) throws Exception {
		if (exists(agentId)) {
			throw new Exception("Cannot create context, " + 
					"context with id '" + agentId + "' already exists.");
		}
		
		// store the new (empty) file
		// TODO: it is not so nice solution to create an empty file to mark the context as created.		
		String filename = getFilename(agentId);
		File file = new File(filename);
		file.createNewFile();
		
		// instantiate the context
		return new FileContext(agentFactory, agentId, filename);
	}
	
	/**
	 * Delete a context. If the context does not exist, nothing will happen.
	 * @param agentId
	 */
	@Override
	public void delete(String agentId) {
		File file = new File(getFilename(agentId));
		if (file.exists()) {
			file.delete();
		}
	}

	/**
	 * Test if a context with given agentId exists
	 * @param agentId
	 */
	@Override
	public boolean exists(String agentId) {
		File file = new File(getFilename(agentId));
		return file.exists();
	}

	/**
	 * Get the current environment, "Production" or "Development". 
	 * In case of a file context, this will always return "Production".
	 * @return environment
	 */
	@Override
	public String getEnvironment() {
		return "Production";
	}

	/**
	 * Get the filename of the saved
	 * @param agentId
	 * @return
	 */
	private String getFilename(String agentId) {
		return (path != null ? path : "") + agentId;
	}

	private String path = null;
	private Logger logger = Logger.getLogger(this.getClass().getSimpleName());
}