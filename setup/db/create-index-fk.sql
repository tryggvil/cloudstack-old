ALTER TABLE `vmops`.`disk_template_ref` ADD INDEX `i_disk_template_ref__removed`(`removed`);
ALTER TABLE `vmops`.`disk_template_ref` ADD INDEX `i_disk_template_ref__type__size`(`type`, `size`);

ALTER TABLE `vmops`.`volumes` ADD INDEX `i_volumes__removed`(`removed`);
ALTER TABLE `vmops`.`volumes` ADD INDEX `i_volumes__pod_id`(`pod_id`);
ALTER TABLE `vmops`.`volumes` ADD INDEX `i_volumes__data_center_id`(`data_center_id`);

ALTER TABLE `vmops`.`volumes` ADD CONSTRAINT `fk_volumes__account_id` FOREIGN KEY `fk_volumes__account_id` (`account_id`) REFERENCES `account` (`id`);
ALTER TABLE `vmops`.`volumes` ADD INDEX `i_volumes__account_id`(`account_id`);

ALTER TABLE `vmops`.`volumes` ADD CONSTRAINT `fk_volumes__host_id` FOREIGN KEY `fk_volumes__host_id` (`host_id`) REFERENCES `host` (`id`);
ALTER TABLE `vmops`.`volumes` ADD INDEX `i_volumes__host_id`(`host_id`);

ALTER TABLE `vmops`.`volumes` ADD CONSTRAINT `fk_volumes__pool_id` FOREIGN KEY `fk_volumes__pool_id` (`pool_id`) REFERENCES `storage_pool` (`id`);
ALTER TABLE `vmops`.`volumes` ADD INDEX `i_volumes__pool_id`(`pool_id`);

ALTER TABLE `vmops`.`volumes` ADD CONSTRAINT `fk_volumes__instance_id` FOREIGN KEY `fk_volumes__instance_id` (`instance_id`) REFERENCES `vm_instance` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`volumes` ADD INDEX `i_volumes__instance_id`(`instance_id`);

ALTER TABLE `vmops`.`template_spool_ref` ADD UNIQUE `i_template_spool_ref__template_id__pool_id`(`template_id`, `pool_id`);

ALTER TABLE `vmops`.`op_ha_work` ADD CONSTRAINT `fk_op_ha_work__instance_id` FOREIGN KEY `fk_op_ha_work__instance_id` (`instance_id`) REFERENCES `vm_instance` (`id`);
ALTER TABLE `vmops`.`op_ha_work` ADD INDEX `i_op_ha_work__instance_id`(`instance_id`);

ALTER TABLE `vmops`.`op_ha_work` ADD CONSTRAINT `fk_op_ha_work__host_id` FOREIGN KEY `fk_op_ha_work__host_id` (`host_id`) REFERENCES `host` (`id`);
ALTER TABLE `vmops`.`op_ha_work` ADD INDEX `i_op_ha_work__host_id`(`host_id`);

ALTER TABLE `vmops`.`op_ha_work` ADD INDEX `i_op_ha_work__step`(`step`);
ALTER TABLE `vmops`.`op_ha_work` ADD INDEX `i_op_ha_work__type`(`type`);
ALTER TABLE `vmops`.`op_ha_work` ADD INDEX `i_op_ha_work__mgmt_server_id`(`mgmt_server_id`);

ALTER TABLE `vmops`.`op_dc_ip_address_alloc` ADD INDEX `i_op_dc_ip_address_alloc__pod_id__data_center_id__taken` (`pod_id`, `data_center_id`, `taken`, `instance_id`);
ALTER TABLE `vmops`.`op_dc_ip_address_alloc` ADD UNIQUE `i_op_dc_ip_address_alloc__ip_address__data_center_id`(`ip_address`, `data_center_id`);
ALTER TABLE `vmops`.`op_dc_ip_address_alloc` ADD CONSTRAINT `fk_op_dc_ip_address_alloc__pod_id` FOREIGN KEY `fk_op_dc_ip_address_alloc__pod_id` (`pod_id`) REFERENCES `host_pod_ref` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`op_dc_ip_address_alloc` ADD INDEX `i_op_dc_ip_address_alloc__pod_id`(`pod_id`);

ALTER TABLE `vmops`.`host_pod_ref` ADD INDEX `i_host_pod_ref__data_center_id`(`data_center_id`);

