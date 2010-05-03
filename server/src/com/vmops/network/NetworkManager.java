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
package com.vmops.network;

import java.util.List;
import java.util.Map;

import com.vmops.host.HostVO;
import com.vmops.async.executor.AssignToLoadBalancerExecutor;
import com.vmops.async.executor.LoadBalancerParam;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.VlanVO;
import com.vmops.exception.ConcurrentOperationException;
import com.vmops.exception.InsufficientCapacityException;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.exception.ResourceAllocationException;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.user.AccountVO;
import com.vmops.utils.component.Manager;
import com.vmops.vm.DomainRouter;
import com.vmops.vm.DomainRouterVO;
import com.vmops.vm.UserVmVO;

/**
 * NetworkManager manages the network for the different end users.
 *
 */
public interface NetworkManager extends Manager {
    /**
     * Assigns a router to the user.
     * 
     * @param userId user id.
     * @param dcId data center id.
     * @param podId pod id.
     * @param domain domain to use
     * @return DomainRouter if one is assigned.
     * @throws InsufficientCapacityException if assigning causes any capacity issues.
     */
    DomainRouterVO assignRouter(long userId, long accountId, long dcId, long podId, String domain, String instance) throws InsufficientCapacityException;
    
    /**
     * create the router.
     * 
     * @param accountId account Id the router belongs to.
     * @param ipAddress public ip address the router should use to access the internet.
     * @param dcId data center id the router should live in.
     * @param domain domain name of this network.
     * @param offering service offering associated with this request
     * @return DomainRouterVO if created.  null if not.
     */
    DomainRouterVO createRouter(long accountId, String ipAddress, long dcId, String domain, ServiceOfferingVO offering) throws ConcurrentOperationException;

    /**
     * create a DHCP server/user data server for directly connected VMs
     * @param dcId data center id the router should live in.
     * @param domain domain name of this network.
     * @return DomainRouterVO if created.  null if not.
     */
	DomainRouterVO createDhcpServerForDirectlyAttachedGuests(DataCenterVO dc, HostPodVO pod, VlanVO vlan) throws ConcurrentOperationException;

    /**
     * Adds or deletes a vlan
     * @param zone
     * @param add
     * @param vlanId
     * @param vlanGateway
     * @param vlanNetmask
     * @param description
     * @param name
     * @throws InvalidParameterValueException if add/delete Vlan failed
     * @return VlanVO object if the add operation is successful; null if delete operation is successful
     */
    VlanVO addOrDeleteVlan(long zoneId, boolean add, String vlanId, String vlanGateway, String vlanNetmask, String description, String name) throws InvalidParameterValueException;
    
    /**
     * Adds VLAN to to the specified host
     * @param hostId
     * @param vlanId
     * @param vlanGateway
     * @return True if successful, false if not
     */
    boolean addVlanToHost(Long hostId, String vlanId, String vlanGateway);
    
    /**
     * save a vm password on the router.
     * 
	 * @param routerId the ID of the router to save the password to
	 * @param vmIpAddress the IP address of the User VM that will use the password
	 * @param password the password to save to the router
     */
    boolean savePasswordToRouter(long routerId, String vmIpAddress, String password);
    
    DomainRouterVO startRouter(long routerId);
    
    boolean releaseRouter(long routerId);
    
    boolean destroyRouter(long routerId);
    
    boolean stopRouter(long routerId);
    
    boolean getRouterStatistics(long vmId, Map<String, long[]> netStats, Map<String, long[]> diskStats);

    boolean rebootRouter(long routerId);
    /**
     * @param hostId get all of the virtual machine routers on a host.
     * @return collection of VirtualMachineRouter
     */
    List<? extends DomainRouter> getRouters(long hostId);
    
    /**
     * @param routerId id of the router
     * @return VirtualMachineRouter
     */
    DomainRouterVO getRouter(long routerId);
    
    /**
     * Do all of the work of releasing public ip addresses.  Note that
     * if this method fails, there can be side effects.
     * @param userId
     * @param ipAddress
     * @return true if it did; false if it didn't
     */
    public boolean releasePublicIpAddress(long userId, String ipAddress);
    
    /**
     * Find or create the source nat ip address a user uses within the
     * data center.
     * 
     * @param account account
     * @param dc data center
     * @param domain domain used for user's network.
     * @param so service offering associated with this request
     * @return public ip address.
     */
    public String assignSourceNatIpAddress(AccountVO account, DataCenterVO dc, String domain, ServiceOfferingVO so) throws ResourceAllocationException;
    
    /**
     * @param fwRules list of rules to be updated
     * @param router  router where the rules have to be updated
     * @return list of rules successfully updated
     */
    public List<FirewallRuleVO> updatePortForwardingRules(List<FirewallRuleVO> fwRules, DomainRouterVO router, Long hostId);

    /**
     * @param fwRules list of rules to be updated
     * @param router  router where the rules have to be updated
     * @return success
     */
    public boolean updateLoadBalancerRules(List<FirewallRuleVO> fwRules, DomainRouterVO router, Long hostId);
    
    /**
     * @param publicIpAddress public ip address associated with the fwRules
     * @param fwRules list of rules to be updated
     * @param router router where the rules have to be updated
     * @return list of rules successfully updated
     */
    public List<FirewallRuleVO> updateFirewallRules(String publicIpAddress, List<FirewallRuleVO> fwRules, DomainRouterVO router);
    
    /**
     * Associates or disassociates a list of public IP address for a router.
     * @param router router object to send the association to
     * @param ipAddrList list of public IP addresses
     * @param add true if associate, false if disassociate
     * @param firstIp true if ipAddrList has only one IP, and this IP is the first or last IP in its VLAN
     * @return
     */
    boolean associateIP(DomainRouterVO router, List<String> ipAddrList, boolean add, boolean firstIp) throws ResourceAllocationException;
    
    boolean updateFirewallRule(FirewallRuleVO fwRule, String oldPrivateIP, String oldPrivatePort);
    boolean executeAssignToLoadBalancer(AssignToLoadBalancerExecutor executor, LoadBalancerParam param);
    
    /**
     * Add a DHCP entry on the domr dhcp server
     * @param routerHostId - the host id of the domr
     * @param routerIp - the private ip address of the domr
     * @param vmName - the name of the VM (e.g., i-10-TEST)
     * @param vmMac  - the mac address of the eth0 interface of the VM
     * @param vmIp   - the ip address to hand out.
     * @return success or failure
     */
    public boolean addDhcpEntry(long routerHostId, String routerIp, String vmName, String vmMac, String vmIp);
    
    /**
     * Adds a virtual machine into the guest network.
     *   1. Starts the domR
     *   2. Sets the dhcp Entry on the domR
     *   3. Sets the domR
     * 
     * @param vm user vm to add to the guest network
     * @param password password for this vm.  Can be null
     * @return DomainRouterVO if everything is successful.  null if not.
     * 
     * @throws ConcurrentOperationException if multiple starts are being attempted.
     */
	public DomainRouterVO addVirtualMachineToGuestNetwork(UserVmVO vm, String password) throws ConcurrentOperationException;
	
	/**
	 * Finds a VLAN in the specified zone that already has allocated IP addresses, but is not completely used up.
	 * If all VLANs with allocated IP addresses are completely used up, returns an arbitrary VLAN that is empty.
	 * @param zoneId
	 * @return Database ID of VLAN to use
	 */
	public long findNextVlan(long zoneId);
	
	
}
