package com.wizecore.graylog2.plugin;


import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
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
    
    private Connection connection;
    
    @Inject 
    public JDBCOutput(@Assisted Stream stream, @Assisted Configuration conf) {
    	url = conf.getString("url");
    	username = conf.getString("username");
    	password = conf.getString("password");
    	driver = conf.getString("driver");
    	log.info("Creating JDBC output " + url);
    }
    
    @Override
    public boolean isRunning() {
    	return connection != null;
    }
    
    @Override
    public void stop() {
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