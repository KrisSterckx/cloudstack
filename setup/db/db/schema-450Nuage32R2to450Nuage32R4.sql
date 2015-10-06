DROP TABLE IF EXISTS `cloud`.`external_nuage_vsp_devices_details`;
CREATE TABLE `cloud`.`external_nuage_vsp_devices_details` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `nuage_vsp_device_id` bigint unsigned NOT NULL COMMENT 'id of the nuage vsp device this detail belongs to',
  `name` varchar(255) NOT NULL COMMENT 'the name of the detail',
  `value` varchar(2048) NOT NULL COMMENT 'the value of the detail',
  `display` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'True if the detail can be displayed to the end user',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_external_nuage_vsp_devices_details__nuage_vsp_device_id` FOREIGN KEY (`nuage_vsp_device_id`) REFERENCES `external_nuage_vsp_devices`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;