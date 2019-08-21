# Graylog output plugin JDBC

Sends log to traditional RDMBS databases with the use of JDBC.

## How to build

- Use `mvn package` to create JAR
- See **Providing JDBC driver** on how to adjust JAR for your RDBMS
- Put prepared JAR inside **Graylog** plugins folder
- Put JDBC driver inside **Graylog** plugins folder
- Restart Graylog
- Create new output globally or inside stream
- Connect output to your stream
- Make sure your RDBMS commit speed is sufficient to handle output message rate

## Providing JDBC driver

Graylog does not ship with JDBC drivers.
You must add your driver JAR manually to plugins and **update** graylog-output-jdbc-2.5.1.jar accordingly.

Add following line to graylog2-output-jdbc-2.5.1.jar/META-INF/MANIFEST.MF

```
Class-Path: driver.jar
```

Example:

```
Class-Path: mysql-connector-java-8.0.17.jar
```

### How to update using command line?

Create manifest.mf and use jar tool from JDK:

```
jar -uvmf manifest-mysql.mf target/graylog-output-jdbc-2.5.1.jar
```

## Output options

#### Driver class to use (optional)

Driver class to initialize. Needed so URL can be handled properly. Recent JDK 8+ have autodiscovery of JDBC drivers so it might be not needed.

Default value: NONE

Example: `com.mysql.jdbc.Driver`

#### JDBC URL

Fully qualified JDBC url to connect to.

Default value: NONE

Example: `jdbc:mysql://localhost:3307/graylog`

#### Username (optional)

Username to connect to RDBMS as. If not specified, no password will be passed to driver.

Default value: NONE

#### Password (optional)

Password for user.

Default value: NONE

#### Additional fields (optional)

Comma separated list of additional attributes for Message insert query. I.e. if you have custom attribute defined in your log i.e. `hostname`, specify it here and adjust a query accordingly, i.e. `insert into log (message_date, message_id, source, message, hostname) values (?, ?, ?, ?, ?)`

This way you don`t need to use log_attribute table, greatly increasing commit speed.

Default value: NONE

#### Message insert query

Query to execute to add log entry. Must contain required 4 columns and optional (see Additional fields). Must produce generated key (ID).

Default value: `insert into log (message_date, message_id, source, message) values (?, ?, ?, ?)`

#### Attribute insert query (optional)

If specified all attributes will be added using this query.

Default value: `insert into log_attribute (log_id, name, value) values (?, ?, ?)`

### Table definition

By default output uses table 'log' for main message entry and 'log_attribute' for attributes.
Sample table creation script (MySQL):

```sql
	create table if not exists log (
		id int not null auto_increment,
		message_date datetime,
		message_id varchar(64),
		source varchar(32),
		message varchar(4096) null,
		PRIMARY KEY (id)
	);

	create table if not exists log_attribute (
		id int not null auto_increment,
		log_id numeric(10,0),
		name varchar(255),
		value varchar(4096) null,
		PRIMARY key (ID)
	);
```

Make sure that for table log, column ID are generated automatically. For example add 'identity' (MS SQL/Sybase ASE) or AUTO_INCREMENT (MySQL) into column definition.

## Links

- https://github.com/Graylog2
