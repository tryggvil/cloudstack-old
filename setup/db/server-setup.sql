/* This file specifies default values that go into the database, before the Management Server is run. */

/* Root Domain */
INSERT INTO `vmops`.`domain` (id, name, parent, path, owner) VALUES (1, 'ROOT', NULL, '', 2);

/* Configuration Table */

INSERT INTO `vmops`.`configuration` (category, instance, component, name, value, description) VALUES ('Hidden', 'DEFAULT', 'none', 'init', 'false', null);
-- INSERT INTO `vmops`.`configuration` (category, instance, component, name, value, description) VALUES ('Advanced', 'DEFAULT', 'AgentManager', 'xen.public.network.device', 'public-network', "[OPTIONAL]The name of the Xen network containing the physical network interface that is connected to the public network ");


