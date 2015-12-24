Graylog2 output plugin JDBC
=============================

Sends log to traditional RDMBS databases with the use of JDBC.

## How to build

Use eclise to build + export as JAR.
Use mvn package to create package.

## How to use

  * Download graylog2-output-jdbc.jar from releases and put inside /graylog-1.x/plugins folder
  * Restart Graylog2
  * Create new output globally or inside stream.
  * Make sure your RDBMS commit speed is suffcient to handle output message rate. 

## Important note about JDBC driver

Graylog does not ship with JDBC drivers. You must add your driver manually AND update graylog2-output-jdbc.jar accordingly.

Add following line to graylog2-output-jdbc.jar/META-INF/MANIFEST.MF

	Class-Path: <driver>.jar

## Output options

#### Driver to use

Driver to initialize. Needed so URL can be handled properly.

Default value: NONE

#### JDBC URL

Required. Fully qualified JDBC url to connect to.

Default value: NONE

#### Username

Optional. Username to connect as. If not specified, no password will be passed to driver.

Default value: NONE 

#### Password

Optional. Password for user.

Default value: NONE 

#### Additional fields

Optional. Comma separated list of additional fields for Message insert query

Default value: NONE

#### Message insert query

Required. Query to execute to add log entry. Must contain required 4 columns and optional (see Additional fields). Must produce generated key (ID).

Default value: insert into log (message_date, message_id, source, message) values (?, ?, ?, ?)

#### Attribute insert query

Optional. If specified all attributes will be added using this query.

Default value: insert into log_attribute (log_id, name, value) values (?, ?, ?)

### Table definition

By default output uses table 'log' for main message entry and 'log_attribute' for attributes.
Sample table creation script:

	create table log (
		id numeric(10,0),
		message_date datetime,
		message_id varchar(32), 
		source varchar(32),
		message varchar(255) null
	)

	create table log_attribute (
		id numeric(10,0) identity,
		log_id numeric(10,0), 
		name varchar(255),
		value varchar(4096) null
	)

Make sure table log, column ID is generates IDs automatically. For example add 'identity' (MS SQL/Sybase ASE) or AUTO_INCREMENT (MySQL) into column definition.   

## Links

  * https://github.com/Graylog2

