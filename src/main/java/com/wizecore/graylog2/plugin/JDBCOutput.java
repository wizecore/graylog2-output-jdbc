package com.wizecore.graylog2.plugin;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
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
    
	public final static int PORT_MIN = 9000;
	public final static int PORT_MAX = 9099;
	
	private Logger log = Logger.getLogger(JDBCOutput.class.getName());
    private String url;
    private String username;
    private String password;
    private String driver;
    private boolean shutdown;
    
    private Connection connection;
	private PreparedStatement logInsert;
	private PreparedStatement logInsertAttribute;
    
    @Inject 
    public JDBCOutput(@Assisted Stream stream, @Assisted Configuration conf) throws SQLException {
    	url = conf.getString("url");
    	username = conf.getString("username");
    	password = conf.getString("password");
    	driver = conf.getString("driver");
    	log.info("Creating JDBC output " + url);
    	
    	if (driver != null && !driver.trim().isEmpty()) {
    		try {
    			Class.forName(driver);
    		} catch (Exception e) {
    			log.log(Level.SEVERE, "Failed to find/register driver (" + driver + "): " + e.getMessage(), e);
    		}
    	}
    	
    	reconnect();
    }

	private void reconnect() throws SQLException {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
		
		connection = username != null && !username.trim().isEmpty() ? 
    			DriverManager.getConnection(url, username.trim(), password != null ? password.trim() : null) :
    			DriverManager.getConnection(url);
    			
    	logInsert = connection.prepareStatement("insert into log (message_date, message_id, source, message) values (?, ?, ?, ?)");
    	logInsertAttribute = connection.prepareStatement("insert into log_attribute (message_id, name, value) values (?, ?, ?)");
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
    	if (shutdown) {
    		return;
    	}
    	
    	try {
    		if (connection == null) {
    			reconnect();
    		}
    		
    		int index = 1;
    		logInsert.setTimestamp(index++, new Timestamp(msg.getTimestamp().getMillis()));
    		logInsert.setString(index++, msg.getId());
    		logInsert.setString(index++, msg.getSource());
    		logInsert.setString(index++, msg.getMessage());
    		logInsert.execute();
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
    				logInsertAttribute.setString(3, s);
    				logInsertAttribute.execute();
    			}
    		} else {
    			throw new SQLException("Failed to generate ID for primary log record!");
    		}
    	} catch (SQLException e) {
    		log.log(Level.WARNING, "JDBC output error: " + e.getMessage(), e);
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ee) {
                // Don`t care
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
			return configurationRequest;
		}
	}

}