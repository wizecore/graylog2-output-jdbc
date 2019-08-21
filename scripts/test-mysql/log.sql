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
	