ALTER TABLE `vmops`.`op_dc_vnet_alloc` ADD UNIQUE `i_op_dc_vnet_alloc__vnet__data_center_id__account_id`(`vnet`, `data_center_id`, `account_id`);
ALTER TABLE `vmops`.`op_dc_vnet_alloc` ADD INDEX `i_op_dc_vnet_alloc__dc_taken`(`data_center_id`, `taken`);
ALTER TABLE `vmops`.`op_dc_vnet_alloc` ADD UNIQUE `i_op_dc_vnet_alloc__vnet__data_center_id`(`vnet`, `data_center_id`);

ALTER TABLE `vmops`.`host` ADD INDEX `i_host__removed`(`removed`);
ALTER TABLE `vmops`.`host` ADD INDEX `i_host__last_ping`(`last_ping`);
ALTER TABLE `vmops`.`host` ADD INDEX `i_host__status`(`status`);
ALTER TABLE `vmops`.`host` ADD INDEX `i_host__data_center_id`(`data_center_id`);
ALTER TABLE `vmops`.`host` ADD CONSTRAINT `fk_host__pod_id` FOREIGN KEY `fk_host__pod_id` (`pod_id`) REFERENCES `host_pod_ref` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`host` ADD INDEX `i_host__pod_id`(`pod_id`);

ALTER TABLE `vmops`.`storage_pool` ADD CONSTRAINT `fk_storage_pool__pod_id` FOREIGN KEY `fk_storage_pool__pod_id` (`pod_id`) REFERENCES `host_pod_ref` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`storage_pool` ADD INDEX `i_storage_pool__pod_id`(`pod_id`);

ALTER TABLE `vmops`.`op_vm_host` ADD CONSTRAINT `fk_op_vm_host__id` FOREIGN KEY `fk_op_vm_host__id` (`id`) REFERENCES `host` (`id`) ON DELETE CASCADE;

ALTER TABLE `vmops`.`user` ADD INDEX `i_user__secret_key_removed`(`secret_key`, `removed`);
ALTER TABLE `vmops`.`user` ADD INDEX `i_user__removed`(`removed`);
ALTER TABLE `vmops`.`user` ADD UNIQUE `i_user__username__removed`(`username`, `removed`);
ALTER TABLE `vmops`.`user` ADD UNIQUE `i_user__api_key`(`api_key`);
ALTER TABLE `vmops`.`user` ADD CONSTRAINT `fk_user__account_id` FOREIGN KEY `fk_user__account_id` (`account_id`) REFERENCES `account` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`user` ADD INDEX `i_user__account_id`(`account_id`);

ALTER TABLE `vmops`.`account` ADD CONSTRAINT `fk_account__domain_id` FOREIGN KEY `fk_account__domain_id` (`domain_id`) REFERENCES `domain` (`id`);
ALTER TABLE `vmops`.`account` ADD INDEX `i_account__domain_id`(`domain_id`);

ALTER TABLE `vmops`.`account` ADD INDEX `i_account__cleanup_needed`(`cleanup_needed`);
ALTER TABLE `vmops`.`account` ADD INDEX `i_account__removed`(`removed`);
ALTER TABLE `vmops`.`account` ADD INDEX `i_account__account_name__domain_id__removed`(`account_name`, `domain_id`, `removed`); 

ALTER TABLE `vmops`.`resource_limit` ADD CONSTRAINT `fk_resource_limit__domain_id` FOREIGN KEY `fk_resource_limit__domain_id` (`domain_id`) REFERENCES `domain` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`resource_limit` ADD INDEX `i_resource_limit__domain_id`(`domain_id`);
ALTER TABLE `vmops`.`resource_limit` ADD CONSTRAINT `fk_resource_limit__account_id` FOREIGN KEY `fk_resource_limit__account_id` (`account_id`) REFERENCES `account` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`resource_limit` ADD INDEX `i_resource_limit__account_id`(`account_id`);

ALTER TABLE `vmops`.`event` ADD INDEX `i_event__created`(`created`);
ALTER TABLE `vmops`.`event` ADD INDEX `i_event__user_id`(`user_id`);
ALTER TABLE `vmops`.`event` ADD INDEX `i_event__account_id` (`account_id`);
ALTER TABLE `vmops`.`event` ADD INDEX `i_event__level_id`(`level`);
ALTER TABLE `vmops`.`event` ADD INDEX `i_event__type_id`(`type`);

