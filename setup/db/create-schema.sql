SET foreign_key_checks = 0;
DROP TABLE IF EXISTS `vmops`.`configuration`;
DROP TABLE IF EXISTS `vmops`.`ip_forwarding`;
DROP TABLE IF EXISTS `vmops`.`management_agent`;
DROP TABLE IF EXISTS `vmops`.`host`;
DROP TABLE IF EXISTS `vmops`.`mshost`;
DROP TABLE IF EXISTS `vmops`.`service_offering`;
DROP TABLE IF EXISTS `vmops`.`user`;
DROP TABLE IF EXISTS `vmops`.`user_ip_address`;
DROP TABLE IF EXISTS `vmops`.`user_statistics`;
DROP TABLE IF EXISTS `vmops`.`vm_template`;
DROP TABLE IF EXISTS `vmops`.`vm_instance`;
DROP TABLE IF EXISTS `vmops`.`domain_router`;
DROP TABLE IF EXISTS `vmops`.`event`;
DROP TABLE IF EXISTS `vmops`.`host_details`;
DROP TABLE IF EXISTS `vmops`.`host_pod_ref`;
DROP TABLE IF EXISTS `vmops`.`host_zone_ref`;
DROP TABLE IF EXISTS `vmops`.`data_ceneter`;
DROP TABLE IF EXISTS `vmops`.`volumes`;
DROP TABLE IF EXISTS `vmops`.`storage`;
DROP TABLE IF EXISTS `vmops`.`disk_template_ref`;
DROP TABLE IF EXISTS `vmops`.`data_center`;
DROP TABLE IF EXISTS `vmops`.`pricing`;
DROP TABLE IF EXISTS `vmops`.`sequence`;
DROP TABLE IF EXISTS `vmops`.`user_vm`;
DROP TABLE IF EXISTS `vmops`.`template_host_ref`;
DROP TABLE IF EXISTS `vmops`.`ha_work`;
DROP TABLE IF EXISTS `vmops`.`dc_vnet_alloc`;
DROP TABLE IF EXISTS `vmops`.`dc_ip_address_alloc`;
DROP TABLE IF EXISTS `vmops`.`vlan`;
DROP TABLE IF EXISTS `vmops`.`host_vlan_map`;
DROP TABLE IF EXISTS `vmops`.`pod_vlan_map`;
DROP TABLE IF EXISTS `vmops`.`vm_host`;
DROP TABLE IF EXISTS `vmops`.`op_ha_work`;
DROP TABLE IF EXISTS `vmops`.`op_dc_vnet_alloc`;
DROP TABLE IF EXISTS `vmops`.`op_dc_ip_address_alloc`;
DROP TABLE IF EXISTS `vmops`.`op_vm_host`;
DROP TABLE IF EXISTS `vmops`.`op_host_queue`;
DROP TABLE IF EXISTS `vmops`.`console_proxy`;
DROP TABLE IF EXISTS `vmops`.`secondary_storage_vm`;
DROP TABLE IF EXISTS `vmops`.`domain`;
DROP TABLE IF EXISTS `vmops`.`account`;
DROP TABLE IF EXISTS `vmops`.`limit`;
DROP TABLE IF EXISTS `vmops`.`op_host_capacity`;
DROP TABLE IF EXISTS `vmops`.`alert`;
DROP TABLE IF EXISTS `vmops_usage`.`event`;
DROP TABLE IF EXISTS `vmops_usage`.`vmops_usage`;
DROP TABLE IF EXISTS `vmops_usage`.`usage_vm_instance`;
DROP TABLE IF EXISTS `vmops_usage`.`usage_ip_address`;
DROP TABLE IF EXISTS `vmops_usage`.`usage_network`;
DROP TABLE IF EXISTS `vmops_usage`.`usage_job`;
DROP TABLE IF EXISTS `vmops_usage`.`account`;
DROP TABLE IF EXISTS `vmops_usage`.`user_statistics`;
DROP TABLE IF EXISTS `vmops_usage`.`usage_volume`;
DROP TABLE IF EXISTS `vmops_usage`.`usage_storage`;
DROP TABLE IF EXISTS `vmops_usage`.`usage_security_group`;
DROP TABLE IF EXISTS `vmops_usage`.`usage_load_balancer_policy`;
DROP TABLE IF EXISTS `vmops`.`op_lock`;
DROP TABLE IF EXISTS `vmops`.`op_host_upgrade`;
DROP TABLE IF EXISTS `vmops`.`snapshots`;
DROP TABLE IF EXISTS `vmops`.`scheduled_volume_backups`;
DROP TABLE IF EXISTS `vmops`.`vm_disk`;
DROP TABLE IF EXISTS `vmops`.`disk_offering`;
DROP TABLE IF EXISTS `vmops`.`security_group`;
DROP TABLE IF EXISTS `vmops`.`network_rule_config`;
DROP TABLE IF EXISTS `vmops`.`host_details`;
DROP TABLE IF EXISTS `vmops`.`launch_permission`;
DROP TABLE IF EXISTS `vmops`.`resource_limit`;
DROP TABLE IF EXISTS `vmops`.`async_job`;
DROP TABLE IF EXISTS `vmops`.`sync_queue`;
DROP TABLE IF EXISTS `vmops`.`sync_queue_item`;
DROP TABLE IF EXISTS `vmops`.`security_group_vm_map`;
DROP TABLE IF EXISTS `vmops`.`load_balancer_vm_map`;
DROP TABLE IF EXISTS `vmops`.`load_balancer`;
DROP TABLE IF EXISTS `vmops`.`storage_pool`;
DROP TABLE IF EXISTS `vmops`.`storage_pool_host_ref`;
DROP TABLE IF EXISTS `vmops`.`template_spool_ref`;
DROP TABLE IF EXISTS `vmops`.`guest_os`;
DROP TABLE IF EXISTS `vmops`.`snapshot_policy`;
DROP TABLE IF EXISTS `vmops`.`snapshot_policy_ref`;
DROP TABLE IF EXISTS `vmops`.`snapshot_schedule`;


