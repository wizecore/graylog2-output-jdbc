package com.wizecore.graylog2.plugin;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.graylog2.plugin.Message;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.outputs.MessageOutput;
import org.graylog2.plugin.streams.Stream;

import com.google.inject.assistedinject.Assisted;

/**
 * Implementation of plugin to Graylog 1.0 to send stream via JDBC
 * 
 * @author Huksley <huksley@sdot.ru>
 */
public class JDBCOutput implements MessageOutput {
	
	/**
	 * Maximum length of message column. More will be cut from Java side.
	 */
	public final static int MAX_MESSAGE = 255;
	
	/**
	 * Maximum length of value in log_attribute. More will be cut from Java side.
	 */
	public final static int MAX_VALUE = 4096;
	
	private final static String DEFAULT_LOG_QUERY = "insert into log (message_date, message_id, source, message) values (?, ?, ?, ?)"; 
	private final static String DEFAULT_LOG_ATTR_QUERY = "insert into log_attribute (log_id, name, value) values (?, ?, ?)";
	
	private Logger log = Logger.getLogger(JDBCOutput.class.getName());
	
	/**
	 * JDBC url
	 */
    private String url;
    
    /**
     * JDBC username.
     */
    private String username;
    
    /**
     * JDBC password.
     */
    private String password;
    
    /**
     * Driver to try. If set and driver creation fails no logs will be sent.
     */
    private String driver;
    
    /**
     * Don`t attempt anything.
     */
    private boolean driverFailed = false;
    
    /**
     * {@link #stop()} was called. Shutdown.
     */
    private boolean shutdown;
    
    /**
     * Additional fields to specify in main insert query.
     */
    private String[] fields;
    
    /**
     * Main insert query. Must generate ID.
     */
    private String logInsertQuery = DEFAULT_LOG_QUERY;
    
    /**
     * Attribute query, optional.
     */
    private String logInsertAttributeQuery = DEFAULT_LOG_ATTR_QUERY; 
    
    private Connection connection;
	private PreparedStatement logInsert;
	private PreparedStatement logInsertAttribute;
    
    @Inject 
    public JDBCOutput(@Assisted Stream stream, @Assisted Configuration conf) throws SQLException {
    	url = conf.getString("url");
    	username = conf.getString("username");
    	password = conf.getString("password");
    	driver = conf.getString("driver");
    	String f = conf.getString("fields");
    	if (f != null && !f.trim().equals("")) {
    		List<String> l = new ArrayList<String>();
    		StringTokenizer tk = new StringTokenizer(f, "\n;, ");
    		while (tk.hasMoreTokens()) {
    			l.add(tk.nextToken().trim());
    		}
    		fields = l.toArray(new String[l.size()]);
    	}
    	logInsertQuery = conf.getString("logInsertQuery");
    	logInsertAttributeQuery = conf.getString("logInsertAttributeQuery");
    	
    	log.info("Creating JDBC output " + url);
    	
    	if (driver != null && !driver.trim().isEmpty()) {
    		try {
    			Class.forName(driver);
    		} catch (Exception e) {
    			log.log(Level.SEVERE, "Failed to find/register driver (" + driver + "): " + e.getMessage(), e);
    			driverFailed = true;
    		}
    	}
    	
    	reconnect();
    }

	private void reconnect() throws SQLException {
		if (driverFailed) {
			return;
		}
		
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
		
		logInsert = null;
		logInsertAttribute = null;
		connection = username != null && !username.trim().isEmpty() ? 
    			DriverManager.getConnection(url, username.trim(), password != null ? password.trim() : null) :
    			DriverManager.getConnection(url);
    			
    	logInsert = connection.prepareStatement(logInsertQuery, Statement.RETURN_GENERATED_KEYS);
    	
    	if (logInsertAttributeQuery != null && !logInsertAttributeQuery.trim().equals("")) {
    		logInsertAttribute = connection.prepareStatement(logInsertAttributeQuery);
    	}
    	
    	// Disable autocommit
	    connection.setAutoCommit(false);
	}
    
    @Override
    public boolean isRunning() {
    	return connection != null;
    }
    
    @Override
    public void stop() {
    	shutdown = true;
    	
    	if (logInsertAttribute != null) {
    		try {
				logInsertAttribute.close();
    		} catch (SQLException e) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
    		logInsert = null;
    	}
    	
    	if (logInsert != null) {
    		try {
				logInsert.close();
    		} catch (SQLException e) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
    		logInsert = null;
    	}
    	
        if (connection != null) {
        	try {
				connection.close();
			} catch (SQLException e) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
        	connection = null;
        }
    }
    
