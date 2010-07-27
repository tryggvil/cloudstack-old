use cloud;

--
-- We don't want to mess up with customer's data, all data updates are wrapped in a big transactions
-- hopefully we don't have a huge data set to deal with
--
START TRANSACTION;

UPDATE service_offering SET guest_ip_type='VirtualNetwork';
UPDATE vlan SET vlan_type='VirtualNetwork';

INSERT INTO configuration (`category`, `instance`, `component`, `name`, `value`, `description`) VALUES ('Advanced', 'DEFAULT', 'management-server', 'linkLocalIp.nums', '10', 'The number of link local ip that needed by domR(in power of 2)'); 
UPDATE host SET resource='com.cloud.hypervisor.xen.resource.XenServer56Resource' WHERE resource='com.cloud.resource.xen.XenServer56Resource'; 

COMMIT;