CREATE TABLE `vmops`.`op_host_upgrade` (
  `host_id` bigint unsigned NOT NULL UNIQUE COMMENT 'host id',
  `version` varchar(20) NOT NULL COMMENT 'version',
  `state` varchar(20) NOT NULL COMMENT 'state',
  PRIMARY KEY (`host_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`op_lock` (
  `key` varchar(128) NOT NULL COMMENT 'primary key of the table',
  `mac` varchar(17) NOT NULL COMMENT 'mac address of who acquired this lock',
  `ip` varchar(15) NOT NULL COMMENT 'ip address of who acquired this lock',
  `thread` varchar(256) NOT NULL COMMENT 'Thread that acquired this lock',
  `acquired_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Time acquired',
  `waiters` int NOT NULL DEFAULT 0 COMMENT 'How many have waited for this',
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`op_host_queue` (
  `id` bigint unsigned UNIQUE NOT NULL AUTO_INCREMENT COMMENT 'id',
  `host_id` bigint unsigned NOT NULL COMMENT 'id of the host',
  `command` varchar(256) NOT NULL COMMENT 'command sending to the host',
  `listener` varchar(256) NOT NULL COMMENT 'handler class for this command', 
  `instance_id` bigint unsigned COMMENT 'id of the vm instance',
  `request` blob NOT NULL COMMENT 'Command being sent to the host',
  `sequence` bigint unsigned NOT NULL COMMENT 'Sequence number of the communication',
  `active` tinyint(1) unsigned NOT NULL COMMENT 'Is this the active command',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB Default CHARSET=utf8;
  
CREATE TABLE  `vmops`.`configuration` (
  `category` varchar(255) NOT NULL DEFAULT 'Advanced',
  `instance` varchar(255) NOT NULL,
  `component` varchar(255) NOT NULL DEFAULT 'management-server',
  `name` varchar(255) NOT NULL,
  `value` varchar(1024),
  `description` varchar(1024),
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`op_ha_work` (
  `id` bigint unsigned UNIQUE NOT NULL AUTO_INCREMENT COMMENT 'id',
  `instance_id` bigint unsigned NOT NULL COMMENT 'vm instance that needs to be ha.',
  `type` varchar(32) NOT NULL COMMENT 'type of work',
  `vm_type` varchar(32) NOT NULL COMMENT 'VM type',
  `state` varchar(32) NOT NULL COMMENT 'state of the vm instance when this happened.',
  `mgmt_server_id` bigint unsigned COMMENT 'management server that has taken up the work of doing ha',
  `host_id` bigint unsigned COMMENT 'host that the vm is suppose to be on',
  `created` datetime NOT NULL COMMENT 'time the entry was requested',
  `tried` int(10) unsigned COMMENT '# of times tried',
  `taken` datetime COMMENT 'time it was taken by the management server',
  `step` varchar(32) NOT NULL COMMENT 'Step in the work',
  `time_to_try` bigint COMMENT 'time to try do this work',
  `updated` bigint unsigned NOT NULL COMMENT 'time the VM state was updated when it was stored into work queue',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`sequence` (
  `name` varchar(64) UNIQUE NOT NULL COMMENT 'name of the sequence',
  `value` bigint unsigned NOT NULL COMMENT 'sequence value',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `vmops`.`sequence` (name, value) VALUES ('vm_instance_seq', 1);
INSERT INTO `vmops`.`sequence` (name, value) VALUES ('vm_template_seq', 200);
INSERT INTO `vmops`.`sequence` (name, value) VALUES ('public_mac_address_seq', 1);
INSERT INTO `vmops`.`sequence` (name, value) VALUES ('private_mac_address_seq', 1);
INSERT INTO `vmops`.`sequence` (name, value) VALUES ('storage_pool_seq', 200);

CREATE TABLE  `vmops`.`disk_template_ref` (
  `id` bigint unsigned NOT NULL auto_increment,
  `description` varchar(255) NOT NULL,
  `host` varchar(255) NOT NULL COMMENT 'host on which the server exists',
  `parent` varchar(255) NOT NULL COMMENT 'parent path',
  `path` varchar(255) NOT NULL,
  `size` int(10) unsigned NOT NULL COMMENT 'size of the disk',
  `type` varchar(255) NOT NULL COMMENT 'file system type',
  `created` datetime NOT NULL COMMENT 'Date created',
  `removed` datetime COMMENT 'Date removed if not null',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`volumes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary Key',
  `account_id` bigint unsigned NOT NULL COMMENT 'owner.  foreign key to account table',
  `domain_id` bigint unsigned NOT NULL COMMENT 'the domain that the owner belongs to',
  `host_id` bigint unsigned  COMMENT 'server it belongs to. foreign key to host table',
  `pool_id` bigint unsigned  COMMENT 'pool it belongs to. foreign key to storage_pool table',
  `instance_id` bigint unsigned NULL COMMENT 'vm instance it belongs to. foreign key to vm_instance table',
  `name` varchar(255) COMMENT 'A user specified name for the volume',
  `name_label` varchar(255) COMMENT 'A name label for the volume that is automatically generated',
  `size` bigint unsigned NOT NULL COMMENT 'total size',
  `folder` varchar(255)  COMMENT 'The folder where the volume is saved',
  `path` varchar(255) COMMENT 'Path',
  `pod_id` bigint unsigned COMMENT 'pod this volume belongs to',
  `data_center_id` bigint unsigned NOT NULL COMMENT 'data center this volume belongs to',
  `iscsi_name` varchar(255) COMMENT 'iscsi target name',
  `host_ip` varchar(15)  COMMENT 'host ip address for convenience',
  `volume_type` varchar(64) COMMENT 'root, swap or data',
  `resource_type` varchar(64) COMMENT 'pool-based or host-based',
  `mirror_state` varchar(64) COMMENT 'not_mirrored, active or defunct',
  `mirror_vol` bigint unsigned COMMENT 'the other half of the mirrored set if mirrored',
  `disk_offering_id` bigint unsigned COMMENT 'can be null for system VMs',
  `template_name` varchar (255) COMMENT 'fk to vm_template.unique_name',
  `template_id` bigint unsigned COMMENT 'fk to vm_template.id',
  `destroyed` tinyint(1) COMMENT 'indicates whether the volume was destroyed by the user or not',
  `created` datetime COMMENT 'Date Created',
  `removed` datetime COMMENT 'Date removed.  not null if removed',
  `status` varchar(32) COMMENT 'Async API volume creation status',
  `last_snap_id` bigint unsigned COMMENT 'Id of the most recent snapshot. foreign key to snapshot table',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`snapshots` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary Key',
  `account_id` bigint unsigned NOT NULL COMMENT 'owner.  foreign key to account table',
  `host_id` bigint unsigned  COMMENT 'server it belongs to. foreign key to host table',
  `pool_id` bigint unsigned COMMENT 'server it belongs to. foreign key to storage pool table',
  `volume_id` bigint unsigned NOT NULL COMMENT 'volume it belongs to. foreign key to volume table',
  `status` varchar(32) COMMENT 'snapshot creation status',
  `path` varchar(255) COMMENT 'Path',
  `name` varchar(255) NOT NULL COMMENT 'snapshot name',
  `snapshot_type` int(4) NOT NULL COMMENT 'type of snapshot, e.g. hourly, daily, one-off',
  `type_description` varchar(25) COMMENT 'description of the type of snapshot, e.g. hourly, daily, one-off',
  `created` datetime COMMENT 'Date Created',
  `removed` datetime COMMENT 'Date removed.  not null if removed',
  `backup_snap_id` varchar(255) COMMENT 'Back up uuid of the snapshot',
  `prev_snap_id` bigint unsigned COMMENT 'Id of the most recent snapshot',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`vlan` (
  `id` bigint unsigned NOT NULL UNIQUE AUTO_INCREMENT,
  `vlan_id` varchar(255),
  `vlan_gateway` varchar(255),
  `vlan_netmask` varchar(255),
  `vlan_name` varchar(255),
  `description` varchar(255),
  `vlan_type` varchar(255) COMMENT 'usually specified as public or private',
  `data_center_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`host_vlan_map` (
  `id` bigint unsigned NOT NULL UNIQUE AUTO_INCREMENT,
  `host_id` bigint unsigned NOT NULL COMMENT 'database id of host. foreign key to host table',
  `vlan_id` varchar(255),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`pod_vlan_map` (
  `id` bigint unsigned NOT NULL UNIQUE AUTO_INCREMENT,
  `pod_id` bigint unsigned NOT NULL COMMENT 'database id of host. foreign key to host table',
  `vlan_db_id` bigint unsigned NOT NULL COMMENT 'database id of vlan. foreign key to vlan table',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`data_center` (
  `id` bigint unsigned NOT NULL UNIQUE AUTO_INCREMENT,
  `name` varchar(255),
  `description` varchar(255),
  `dns1` varchar(255) NOT NULL,
  `dns2` varchar(255),
  `dns3` varchar(255),
  `dns4` varchar(255),
  `gateway` varchar(15),
  `netmask` varchar(15),
  `vnet` varchar(255),
  `router_mac_address` varchar(17) NOT NULL DEFAULT '02:00:00:00:00:01' COMMENT 'mac address for the router within the domain',
  `mac_address` bigint unsigned NOT NULL DEFAULT '1' COMMENT 'Next available mac address for the ethernet card interacting with public internet',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`xen_server_pool` (
  `pool_uuid` varchar(255) NOT NULL UNIQUE,
  `name` varchar(255),
  `pod_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`pool_uuid`)  
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`op_dc_ip_address_alloc` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `ip_address` varchar(15) NOT NULL COMMENT 'ip address',
  `data_center_id` bigint unsigned NOT NULL COMMENT 'data center it belongs to',
  `pod_id` bigint unsigned NOT NULL COMMENT 'pod it belongs to',
  `instance_id` bigint unsigned NULL COMMENT 'instance id',
  `taken` datetime COMMENT 'Date taken',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`host_pod_ref` (
  `id` bigint unsigned NOT NULL UNIQUE auto_increment,
  `name` varchar(255) NOT NULL,
  `data_center_id` bigint unsigned NOT NULL,
  `cidr_address` varchar(15) NOT NULL COMMENT 'CIDR address for the pod',
  `cidr_size` bigint unsigned NOT NULL COMMENT 'CIDR size for the pod',
  `description` varchar(255) COMMENT 'store private ip range in startIP-endIP format',
  PRIMARY KEY  (`id`),
  UNIQUE KEY (`name`, `data_center_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`op_dc_vnet_alloc` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'primary id',
    `vnet` varchar(18) NOT NULL COMMENT 'vnet',
    `data_center_id` bigint unsigned NOT NULL COMMENT 'data center the vnet belongs to',
    `account_id` bigint unsigned NULL COMMENT 'account the vnet belongs to right now',
    `taken` datetime COMMENT 'Date taken',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`ip_forwarding` (
  `id` bigint unsigned NOT NULL auto_increment,
  `group_id` bigint unsigned NOT NULL,
  `public_ip_address` varchar(15) NOT NULL,
  `public_port` varchar(10) default NULL,
  `private_ip_address` varchar(15) NOT NULL,
  `private_port` varchar(10) default NULL,
  `enabled` tinyint(1) NOT NULL default '1',
  `protocol` varchar(16) NOT NULL default 'TCP',
  `forwarding` tinyint(1) NOT NULL default '1',
  `algorithm` varchar(255) default NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`host` (
  `id` bigint unsigned NOT NULL auto_increment,
  `name` varchar(255) NOT NULL,
  `status` varchar(32) NOT NULL,
  `type` varchar(32) NOT NULL,
  `private_ip_address` varchar(15) NOT NULL,
  `private_netmask` varchar(15),
  `private_mac_address` varchar(17),
  `storage_ip_address` varchar(15) NOT NULL,
  `storage_netmask` varchar(15),
  `storage_mac_address` varchar(17),
  `storage_ip_address_2` varchar(15),
  `storage_mac_address_2` varchar(17),
  `storage_netmask_2` varchar(15),
  `public_ip_address` varchar(15) UNIQUE,
  `public_netmask` varchar(15),
  `public_mac_address` varchar(17),
  `proxy_port` int(10) unsigned,
  `data_center_id` bigint unsigned NOT NULL,
  `pod_id` bigint unsigned,
  `cpus` int(10) unsigned,
  `speed` int(10) unsigned,
  `url` varchar(255) COMMENT 'iqn for the servers',
  `fs_type` varchar(32),
  `hypervisor_type` varchar(32) COMMENT 'hypervisor type, can be NONE for storage',
  `ram` bigint unsigned,
  `resource` varchar(255) DEFAULT NULL COMMENT 'If it is a local resource, this is the class name',
  `version` varchar(40) NOT NULL,
  `sequence` bigint unsigned NOT NULL DEFAULT 1,
  `parent` varchar(255) COMMENT 'parent path for the storage server',
  `total_size` bigint unsigned COMMENT 'TotalSize',
  `capabilities` varchar(255) COMMENT 'host capabilities in comma separated list',
  `guid` varchar(255) UNIQUE,
  `dom0_memory` bigint unsigned NOT NULL COMMENT 'memory used by dom0 for computing and routing servers',
  `last_ping` int(10) unsigned NOT NULL COMMENT 'time in seconds from the start of machine of the last ping',
  `mgmt_server_id` bigint unsigned COMMENT 'ManagementServer this host is connected to.',
  `disconnected` datetime COMMENT 'Time this was disconnected',
  `created` datetime COMMENT 'date the host first signed on',
  `removed` datetime COMMENT 'date removed if not null',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`host_details` (
  `id` bigint unsigned NOT NULL auto_increment,
  `host_id` bigint NOT NULL COMMENT 'host id',
  `name` varchar(255) NOT NULL,
  `value` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`mshost` (
  `id` bigint unsigned NOT NULL auto_increment,
  `msid` bigint  NOT NULL UNIQUE COMMENT 'management server id derived from MAC address',
  `name` varchar(255),
  `version` varchar(255),
  `service_ip` varchar(15) NOT NULL,
  `service_port` integer NOT NULL,
  `last_update` DATETIME NULL COMMENT 'Last record update time',
  `removed` datetime COMMENT 'date removed if not null',
  `alert_count` integer NOT NULL DEFAULT 0,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`op_vm_host` (
  `id` bigint unsigned NOT NULL UNIQUE COMMENT 'foreign key to host_id',
  `vnc_ports` bigint unsigned NOT NULL DEFAULT '0' COMMENT 'vnc ports open on the host',
  `start_at` int(5) unsigned  NOT NULL DEFAULT '0' COMMENT 'Start the vnc port look up at this bit',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`service_offering` (
  `id` bigint unsigned NOT NULL auto_increment,
  `name` varchar(255) NOT NULL,
  `cpu` int(10) unsigned NOT NULL COMMENT '# of cores',
  `speed` int(10) unsigned NOT NULL COMMENT 'speed per core in mhz',
  `ram_size` bigint unsigned NOT NULL,
  `nw_rate` smallint unsigned default 200 COMMENT 'network rate throttle mbits/s',
  `mc_rate` smallint unsigned default 10 COMMENT 'mcast rate throttle mbits/s',
  `created` datetime NOT NULL COMMENT 'date created',
  `removed` datetime NULL COMMENT 'date removed',
  `ha_enabled` tinyint(1) unsigned NOT NULL DEFAULT 0 COMMENT 'Enable HA',
  `mirrored` tinyint(1) unsigned NOT NULL DEFAULT 1 COMMENT 'Enable mirroring?',
  `display_text` varchar(4096) NULL COMMENT 'Description text set by the admin for display purpose only',
  `guest_ip_type` varchar(255) NOT NULL DEFAULT 'Virtualized' COMMENT 'Type of guest network -- direct or virtualized',
  `use_local_storage` tinyint(1) unsigned NOT NULL DEFAULT 0 COMMENT 'Indicates whether local storage pools should be used',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`user` (
  `id` bigint unsigned NOT NULL auto_increment,
  `username` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `firstname` varchar(255) default NULL,
  `lastname` varchar(255) default NULL,
  `email` varchar(255) default NULL,
  `disabled` int(1) unsigned NOT NULL default '0',
  `api_key` varchar(255) default NULL,
  `secret_key` varchar(255) default NULL,
  `created` datetime NOT NULL COMMENT 'date created',
  `removed` datetime COMMENT 'date removed',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`event` (
  `id` bigint unsigned NOT NULL auto_increment,
  `type` varchar(32) NOT NULL,
  `description` varchar(1024) NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `created` datetime NOT NULL,
  `level` varchar(16) NOT NULL,
  `parameters` varchar(1024) NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`user_ip_address` (
  `account_id` bigint unsigned NULL,
  `domain_id` bigint unsigned NULL,
  `public_ip_address` varchar(15) unique NOT NULL,
  `data_center_id` bigint unsigned NOT NULL COMMENT 'zone that it belongs to',
  `source_nat` int(1) unsigned NOT NULL default '0',
  `allocated` datetime NULL COMMENT 'Date this ip was allocated to someone',
  `vlan_db_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`public_ip_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`user_statistics` (
  `id` bigint unsigned UNIQUE NOT NULL AUTO_INCREMENT,
  `data_center_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `net_bytes_received` bigint unsigned NOT NULL default '0',
  `net_bytes_sent` bigint unsigned NOT NULL default '0',
  `current_bytes_received` bigint unsigned NOT NULL default '0',
  `current_bytes_sent` bigint unsigned NOT NULL default '0',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`vm_template` (
  `id` bigint unsigned NOT NULL auto_increment,
  `unique_name` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `public` int(1) unsigned NOT NULL,
  `type` varchar(32) NULL,
  `hvm`  int(1) unsigned NOT NULL COMMENT 'requires HVM',
  `bits` int(6) unsigned NOT NULL COMMENT '32 bit or 64 bit',
  `url` varchar(255) NULL COMMENT 'the url where the template exists externally',
  `format` varchar(32) NOT NULL COMMENT 'format for the template', 
  `created` datetime NOT NULL COMMENT 'Date created',
  `removed` datetime COMMENT 'Date removed if not null',
  `account_id` bigint unsigned NOT NULL COMMENT 'id of the account that created this template',
  `checksum` varchar(255) COMMENT 'checksum for the template root disk',
  `ready` int(1) unsigned NOT NULL COMMENT 'ready to be used',
  `create_status` varchar(32) COMMENT 'template creation status',
  `display_text` varchar(4096) NULL COMMENT 'Description text set by the admin for display purpose only',
  `enable_password` int(1) unsigned NOT NULL default 1 COMMENT 'true if this template supports password reset',
  `guest_os_id` bigint unsigned NOT NULL COMMENT 'the OS of the template',
  `bootable` int(1) unsigned NOT NULL default 1 COMMENT 'true if this template represents a bootable ISO',
  `prepopulate` int(1) unsigned NOT NULL default 0 COMMENT 'prepopulate this template to primary storage',
  `cross_zones` int(1) unsigned NOT NULL default 0 COMMENT 'Make this template available in all zones',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`vm_instance` (
  `id` bigint unsigned UNIQUE NOT NULL,
  `name` varchar(255) NOT NULL,
  `display_name` varchar(255),
  `group` varchar(255),
  `instance_name` varchar(255) NOT NULL COMMENT 'name of the vm instance running on the hosts',
  `state` varchar(32) NOT NULL,
  `vm_template_id` bigint unsigned,
  `iso_id` bigint unsigned,
  `guest_os_id` bigint unsigned NOT NULL,
  `pool_id` bigint unsigned,
  `private_mac_address` varchar(17),
  `private_ip_address` varchar(15),
  `private_netmask` varchar(15),
  `pod_id` bigint unsigned NOT NULL,
  `storage_ip` varchar(15),
  `data_center_id` bigint unsigned NOT NULL COMMENT 'Data Center the instance belongs to',
  `host_id` bigint unsigned,
  `ha_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Should HA be enabled for this VM',
  `mirrored_vols` tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Are the volumes mirrored',
  `update_count` bigint unsigned NOT NULL DEFAULT 0 COMMENT 'date state was updated',
  `update_time` datetime COMMENT 'date the destroy was requested',
  `created` datetime NOT NULL COMMENT 'date created',
  `removed` datetime COMMENT 'date removed if not null',
  `type` varchar(32) NOT NULL COMMENT 'type of vm it is',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`pricing` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `price` FLOAT UNSIGNED NOT NULL,
  `price_unit` VARCHAR(45) NOT NULL,
  `type` VARCHAR(255) NOT NULL,
  `type_id` INTEGER UNSIGNED,
  `created` DATETIME NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`user_vm` (
  `id` bigint unsigned UNIQUE NOT NULL,
  `domain_router_id` bigint unsigned NOT NULL COMMENT 'router id',
  `vnc_port` integer unsigned COMMENT 'vnc port of the vm',
  `vnc_password` varchar(255) NOT NULL COMMENT 'vnc password',
  `service_offering_id` bigint unsigned NOT NULL COMMENT 'service offering id',
  `vnet` varchar(18) COMMENT 'vnet',
  `account_id` bigint unsigned NOT NULL COMMENT 'user id of owner',
  `domain_id` bigint unsigned NOT NULL,
  `proxy_id` bigint unsigned NULL COMMENT 'console proxy allocated in previous session',
  `proxy_assign_time` DATETIME NULL COMMENT 'time when console proxy was assigned',
  `guest_ip_address` varchar(15) NOT NULL COMMENT 'ip address within the guest network',
  `guest_mac_address` varchar(17) NOT NULL COMMENT 'mac address within the guest network',
  `guest_netmask` varchar(15) NOT NULL COMMENT 'netmask within the guest network',
  `external_ip_address` varchar(15)  COMMENT 'ip address within the external network',
  `external_mac_address` varchar(17)  COMMENT 'mac address within the external network',
  `external_vlan_db_id` bigint unsigned  COMMENT 'foreign key into vlan table',
  `user_data` varchar(2048),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`domain_router` (
  `id` bigint unsigned UNIQUE NOT NULL COMMENT 'Primary Key',
  `gateway` varchar(15)  NOT NULL COMMENT 'ip address of the gateway to this domR',
  `ram_size` int(10) unsigned NOT NULL DEFAULT 128 COMMENT 'memory to use in mb',
  `dns1` varchar(15) COMMENT 'dns1',
  `dns2` varchar(15) COMMENT 'dns2',
  `domain` varchar(255) COMMENT 'domain',
  `public_mac_address` varchar(17)   COMMENT 'mac address of the public facing network card',
  `public_ip_address` varchar(15)  COMMENT 'public ip address used for source net',
  `public_netmask` varchar(15)  COMMENT 'netmask used for the domR',
  `guest_mac_address` varchar(17) NOT NULL COMMENT 'mac address of the data center facing network card',
  `guest_netmask` varchar(15) NOT NULL COMMENT 'netmask used for the guest network',
  `guest_ip_address` varchar(15) NOT NULL COMMENT ' ip address in the guest network',
  `vnet` varchar(18) COMMENT 'vnet',
  `vlan_db_id` bigint unsigned COMMENT 'Foreign key into vlan id table',
  `vlan_id` varchar(255) COMMENT 'optional VLAN ID for DomainRouter that can be used in rundomr.sh',
  `account_id` bigint unsigned NOT NULL COMMENT 'account id of owner',
  `domain_id` bigint unsigned NOT NULL,
  `dhcp_ip_address` bigint unsigned NOT NULL DEFAULT 2 COMMENT 'next ip address for dhcp for this domR',
  `role` varchar(64) NOT NULL COMMENT 'type of role played by this router',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET=utf8 COMMENT = 'information about the domR instance';

CREATE TABLE  `vmops`.`template_host_ref` (
  `id` bigint unsigned NOT NULL auto_increment,
  `host_id` bigint unsigned NOT NULL,
  `pool_id` bigint unsigned,
  `template_id` bigint unsigned NOT NULL,
  `created` DATETIME NOT NULL,
  `last_updated` DATETIME,
  `job_id` varchar(255),
  `download_pct` int(10) unsigned,
  `size` bigint unsigned,
  `download_state` varchar(255),
  `error_str` varchar(255),
  `local_path` varchar(255),
  `install_path` varchar(255),
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`event` (
  `id` bigint unsigned NOT NULL auto_increment,
  `type` varchar(32) NOT NULL,
  `description` varchar(1024) NOT NULL,
  `user_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `created` datetime NOT NULL,
  `level` varchar(16) NOT NULL,
  `parameters` varchar(1024) NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`vmops_usage` (
  `id` bigint unsigned NOT NULL auto_increment,
  `zone_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `domain_id` bigint unsigned NOT NULL,
  `description` varchar(1024) NOT NULL,
  `usage_display` varchar(255) NOT NULL,
  `usage_type` int(1) unsigned,
  `raw_usage` FLOAT UNSIGNED NOT NULL,
  `vm_instance_id` bigint unsigned,
  `vm_name` varchar(255),
  `service_offering_id` bigint unsigned,
  `template_id` bigint unsigned,
  `size` bigint unsigned,
  `start_date` DATETIME NOT NULL,
  `end_date` DATETIME NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`usage_vm_instance` (
  `usage_type` int(1) unsigned,
  `zone_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `vm_instance_id` bigint unsigned NOT NULL,
  `vm_name` varchar(255) NOT NULL,
  `service_offering_id` bigint unsigned NOT NULL,
  `template_id` bigint unsigned NOT NULL,
  `start_date` DATETIME NOT NULL,
  `end_date` DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`usage_network` (
  `account_id` bigint unsigned NOT NULL,
  `zone_id` bigint unsigned NOT NULL,
  `bytes_sent` bigint unsigned NOT NULL default '0',
  `bytes_received` bigint unsigned NOT NULL default '0',
  `net_bytes_received` bigint unsigned NOT NULL default '0',
  `net_bytes_sent` bigint unsigned NOT NULL default '0',
  `current_bytes_received` bigint unsigned NOT NULL default '0',
  `current_bytes_sent` bigint unsigned NOT NULL default '0',
  `event_time_millis` bigint unsigned NOT NULL default '0',
  PRIMARY KEY (`account_id`, `zone_id`, `event_time_millis`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`usage_ip_address` (
  `account_id` bigint unsigned NOT NULL,
  `domain_id` bigint unsigned NOT NULL,
  `zone_id` bigint unsigned NOT NULL,
  `public_ip_address` varchar(15) NOT NULL,
  `assigned` DATETIME NOT NULL,
  `released` DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`usage_job` (
  `id` bigint unsigned NOT NULL auto_increment,
  `host` varchar(255),
  `pid` int(5),
  `job_type` int(1),
  `scheduled` int(1),
  `start_millis` bigint unsigned NOT NULL default '0' COMMENT 'start time in milliseconds of the aggregation range used by this job',
  `end_millis` bigint unsigned NOT NULL default '0' COMMENT 'end time in milliseconds of the aggregation range used by this job',
  `exec_time` bigint unsigned NOT NULL default '0' COMMENT 'how long in milliseconds it took for the job to execute',
  `start_date` DATETIME COMMENT 'start date of the aggregation range used by this job',
  `end_date` DATETIME COMMENT 'end date of the aggregation range used by this job',
  `success` int(1),
  `heartbeat` DATETIME NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`account` (
  `id` bigint unsigned NOT NULL,
  `account_name` varchar(100) COMMENT 'an account name set by the creator of the account, defaults to username for single accounts',
  `type` int(1) unsigned NOT NULL,
  `domain_id` bigint unsigned,
  `disabled` int(1) unsigned NOT NULL default '0',
  `removed` datetime COMMENT 'date removed',
  `cleanup_needed` tinyint(1) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`user_statistics` (
  `id` bigint unsigned UNIQUE NOT NULL,
  `data_center_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `net_bytes_received` bigint unsigned NOT NULL default '0',
  `net_bytes_sent` bigint unsigned NOT NULL default '0',
  `current_bytes_received` bigint unsigned NOT NULL default '0',
  `current_bytes_sent` bigint unsigned NOT NULL default '0',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`usage_volume` (
  `id` bigint unsigned NOT NULL,
  `zone_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `domain_id` bigint unsigned NOT NULL,
  `disk_offering_id` bigint unsigned NOT NULL,
  `created` DATETIME NOT NULL,
  `deleted` DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`usage_storage` (
  `id` bigint unsigned NOT NULL,
  `zone_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `domain_id` bigint unsigned NOT NULL,
  `storage_type` int(1) unsigned NOT NULL,
  `size` bigint unsigned NOT NULL,
  `created` DATETIME NOT NULL,
  `deleted` DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`usage_security_group` (
  `id` bigint unsigned NOT NULL,
  `zone_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `domain_id` bigint unsigned NOT NULL,
  `vm_id` bigint unsigned NOT NULL,
  `num_rules` bigint unsigned NOT NULL,
  `created` DATETIME NOT NULL,
  `deleted` DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops_usage`.`usage_load_balancer_policy` (
  `id` bigint unsigned NOT NULL,
  `zone_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `domain_id` bigint unsigned NOT NULL,
  `created` DATETIME NOT NULL,
  `deleted` DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`console_proxy` (
  `id` bigint unsigned NOT NULL auto_increment,
  `gateway` varchar(15)  NOT NULL COMMENT 'gateway info for this console proxy towards public network interface',
  `dns1` varchar(15) COMMENT 'dns1',
  `dns2` varchar(15) COMMENT 'dns2',
  `domain` varchar(255) COMMENT 'domain',
  `public_mac_address` varchar(17) NOT NULL unique COMMENT 'mac address of the public facing network card',
  `public_ip_address` varchar(15) UNIQUE COMMENT 'public ip address for the console proxy',
  `public_netmask` varchar(15) NOT NULL COMMENT 'public netmask used for the console proxy',
  `vlan_db_id` bigint unsigned COMMENT 'Foreign key into vlan id table',
  `vlan_id` varchar(255) COMMENT 'optional VLAN ID for console proxy that can be used',
  `ram_size` int(10) unsigned NOT NULL DEFAULT 512 COMMENT 'memory to use in mb',
  `active_session` int(10) NOT NULL DEFAULT 0 COMMENT 'active session number',
  `last_update` DATETIME NULL COMMENT 'Last session update time',
  `session_details` BLOB NULL COMMENT 'session detail info',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`secondary_storage_vm` (
  `id` bigint unsigned NOT NULL auto_increment,
  `gateway` varchar(15)  NOT NULL COMMENT 'gateway info for this sec storage vm towards public network interface',
  `dns1` varchar(15) COMMENT 'dns1',
  `dns2` varchar(15) COMMENT 'dns2',
  `domain` varchar(255) COMMENT 'domain',
  `public_mac_address` varchar(17) NOT NULL unique COMMENT 'mac address of the public facing network card',
  `public_ip_address` varchar(15) UNIQUE COMMENT 'public ip address for the sec storage vm',
  `public_netmask` varchar(15) NOT NULL COMMENT 'public netmask used for the sec storage vm',
  `vlan_db_id` bigint unsigned COMMENT 'Foreign key into vlan id table',
  `vlan_id` varchar(255) COMMENT 'optional VLAN ID for sec storage vm that can be used',
  `ram_size` int(10) unsigned NOT NULL DEFAULT 512 COMMENT 'memory to use in mb',
  `last_update` DATETIME NULL COMMENT 'Last session update time',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`domain` (
  `id` bigint unsigned NOT NULL auto_increment,
  `parent` bigint unsigned,
  `name` varchar(255),
  `owner` bigint unsigned NOT NULL,
  `path` varchar(255),
  `level` int(10) NOT NULL DEFAULT 0,
  `child_count` int(10) NOT NULL DEFAULT 0,
  `next_child_seq` bigint unsigned NOT NULL DEFAULT 1,
  `removed` datetime COMMENT 'date removed',
  PRIMARY KEY  (`id`),
  UNIQUE (parent, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`account` (
  `id` bigint unsigned NOT NULL auto_increment,
  `account_name` varchar(100) COMMENT 'an account name set by the creator of the account, defaults to username for single accounts',
  `type` int(1) unsigned NOT NULL,
  `domain_id` bigint unsigned,
  `disabled` int(1) unsigned NOT NULL default '0',
  `removed` datetime COMMENT 'date removed',
  `cleanup_needed` tinyint(1) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`resource_limit` (
  `id` bigint unsigned NOT NULL auto_increment,
  `domain_id` bigint unsigned,
  `account_id` bigint unsigned,
  `type` varchar(255),
  `max` bigint NOT NULL default '-1',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`resource_count` (
  `id` bigint unsigned NOT NULL auto_increment,
  `account_id` bigint unsigned NOT NULL,
  `type` varchar(255),
  `count` bigint NOT NULL default '0',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`op_host_capacity` (
  `id` bigint unsigned NOT NULL auto_increment,
  `host_id` bigint unsigned,
  `data_center_id` bigint unsigned NOT NULL,
  `pod_id` bigint unsigned,
  `used_capacity` bigint unsigned NOT NULL,
  `total_capacity` bigint unsigned NOT NULL,
  `capacity_type` int(1) unsigned NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`alert` (
  `id` bigint unsigned NOT NULL auto_increment,
  `type` int(1) unsigned NOT NULL,
  `pod_id` bigint unsigned,
  `data_center_id` bigint unsigned NOT NULL,
  `subject` varchar(999) COMMENT 'according to SMTP spec, max subject length is 1000 including the CRLF character, so allow enough space to fit long pod/zone/host names',
  `sent_count` int(3) unsigned NOT NULL,
  `created` DATETIME NULL COMMENT 'when this alert type was created',
  `last_sent` DATETIME NULL COMMENT 'Last time the alert was sent',
  `resolved` DATETIME NULL COMMENT 'when the alert status was resolved (available memory no longer at critical level, etc.)',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`scheduled_volume_backups` (
  `id` bigint unsigned NOT NULL auto_increment,
  `volume_id` bigint unsigned NOT NULL COMMENT 'volume id',
  `interval` int(4) NOT NULL COMMENT 'backup schedule, e.g. hourly, daily, etc.',
  `max_hourly` int(8) NOT NULL default '0' COMMENT 'maximum number of hourly backups to maintain',
  `max_daily` int(8) NOT NULL default '0' COMMENT 'maximum number of daily backups to maintain',
  `max_weekly` int(8) NOT NULL default '0' COMMENT 'maximum number of weekly backups to maintain',
  `max_monthly` int(8) NOT NULL default '0' COMMENT 'maximum number of monthly backups to maintain',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`async_job` (
  `id` bigint unsigned NOT NULL auto_increment,
  `user_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `session_key` varchar(64) COMMENT 'all async-job manage to apply session based security enforcement',
  `instance_type` varchar(64) COMMENT 'instance_type and instance_id work together to allow attaching an instance object to a job',			
  `instance_id` bigint unsigned, 
  `job_cmd` varchar(64) NOT NULL COMMENT 'command name',
  `job_cmd_originator` varchar(64) COMMENT 'command originator',
  `job_cmd_info` text COMMENT 'command parameter info',
  `job_cmd_ver` int(1) COMMENT 'command version',
  `callback_type` int(1) COMMENT 'call back type, 0 : polling, 1 : email',
  `callback_address` varchar(128) COMMENT 'call back address by callback_type',
  `job_status` int(1) COMMENT 'general job execution status',
  `job_process_status` int(1) COMMENT 'job specific process status for asynchronize progress update',
  `job_result_code` int(1) COMMENT 'job result code, specify error code corresponding to result message',
  `job_result` text COMMENT 'job result info',
  `job_init_msid` bigint COMMENT 'the initiating msid',
  `job_complete_msid` bigint  COMMENT 'completing msid',
  `created` datetime COMMENT 'date created',
  `last_updated` datetime COMMENT 'date created',
  `last_polled` datetime COMMENT 'date polled',
  `removed` datetime COMMENT 'date removed',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`sync_queue` (
  `id` bigint unsigned NOT NULL auto_increment,
  `sync_objtype` varchar(64) NOT NULL, 
  `sync_objid` bigint unsigned NOT NULL,
  `queue_proc_msid` bigint,
  `queue_proc_number` bigint COMMENT 'process number, increase 1 for each iteration',
  `queue_proc_time` datetime COMMENT 'last time to process the queue',
  `created` datetime COMMENT 'date created',
  `last_updated` datetime COMMENT 'date created',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`sync_queue_item` (
  `id` bigint unsigned NOT NULL auto_increment,
  `queue_id` bigint unsigned NOT NULL,
  `content_type` varchar(64),
  `content_id` bigint,
  `queue_proc_msid` bigint COMMENT 'owner msid when the queue item is being processed',
  `queue_proc_number` bigint COMMENT 'used to distinguish raw items and items being in process',
  `created` datetime COMMENT 'time created',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`vm_disk` (
  `id` bigint unsigned NOT NULL auto_increment,
  `instance_id` bigint unsigned NOT NULL,
  `disk_offering_id` bigint unsigned NOT NULL,
  `removed` datetime COMMENT 'date removed',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`disk_offering` (
  `id` bigint unsigned NOT NULL auto_increment,
  `domain_id` bigint unsigned NOT NULL,
  `name` varchar(255) NOT NULL,
  `display_text` varchar(4096) NULL COMMENT 'Description text set by the admin for display purpose only',
  `disk_size` bigint unsigned NOT NULL COMMENT 'disk space in mbs',
  `mirrored` tinyint(1) unsigned NOT NULL DEFAULT 1 COMMENT 'Enable mirroring?',
  `removed` datetime COMMENT 'date removed',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`security_group` (
  `id` bigint unsigned NOT NULL auto_increment,
  `name` varchar(255) NOT NULL,
  `description` varchar(4096) NULL,
  `domain_id` bigint unsigned NULL,
  `account_id` bigint unsigned NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`network_rule_config` (
  `id` bigint unsigned NOT NULL auto_increment,
  `security_group_id` bigint unsigned NOT NULL,
  `public_port` varchar(10) default NULL,
  `private_port` varchar(10) default NULL,
  `protocol` varchar(16) NOT NULL default 'TCP',
  `create_status` varchar(32) COMMENT 'rule creation status',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`security_group_vm_map` (
  `id` bigint unsigned NOT NULL auto_increment,
  `security_group_id` bigint unsigned NOT NULL,
  `ip_address` varchar(15) NOT NULL,
  `instance_id` bigint unsigned NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`load_balancer_vm_map` (
  `id` bigint unsigned NOT NULL auto_increment,
  `load_balancer_id` bigint unsigned NOT NULL,
  `instance_id` bigint unsigned NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`load_balancer` (
  `id` bigint unsigned NOT NULL auto_increment,
  `name` varchar(255) NOT NULL,
  `description` varchar(4096) NULL,
  `account_id` bigint unsigned NOT NULL,
  `ip_address` varchar(15) NOT NULL,
  `public_port` varchar(10) NOT NULL,
  `private_port` varchar(10) NOT NULL,
  `algorithm` varchar(255) NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`storage_pool` (
  `id` bigint unsigned UNIQUE NOT NULL,
  `name` varchar(255) COMMENT 'should be NOT NULL',
  `uuid` varchar(255) UNIQUE NOT NULL,
  `pool_type` varchar(32) NOT NULL,
  `port` int unsigned NOT NULL,
  `data_center_id` bigint unsigned NOT NULL,
  `pod_id` bigint unsigned,
  `available_bytes` bigint unsigned,
  `capacity_bytes` bigint unsigned,
  `host_address` varchar(255) NOT NULL COMMENT 'FQDN or IP of storage server',
  `path` varchar(255) NOT NULL COMMENT 'Filesystem path that is shared',
  `created` datetime COMMENT 'date the pool created',
  `removed` datetime COMMENT 'date removed if not null',
  `update_time` DATETIME,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`storage_pool_host_ref` (
  `id` bigint unsigned NOT NULL auto_increment,
  `host_id` bigint unsigned NOT NULL,
  `pool_id` bigint unsigned NOT NULL,
  `created` DATETIME NOT NULL,
  `last_updated` DATETIME,
  `local_path` varchar(255),
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`template_spool_ref` (
  `id` bigint unsigned NOT NULL auto_increment,
  `pool_id` bigint unsigned NOT NULL,
  `template_id` bigint unsigned NOT NULL,
  `created` DATETIME NOT NULL,
  `last_updated` DATETIME,
  `job_id` varchar(255),
  `download_pct` int(10) unsigned,
  `download_state` varchar(255),
  `error_str` varchar(255),
  `local_path` varchar(255),
  `install_path` varchar(255),
  `template_size` bigint unsigned NOT NULL COMMENT 'the size of the template on the pool',
  `marked_for_gc` tinyint(1) unsigned NOT NULL DEFAULT 0 COMMENT 'if true, the garbage collector will evict the template from this pool.',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`guest_os` (
  `id` bigint unsigned NOT NULL auto_increment,
  `category_id` bigint unsigned NOT NULL,
  `name` varchar(255) NOT NULL,
  `display_name` varchar(255) NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`guest_os_category` (
  `id` bigint unsigned NOT NULL auto_increment,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`launch_permission` (
  `id` bigint unsigned NOT NULL auto_increment,
  `template_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

CREATE TABLE `vmops`.`snapshot_policy` (
  `id` bigint unsigned NOT NULL auto_increment,
  `volume_id` bigint unsigned NOT NULL,
  `schedule` varchar(100) NOT NULL COMMENT 'schedule time of execution',
  `interval` int(4) NOT NULL COMMENT 'backup schedule, e.g. hourly, daily, etc.',
  `max_snaps` int(8) NOT NULL default '0' COMMENT 'maximum number of snapshots to maintain',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`snapshot_policy_ref` (
  `snap_id` bigint unsigned NOT NULL,
  `volume_id` bigint unsigned NOT NULL,
  `policy_id` bigint unsigned NOT NULL,
  UNIQUE (snap_id, policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE  `vmops`.`snapshot_schedule` (
  `id` bigint unsigned NOT NULL auto_increment,
  `volume_id` bigint unsigned NOT NULL COMMENT 'The volume for which this snapshot is being taken',
  `policy_id` bigint unsigned NOT NULL COMMENT 'One of the policyIds for which this snapshot was taken',
  `scheduled_timestamp` datetime NOT NULL COMMENT 'Time at which the snapshot was scheduled for execution',
  PRIMARY KEY  (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

SET foreign_key_checks = 1;