    @Override
    public void write(List<Message> msgs) throws Exception {
    	for (Message msg: msgs) {
    		write(msg);
    	}
    }
    
    @Override
    public void write(Message msg) throws Exception {
    	if (shutdown || driverFailed) {
    		return;
    	}
    	    
    	try {
    		if (connection == null) {
    			reconnect();
    		}
    		
    		if (connection == null) {
    			return;
    		}
    		    		
    		synchronized (connection) {
        		int index = 1;
        		logInsert.setTimestamp(index++, new Timestamp(msg.getTimestamp().getMillis()));
        		logInsert.setString(index++, msg.getId());
        		logInsert.setString(index++, msg.getSource());
        		String ms = msg.getMessage();
        		if (ms != null && ms.length() > MAX_MESSAGE) {
        			ms = ms.substring(0, MAX_MESSAGE);
        		}
        		logInsert.setString(index++, ms);
        		
        		if (fields != null) {
        			for (String f: fields) {
        				Object value = msg.getField(f);
        				String s = value != null ? value.toString() : null;
        				if (s == null) {
        					logInsert.setNull(index++, Types.VARCHAR);
        				} else {
        					if (s.length() > MAX_VALUE) {
        						s = s.substring(0, MAX_VALUE);
        					}
        					logInsert.setString(index++, s);
        				}
        			}
        		}
        		
        		logInsert.executeUpdate();
        		
        		if (logInsertAttribute != null) {
            		Object id = null;
            		ResultSet ids = logInsert.getGeneratedKeys();
            		while (ids != null && ids.next()) {
            			id = ids.getObject(1);
            		}
            		if (id != null) {
            			for (Entry<String, Object> e: msg.getFieldsEntries()) {
            				String name = e.getKey();
            				Object value = e.getValue();
        					String s = value != null ? value.toString() : null;
            				logInsertAttribute.setObject(1, id);
            				logInsertAttribute.setString(2,  name);
        					if (s.length() > MAX_VALUE) {
        						s = s.substring(0, MAX_VALUE);
        					}
            				logInsertAttribute.setString(3, s);
            				logInsertAttribute.executeUpdate();
            			}
            		} else {
            			throw new SQLException("Failed to generate ID for primary log record!");
            		}
        		}
    		}
    	} catch (SQLException e) {
    		log.log(Level.WARNING, "JDBC output error: " + e.getMessage(), e);
    		if (connection != null) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                } catch (SQLException ee) {
                    // Don`t care
                }
    		}
    		connection = null;
    	} finally {
            if (connection != null) {
                connection.commit();
            }
        }
    }
            
	public interface Factory extends MessageOutput.Factory<JDBCOutput> {
		@Override
		JDBCOutput create(Stream stream, Configuration configuration);

		@Override
		Config getConfig();

		@Override
		Descriptor getDescriptor();
	}
    
    public static class Descriptor extends MessageOutput.Descriptor { 
    	public Descriptor() { 
    		super("JDBC Output", false, "", "Forwards stream to JDBC."); 
    	} 
    }

	public static class Config extends MessageOutput.Config {
		@Override
		public ConfigurationRequest getRequestedConfiguration() {
			final ConfigurationRequest configurationRequest = new ConfigurationRequest();
			configurationRequest.addField(new TextField("driver", "Driver to use", "", "Driver to initialize. Needed so URL can be handled properly.", ConfigurationField.Optional.OPTIONAL));
			configurationRequest.addField(new TextField("url", "JDBC URL", "", "Fully qualified url proto://host/db to connect to.", ConfigurationField.Optional.NOT_OPTIONAL));
			configurationRequest.addField(new TextField("username", "Username", "", "Username to connect as. Optional.", ConfigurationField.Optional.OPTIONAL));
			configurationRequest.addField(new TextField("password", "Password", "", "Password for user. Optional.", ConfigurationField.Optional.OPTIONAL));
			configurationRequest.addField(new TextField("fields", "Additional fields", "", "Comma separated list of additional fields for Message insert query", ConfigurationField.Optional.OPTIONAL));
			configurationRequest.addField(new TextField("logInsertQuery", "Message insert query", DEFAULT_LOG_QUERY, "Query to execute to add log entry. Must contain required 4 columns and optional (see Additional fields). Must produce generated key (ID).", ConfigurationField.Optional.NOT_OPTIONAL));
			configurationRequest.addField(new TextField("logInsertAttributeQuery", "Attribute insert query", DEFAULT_LOG_ATTR_QUERY, "Optional. If specified all attributes will be added.", ConfigurationField.Optional.OPTIONAL));			
			return configurationRequest;
		}
	}

}