ALTER TABLE `vmops`.`user_ip_address` ADD CONSTRAINT `fk_user_ip_address__account_id` FOREIGN KEY `fk_user_ip_address__account_id` (`account_id`) REFERENCES `account` (`id`);
ALTER TABLE `vmops`.`user_ip_address` ADD INDEX `i_user_ip_address__account_id`(`account_id`);
ALTER TABLE `vmops`.`user_ip_address` ADD CONSTRAINT `fk_user_ip_address__vlan_db_id` FOREIGN KEY `fk_user_ip_address__vlan_db_id` (`vlan_db_id`) REFERENCES `vlan` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`user_ip_address` ADD INDEX `i_user_ip_address__vlan_db_id`(`vlan_db_id`);

ALTER TABLE `vmops`.`user_ip_address` ADD INDEX `i_user_ip_address__data_center_id`(`data_center_id`);
ALTER TABLE `vmops`.`user_ip_address` ADD INDEX `i_user_ip_address__source_nat`(`source_nat`);
ALTER TABLE `vmops`.`user_ip_address` ADD INDEX `i_user_ip_address__allocated`(`allocated`);
ALTER TABLE `vmops`.`user_ip_address` ADD INDEX `i_user_ip_address__public_ip_address`(`public_ip_address`);

ALTER TABLE `vmops`.`vm_template` ADD INDEX `i_vm_template__removed`(`removed`);
ALTER TABLE `vmops`.`vm_template` ADD INDEX `i_vm_template__public`(`public`);
ALTER TABLE `vmops`.`vm_template` ADD INDEX `i_vm_template__ready`(`ready`);

ALTER TABLE `vmops`.`vm_instance` ADD INDEX `i_vm_instance__removed`(`removed`);
ALTER TABLE `vmops`.`vm_instance` ADD INDEX `i_vm_instance__type`(`type`);
ALTER TABLE `vmops`.`vm_instance` ADD INDEX `i_vm_instance__pod_id`(`pod_id`);
ALTER TABLE `vmops`.`vm_instance` ADD INDEX `i_vm_instance__update_time`(`update_time`);
ALTER TABLE `vmops`.`vm_instance` ADD INDEX `i_vm_instance__update_count`(`update_count`);
ALTER TABLE `vmops`.`vm_instance` ADD INDEX `i_vm_instance__state`(`state`);
ALTER TABLE `vmops`.`vm_instance` ADD INDEX `i_vm_instance__data_center_id`(`data_center_id`);
ALTER TABLE `vmops`.`vm_instance` ADD CONSTRAINT `fk_vm_instance__host_id` FOREIGN KEY `fk_vm_instance__host_id` (`host_id`) REFERENCES `host` (`id`);
ALTER TABLE `vmops`.`vm_instance` ADD INDEX `i_vm_instance__host_id`(`host_id`);

ALTER TABLE `vmops`.`vm_instance` ADD CONSTRAINT `fk_vm_instance__template_id` FOREIGN KEY `fk_vm_instance__template_id` (`vm_template_id`) REFERENCES `vm_template` (`id`);
ALTER TABLE `vmops`.`vm_instance` ADD INDEX `i_vm_instance__template_id`(`vm_template_id`);

