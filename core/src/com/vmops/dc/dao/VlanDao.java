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

package com.vmops.dc.dao;

import java.util.List;

import com.vmops.dc.Vlan;
import com.vmops.dc.VlanVO;
import com.vmops.dc.Vlan.VlanType;
import com.vmops.utils.Pair;
import com.vmops.utils.db.GenericDao;

public interface VlanDao extends GenericDao<VlanVO, Long> {
	
	public VlanVO findByZoneAndVlanId(long zoneId, String vlanId);
	
	public List<VlanVO> findByZone(long zoneId);
	
	public List<VlanVO> listByZoneAndType(long zoneId, Vlan.VlanType vlanType);
	
	public boolean enableExternalFirewall(long vlanDbId, String firewallIp, String firewallUser, String encryptedFirewallPassword, String encryptedFirewallEnablePassword);
	
	public boolean disableExternalFirewall(long vlanDbId);
	
	public List<VlanVO> listVlansForPod(long podId);
	
	public List<VlanVO> listVlansForPodByType(long podId, Vlan.VlanType vlanType);
	
	public void addToPod(long podId, long vlanDbId);
	
    public Pair<String, VlanVO>  assignIpAddress(long zoneId, long accountId, long domainId, VlanType vlanType, boolean sourceNat);

}
