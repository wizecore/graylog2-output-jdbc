Graylog2 output plugin JDBC
=============================

Sends log to traditional RDMBS databases with the use of JDBC.

## How to build

Use eclise to build + export as JAR.
Use mvn package to create package.

## How to use

Download graylog2-output-jdbc.jar from releases and put inside /graylog-1.x/plugins folder
Restart Graylog2
Create new output globally or inside stream.
Make sure your RDBMS commit speed is suffcient to handle output message rate.

## Links

  * https://github.com/Graylog2