ALTER TABLE `vmops`.`user_vm` ADD CONSTRAINT `fk_user_vm__domain_router_id` FOREIGN KEY `fk_user_vm__domain_router_id` (`domain_router_id`) REFERENCES `domain_router` (`id`);
ALTER TABLE `vmops`.`user_vm` ADD INDEX `i_user_vm__domain_router_id`(`domain_router_id`);
ALTER TABLE `vmops`.`user_vm` ADD CONSTRAINT `fk_user_vm__service_offering_id` FOREIGN KEY `fk_user_vm__service_offering_id` (`service_offering_id`) REFERENCES `service_offering` (`id`);
ALTER TABLE `vmops`.`user_vm` ADD INDEX `i_user_vm__service_offering_id`(`service_offering_id`);
ALTER TABLE `vmops`.`user_vm` ADD CONSTRAINT `fk_user_vm__id` FOREIGN KEY `fk_user_vm__id` (`id`) REFERENCES `vm_instance`(`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`user_vm` ADD CONSTRAINT `fk_user_vm__account_id` FOREIGN KEY `fk_user_vm__account_id` (`account_id`) REFERENCES `account` (`id`);
ALTER TABLE `vmops`.`user_vm` ADD INDEX `i_user_vm__account_id`(`account_id`);
ALTER TABLE `vmops`.`user_vm` ADD CONSTRAINT `fk_user_vm__external_ip_address` FOREIGN KEY `fk_user_vm__external_ip_address` (`external_ip_address`) REFERENCES `user_ip_address` (`public_ip_address`);
ALTER TABLE `vmops`.`user_vm` ADD INDEX `i_user_vm__external_ip_address`(`external_ip_address`);
ALTER TABLE `vmops`.`user_vm` ADD CONSTRAINT `fk_user_vm__external_vlan_db_id` FOREIGN KEY `fk_user_vm__external_vlan_db_id` (`external_vlan_db_id`) REFERENCES `vlan` (`id`);
ALTER TABLE `vmops`.`user_vm` ADD INDEX `i_user_vm__external_vlan_db_id`(`external_vlan_db_id`);

ALTER TABLE `vmops`.`domain_router` ADD CONSTRAINT `fk_domain_router__public_ip_address` FOREIGN KEY `fk_domain_router__public_ip_address` (`public_ip_address`) REFERENCES `user_ip_address` (`public_ip_address`);
ALTER TABLE `vmops`.`domain_router` ADD INDEX `i_domain_router__public_ip_address`(`public_ip_address`);
ALTER TABLE `vmops`.`domain_router` ADD CONSTRAINT `fk_domain_router__id` FOREIGN KEY `fk_domain_router__id` (`id`) REFERENCES `vm_instance`(`id`) ON DELETE CASCADE;

ALTER TABLE `vmops`.`domain_router` ADD CONSTRAINT `fk_domain_router__account_id` FOREIGN KEY `fk_domain_router__account_id` (`account_id`) REFERENCES `account` (`id`);
ALTER TABLE `vmops`.`domain_router` ADD INDEX `i_domain_router__account_id`(`account_id`);

ALTER TABLE `vmops`.`domain_router` ADD CONSTRAINT `fk_domain_router__vlan_id` FOREIGN KEY `fk_domain_router__vlan_id` (`vlan_db_id`) REFERENCES `vlan` (`id`);
ALTER TABLE `vmops`.`domain_router` ADD INDEX `i_domain_router__vlan_id`(`vlan_db_id`);

ALTER TABLE `vmops`.`console_proxy` ADD CONSTRAINT `fk_console_proxy__vlan_id` FOREIGN KEY `fk_console_proxy__vlan_id` (`vlan_db_id`) REFERENCES `vlan` (`id`);
ALTER TABLE `vmops`.`console_proxy` ADD INDEX `i_console_proxy__vlan_id`(`vlan_db_id`);

ALTER TABLE `vmops`.`op_host_capacity` ADD INDEX `i_op_host_capacity__host_type`(`host_id`, `capacity_type`);
ALTER TABLE `vmops`.`op_host_capacity` ADD CONSTRAINT `fk_op_host_capacity__pod_id` FOREIGN KEY `fk_op_host_capacity__pod_id` (`pod_id`) REFERENCES `host_pod_ref` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`op_host_capacity` ADD INDEX `i_op_host_capacity__pod_id`(`pod_id`);
ALTER TABLE `vmops`.`op_host_capacity` ADD CONSTRAINT `fk_op_host_capacity__data_center_id` FOREIGN KEY `fk_op_host_capacity__data_center_id` (`data_center_id`) REFERENCES `data_center` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`op_host_capacity` ADD INDEX `i_op_host_capacity__data_center_id`(`data_center_id`);

ALTER TABLE `vmops`.`template_host_ref` ADD CONSTRAINT `fk_template_host_ref__host_id` FOREIGN KEY `fk_template_host_ref__host_id` (`host_id`) REFERENCES `host` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`template_host_ref` ADD INDEX `i_template_host_ref__host_id`(`host_id`);
ALTER TABLE `vmops`.`template_host_ref` ADD CONSTRAINT `fk_template_host_ref__template_id` FOREIGN KEY `fk_template_host_ref__template_id` (`template_id`) REFERENCES `vm_template` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`template_host_ref` ADD INDEX `i_template_host_ref__template_id`(`template_id`);

ALTER TABLE `vmops`.`pod_vlan_map` ADD CONSTRAINT `fk_pod_vlan_map__pod_id` FOREIGN KEY `fk_pod_vlan_map__pod_id` (`pod_id`) REFERENCES `host_pod_ref` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`pod_vlan_map` ADD INDEX `i_pod_vlan_map__pod_id`(`pod_id`);

ALTER TABLE `vmops`.`pod_vlan_map` ADD CONSTRAINT `fk_pod_vlan_map__vlan_id` FOREIGN KEY `fk_pod_vlan_map__vlan_id` (`vlan_db_id`) REFERENCES `vlan` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`pod_vlan_map` ADD INDEX `i_pod_vlan_map__vlan_id`(`vlan_db_id`);

ALTER TABLE `vmops`.`ip_forwarding` ADD INDEX `i_ip_forwarding__forwarding`(`forwarding`);
ALTER TABLE `vmops`.`ip_forwarding` ADD INDEX `i_ip_forwarding__public_ip_address__public_port`(`public_ip_address`, `public_port`);
ALTER TABLE `vmops`.`ip_forwarding` ADD CONSTRAINT `fk_ip_forwarding__public_ip_address` FOREIGN KEY `fk_ip_forwarding__public_ip_address` (`public_ip_address`) REFERENCES `user_ip_address` (`public_ip_address`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`ip_forwarding` ADD INDEX `i_ip_forwarding__public_ip_address`(`public_ip_address`);

ALTER TABLE `vmops`.`op_host_queue` ADD INDEX `i_op_host_queue__host_id__sequence__active`(`host_id`, `sequence`, `active`);

ALTER TABLE `vmops`.`user_statistics` ADD CONSTRAINT `fk_user_statistics__account_id` FOREIGN KEY `fk_user_statistics__account_id` (`account_id`) REFERENCES `account` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`user_statistics` ADD INDEX `i_user_statistics__account_id`(`account_id`);

ALTER TABLE `vmops`.`user_statistics` ADD INDEX `i_user_statistics__account_id_data_center_id`(`account_id`, `data_center_id`);

ALTER TABLE `vmops`.`snapshots` ADD INDEX `i_snapshots__volume_id`(`volume_id`);
ALTER TABLE `vmops`.`snapshots` ADD INDEX `i_snapshots__removed`(`removed`);
ALTER TABLE `vmops`.`snapshots` ADD INDEX `i_snapshots__snapshot_type`(`snapshot_type`);

ALTER TABLE `vmops`.`async_job` ADD INDEX `i_async__user_id`(`user_id`);
ALTER TABLE `vmops`.`async_job` ADD INDEX `i_async__account_id`(`account_id`);
ALTER TABLE `vmops`.`async_job` ADD INDEX `i_async__instance_type_id`(`instance_type`,`instance_id`);
ALTER TABLE `vmops`.`async_job` ADD INDEX `i_async__job_cmd`(`job_cmd`);
ALTER TABLE `vmops`.`async_job` ADD INDEX `i_async__created`(`created`);
ALTER TABLE `vmops`.`async_job` ADD INDEX `i_async__last_updated`(`last_updated`);
ALTER TABLE `vmops`.`async_job` ADD INDEX `i_async__last_poll`(`last_polled`);
ALTER TABLE `vmops`.`async_job` ADD INDEX `i_async__removed`(`removed`);

ALTER TABLE `vmops`.`sync_queue` ADD UNIQUE `i_sync_queue__objtype__objid`(`sync_objtype`, `sync_objid`);
ALTER TABLE `vmops`.`sync_queue` ADD INDEX `i_sync_queue__created`(`created`);
ALTER TABLE `vmops`.`sync_queue` ADD INDEX `i_sync_queue__last_updated`(`last_updated`);
ALTER TABLE `vmops`.`sync_queue` ADD INDEX `i_sync_queue__queue_proc_time`(`queue_proc_time`);

ALTER TABLE `vmops`.`sync_queue_item` ADD CONSTRAINT `fk_sync_queue_item__queue_id` FOREIGN KEY `fk_sync_queue_item__queue_id` (`queue_id`) REFERENCES `sync_queue` (`id`) ON DELETE CASCADE;
ALTER TABLE `vmops`.`sync_queue_item` ADD INDEX `i_sync_queue_item__queue_id`(`queue_id`);
ALTER TABLE `vmops`.`sync_queue_item` ADD INDEX `i_sync_queue_item__created`(`created`);

ALTER TABLE `vmops`.`launch_permission` ADD INDEX `i_launch_permission_template_id`(`template_id`);

ALTER TABLE `vmops`.`guest_os` ADD CONSTRAINT `fk_guest_os__category_id` FOREIGN KEY `fk_guest_os__category_id` (`category_id`) REFERENCES `guest_os_category` (`id`) ON DELETE CASCADE;

