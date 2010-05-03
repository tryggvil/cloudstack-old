/**
 *  Copyright (C) 2010 VMOps, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.  
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.vmops.api;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.server.ManagementServer;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public abstract class BaseCmd {
    private static final Logger s_logger = Logger.getLogger(BaseCmd.class.getName());
    public static final int PROGRESS_INSTANCE_CREATED = 1;
    
    public static final String RESPONSE_TYPE_XML = "xml";
    public static final String RESPONSE_TYPE_JSON = "json";
    
    private Map<String, String> _params;
    private ManagementServer _ms = null;

    public static final short TYPE_STRING = 0;
    public static final short TYPE_INT = 1;
    public static final short TYPE_LONG = 2;
    public static final short TYPE_DATE = 3;
    public static final short TYPE_FLOAT = 4;
    public static final short TYPE_BOOLEAN = 5;
    public static final short TYPE_OBJECT = 6;

    // Client error codes
    public static final int MALFORMED_PARAMETER_ERROR = 430;
    public static final int VM_INVALID_PARAM_ERROR = 431;
    public static final int NET_INVALID_PARAM_ERROR = 432;
    public static final int VM_ALLOCATION_ERROR = 433;
    public static final int IP_ALLOCATION_ERROR = 434;
    public static final int SNAPSHOT_INVALID_PARAM_ERROR = 435;
    public static final int PARAM_ERROR = 436;

    // Server error codes
    public static final int INTERNAL_ERROR = 530;
    public static final int ACCOUNT_ERROR = 531;
    public static final int UNSUPPORTED_ACTION_ERROR = 532;

    public static final int VM_DEPLOY_ERROR = 540;
    public static final int VM_DESTROY_ERROR = 541;
    public static final int VM_REBOOT_ERROR = 542;
    public static final int VM_START_ERROR = 543;
    public static final int VM_STOP_ERROR = 544;
    public static final int VM_RESET_PASSWORD_ERROR = 545;
    public static final int VM_CHANGE_SERVICE_ERROR = 546;
    public static final int VM_LIST_ERROR = 547;
    public static final int VM_RECOVER_ERROR = 548;
    public static final int SNAPSHOT_LIST_ERROR = 549;
    public static final int SNAPSHOT_ROLLBACK_ERROR = 550;
    public static final int VM_INSUFFICIENT_CAPACITY = 551;
    public static final int CREATE_PRIVATE_TEMPLATE_ERROR = 552;

    public static final int NET_IP_ASSOC_ERROR = 560;
    public static final int NET_IP_DIASSOC_ERROR = 561;
    public static final int NET_CREATE_IPFW_RULE_ERROR = 562;
    public static final int NET_DELETE_IPFW_RULE_ERROR = 563;
    public static final int NET_CONFLICT_IPFW_RULE_ERROR = 564;
    public static final int NET_CREATE_LB_RULE_ERROR = 566;
    public static final int NET_DELETE_LB_RULE_ERROR = 567;
    public static final int NET_CONFLICT_LB_RULE_ERROR = 568;
    public static final int NET_LIST_ERROR = 570;
    
    public static final int STORAGE_RESOURCE_IN_USE = 580;


    private static final DateFormat _format = new SimpleDateFormat("yyyy-MM-dd");
    private static final DateFormat _outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    public enum Properties {
        ACCOUNT("account", BaseCmd.TYPE_STRING, "account"),
        ACCOUNT_ID("accountid", BaseCmd.TYPE_LONG, "accountId"),
        ACCOUNT_NAMES("accounts", BaseCmd.TYPE_STRING, "accounts"),
        ACCOUNT_TYPE("accounttype", BaseCmd.TYPE_LONG, "accounttype"),
        ACCOUNT_OBJ("accountobj", BaseCmd.TYPE_OBJECT, "accountobj"),
        ADD("add", BaseCmd.TYPE_BOOLEAN, "add"),
        ALGORITHM("algorithm", BaseCmd.TYPE_STRING, "algorithm"),
        ALLOCATED("allocated", BaseCmd.TYPE_DATE, "allocated"),
        ALLOCATED_ONLY("allocatedonly", BaseCmd.TYPE_BOOLEAN, "allocatedOnly"),
        API_KEY("apikey", BaseCmd.TYPE_STRING, "apiKey"),
        APPLIED("applied", BaseCmd.TYPE_BOOLEAN, "applied"),
        ASSIGN_DATE("assigneddate", BaseCmd.TYPE_DATE, "assigneddate"),
        BOOTABLE("bootable", BaseCmd.TYPE_BOOLEAN, "bootable"),
        BITS("bits", BaseCmd.TYPE_INT, "bits"),
        BYTES_RECEIVED("receivedbytes", BaseCmd.TYPE_LONG, "receivedbytes"),
        BYTES_SENT("sentbytes", BaseCmd.TYPE_LONG, "sentbytes"),
        CAPABILITIES("capabilities", BaseCmd.TYPE_STRING, "capabilities"),
        CAPACITY_TOTAL("capacitytotal", BaseCmd.TYPE_LONG, "capacitytotal"),
        CAPACITY_USED("capacityused", BaseCmd.TYPE_LONG, "capacityused"),
        CATEGORY("category", BaseCmd.TYPE_STRING, "category"),
        CONSOLE_HOST("consolehost", BaseCmd.TYPE_STRING, "consoleHost"),
        CONSOLE_IMAGE_URL("consoleimageurl", BaseCmd.TYPE_STRING, "consoleImageUrl"),
        CONSOLE_PASSWORD("consolepassword", BaseCmd.TYPE_STRING, "consolePassword"),
        CONSOLE_PORT("consoleport", BaseCmd.TYPE_STRING, "consolePort"),
        CONSOLE_PROXY_HOST("consoleproxyhost", BaseCmd.TYPE_STRING, "consoleProxyHost"),
        CONSOLE_PROXY_PORT("consoleproxyport", BaseCmd.TYPE_STRING, "consoleProxyPort"),
        CONSOLE_PROXY_SSLENABLED("consoleproxysslenabled", BaseCmd.TYPE_BOOLEAN, "consoleProxySslEnabled"),
        CIDR("cidr", BaseCmd.TYPE_STRING, "cidr"),
        CIDR_ADDRESS("cidraddress", BaseCmd.TYPE_STRING, "CidrAddress"),
        CIDR_SIZE("cidrsize", BaseCmd.TYPE_LONG, "CidrSize"),
        CPU_NUMBER("cpunumber", BaseCmd.TYPE_LONG, "CpuNumber"),
        CPU_SPEED("cpuspeed", BaseCmd.TYPE_LONG, "CpuSpeed"),
        CPU_ALLOCATED("cpuallocated", BaseCmd.TYPE_LONG, "cpuallocated"),
        CPU_USED("cpuused", BaseCmd.TYPE_LONG, "cpuused"),
        CREATED("created", BaseCmd.TYPE_DATE, "created"),
        DAILY_MAX("dailymax", BaseCmd.TYPE_INT, "dailyMax"),
        DESCRIPTION("description", BaseCmd.TYPE_STRING, "description"),
        DEVICE_NAME("devicename", BaseCmd.TYPE_STRING, "deviceName"),
        DISCONNECTED("disconnected", BaseCmd.TYPE_DATE, "disconnected"),
        DATA_DISK_OFFERING_ID("datadiskofferingid", BaseCmd.TYPE_LONG, "dataDiskOfferingId"),
        DATA_DISK_OFFERING_NAME("datadiskofferingname", BaseCmd.TYPE_LONG, "dataDiskOfferingName"),
        DISK_OFFERING_ID("diskofferingid", BaseCmd.TYPE_LONG, "diskOfferingId"),
        DISK_SIZE("disksize", BaseCmd.TYPE_LONG, "diskSize"),
        DISK_SIZE_ALLOCATED("disksizeallocated", BaseCmd.TYPE_LONG, "disksizeallocated"),
        DISK_SIZE_TOTAL("disksizetotal", BaseCmd.TYPE_LONG, "disksizetotal"),
        DISPLAY_NAME("displayname", BaseCmd.TYPE_STRING, "displayname"),
        DNS1("dns1", BaseCmd.TYPE_STRING, "dns1"),
        DNS2("dns2", BaseCmd.TYPE_STRING, "dns2"),
        DNS3("dns3", BaseCmd.TYPE_STRING, "dns3"),
        DNS4("dns4", BaseCmd.TYPE_STRING, "dns4"),
        DOMAIN("domain", BaseCmd.TYPE_STRING, "domain"),
        DOMAIN_ID("domainid", BaseCmd.TYPE_LONG, "domainId"),
        DOMAIN_LEVEL("level", BaseCmd.TYPE_INT, "level"),
        END_DATE("enddate", BaseCmd.TYPE_DATE, "endDate"),
        EMAIL("email", BaseCmd.TYPE_STRING, "email"),
        FIREWALL_ENABLE_PASSWORD("firewallenablepassword", BaseCmd.TYPE_STRING, "firewallEnablePassword"),
        FIREWALL_IP("firewallip", BaseCmd.TYPE_STRING, "firewallIp"),
        FIREWALL_PASSWORD("firewallpassword", BaseCmd.TYPE_STRING, "firewallPassword"),
        FIREWALL_RULE_ID("firewallruleid", BaseCmd.TYPE_LONG, "firewallRuleId"),
        FIREWALL_USER("firewalluser", BaseCmd.TYPE_STRING, "firewallUser"),
        FIRSTNAME("firstname", BaseCmd.TYPE_STRING, "firstname"),
        FORMAT("format", BaseCmd.TYPE_STRING, "format"),
        GATEWAY("gateway", BaseCmd.TYPE_STRING, "gateway"),
        GROUP("group", BaseCmd.TYPE_STRING, "group"),
        GROUP_ID("groupid", BaseCmd.TYPE_LONG, "groupId"),
        GROUP_IDS("groupids", BaseCmd.TYPE_STRING, "groupIds"),
        OS_TYPE_ID("ostypeid", BaseCmd.TYPE_LONG, "osTypeId"),
        OS_TYPE_NAME("ostypename", BaseCmd.TYPE_STRING, "osTypeName"),
        OS_CATEGORY_ID("oscategoryid", BaseCmd.TYPE_LONG, "osCategoryId"),
        OS_CATEGORY_NAME("oscategoryname", BaseCmd.TYPE_STRING, "osCategoryName"),
        HOST_ID("hostid", BaseCmd.TYPE_LONG, "hostId"),
        HOST_IDS("hostids", BaseCmd.TYPE_STRING, "hostIds"),
        HOST_NAME("hostname", BaseCmd.TYPE_STRING, "hostname"),
        HOURLY_MAX("hourlymax", BaseCmd.TYPE_INT, "hourlyMax"),
        HYPERVISOR("hypervisor", BaseCmd.TYPE_STRING, "hypervisor"),
        ID("id", BaseCmd.TYPE_LONG, "id"),
        INTERVAL("interval", BaseCmd.TYPE_INT, "interval"),
        INTERVAL_TYPE("intervaltype", BaseCmd.TYPE_STRING, "intervalType"),
        IP_ADDRESS("ipaddress", BaseCmd.TYPE_STRING, "ipAddress"),
        IP_AVAIL("ipavailable", BaseCmd.TYPE_INT, "ipavailable"),
        IP_LIMIT("iplimit", BaseCmd.TYPE_INT, "iplimit"),
        IP_TOTAL("iptotal", BaseCmd.TYPE_INT, "iptotal"),
        IS_RECURSIVE("isrecursive", BaseCmd.TYPE_BOOLEAN, "isrecursive"),
        IS_PUBLIC("ispublic", BaseCmd.TYPE_BOOLEAN, "isPublic"),
        IS_DISABLED("isdisabled", BaseCmd.TYPE_BOOLEAN, "isdisabled"),
        IS_CLEANUP_REQUIRED("iscleanuprequired", BaseCmd.TYPE_BOOLEAN, "iscleanuprequired"),
        IS_ENABLED("isenabled", BaseCmd.TYPE_BOOLEAN, "isEnabled"),
        IS_MIRRORED("ismirrored", BaseCmd.TYPE_BOOLEAN, "isMirrored"),
        ISO_ID("isoid", BaseCmd.TYPE_LONG, "isoId"),
        ISO_NAME("isoname", BaseCmd.TYPE_STRING, "isoName"),
        ISO_PATH("isopath", BaseCmd.TYPE_STRING, "isoPath"),
        IS_READY("isready", BaseCmd.TYPE_BOOLEAN, "isReady"),
        IS_SOURCE_NAT("issourcenat", BaseCmd.TYPE_BOOLEAN, "isSourceNat"),
        KEYWORD("keyword", BaseCmd.TYPE_STRING, "keyword"),
        LASTNAME("lastname", BaseCmd.TYPE_STRING, "lastname"),
        LASTPINGED("lastpinged", BaseCmd.TYPE_DATE, "lastpinged"),
        LEVEL("level", BaseCmd.TYPE_STRING, "level"),
        HAS_CHILD("haschild", BaseCmd.TYPE_BOOLEAN, "haschild"),
        MAC_ADDRESS("macaddress", BaseCmd.TYPE_STRING, "macaddress"),
        MAX("max", BaseCmd.TYPE_LONG, "max"),
        MAX_SNAPS("maxsnaps", BaseCmd.TYPE_INT, "maxSnaps"),
        M_SERVER_ID("managementserverid", BaseCmd.TYPE_LONG, "managementserverid"),
        MEMORY("memory", BaseCmd.TYPE_LONG, "memory"),
        MEMORY_TOTAL("memorytotal", BaseCmd.TYPE_LONG, "memorytotal"),
        MEMORY_USED("memoryused", BaseCmd.TYPE_LONG, "memoryused"),
        MEMORY_ALLOCATED("memoryallocated", BaseCmd.TYPE_LONG, "memoryallocated"),
        MONTHLY_MAX("monthlymax", BaseCmd.TYPE_INT, "monthlyMax"),
        NAME("name", BaseCmd.TYPE_STRING, "name"),
        NEW_NAME("newname", BaseCmd.TYPE_STRING, "newname"),
        NETMASK("netmask", BaseCmd.TYPE_STRING, "netmask"),
        NETWORK_DOMAIN("networkdomain", BaseCmd.TYPE_STRING, "networkdomain"),
        OLD_POD_NAME("oldpodname", BaseCmd.TYPE_STRING, "oldPodName"),
        OLD_ZONE_NAME("oldzonename", BaseCmd.TYPE_STRING, "oldZoneName"),
        OP("op", BaseCmd.TYPE_STRING, "op"),
        PAGE("page", BaseCmd.TYPE_INT, "page"),
        PAGESIZE("pagesize", BaseCmd.TYPE_INT, "pagesize"),
        PARENT_DOMAIN_ID("parentdomainid", BaseCmd.TYPE_LONG, "parentDomainId"),
        PARENT_DOMAIN_NAME("parentdomainname", BaseCmd.TYPE_STRING, "parentDomainName"),
        PASSWORD("password", BaseCmd.TYPE_STRING, "password"),
        PATH("path", BaseCmd.TYPE_STRING, "path"),
        PERCENT_USED("percentused", BaseCmd.TYPE_STRING, "percentused"),
        POD_ID("podid", BaseCmd.TYPE_LONG, "podId"),
        POD_NAME("podname", BaseCmd.TYPE_STRING, "podName"),
        PRIVATE_IP("privateip", BaseCmd.TYPE_STRING, "privateIp"),
        PRIVATE_MAC_ADDRESS("privatemacaddress", BaseCmd.TYPE_STRING, "privatemacaddress"),
        PRIVATE_NETMASK("privatenetmask", BaseCmd.TYPE_STRING, "privatenetmask"),
        PRIVATE_PORT("privateport", BaseCmd.TYPE_STRING, "privatePort"),
        PROTOCOL("protocol", BaseCmd.TYPE_STRING, "protocol"),
        PUBLIC_IP("publicip", BaseCmd.TYPE_STRING, "publicIp"),
        PUBLIC_MAC_ADDRESS("publicmacaddress", BaseCmd.TYPE_STRING, "publicMacAddress"),
        PUBLIC_NETMASK("publicnetmask", BaseCmd.TYPE_STRING, "publicNetmask"),
        PUBLIC_PORT("publicport", BaseCmd.TYPE_STRING, "publicPort"),
        RAW_USAGE("rawusage", BaseCmd.TYPE_FLOAT, "rawUsage"),
        RELEASE_DATE("releaseddate", BaseCmd.TYPE_DATE, "releaseddate"),
        REMOVED("removed", BaseCmd.TYPE_DATE, "removed"),
        REQUIRES_HVM("requireshvm", BaseCmd.TYPE_BOOLEAN, "requireshvm"),
        ROOT_DISK_OFFERING_ID("rootdiskofferingid", BaseCmd.TYPE_LONG, "rootDiskOfferingId"),
        RULE_ID("ruleid", BaseCmd.TYPE_LONG, "ruleId"),
        SCHEDULE("schedule", BaseCmd.TYPE_STRING, "schedule"),
        SHOW_ALL("showall", BaseCmd.TYPE_BOOLEAN, "showall"),
        SECRET_KEY("secretkey", BaseCmd.TYPE_STRING, "secretKey"),
        SECURITY_GROUP_ID("securitygroupid", BaseCmd.TYPE_LONG, "securityGroupId"),
        SENT("sent", BaseCmd.TYPE_DATE, "sent"),
        SERVICE_OFFERING_ID("serviceofferingid", BaseCmd.TYPE_LONG, "serviceOfferingId"),
        SERVICE_OFFERING_NAME("serviceofferingname", BaseCmd.TYPE_STRING, "serviceOfferingName"),
        START_DATE("startdate", BaseCmd.TYPE_DATE, "startDate"),
        START_IP("startip", BaseCmd.TYPE_STRING, "startIp"),
        END_IP("endip", BaseCmd.TYPE_STRING, "endIp"),
        START_VLAN("startvlan", BaseCmd.TYPE_LONG, "startvlan"),
        END_VLAN("endvlan", BaseCmd.TYPE_LONG, "endvlan"),
        SIZE("size", BaseCmd.TYPE_LONG, "size"),
        STATE("state", BaseCmd.TYPE_STRING, "state"),
        STORAGE("storage", BaseCmd.TYPE_LONG, "storage"),
        SUCCESS("success", BaseCmd.TYPE_BOOLEAN, "success"),
        SNAPSHOT_ID("snapshotid", BaseCmd.TYPE_LONG, "snapshotid"),
        SNAPSHOT_POLICY_ID("snapshotpolicyid", BaseCmd.TYPE_LONG, "snapshotPolicyId"),
        SNAPSHOT_POLICY_IDS("snapshotpolicyids", BaseCmd.TYPE_STRING, "snapshotPolicyIds"),
        SCHEDULED("scheduled", BaseCmd.TYPE_DATE, "scheduled"),
        STORAGE_TYPE("storagetype", BaseCmd.TYPE_STRING, "storageType"),
        TEMPLATE_ID("templateid", BaseCmd.TYPE_LONG, "templateId"),
        TEMPLATE_NAME("templatename", BaseCmd.TYPE_STRING, "templateName"),
        TEMPLATE_DISPLAY_TEXT("templatedisplaytext", BaseCmd.TYPE_STRING, "templateDisplayText"),
        TEMPLATE_STATUS("templatestatus", BaseCmd.TYPE_STRING, "templateStatus"),
        TOTAL_MEMORY("totalmemory", BaseCmd.TYPE_LONG, "totalmemory"),
        OS_ARCHITECTURE("osarchitecture", BaseCmd.TYPE_INT, "osArchitecture"),
        TYPE("type", BaseCmd.TYPE_STRING, "type"),
        RESOURCE_TYPE("resourcetype", BaseCmd.TYPE_INT, "resourcetype"),
        USAGE("usage", BaseCmd.TYPE_STRING, "usage"),
        USER_ID("userid", BaseCmd.TYPE_LONG, "userId"),
        USERNAME("username", BaseCmd.TYPE_STRING, "username"),
        USER_DATA("userdata", BaseCmd.TYPE_STRING, "userData"),
        VALUE("value", BaseCmd.TYPE_STRING, "value"),
        VERSION("version", BaseCmd.TYPE_STRING, "version"),
        VIRTUAL_MACHINE_ID("virtualmachineid", BaseCmd.TYPE_LONG, "virtualMachineId"),
        VIRTUAL_MACHINE_IDS("virtualmachineids", BaseCmd.TYPE_STRING, "virtualMachineIds"),
        VIRTUAL_MACHINE_NAME("vmname", BaseCmd.TYPE_STRING, "vmname"),
        VIRTUAL_MACHINE_STATE("vmstate", BaseCmd.TYPE_STRING, "vmState"),
        VLAN_ID("vlanname", BaseCmd.TYPE_STRING, "vlanName"),
        VLAN_DB_ID("vlanid", BaseCmd.TYPE_LONG, "vlanId"),
        VLAN("vlan", BaseCmd.TYPE_STRING, "vlan"),
        VNET("vlan", BaseCmd.TYPE_STRING, "vlan"),
        //VIRTUAL_MACHINE_PASSWORD("virtualmachinepassword", BaseCmd.TYPE_STRING, "virtualMachinePassword"),
        VOLUME_ID("volumeid", BaseCmd.TYPE_LONG, "volumeId"), // FIXME: this is an array of longs
        WEEKLY_MAX("weeklymax", BaseCmd.TYPE_INT, "weeklyMax"),
        ZONE_ID("zoneid", BaseCmd.TYPE_LONG, "zoneId"),
        ZONE_NAME("zonename", BaseCmd.TYPE_STRING, "zoneName"),
        DISPLAY_TEXT("displaytext", BaseCmd.TYPE_STRING, "displayText"),
        HA_ENABLE("haenable", BaseCmd.TYPE_BOOLEAN, "haEnable"),
        AVAILABLE("available", BaseCmd.TYPE_BOOLEAN, "available"),
        NETWORK_RATE("networkrate", BaseCmd.TYPE_INT, "networkrate"),
        MULTICAST_RATE("multicastrate", BaseCmd.TYPE_INT, "multicastrate"),
        PASSWORD_ENABLED("passwordenabled", BaseCmd.TYPE_BOOLEAN, "passwordenabled"),
        JOB_ID("jobid", BaseCmd.TYPE_LONG, "jobid"),
        JOB_STATUS("jobstatus", BaseCmd.TYPE_INT, "jobstatus"),
        JOB_PROCESS_STATUS("jobprocstatus", BaseCmd.TYPE_INT, "jobprocstatus"),
        JOB_RESULT_CODE("jobresultcode", BaseCmd.TYPE_INT, "jobresultcode"),
        JOB_RESULT("jobresult", BaseCmd.TYPE_STRING, "jobresult"),
        JOB_RESULT_TYPE("jobresulttype", BaseCmd.TYPE_STRING, "jobresulttype"),
        JOB_INSTANCE_TYPE("jobinstancetype", BaseCmd.TYPE_STRING, "jobinstancetype"),
        JOB_INSTANCE_ID("jobinstanceid", BaseCmd.TYPE_LONG, "jobinstanceid"),
        JOB_CMD("jobcmd", BaseCmd.TYPE_LONG, "jobcmd"),
        RUNNING_VMS("runningvms", BaseCmd.TYPE_LONG, "runningvms"),
        SNAPSHOT_AVAIL("snapshotavailable", BaseCmd.TYPE_INT, "snapshotavailable"),
        SNAPSHOT_LIMIT("snapshotlimit", BaseCmd.TYPE_INT, "snapshotlimit"),
        SNAPSHOT_TOTAL("snapshottotal", BaseCmd.TYPE_INT, "snapshottotal"),
        STOPPED_VMS("stoppedvms", BaseCmd.TYPE_LONG, "stoppedvms"),
        TOTAL_VMS("totalvms", BaseCmd.TYPE_LONG, "totalvms"),
        TEMPLATE_AVAIL("templateavailable", BaseCmd.TYPE_INT, "templateavailable"),
        TEMPLATE_LIMIT("templatelimit", BaseCmd.TYPE_INT, "templatelimit"),
        TEMPLATE_TOTAL("templatetotal", BaseCmd.TYPE_INT, "templatetotal"),
        VOLUME_AVAIL("volumeavailable", BaseCmd.TYPE_INT, "volumeavailable"),
        VOLUME_LIMIT("volumelimit", BaseCmd.TYPE_INT, "volumelimit"),
        VOLUME_TOTAL("volumetotal", BaseCmd.TYPE_INT, "volumetotal"),
        VM_AVAIL("vmavailable", BaseCmd.TYPE_LONG, "vmavailable"),
        VM_LIMIT("vmlimit", BaseCmd.TYPE_LONG, "vmlimit"),
        VM_TOTAL("vmtotal", BaseCmd.TYPE_LONG, "vmtotal"),
        VM_STOPPED("vmstopped", BaseCmd.TYPE_LONG, "vmstopped"),
        VM_RUNNING("vmrunning", BaseCmd.TYPE_LONG, "vmrunning"),
        URL("url", BaseCmd.TYPE_STRING, "url"),
        CMD("cmd", BaseCmd.TYPE_STRING, "cmd"),
        ACTIVE_VIEWER_SESSIONS("activeviewersessions", BaseCmd.TYPE_INT, "activeviewersessions");

        private final String _name;
        private final short _dataType;
        private final String _tagName;

        Properties(String name, short dataType, String tagName) {
            _name = name;
            _dataType = dataType;
            _tagName = tagName;
        }

        public short getDataType() { return _dataType; }
        public String getName() { return _name; }
        public String getTagName() { return _tagName; }
    }

    public abstract List<Pair<String, Object>> execute(Map<String, Object> params);
    public abstract String getName();
    public abstract List<Pair<Enum, Boolean>> getProperties();

    public Map<String, String> getParams() {
        return _params;
    }
    public void setParams(Map<String, String> params) {
        _params = params;
    }
    public ManagementServer getManagementServer() {
        return _ms;
    }
    public void setManagementServer(ManagementServer ms) {
        _ms = ms;
    }

    public String getDateString(Date date) {
        if (date == null) {
            return "";
        }
        String formattedString = null;
        synchronized(_outputFormat) {
            formattedString = _outputFormat.format(date);
        }
        return formattedString;
    }

    public Map<String, Object> validateParams(Map<String, Object> params, boolean decode) {
        List<Pair<Enum, Boolean>> properties = getProperties();

        // step 1 - all parameter names passed in will be converted to lowercase
        Map<String, Object> processedParams = lowercaseParams(params);

        // step 2 - make sure all required params exist, and all existing params adhere to the appropriate data type
        Map<String, Object> validatedParams = new HashMap<String, Object>();
        for (Pair<Enum, Boolean> propertyPair : properties) {
            Properties prop = (Properties)propertyPair.first();
            Object param = processedParams.get(prop.getName());
            // possible validation errors are
            //       - NULL (not specified)
            //       - MALFORMED
            if (param != null) {
                short propertyType = prop.getDataType();
                String decodedParam = null;
                if (propertyType != TYPE_OBJECT) {
                    decodedParam = (String)param;
                    if (decode) {
                        try {
                            decodedParam = URLDecoder.decode((String)param, "UTF-8");
                        } catch (UnsupportedEncodingException usex) {
                            s_logger.warn(prop.getName() + " could not be decoded, value = " + param);
                            throw new ServerApiException(PARAM_ERROR, prop.getName() + " could not be decoded");
                        }
                    }
                }

                switch (propertyType) {
                case TYPE_INT:
                    try {
                        validatedParams.put(prop.getName(), Integer.valueOf(Integer.parseInt(decodedParam)));
                    } catch (NumberFormatException ex) {
                        s_logger.warn(prop.getName() + " (type is int) is malformed, value = " + decodedParam);
                        throw new ServerApiException(MALFORMED_PARAMETER_ERROR, prop.getName() + " is malformed");
                    }
                    break;
                case TYPE_LONG:
                    try {
                        validatedParams.put(prop.getName(), Long.valueOf(Long.parseLong(decodedParam)));
                    } catch (NumberFormatException ex) {
                        s_logger.warn(prop.getName() + " (type is long) is malformed, value = " + decodedParam);
                        throw new ServerApiException(MALFORMED_PARAMETER_ERROR, prop.getName() + " is malformed");
                    }
                    break;
                case TYPE_DATE:
                    try {
                        synchronized(_format) { // SimpleDataFormat is not thread safe, synchronize on it to avoid parse errors
                            validatedParams.put(prop.getName(), _format.parse(decodedParam));
                        }
                    } catch (ParseException ex) {
                        s_logger.warn(prop.getName() + " (type is date) is malformed, value = " + decodedParam);
                        throw new ServerApiException(MALFORMED_PARAMETER_ERROR, prop.getName() + " uses an unsupported date format");
                    }
                    break;
                case TYPE_FLOAT:
                    try {
                        validatedParams.put(prop.getName(), Float.valueOf(Float.parseFloat(decodedParam)));
                    } catch (NumberFormatException ex) {
                        s_logger.warn(prop.getName() + " (type is float) is malformed, value = " + decodedParam);
                        throw new ServerApiException(MALFORMED_PARAMETER_ERROR, prop.getName() + " is malformed");
                    }
                    break;
                case TYPE_BOOLEAN:
                	validatedParams.put(prop.getName(), Boolean.valueOf(Boolean.parseBoolean(decodedParam)));
                	break;
                case TYPE_STRING:
                    validatedParams.put(prop.getName(), decodedParam);
                    break;
                default:
                    validatedParams.put(prop.getName(), param);
                    break;
                }
            } else if (propertyPair.second().booleanValue() == true) {
                s_logger.warn("missing parameter, " + prop.getTagName() + " is not specified");
                throw new ServerApiException(MALFORMED_PARAMETER_ERROR, prop.getTagName() + " is not specified");
            }
        }

        return validatedParams;
    }

    private Map<String, Object> lowercaseParams(Map<String, Object> params) {
        Map<String, Object> lowercaseParams = new HashMap<String, Object>();
        for (String key : params.keySet()) {
            lowercaseParams.put(key.toLowerCase(), params.get(key));
        }
        return lowercaseParams;
    }

    public String buildResponse(ServerApiException apiException, String responseType) {
        StringBuffer sb = new StringBuffer();
        if (RESPONSE_TYPE_JSON.equalsIgnoreCase(responseType)) {
            // JSON response
            sb.append("{ \"" + getName() + "\" : { \"errorcode\" : \"" + apiException.getErrorCode() + "\", \"description\" : \"" + apiException.getDescription() + "\" } }");
        } else {
            sb.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
            sb.append("<" + getName() + ">");
            sb.append("<errorcode>" + apiException.getErrorCode() + "</errorcode>");
            sb.append("<description>" + escapeXml(apiException.getDescription()) + "</description>");
            sb.append("</" + getName() + ">");
        }
        return sb.toString();
    }

    public String buildResponse(List<Pair<String, Object>> tagList, String responseType) {
        StringBuffer sb = new StringBuffer();

        // set up the return value with the name of the response
        if (RESPONSE_TYPE_JSON.equalsIgnoreCase(responseType)) {
            sb.append("{ \"" + getName() + "\" : { ");
        } else {
            sb.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>");
            sb.append("<" + getName() + ">");
        }

        int i = 0;
        for (Pair<String, Object> tagData : tagList) {
            String tagName = tagData.first();
            Object tagValue = tagData.second();
            if (tagValue instanceof Object[]) {
                Object[] subObjects = (Object[])tagValue;
                if (subObjects.length < 1) continue;
                String separator = ((i > 0) ? ", " : "");
                sb.append(separator);
                int j = 0;
                for (Object subObject : subObjects) {
                    if (subObject instanceof List) {
                        List subObjList = (List)subObject;
                        writeSubObject(sb, tagName, subObjList, responseType, j++);
                    }
                }

                if (RESPONSE_TYPE_JSON.equalsIgnoreCase(responseType)) {
                    sb.append("]");
                }
                i++;
            } else {
                writeNameValuePair(sb, tagName, tagValue, responseType, i++);
            }
        }

        // close the response
        if (RESPONSE_TYPE_JSON.equalsIgnoreCase(responseType)) {
            sb.append("} }");
        } else {
            sb.append("</" + getName() + ">");
        }
        return sb.toString();
    }

    private void writeNameValuePair(StringBuffer sb, String tagName, Object tagValue, String responseType, int propertyCount) {
        if (tagValue == null) {
            return;
        }
        if (RESPONSE_TYPE_JSON.equalsIgnoreCase(responseType)) {
            String seperator = ((propertyCount > 0) ? ", " : "");
            sb.append(seperator + "\"" + tagName + "\" : \"" + tagValue.toString() + "\"");
        } else {
            sb.append("<" + tagName + ">" + escapeXml(tagValue.toString()) + "</" + tagName + ">");
        }
    }

    private void writeSubObject(StringBuffer sb, String tagName, List tagList, String responseType, int objectCount) {
        if (RESPONSE_TYPE_JSON.equalsIgnoreCase(responseType)) {
            sb.append(((objectCount == 0) ? "\"" + tagName + "\" : [  { " : ", { "));
        } else {
            sb.append("<" + tagName + ">");
        }

        int i = 0;
        for (Object tag : tagList) {
            if (tag instanceof Pair) {
                Pair nameValuePair = (Pair)tag;
                writeNameValuePair(sb, (String)nameValuePair.first(), nameValuePair.second(), responseType, i++);
            }
        }

        if (RESPONSE_TYPE_JSON.equalsIgnoreCase(responseType)) {
            sb.append("}");
        } else {
            sb.append("</" + tagName + ">");
        }
    }
    
    /**
     * Escape xml response set to false by default. API commands to override this method to allow escaping
     */
    public boolean requireXmlEscape() {
		return true;
	}
    
	private String escapeXml(String xml){
		if(!requireXmlEscape()){
			return xml;
		}
		int iLen = xml.length();
		if (iLen == 0)
			return xml;
		StringBuffer sOUT = new StringBuffer(iLen + 256);
		int i = 0;
		for (; i < iLen; i++) {
			char c = xml.charAt(i);
			if (c == '<')
				sOUT.append("&lt;");
			else if (c == '>')
				sOUT.append("&gt;");
			else if (c == '&')
				sOUT.append("&amp;");
			else if (c == '"')
				sOUT.append("&quot;");
			else if (c == '\'')
				sOUT.append("&apos;");
			else
				sOUT.append(c);
		}
		return sOUT.toString();
	}
	
	protected long waitInstanceCreation(long jobId) {
        ManagementServer mgr = getManagementServer();

        long instanceId = 0;
        AsyncJobVO job = null;
        boolean interruped = false;
        
        // as job may be executed in other management server, we need to do a database polling here
        try {
        	boolean quit = false;
	        while(!quit) {
	        	job = mgr.findAsyncJobById(jobId);
	        	if(job == null) {
	        		s_logger.error("Async command " + this.getClass().getName() + " waitInstanceCreation error: job-" + jobId + " no longer exists");
	        		break;
	        	}
	        	
	        	switch(job.getStatus()) {
	        	case AsyncJobResult.STATUS_IN_PROGRESS :
	        		if(job.getProcessStatus() == BaseCmd.PROGRESS_INSTANCE_CREATED) {
	        			Long id = (Long)AsyncJobResult.fromResultString(job.getResult());
	        			if(id != null) {
	        				instanceId = id.longValue();
	        				if(s_logger.isDebugEnabled())
	        					s_logger.debug("Async command " + this.getClass().getName() + " succeeded in waiting for new instance to be created, instance Id: " + instanceId);
	        			} else {
	        				s_logger.warn("Async command " + this.getClass().getName() + " has new instance created, but value as null?");
	        			}
	        			quit = true;
	        		}
	        		break;
	        		
	        	case AsyncJobResult.STATUS_SUCCEEDED :
	        		instanceId = getInstanceIdFromJobSuccessResult(job.getResult());
	        		quit = true;
	        		break;
	        		
	        	case AsyncJobResult.STATUS_FAILED :
        			s_logger.error("Async command " + this.getClass().getName() + " executing job-" + jobId + " failed, result: " + job.getResult());
	        		quit = true;
	        		break;
	        	}
	        	
	        	if(quit)
	        		break;
	        	
	        	try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					interruped = true;
				}
	        }
        } finally {
	        if(interruped)
	        	Thread.currentThread().interrupt();
        }
        return instanceId;
	}
	
	protected long getInstanceIdFromJobSuccessResult(String result) {
		s_logger.warn("getInstanceIdFromJobSuccessResult should be overrid in subclass " + this.getClass().getName());
		return 0;
	}

	public static boolean isAdmin(short accountType) {
	    return ((accountType == Account.ACCOUNT_TYPE_ADMIN) ||
	            (accountType == Account.ACCOUNT_TYPE_DOMAIN_ADMIN) ||
	            (accountType == Account.ACCOUNT_TYPE_READ_ONLY_ADMIN));
	}
}
