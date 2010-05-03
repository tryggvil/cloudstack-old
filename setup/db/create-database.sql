SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ANSI';

USE `mysql`;

DROP PROCEDURE IF EXISTS `mysql`.`vmops_drop_user_if_exists` ;
DELIMITER $$
CREATE PROCEDURE `mysql`.`vmops_drop_user_if_exists`()
BEGIN
  DECLARE foo BIGINT DEFAULT 0 ;
  SELECT COUNT(*)
  INTO foo
    FROM `mysql`.`user`
      WHERE `User` = 'vmops' and host = 'localhost';
  
  IF foo > 0 THEN 
         DROP USER 'vmops'@'localhost' ;
  END IF;
  
  SELECT COUNT(*)
  INTO foo
    FROM `mysql`.`user`
      WHERE `User` = 'vmops' and host = '%';
  
  IF foo > 0 THEN 
         DROP USER 'vmops'@'%' ;
  END IF;
END ;$$
DELIMITER ;

CALL `mysql`.`vmops_drop_user_if_exists`() ;

DROP PROCEDURE IF EXISTS `mysql`.`vmops_drop_users_if_exists` ;

SET SQL_MODE=@OLD_SQL_MODE ;

DROP DATABASE IF EXISTS `billing`;
DROP DATABASE IF EXISTS `vmops_usage`;
DROP DATABASE IF EXISTS `vmops`;

CREATE DATABASE `vmops`;
CREATE DATABASE `vmops_usage`;

CREATE USER vmops identified by 'vmops';

GRANT ALL ON vmops.* to vmops@`localhost` identified by 'vmops';
GRANT ALL ON vmops.* to vmops@`%` identified by 'vmops';

GRANT ALL ON vmops_usage.* to vmops@`localhost`;
GRANT ALL ON vmops_usage.* to vmops@`%`;

GRANT process ON *.* TO vmops@`localhost`;
GRANT process ON *.* TO vmops@`%`;


commit;
