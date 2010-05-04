/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
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
package com.vmops.resource.xen;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.ejb.Local;
import javax.naming.ConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.trilead.ssh2.SCPClient;
import com.vmops.agent.IAgentControl;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.AttachDiskCommand;
import com.vmops.agent.api.AttachIsoCommand;
import com.vmops.agent.api.BackupSnapshotAnswer;
import com.vmops.agent.api.BackupSnapshotCommand;
import com.vmops.agent.api.CheckHealthAnswer;
import com.vmops.agent.api.CheckHealthCommand;
import com.vmops.agent.api.CheckVirtualMachineAnswer;
import com.vmops.agent.api.CheckVirtualMachineCommand;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.CreateVolumeFromSnapshotAnswer;
import com.vmops.agent.api.CreateVolumeFromSnapshotCommand;
import com.vmops.agent.api.DeleteSnapshotBackupAnswer;
import com.vmops.agent.api.DeleteSnapshotBackupCommand;
import com.vmops.agent.api.GetHostStatsAnswer;
import com.vmops.agent.api.GetHostStatsCommand;
import com.vmops.agent.api.GetStorageStatsAnswer;
import com.vmops.agent.api.GetStorageStatsCommand;
import com.vmops.agent.api.GetVmStatsAnswer;
import com.vmops.agent.api.GetVmStatsCommand;
import com.vmops.agent.api.GetVncPortAnswer;
import com.vmops.agent.api.GetVncPortCommand;
import com.vmops.agent.api.ManageSnapshotAnswer;
import com.vmops.agent.api.ManageSnapshotCommand;
import com.vmops.agent.api.MigrateAnswer;
import com.vmops.agent.api.MigrateCommand;
import com.vmops.agent.api.ModifyStoragePoolAnswer;
import com.vmops.agent.api.ModifyStoragePoolCommand;
import com.vmops.agent.api.ModifyVlanCommand;
import com.vmops.agent.api.PingCommand;
import com.vmops.agent.api.PingRoutingCommand;
import com.vmops.agent.api.PrepareForMigrationAnswer;
import com.vmops.agent.api.PrepareForMigrationCommand;
import com.vmops.agent.api.ReadyAnswer;
import com.vmops.agent.api.ReadyCommand;
import com.vmops.agent.api.RebootAnswer;
import com.vmops.agent.api.RebootCommand;
import com.vmops.agent.api.RebootRouterCommand;
import com.vmops.agent.api.StartAnswer;
import com.vmops.agent.api.StartCommand;
import com.vmops.agent.api.StartConsoleProxyAnswer;
import com.vmops.agent.api.StartConsoleProxyCommand;
import com.vmops.agent.api.StartRouterAnswer;
import com.vmops.agent.api.StartRouterCommand;
import com.vmops.agent.api.StartSecStorageVmAnswer;
import com.vmops.agent.api.StartSecStorageVmCommand;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupRoutingCommand;
import com.vmops.agent.api.StartupStorageCommand;
import com.vmops.agent.api.StopAnswer;
import com.vmops.agent.api.StopCommand;
import com.vmops.agent.api.StoragePoolInfo;
import com.vmops.agent.api.VmStatsEntry;
import com.vmops.agent.api.WatchNetworkAnswer;
import com.vmops.agent.api.WatchNetworkCommand;
import com.vmops.agent.api.proxy.CheckConsoleProxyLoadCommand;
import com.vmops.agent.api.proxy.ConsoleProxyLoadAnswer;
import com.vmops.agent.api.proxy.WatchConsoleProxyLoadCommand;
import com.vmops.agent.api.routing.DhcpEntryCommand;
import com.vmops.agent.api.routing.IPAssocCommand;
import com.vmops.agent.api.routing.RoutingCommand;
import com.vmops.agent.api.routing.SavePasswordCommand;
import com.vmops.agent.api.routing.SetFirewallRuleCommand;
import com.vmops.agent.api.storage.CreateAnswer;
import com.vmops.agent.api.storage.CreateCommand;
import com.vmops.agent.api.storage.CreatePrivateTemplateAnswer;
import com.vmops.agent.api.storage.CreatePrivateTemplateCommand;
import com.vmops.agent.api.storage.DestroyCommand;
import com.vmops.agent.api.storage.DownloadAnswer;
import com.vmops.agent.api.storage.ManageVolumeAnswer;
import com.vmops.agent.api.storage.ManageVolumeCommand;
import com.vmops.agent.api.storage.PrimaryStorageDownloadCommand;
import com.vmops.agent.api.storage.ShareAnswer;
import com.vmops.agent.api.storage.ShareCommand;
import com.vmops.agent.resource.virtualnetwork.VirtualRoutingResource;
import com.vmops.host.Host.HypervisorType;
import com.vmops.host.Host.Type;
import com.vmops.resource.ServerResource;
import com.vmops.storage.StoragePool;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.StoragePool.StoragePoolType;
import com.vmops.storage.Volume.StorageResourceType;
import com.vmops.storage.template.TemplateInfo;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.Ternary;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.script.Script;
import com.vmops.vm.ConsoleProxyVO;
import com.vmops.vm.DomainRouter;
import com.vmops.vm.SecondaryStorageVmVO;
import com.vmops.vm.State;
import com.vmops.vm.VirtualMachineName;
import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Console;
import com.xensource.xenapi.Host;
import com.xensource.xenapi.HostCpu;
import com.xensource.xenapi.HostMetrics;
import com.xensource.xenapi.Network;
import com.xensource.xenapi.PBD;
import com.xensource.xenapi.PIF;
import com.xensource.xenapi.Pool;
import com.xensource.xenapi.SR;
import com.xensource.xenapi.Types;
import com.xensource.xenapi.VBD;
import com.xensource.xenapi.VDI;
import com.xensource.xenapi.VIF;
import com.xensource.xenapi.VLAN;
import com.xensource.xenapi.VM;
import com.xensource.xenapi.VMGuestMetrics;
import com.xensource.xenapi.XenAPIObject;
import com.xensource.xenapi.Types.BadServerResponse;
import com.xensource.xenapi.Types.JoiningHostCannotContainSharedSrs;
import com.xensource.xenapi.Types.VmPowerState;
import com.xensource.xenapi.Types.XenAPIException;



/**
 * Encapsulates the interface to the XenServer API.
 * 
 */
@Local(value = ServerResource.class)
public class XenServerResource implements ServerResource {
    private static final Logger s_logger = Logger.getLogger(XenServerResource.class);
    String _name;
    String _hostUuid;
    String _username;
    String _password;
    String _hostUrl;
    String _scriptsDir = "scripts/vm/storage/xenserver";
    private final int _retry = 24;
    private final int _sleep = 5000;
    long _dcId;
    static VirtualRoutingResource s_vrr;
    String _pod;
    protected HashMap<String, State> _vms = new HashMap<String, State>(20);
    String _patchPath;
    XenServerConnectionPool _connPool;
    String _privateNic;
    String _publicNic;
    protected int _wait;
    private IAgentControl _agentControl;
    Map<String, String> _domrIPMap = new HashMap<String, String>();
    private String _poolUuid;
    
    // Guest and Host Performance Statistics
    boolean _collectHostStats = true;
    String _consolidationFunction = "AVERAGE";
    int _pollingIntervalInSeconds = 60;
    

    private enum SRType {
        NFS, LVM, ISCSI, ISO;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        public boolean equals(String type) {
            return super.toString().equalsIgnoreCase(type);
        }
    }

    protected static HashMap<Types.VmPowerState, State> s_statesTable;
    static {
        s_statesTable = new HashMap<Types.VmPowerState, State>();
        s_statesTable.put(Types.VmPowerState.HALTED, State.Stopped);
        s_statesTable.put(Types.VmPowerState.PAUSED, State.Running);
        s_statesTable.put(Types.VmPowerState.RUNNING, State.Running);
        s_statesTable.put(Types.VmPowerState.SUSPENDED, State.Running);
        s_statesTable.put(Types.VmPowerState.UNKNOWN, State.Unknown);
        s_statesTable.put(Types.VmPowerState.UNRECOGNIZED, State.Unknown);
    }

    private boolean isRefNull(XenAPIObject object) {
        return (object == null || object.toWireString().equals("OpaqueRef:NULL"));
    }

    @Override
    public void disconnected() {
        _connPool.disconnect(_hostUuid, _poolUuid);
        _poolUuid = null;
    }

    protected void cleanupDiskMounts() {
        Connection conn = getConnection();

        Map<SR, SR.Record> srs;
        try {
            srs = SR.getAllRecords(conn);
        } catch (XenAPIException e) {
            s_logger.warn("Unable to get the SRs " + e.toString(), e);
            throw new VmopsRuntimeException("Unable to get SRs " + e.toString(), e);
        } catch (XmlRpcException e) {
            throw new VmopsRuntimeException("Unable to get SRs " + e.getMessage());
        }

        for (Map.Entry<SR, SR.Record> sr : srs.entrySet()) {
            SR.Record rec = sr.getValue();
            if (SRType.NFS.equals(rec.type) || (SRType.ISO.equals(rec.type) && rec.nameLabel.endsWith("iso"))) {
                if (rec.PBDs == null || rec.PBDs.size() == 0) {
                    cleanSR(sr.getKey(), rec);
                    continue;
                }

                for (PBD pbd : rec.PBDs) {

                    if (isRefNull(pbd)) {
                        continue;
                    }
                    PBD.Record pbdr = null;
                    try {
                        pbdr = pbd.getRecord(conn);
                    } catch (XenAPIException e) {
                        s_logger.warn("Unable to get pbd record " + e.toString());
                    } catch (XmlRpcException e) {
                        s_logger.warn("Unable to get pbd record " + e.getMessage());
                    }

                    if (pbdr == null) {
                        continue;
                    }

                    try {
                        if (pbdr.host.getUuid(conn).equals(_hostUuid)) {
                            if (!currentlyAttached(sr.getKey(), rec, pbd, pbdr)) {
                                pbd.unplug(conn);
                                pbd.destroy(conn);
                                cleanSR(sr.getKey(), rec);
                            } else if (!pbdr.currentlyAttached) {
                                pbd.plug(conn);
                            }
                        }

                    } catch (XenAPIException e) {
                        s_logger.warn("Catch XenAPIException due to" + e.toString(), e);
                    } catch (XmlRpcException e) {
                        s_logger.warn("Catch XmlRpcException due to" + e.getMessage(), e);
                    }
                }
            }
        }
    }

    protected boolean currentlyAttached(SR sr, SR.Record rec, PBD pbd, PBD.Record pbdr) {
        String status;
        status = callHostPlugin("checkMount", "mount", rec.uuid);

        if (status != null && status.equalsIgnoreCase("1")) {
            s_logger.debug("currently attached " + rec.uuid);
            return true;
        } else {
            s_logger.debug("currently not attached " + rec.uuid);
            return false;
        }
    }
    
    protected boolean pingdomr(String host, String port) {
        String status;
        status = callHostPlugin("pingdomr", "host", host, "port", port);

        if (status == null || status.isEmpty()) { 
            return false;
        }

        return true;

    }

    private String logX(XenAPIObject obj, String msg) {
        return new StringBuilder("Host ").append(_hostUuid).append(" ").append(obj.toWireString()).append(" ").append(msg).toString();
    }

    protected void cleanSR(SR sr, SR.Record rec) {
        Connection conn = getConnection();
        if (rec.VDIs != null) {
            for (VDI vdi : rec.VDIs) {

                VDI.Record vdir;
                try {
                    vdir = vdi.getRecord(conn);
                } catch (XenAPIException e) {
                    s_logger.debug("Unable to get VDI: " + e.toString());
                    continue;
                } catch (XmlRpcException e) {
                    s_logger.debug("Unable to get VDI: " + e.getMessage());
                    continue;
                }

                if (vdir.VBDs == null)
                    continue;

                for (VBD vbd : vdir.VBDs) {
                    try {
                        VBD.Record vbdr = vbd.getRecord(conn);
                        VM.Record vmr = vbdr.VM.getRecord(conn);
                        if ((!isRefNull(vmr.residentOn) && vmr.residentOn.getUuid(conn).equals(_hostUuid))
                                || (isRefNull(vmr.residentOn) && !isRefNull(vmr.affinity) && vmr.affinity.getUuid(conn).equals(_hostUuid))) {
                            if (vmr.powerState != VmPowerState.HALTED && vmr.powerState != VmPowerState.UNKNOWN && vmr.powerState != VmPowerState.UNRECOGNIZED) {
                                try {
                                    vbdr.VM.hardShutdown(conn);
                                } catch (XenAPIException e) {
                                    s_logger.debug("Shutdown hit error " + vmr.nameLabel + ": " + e.toString());
                                }
                            }
                            try {
                                vbdr.VM.destroy(conn);
                            } catch (XenAPIException e) {
                                s_logger.debug("Destroy hit error " + vmr.nameLabel + ": " + e.toString());
                            } catch (XmlRpcException e) {
                                s_logger.debug("Destroy hit error " + vmr.nameLabel + ": " + e.getMessage());
                            }
                            vbd.destroy(conn);
                            break;
                        }
                    } catch (XenAPIException e) {
                        s_logger.debug("Unable to get VBD: " + e.toString());
                        continue;
                    } catch (XmlRpcException e) {
                        s_logger.debug("Uanbel to get VBD: " + e.getMessage());
                        continue;
                    }
                }
            }
        }

        if (rec.PBDs == null || rec.PBDs.size() == 0) {
            try {
                sr.forget(conn);
            } catch (XenAPIException e) {
                s_logger.warn("Unabel to forget sr " + rec.uuid + ": " + e.toString());
            } catch (XmlRpcException e) {
                s_logger.debug("Unabel to forget sr " + rec.uuid + ": " + e.getMessage());
            }
            return;
        }

        for (PBD pbd : rec.PBDs) {

            try {
                PBD.Record pbdr = pbd.getRecord(conn);

                if (pbdr == null || isRefNull(pbdr.host) || pbdr.host.getUuid(conn).equals(_hostUuid)) {
                    try {
                        pbd.unplug(conn);
                    } catch (XenAPIException e) {
                        s_logger.warn("Unable to unplug the pbd " + (pbdr != null ? pbdr.uuid : "") + " due to " + e.toString());
                        continue;
                    } catch (XmlRpcException e) {
                        s_logger.warn("Unable to unplug the pbd " + (pbdr != null ? pbdr.uuid : "") + " due to " + e.getMessage());
                        continue;
                    }
                    try {
                        pbd.plug(conn);
                    } catch (XenAPIException e) {
                        s_logger.warn("Unable to plug the pbd " + (pbdr != null ? pbdr.uuid : "") + " due to " + e.toString());
                        try {
                            pbd.destroy(conn);
                        } catch (XenAPIException e1) {
                            s_logger.warn("Unable to destroy pbd" + e1.toString());
                        } catch (XmlRpcException e1) {
                            s_logger.warn("Unable to destroy pbd" + e1.toString());
                        }
                    } catch (XmlRpcException e) {
                        s_logger.warn("Unable to plug the pbd " + (pbdr != null ? pbdr.uuid : "") + " due to " + e.getMessage());
                    }
                }
            } catch (XenAPIException e) {
                s_logger.warn("Unable to get record for pbd " + e.toString());
            } catch (XmlRpcException e) {
                s_logger.warn("Unable to get record for pbd " + e.getMessage());
            }
        }

        try {
            rec = sr.getRecord(conn);
            if (rec.PBDs == null || rec.PBDs.size() == 0) {
                sr.forget(conn);
                return;
            }
        } catch (XenAPIException e) {
            s_logger.warn("Unable to retrieve sr again: " + e.toString(), e);
        } catch (XmlRpcException e) {
            s_logger.warn("Unable to retrieve sr again: " + e.getMessage(), e);
        }
    }

    @Override
    public Answer executeRequest(Command cmd) {
        if (cmd instanceof IPAssocCommand) {
            return execute((IPAssocCommand) cmd);
        } else if (cmd instanceof SavePasswordCommand) {
            return execute((SavePasswordCommand) cmd);
        } else if (cmd instanceof DhcpEntryCommand) {
            return execute((DhcpEntryCommand) cmd);
        } else if (cmd instanceof SetFirewallRuleCommand) {
            return execute((SetFirewallRuleCommand) cmd);
        } else if (cmd instanceof RoutingCommand) {
            return s_vrr.executeRequest(cmd);
        }

        if (cmd instanceof StartCommand) {
            return execute((StartCommand) cmd);
        } else if (cmd instanceof StartRouterCommand) {
            return execute((StartRouterCommand) cmd);
        } else if (cmd instanceof ReadyCommand) {
            return execute((ReadyCommand) cmd);
        } else if (cmd instanceof ModifyVlanCommand) {
            return execute((ModifyVlanCommand) cmd);
        } else if (cmd instanceof GetHostStatsCommand) {
            return execute((GetHostStatsCommand) cmd);
        } else if (cmd instanceof GetVmStatsCommand) {
        	return execute((GetVmStatsCommand) cmd);
        } else if (cmd instanceof WatchNetworkCommand) {
            return execute((WatchNetworkCommand) cmd);
        } else if (cmd instanceof CheckHealthCommand) {
            return execute((CheckHealthCommand) cmd);
        } else if (cmd instanceof StopCommand) {
            return execute((StopCommand) cmd);
        } else if (cmd instanceof RebootRouterCommand) {
            return execute((RebootRouterCommand) cmd);
        } else if (cmd instanceof RebootCommand) {
            return execute((RebootCommand) cmd);
        } else if (cmd instanceof CheckVirtualMachineCommand) {
            return execute((CheckVirtualMachineCommand) cmd);
        } else if (cmd instanceof PrepareForMigrationCommand) {
            return execute((PrepareForMigrationCommand) cmd);
        } else if (cmd instanceof MigrateCommand) {
            return execute((MigrateCommand) cmd);
        } else if (cmd instanceof CreateCommand) {
            return execute((CreateCommand) cmd);
        } else if (cmd instanceof DestroyCommand) {
            return execute((DestroyCommand) cmd);
        } else if (cmd instanceof ShareCommand) {
            return execute((ShareCommand) cmd);
        } else if (cmd instanceof ModifyStoragePoolCommand) {
            return execute((ModifyStoragePoolCommand) cmd);
        } else if (cmd instanceof ManageVolumeCommand) {
            return execute((ManageVolumeCommand) cmd);
        } else if (cmd instanceof AttachDiskCommand) {
            return execute((AttachDiskCommand) cmd);
        } else if (cmd instanceof AttachIsoCommand) {
            return execute((AttachIsoCommand) cmd);
        } else if (cmd instanceof ManageSnapshotCommand) {
            return execute((ManageSnapshotCommand) cmd);
        } else if (cmd instanceof BackupSnapshotCommand) {
            return execute((BackupSnapshotCommand) cmd);
        } else if (cmd instanceof DeleteSnapshotBackupCommand) {
            return execute((DeleteSnapshotBackupCommand) cmd);
        } else if (cmd instanceof CreateVolumeFromSnapshotCommand) {
            return execute((CreateVolumeFromSnapshotCommand) cmd);
        } else if (cmd instanceof CreatePrivateTemplateCommand) {
            return execute((CreatePrivateTemplateCommand) cmd);
        } else if (cmd instanceof GetStorageStatsCommand) {
            return execute((GetStorageStatsCommand) cmd);
        } else if (cmd instanceof PrimaryStorageDownloadCommand) {
            return execute((PrimaryStorageDownloadCommand) cmd);
        } else if (cmd instanceof StartConsoleProxyCommand) {
            return execute((StartConsoleProxyCommand) cmd);
        }else if (cmd instanceof StartSecStorageVmCommand) {
            return execute((StartSecStorageVmCommand) cmd);
        } else if (cmd instanceof CheckConsoleProxyLoadCommand) {
            return execute((CheckConsoleProxyLoadCommand) cmd);
        } else if (cmd instanceof WatchConsoleProxyLoadCommand) {
            return execute((WatchConsoleProxyLoadCommand) cmd);
        } else if (cmd instanceof GetVncPortCommand) {
            return execute((GetVncPortCommand) cmd);
        } else {
            return Answer.createUnsupportedCommandAnswer(cmd);
        }
    }

    private Answer execute(StartSecStorageVmCommand cmd) {
    	SecondaryStorageVmVO secStorageVmVO = cmd.getSecondaryStorageVmVO();
		String result = startSystemVM (cmd.getVmName(), cmd.getSecondaryStorageVmVO().getVlanId(),
				cmd.getVolumes(), cmd.getBootArgs(), secStorageVmVO.getPrivateIpAddress(),
				secStorageVmVO.getPrivateMacAddress(), secStorageVmVO.getPublicIpAddress(),
				secStorageVmVO.getPublicMacAddress(), cmd.getProxyCmdPort(), secStorageVmVO.getRamSize());
		if (result == null) {
			return new StartSecStorageVmAnswer(cmd);
		}
		return new StartSecStorageVmAnswer(cmd, result);
	}

	protected Answer execute(final SetFirewallRuleCommand cmd) {
        String args;
        if (cmd.isEnable()) {
            args = "-A";
        } else {
            args = "-D";
        }
        args += " -P " + cmd.getProtocol().toLowerCase();
        args += " -l " + cmd.getPublicIpAddress();
        args += " -p " + cmd.getPublicPort();
        args += " -n " + cmd.getRouterName();
        args += " -i " + cmd.getRouterIpAddress();
        args += " -r " + cmd.getPrivateIpAddress();
        args += " -d " + cmd.getPrivatePort();
        args += " -N " + cmd.getVlanNetmask();
        String oldPrivateIP = cmd.getOldPrivateIP();
        String oldPrivatePort = cmd.getOldPrivatePort();
        if (oldPrivateIP == null)
            oldPrivateIP = "";
        if (oldPrivatePort == null)
            oldPrivatePort = "";
        args += " -w " + oldPrivateIP;
        args += " -x " + oldPrivatePort;

        String result = callHostPlugin("setFirewallRule", "args", args);

        if (result == null || result.isEmpty()) {
            return new Answer(cmd, false, "SetFirewallRule failed");
        }
        return new Answer(cmd);
    }

    protected synchronized Answer execute(final DhcpEntryCommand cmd) {
        String args = "-r " + cmd.getRouterPrivateIpAddress();
        args += " -v " + cmd.getVmIpAddress();
        args += " -m " + cmd.getVmMac();
        args += " -n " + cmd.getVmName();
        String result = callHostPlugin("saveDhcpEntry", "args", args);
        if (result == null || result.isEmpty()) {
            return new Answer(cmd, false, "DhcpEntry failed");
        }
        return new Answer(cmd);
    }

    protected Answer execute(final SavePasswordCommand cmd) {
        final String password = cmd.getPassword();
        final String routerPrivateIPAddress = cmd.getRouterPrivateIpAddress();
        final String vmName = cmd.getVmName();
        final String vmIpAddress = cmd.getVmIpAddress();
        final String local = vmName;

        // Run save_password_to_domr.sh
        String args = "-r " + routerPrivateIPAddress;
        args += " -v " + vmIpAddress;
        args += " -p " + password;
        args += " " + local;
        String result = callHostPlugin("savePassword", "args", args);

        if (result == null || result.isEmpty()) {
            return new Answer(cmd, false, "savePassword failed");
        }
        return new Answer(cmd);
    }

    private boolean assignPublicIpAddress(final String vmName, final String privateIpAddress, final String publicIpAddress, final boolean add, final boolean sourceNat,
            final String vlanId, final String vlanGateway, final String vlanNetmask) {
        String args;
        if (add) {
            args = "-A";
        } else {
            args = "-D";
        }
        if (sourceNat) {
            args += " -f";
        }
        args += " -i ";
        args += privateIpAddress;
        args += " -l ";
        args += publicIpAddress;
        args += " -r ";
        args += vmName;

        args += " -n ";
        args += vlanNetmask;

        if (vlanId != null) {
            args += " -v ";
            args += vlanId;
            args += " -g ";
            args += vlanGateway;
        }
        String result = callHostPlugin("ipassoc", "args", args);
        if (result == null || result.isEmpty()) {
            return false;
        }
        return true;

    }
    
    private String networkUsage(final String privateIpAddress, final String option) {
        String args = null;
        if (option.equals("get")) {
            args = "-g";
        } else if (option.equals("create")){
            args = "-c";
        } else if (option.equals("reset")){
            args = "-r";
        }
        args += " -i ";
        args += privateIpAddress;
        return callHostPlugin("networkUsage", "args", args);
    }

    protected Answer execute(final IPAssocCommand cmd) {
        final boolean status = assignPublicIpAddress(cmd.getRouterName(), cmd.getRouterIp(), cmd.getPublicIp(), cmd.isAdd(), cmd.isSourceNat(), cmd.getVlanId(), cmd
                .getVlanGateway(), cmd.getVlanNetmask());
        if (!status) {
            return new Answer(cmd, false, "IpassocCommand failed");
        }
        return new Answer(cmd);
    }

    protected GetVncPortAnswer execute(GetVncPortCommand cmd) {
        Connection conn = getConnection();

        // getVncPort() will be synchronized globally towards XAPI
        try {
            Set<VM> vms = VM.getByNameLabel(conn, cmd.getName());
            return new GetVncPortAnswer(cmd, getVncPort(vms.iterator().next()));
        } catch (XenAPIException e) {
            s_logger.warn("Unable to get vnc port " + e.toString(), e);
            return new GetVncPortAnswer(cmd, e.toString());
        } catch (Exception e) {
            s_logger.warn("Unable to get vnc port ", e);
            return new GetVncPortAnswer(cmd, e.getMessage());
        }
    }

    protected StorageResourceType getStorageResourceType() {
        return StorageResourceType.STORAGE_POOL;
    }

    protected CheckHealthAnswer execute(CheckHealthCommand cmd) {
        return new CheckHealthAnswer(cmd, true);
    }

    protected WatchNetworkAnswer execute(WatchNetworkCommand cmd) {
        WatchNetworkAnswer answer = new WatchNetworkAnswer(cmd);
        for(String domr : _domrIPMap.keySet()){
            long[] stats = getNetworkStats(domr);
            answer.addStats(domr, stats[0], stats[1]);
        }
        return answer;
    }
    
    private long[] getNetworkStats(String domr){
        String result = networkUsage(_domrIPMap.get(domr), "get");
        long[] stats = new long[2];
        if( result!=null ){
            String[] splitResult = result.split(":");
            if(splitResult.length > 1){
                stats[0] = (new Long(splitResult[0])).longValue();
                stats[1] = (new Long(splitResult[1])).longValue();
            }
        }
        return stats;
    }

    protected GetHostStatsAnswer execute(GetHostStatsCommand cmd) {
        Connection conn = getConnection();
        try {
            Host host = Host.getByUuid(conn, _hostUuid);
            Host.Record record = host.getRecord(conn);

            // Determine CPU utilisation
            Set<HostCpu> cpus = record.hostCPUs;
            double cpuUtilization = 0.0d;
            int count = 0;
            for (HostCpu cpu : cpus) {
                cpuUtilization += cpu.getUtilisation(conn);
                count++;
            }
            cpuUtilization = cpuUtilization / count;

            // Determine memory utilisation
            HostMetrics metrics = record.metrics;
            final long freeMemory = metrics.getMemoryFree(conn);
            final long totalMemory = metrics.getMemoryTotal(conn);

            return new GetHostStatsAnswer(cmd, cpuUtilization, freeMemory, totalMemory, 0, 0);
        } catch (XenAPIException e) {
            String msg = "Unable to get host stats" + e.toString();
            s_logger.warn(msg, e);
            return new GetHostStatsAnswer(cmd, msg);
        } catch (XmlRpcException e) {
            String msg = "Unable to get host stats" + e.getMessage();
            s_logger.warn(msg, e);
            return new GetHostStatsAnswer(cmd, msg);
        }
    }
    
    protected GetVmStatsAnswer execute(GetVmStatsCommand cmd) {
    	List<String> vmNames = cmd.getVmNames();
    	
    	Connection conn = getConnection();
    	try {
    		
    		// Determine the UUIDs of the requested VMs
    		List<String> vmUUIDs = new ArrayList<String>();
    		for (String vmName : vmNames) {
    			VM vm = getVM(conn, vmName);
    			vmUUIDs.add(vm.getUuid(conn));
    		}
    			
    		HashMap<String, VmStatsEntry> vmStatsUUIDMap = getVmStats(cmd, vmUUIDs);
    		HashMap<String, VmStatsEntry> vmStatsNameMap = new HashMap<String, VmStatsEntry>();
    		
    		for (String vmUUID : vmStatsUUIDMap.keySet()) {
    			vmStatsNameMap.put(vmNames.get(vmUUIDs.indexOf(vmUUID)), vmStatsUUIDMap.get(vmUUID));
    		}
    		
    		return new GetVmStatsAnswer(cmd, vmStatsNameMap);
    	} catch (XenAPIException e) {
            String msg = "Unable to get VM stats" + e.toString();
            s_logger.warn(msg, e);
            return new GetVmStatsAnswer(cmd, null);
        } catch (XmlRpcException e) {
            String msg = "Unable to get VM stats" + e.getMessage();
            s_logger.warn(msg, e);
            return new GetVmStatsAnswer(cmd, null);
        }
    }
    
    private GetHostStatsAnswer getHostStats(GetHostStatsCommand cmd, String publicNic) {
    	Object[] rrdData = getRRDData();
    	Integer numRows = (Integer) rrdData[0];
    	Integer numColumns = (Integer) rrdData[1];
    	Node legend = (Node) rrdData[2];
    	Node dataNode = (Node) rrdData[3];
    	
    	int numCPUs = 0;
    	double cpuUtilization = 0;
    	double networkReadKb = 0;
    	double networkWriteKb = 0;
    	double memoryTotalKb = 0;
    	double memoryFreeKb = 0;
    	NodeList legendChildren = legend.getChildNodes();
    	for (int col = 0; col < numColumns; col++) {
    		
    		if (legendChildren == null || legendChildren.item(col) == null) {
    			continue;
    		}
    		
    		String columnMetadata = getXMLNodeValue(legendChildren.item(col));
    		
    		if (columnMetadata == null) {
    			continue;
    		}
    		
    		String[] columnMetadataList = columnMetadata.split(":");
    		
    		if (columnMetadataList.length != 4) {
    			continue;
    		}
    		
    		String type = columnMetadataList[1];
    		String uuid = columnMetadataList[2];
    		String param = columnMetadataList[3];
    		
    		if (type.equals("host")) {
    			if (param.contains("cpu")) {
    				numCPUs += 1;
    				cpuUtilization += getDataAverage(dataNode, col, numRows);
    			} else if (param.equals("pif_" + publicNic + "_rx")) {
    				networkReadKb = getDataAverage(dataNode, col, numRows);
    			} else if (param.equals("pif_" + publicNic + "_tx")) {
    				networkWriteKb = getDataAverage(dataNode, col, numRows);
    			} else if (param.equals("memory_total_kib")) {
    				memoryTotalKb = getDataAverage(dataNode, col, numRows);
    			} else if (param.equals("memory_free_kib")) {
    				memoryFreeKb = getDataAverage(dataNode, col, numRows);
    			}
    		}
    		
    	}
    	
    	cpuUtilization = cpuUtilization / numCPUs;
    	
    	return new GetHostStatsAnswer(cmd, cpuUtilization, new Double(memoryFreeKb).longValue(), new Double(memoryTotalKb).longValue(), networkReadKb, networkWriteKb);
    }
    
    private HashMap<String, VmStatsEntry> getVmStats(GetVmStatsCommand cmd, List<String> vmUUIDs) {
    	HashMap<String, VmStatsEntry> vmResponseMap = new HashMap<String, VmStatsEntry>();
    	
    	for (String vmUUID : vmUUIDs) {
    		vmResponseMap.put(vmUUID, new VmStatsEntry(0, 0, 0, 0));
    	}
    	
    	Object[] rrdData = getRRDData();
    	Integer numRows = (Integer) rrdData[0];
    	Integer numColumns = (Integer) rrdData[1];
    	Node legend = (Node) rrdData[2];
    	Node dataNode = (Node) rrdData[3];
    	
    	NodeList legendChildren = legend.getChildNodes();
    	for (int col = 0; col < numColumns; col++) {
    		
    		if (legendChildren == null || legendChildren.item(col) == null) {
    			continue;
    		}
    		
    		String columnMetadata = getXMLNodeValue(legendChildren.item(col));
    		
    		if (columnMetadata == null) {
    			continue;
    		}
    		
    		String[] columnMetadataList = columnMetadata.split(":");
    		
    		if (columnMetadataList.length != 4) {
    			continue;
    		}
    		
    		String type = columnMetadataList[1];
    		String uuid = columnMetadataList[2];
    		String param = columnMetadataList[3];
    		
    		if (type.equals("vm") && vmResponseMap.keySet().contains(uuid)) {
    			VmStatsEntry vmStatsAnswer = vmResponseMap.get(uuid);
    			
    			if (param.contains("cpu")) {
    				vmStatsAnswer.setNumCPUs(vmStatsAnswer.getNumCPUs() + 1);
    				vmStatsAnswer.setCPUUtilization(vmStatsAnswer.getCPUUtilization() + getDataAverage(dataNode, col, numRows));
    			} else if (param.equals("vif_0_rx")) {
    				vmStatsAnswer.setNetworkReadKBs(vmStatsAnswer.getNetworkReadKBs() + getDataAverage(dataNode, col, numRows));
    			} else if (param.equals("vif_0_tx")) {
    				vmStatsAnswer.setNetworkWriteKBs(vmStatsAnswer.getNetworkWriteKBs() + getDataAverage(dataNode, col, numRows));
    			}
    		}
    		
    	}
    	
    	for (String vmUUID : vmResponseMap.keySet()) {
    		VmStatsEntry vmStatsAnswer = vmResponseMap.get(vmUUID);
    		vmStatsAnswer.setCPUUtilization(vmStatsAnswer.getCPUUtilization() / vmStatsAnswer.getNumCPUs());
    	}
    	
    	return vmResponseMap;
    }
    
    private Object[] getRRDData() {
    	String stats = getHostAndVmStatsRawXML();
    	StringReader statsReader = new StringReader(stats);
    	InputSource statsSource = new InputSource(statsReader);
    	
    	Document doc = null;
    	try {
    		doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(statsSource);
    	} catch (Exception e) {
    	}
    	
    	NodeList firstLevelChildren = doc.getChildNodes();
    	NodeList secondLevelChildren = (firstLevelChildren.item(0)).getChildNodes();
    	Node metaNode = secondLevelChildren.item(0);
    	Node dataNode = secondLevelChildren.item(1);
    	
    	Integer numRows = 0;
    	Integer numColumns = 0;
    	Node legend = null;
    	NodeList metaNodeChildren = metaNode.getChildNodes();
    	for (int i = 0; i < metaNodeChildren.getLength(); i++) {
    		Node n = metaNodeChildren.item(i);
    		if (n.getNodeName().equals("rows")) {
    			numRows = Integer.valueOf(getXMLNodeValue(n));
    		} else if (n.getNodeName().equals("columns")) {
    			numColumns = Integer.valueOf(getXMLNodeValue(n));
    		} else if (n.getNodeName().equals("legend")) {
    			legend = n;
    		}
    	}
    	
    	return new Object[]{numRows, numColumns, legend, dataNode};
    }
    
    private String getXMLNodeValue(Node n) {
    	return n.getChildNodes().item(0).getNodeValue();
    }
    
    private double getDataAverage(Node dataNode, int col, int numRows) {
    	double value = 0;
    	
    	int numRowsUsed = 0;
    	for (int row = 0; row < numRows; row ++) {
			Node data = dataNode.getChildNodes().item(numRows - 1 - row).getChildNodes().item(col + 1);
			Double currentDataAsDouble = Double.valueOf(getXMLNodeValue(data));
			if (!currentDataAsDouble.equals(Double.NaN)) {
				numRowsUsed +=1;
				value += currentDataAsDouble;
			}
		}
		
    	return (numRowsUsed == 0) ? value : (value / numRowsUsed);
    }
    
    private String getHostAndVmStatsRawXML() {
    	Date currentDate = new Date();
    	String startTime = String.valueOf(currentDate.getTime()/1000 - 1000);
    	
    	return callHostPlugin("gethostvmstats",
    						  "collectHostStats", String.valueOf(_collectHostStats),
    						  "consolidationFunction", _consolidationFunction,
    						  "interval", String.valueOf(_pollingIntervalInSeconds),
    						  "startTime", startTime);
    }
    
    private Map<VM, VM.Record> getAllVmsSlowly() {
        Connection conn = getConnection();
        final Map<VM, VM.Record> vmrecs = new HashMap<VM, VM.Record>();

        Set<VM> vms;
        try {
            vms = VM.getAll(conn);
        } catch (XenAPIException e) {
            String msg = "VM.getAll failed" + e.toString();
            s_logger.warn(msg, e);
            return null;
        } catch (XmlRpcException e) {
            String msg = "VM.getAll failed" + e.getMessage();
            s_logger.warn(msg, e);
            return null;
        }

        for (final VM vm : vms) {
            VM.Record record = null;
            int i = 0;
            while (i++ < 3) {
                try {
                    record = vm.getRecord(conn);
                    break;
                } catch (final BadServerResponse e) {
                    // There is a race condition within xen such that if a vm is
                    // deleted and we
                    // happen to ask for it, it throws this stupid response. So
                    // if this happens,
                    // we take a nap and try again which then avoids the race
                    // condition because
                    // the vm's information is now cleaned up by xen. The error
                    // is as follows
                    // com.xensource.xenapi.Types$BadServerResponse
                    // [HANDLE_INVALID, VM,
                    // 3dde93f9-c1df-55a7-2cde-55e1dce431ab]
                    s_logger.info("Unable to get a vm record due to " + e.toString() + ". We are retrying.  Count: " + i);
                    try {
                        Thread.sleep(3000);
                    } catch (final InterruptedException ex) {

                    }
                } catch (XenAPIException e) {
                    s_logger.warn("Unable to get vm record." + e.toString());
                    recordWarning(vm, "Unable to get vm record.", e);
                    break;
                } catch (final XmlRpcException e) {
                    s_logger.warn("Unable to get vm record." + e.getMessage());
                    recordWarning(vm, "Unable to get vm record.", e);
                    break;
                }

            }

            if (record == null) {
                return null;
            }
            vmrecs.put(vm, record);
        }

        return vmrecs;
    }

    protected void recordWarning(final VM vm, final String message, final Throwable e) {
        Connection conn = getConnection();
        final StringBuilder msg = new StringBuilder();
        try {
            final Long domId = vm.getDomid(conn);
            msg.append("[").append(domId != null ? domId : -1l).append("] ");
        } catch (final BadServerResponse e1) {
        } catch (final XmlRpcException e1) {
        } catch (XenAPIException e1) {
        }
        msg.append(message);
    }

    protected State convertToState(Types.VmPowerState ps) {
        final State state = s_statesTable.get(ps);
        return state == null ? State.Unknown : state;
    }

    protected HashMap<String, State> getAllVms() {
        final HashMap<String, State> vmStates = new HashMap<String, State>();
        Connection conn = getConnection();

        Map<VM, VM.Record> vms = null;
        try {
            vms = VM.getAllRecords(conn);
        } catch (final Throwable e) {
            s_logger.warn("Unable to get vms", e);
            vms = getAllVmsSlowly();
        }

        if (vms == null) {
            return null;
        }

        for (Map.Entry<VM, VM.Record> entry : vms.entrySet()) {
            VM.Record record = entry.getValue();

            if (record.isControlDomain || record.isASnapshot || record.isATemplate) {
                continue; // Skip DOM0
            }
            try {
                if (!isRefNull(record.residentOn) && !record.residentOn.getUuid(conn).equals(_hostUuid)) {
                    s_logger.trace("Skipping over " + record.nameLabel);
                    continue;
                }

                if (isRefNull(record.residentOn) && !isRefNull(record.affinity)) {
                    if (record.powerState != Types.VmPowerState.HALTED) {
                        s_logger.trace("Skipping over " + record.nameLabel);
                        continue;
                    } else if (!record.affinity.getUuid(conn).equals(_hostUuid)) {
                        s_logger.trace("Skipping over " + record.nameLabel);
                        continue;
                    }
                }
            } catch (XenAPIException e) {
                s_logger.debug("Skipping vm: " + record.nameLabel);
                continue;
            } catch (final XmlRpcException e) {
                s_logger.warn("Catch XmlRpcException due to " + e.getMessage(), e);
                continue;
            }

            VmPowerState ps = record.powerState;
            final State state = convertToState(ps);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("VM " + record.nameLabel + ": powerstate = " + ps + "; vm state=" + state.toString());
            }
            vmStates.put(record.nameLabel, state);
        }

        return vmStates;
    }

    protected State getVmState(final String vmName) {
        Connection conn = getConnection();
        int retry = 3;
        while (retry-- > 0) {
            try {
                Set<VM> vms = VM.getByNameLabel(conn, vmName);
                for (final VM vm : vms) {
                    return convertToState(vm.getPowerState(conn));
                }
            } catch (final BadServerResponse e) {
                // There is a race condition within xen such that if a vm is
                // deleted and we
                // happen to ask for it, it throws this stupid response. So
                // if this happens,
                // we take a nap and try again which then avoids the race
                // condition because
                // the vm's information is now cleaned up by xen. The error
                // is as follows
                // com.xensource.xenapi.Types$BadServerResponse
                // [HANDLE_INVALID, VM,
                // 3dde93f9-c1df-55a7-2cde-55e1dce431ab]
                s_logger.info("Unable to get a vm PowerState due to " + e.toString() + ". We are retrying.  Count: " + retry);
                try {
                    Thread.sleep(3000);
                } catch (final InterruptedException ex) {

                }
            } catch (XenAPIException e) {
                String msg = "Unable to get a vm PowerState due to " + e.toString();
                s_logger.warn(msg, e);
                break;
            } catch (final XmlRpcException e) {
                String msg = "Unable to get a vm PowerState due to " + e.getMessage();
                s_logger.warn(msg, e);
                break;
            }
        }

        return State.Stopped;
    }

    protected CheckVirtualMachineAnswer execute(final CheckVirtualMachineCommand cmd) {
        final String vmName = cmd.getVmName();
        final State state = getVmState(vmName);
        Integer vncPort = null;
        if (state == State.Running) {
            synchronized (_vms) {
                _vms.put(vmName, State.Running);
            }
        }

        return new CheckVirtualMachineAnswer(cmd, state, vncPort);
    }

    protected PrepareForMigrationAnswer execute(final PrepareForMigrationCommand cmd) {
        /*
         * final String vmName = cmd.getVmName();
         * 
         * String result = null;
         * 
         * List<VolumeVO> vols = cmd.getVolumes(); result = mountwithoutvdi(vols, cmd.getMappings()); if (result !=
         * null) { return new PrepareForMigrationAnswer(cmd, false, result); }
         */

        try {
            Connection conn = getConnection();
            Set<Host> hosts = Host.getAll(conn);
            // workaround before implementing xenserver pool
            // no migration
            if (hosts.size() <= 1) {
                return new PrepareForMigrationAnswer(cmd, false, "not in a same xenserver pool");
            }

            synchronized (_vms) {
                _vms.put(cmd.getVmName(), State.Migrating);
            }
            return new PrepareForMigrationAnswer(cmd, true, null);
        } catch (Exception e) {
            String msg = "catch exception " + e.getMessage();
            s_logger.warn(msg, e);
            return new PrepareForMigrationAnswer(cmd, false, msg);
        }
    }

    private Answer execute(final PrimaryStorageDownloadCommand cmd) {
        SR tmpltsr = null;
        String tmplturl = cmd.getUrl();
        int index = tmplturl.lastIndexOf("/");
        String mountpoint = tmplturl.substring(0, index);
        String tmpltname = null;
        if (index < tmplturl.length() -1)
        	tmpltname = tmplturl.substring(index + 1).replace(".vhd", "");
        try {
            Connection conn = getConnection();
            String pUuid = cmd.getPoolUuid();
            SR poolsr = null;
            Set<SR> srs = SR.getByNameLabel(conn, pUuid);
            if (srs.size() != 1) {
                String msg = "There are " + srs.size() + " SRs with same name: " + pUuid;
                s_logger.warn(msg);
                return new DownloadAnswer(null, 0, msg, com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOAD_ERROR, "", "", 0);
            } else {
                poolsr = srs.iterator().next();
            }

            /* Does the template exist in primary storage pool? If yes, no copy */
            VDI vmtmpltvdi = null;

            Set<VDI> vdis = VDI.getByNameLabel(conn, "Template " + cmd.getName());

            for (VDI vdi : vdis) {
                VDI.Record vdir = vdi.getRecord(conn);
                if (vdir.SR.equals(poolsr)) {
                    vmtmpltvdi = vdi;
                    break;
                }
            }
            String uuid;
            if (vmtmpltvdi == null) {
                tmpltsr = getNfsSRbyMountPoint(mountpoint, false);
                tmpltsr.scan(conn);
                VDI tmpltvdi = null;

                if (tmpltname != null) {
                	tmpltvdi = getVDIbyUuid(tmpltname);
                }
                if (tmpltvdi == null) {
                	vdis = tmpltsr.getVDIs(conn);
                	for (VDI vdi: vdis) {
                		tmpltvdi = vdi;
                		break;
                	}
                }
                if (tmpltvdi == null) {
                	 String msg = "Unable to find template vdi on secondary storage" + "host:" + _hostUuid + "pool: " + tmplturl;
                     s_logger.warn(msg);
                     return new DownloadAnswer(null, 0, msg, com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOAD_ERROR, "", "", 0);
                }
                vmtmpltvdi = tmpltvdi.copy(conn, poolsr);

                vmtmpltvdi.setNameLabel(conn, "Template " + cmd.getName());
                // vmtmpltvdi.setNameDescription(conn, cmd.getDescription());
                uuid = vmtmpltvdi.getUuid(conn);

            } else
                uuid = vmtmpltvdi.getUuid(conn);

            // Determine the size of the template
            long createdSize = vmtmpltvdi.getVirtualSize(conn);

            DownloadAnswer answer = new DownloadAnswer(null, 100, cmd, com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOADED, uuid, uuid);
            answer.setTemplateSize(createdSize);

            return answer;

        } catch (XenAPIException e) {
            String msg = "XenAPIException:" + e.toString() + "host:" + _hostUuid + "pool: " + tmplturl;
            s_logger.warn(msg, e);
            return new DownloadAnswer(null, 0, msg, com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOAD_ERROR, "", "", 0);
        } catch (Exception e) {
            String msg = "XenAPIException:" + e.getMessage() + "host:" + _hostUuid + "pool: " + tmplturl;
            s_logger.warn(msg, e);
            return new DownloadAnswer(null, 0, msg, com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOAD_ERROR, "", "", 0);
        } finally {
            if (tmpltsr != null) {
                try {
                    removeSR(tmpltsr);
                } catch (Exception e) {
                    s_logger.debug(logX(tmpltsr, "Unable to remove SR "));
                }
            }
        }

    }

    protected void removeSR(SR sr) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug(logX(sr, "Removing SR"));
        }
        Connection conn = getConnection();
        try {
            Set<PBD> pbds = sr.getPBDs(conn);
            for (PBD pbd : pbds) {
                if (pbd.getHost(conn).getUuid(conn).equals(_hostUuid)) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug(logX(pbd, "Unplugging pbd"));
                    }
                    pbd.unplug(conn);
                    pbd.destroy(conn);
                }
            }

            pbds = sr.getPBDs(conn);
            if (pbds.size() == 0) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug(logX(sr, "Forgetting"));
                }
                sr.forget(conn);
                return;
            }

            if (s_logger.isDebugEnabled()) {
                s_logger.debug(logX(sr, "There are still pbd attached"));
                if (s_logger.isTraceEnabled()) {
                    for (PBD pbd : pbds) {
                        s_logger.trace(logX(pbd, " Still attached"));
                    }
                }
            }
        } catch (XenAPIException e) {
            s_logger.warn(logX(sr, "Unable to remove SR "), e);
            throw new VmopsRuntimeException(logX(sr, "Unable to remove SR "), e);
        } catch (XmlRpcException e) {
            throw new VmopsRuntimeException(logX(sr, "Unable to remove SR "), e);
        }
    }

    protected MigrateAnswer execute(final MigrateCommand cmd) {
        final String vmName = cmd.getVmName();
        State state = null;

        synchronized (_vms) {
            state = _vms.get(vmName);
            _vms.put(vmName, State.Stopping);
        }
        try {
            Connection conn = getConnection();
            Set<VM> vms = VM.getByNameLabel(conn, vmName);

            String ipaddr = cmd.getDestinationIp();

            Set<Host> hosts = Host.getAll(conn);
            Host dsthost = null;
            for (Host host : hosts) {
                if (host.getAddress(conn).equals(ipaddr)) {
                    dsthost = host;
                    break;
                }
            }
            for (VM vm : vms) {
                String uuid = vm.getUuid(conn);
                String result = callHostPlugin("preparemigration", "uuid", uuid);
                if (result == null || result.isEmpty()) {
                    return new MigrateAnswer(cmd, false, "migration failed", null);
                }
                final Map<String, String> options = new HashMap<String, String>();
                vm.poolMigrate(conn, dsthost, options);

            }
            return new MigrateAnswer(cmd, true, "migration succeeded", null);
        } catch (XenAPIException e) {
            String msg = "migration failed due to " + e.toString();
            s_logger.warn(msg, e);
            return new MigrateAnswer(cmd, false, msg, null);
        } catch (XmlRpcException e) {
            String msg = "migration failed due to " + e.getMessage();
            s_logger.warn(msg, e);
            return new MigrateAnswer(cmd, false, msg, null);
        } finally {
            synchronized (_vms) {
                _vms.put(vmName, state);
            }
        }

    }

    protected State getRealPowerState(String label) {
        Connection conn = getConnection();
        int i = 0;
        s_logger.trace("Checking on the HALTED State");
        for (; i < 20; i++) {
            try {
                Set<VM> vms = VM.getByNameLabel(conn, label);
                if (vms == null || vms.size() == 0) {
                    continue;
                }

                VM vm = vms.iterator().next();

                VmPowerState vps = vm.getPowerState(conn);
                if (vps != null && vps != VmPowerState.HALTED && vps != VmPowerState.UNKNOWN && vps != VmPowerState.UNRECOGNIZED) {
                    return convertToState(vps);
                }
            } catch (XenAPIException e) {
                String msg = "Unable to get real power state due to " + e.toString();
                s_logger.warn(msg, e);
            } catch (XmlRpcException e) {
                String msg = "Unable to get real power state due to " + e.getMessage();
                s_logger.warn(msg, e);
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
        return State.Stopped;
    }

    protected VM.Record getControlDomain() {
        Connection conn = getConnection();
        Map<VM, VM.Record> vms = null;
        try {
            vms = VM.getAllRecords(conn);
        } catch (final Throwable e) {
            s_logger.warn("Unable to get vms", e);
            throw new VmopsRuntimeException("Unable to get vms", e);
        }

        for (final VM.Record record : vms.values()) {
            if (record.isControlDomain) {
                return record;
            }
        }

        return null;
    }

    private HashMap<String, State> sync() {
        HashMap<String, State> newStates;
        HashMap<String, State> oldStates = null;

        final HashMap<String, State> changes = new HashMap<String, State>();

        synchronized (_vms) {
            newStates = getAllVms();
            if (newStates == null) {
                s_logger.debug("Unable to get the vm states so no state sync at this point.");
                return null;
            }

            oldStates = new HashMap<String, State>(_vms.size());
            oldStates.putAll(_vms);

            for (final Map.Entry<String, State> entry : newStates.entrySet()) {
                final String vm = entry.getKey();

                State newState = entry.getValue();
                final State oldState = oldStates.remove(vm);

                if (newState == State.Stopped && oldState != State.Stopping && oldState != null && oldState != State.Stopped) {
                    newState = getRealPowerState(vm);
                }

                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("VM " + vm + ": xen has state " + newState + " and we have state " + (oldState != null ? oldState.toString() : "null"));
                }

                if (vm.startsWith("migrating")) {
                    s_logger.debug("Migrating from xen detected.  Skipping");
                    continue;
                }
                if (oldState == null) {
                    _vms.put(vm, newState);
                    s_logger.debug("Detecting a new state but couldn't find a old state so adding it to the changes: " + vm);
                    changes.put(vm, newState);
                } else if (oldState == State.Starting) {
                    if (newState == State.Running) {
                        _vms.put(vm, newState);
                    } else if (newState == State.Stopped) {
                        s_logger.debug("Ignoring vm " + vm + " because of a lag in starting the vm.");
                    }
                } else if (oldState == State.Migrating) {
                    if (newState == State.Running) {
                        s_logger.debug("Detected that an migrating VM is now running: " + vm);
                        _vms.put(vm, newState);
                    }
                } else if (oldState == State.Stopping) {
                    if (newState == State.Stopped) {
                        _vms.put(vm, newState);
                    } else if (newState == State.Running) {
                        s_logger.debug("Ignoring vm " + vm + " because of a lag in stopping the vm. ");
                    }
                } else if (oldState != newState) {
                    _vms.put(vm, newState);
                    if (newState == State.Stopped) {
                        /*
                         * if (_vmsKilled.remove(vm)) { s_logger.debug("VM " + vm + " has been killed for storage. ");
                         * newState = State.Error; }
                         */
                    }
                    changes.put(vm, newState);
                }
            }

            for (final Map.Entry<String, State> entry : oldStates.entrySet()) {
                final String vm = entry.getKey();
                final State oldState = entry.getValue();

                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("VM " + vm + " is now missing from xen so reporting stopped");
                }

                if (oldState == State.Stopping) {
                    s_logger.debug("Ignoring VM " + vm + " in transition state stopping.");
                    _vms.remove(vm);
                } else if (oldState == State.Starting) {
                    s_logger.debug("Ignoring VM " + vm + " in transition state starting.");
                } else if (oldState == State.Stopped) {
                    _vms.remove(vm);
                } else if (oldState == State.Migrating) {
                    s_logger.debug("Ignoring VM " + vm + " in migrating state.");
                } else {
                    State state = State.Stopped;
                    /*
                     * if (_vmsKilled.remove(entry.getKey())) { s_logger.debug("VM " + vm +
                     * " has been killed by storage monitor"); state = State.Error; }
                     */
                    changes.put(entry.getKey(), state);
                }
            }
        }

        return changes;
    }

    protected Answer execute(ModifyVlanCommand cmd) {
        try {
            if ("untagged".equalsIgnoreCase(cmd.getVlanId())) {
                return new Answer(cmd, true, null);
            }
            createVlanNetwork(Long.parseLong(cmd.getVlanId()), _publicNic);
        } catch (XenAPIException e) {
            String msg = "Unable to provision vlan " + cmd.getVlanId() + " due to " + e.toString();
            s_logger.warn(msg, e);
            return new Answer(cmd, false, msg);
        } catch (XmlRpcException e) {
            String msg = "Unable to provision vlan " + cmd.getVlanId() + " due to " + e.getMessage();
            s_logger.warn(msg, e);
            return new Answer(cmd, false, msg);
        }
        return new Answer(cmd, true, null);
    }

    protected ReadyAnswer execute(ReadyCommand cmd) {
        return new ReadyAnswer(cmd);
    }

    //
    // using synchronized on VM name in the caller does not prevent multiple commands being sent against
    // the same VM, there will be a race condition here in finally clause and the main block if
    // there are multiple requests going on
    //
    // Therefore, a lazy solution is to add a synchronized guard here
    protected int getVncPort(VM vm) {
        Connection conn = getConnection();

        VM.Record record;
        try {
            record = vm.getRecord(conn);
        } catch (XenAPIException e) {
            String msg = "Unable to get vnc-port due to " + e.toString();
            s_logger.warn(msg, e);
            return -1;
        } catch (XmlRpcException e) {
            String msg = "Unable to get vnc-port due to " + e.getMessage();
            s_logger.warn(msg, e);
            return -1;
        }
        String hvm = "true";
        if (record.HVMBootPolicy.isEmpty()) {
            hvm = "false";
        }

        String vncport = callHostPlugin("getvncport", "domID", record.domid.toString(), "hvm", hvm);
        if (vncport == null || vncport.isEmpty()) {
            return -1;
        }

        vncport = vncport.replace("\n", "");
        return NumbersUtil.parseInt(vncport, -1);
    }

    protected Answer execute(final RebootCommand cmd) {

        synchronized (_vms) {
            _vms.put(cmd.getVmName(), State.Starting);
        }

        try {
            Connection conn = getConnection();
            Set<VM> vms = null;
            try {
                vms = VM.getByNameLabel(conn, cmd.getVmName());
            } catch (XenAPIException e0) {
                s_logger.debug("getByNameLabel failed " + e0.toString());
                return new RebootAnswer(cmd, "getByNameLabel failed " + e0.toString());
            } catch (Exception e0) {
                s_logger.debug("getByNameLabel failed " + e0.getMessage());
                return new RebootAnswer(cmd, "getByNameLabel failed");
            }
            for (VM vm : vms) {
                try {
                    vm.cleanReboot(conn);
                } catch (XenAPIException e) {
                    s_logger.debug("Do Not support Clean Reboot, fall back to hard Reboot: " + e.toString());
                    try {
                        vm.hardReboot(conn);
                    } catch (XenAPIException e1) {
                        s_logger.debug("Caught exception on hard Reboot " + e1.toString());
                        return new RebootAnswer(cmd, "reboot failed: " + e1.toString());
                    } catch (XmlRpcException e1) {
                        s_logger.debug("Caught exception on hard Reboot " + e1.getMessage());
                        return new RebootAnswer(cmd, "reboot failed");
                    }
                } catch (XmlRpcException e) {
                    String msg = "Clean Reboot failed due to " + e.getMessage();
                    s_logger.warn(msg, e);
                    return new RebootAnswer(cmd, msg);
                }
            }
            return new RebootAnswer(cmd, "reboot succeeded", null, null);
        } finally {
            synchronized (_vms) {
                _vms.put(cmd.getVmName(), State.Running);
            }
        }
    }

    protected Answer execute(RebootRouterCommand cmd) {
        Long bytesSent = 0L;
        Long bytesRcvd = 0L;
        if(VirtualMachineName.isValidRouterName(cmd.getVmName())){
            long[] stats = getNetworkStats(cmd.getVmName());
            bytesSent = stats[0];
            bytesRcvd = stats[1];
        }
        RebootAnswer answer = (RebootAnswer)execute((RebootCommand) cmd);
        answer.setBytesSent(bytesSent);
        answer.setBytesReceived(bytesRcvd);
        if (answer.getResult()) {
            String cnct = connect(cmd.getVmName(), cmd.getPrivateIpAddress());
            networkUsage(cmd.getPrivateIpAddress(), "create");
            if (cnct == null) {
                synchronized (_domrIPMap) {
                    _domrIPMap.put(cmd.getVmName(),cmd.getPrivateIpAddress());
                }
                return answer;
            } else {
                return new Answer(cmd, false, cnct);
            }
        }
        return answer;
    }

    private VM getVMTemplate(Connection conn, StartCommand cmd) throws XenAPIException, XmlRpcException {
        Set<VM> templates;
        VM vm = null;
        String guestOsTypeName = cmd.getGuestOSDescription();
        templates = VM.getByNameLabel(conn, guestOsTypeName);
        assert templates.size() == 1 : "Should only have 1 template but found " + templates.size();
        VM template = templates.iterator().next();
        vm = template.createClone(conn, cmd.getVmName());
        vm.removeFromOtherConfig(conn, "disks");

        if (!(guestOsTypeName.startsWith("Windows") || guestOsTypeName.startsWith("Ctrix") || guestOsTypeName.startsWith("Other"))) {
            if (cmd.getBootFromISO())
                vm.setPVBootloader(conn, "eliloader");
            else
                vm.setPVBootloader(conn, "pygrub");

            vm.addToOtherConfig(conn, "install-repository", "cdrom");
        }
        return vm;
    }

    public boolean joinPool(String address, String username, String password) {
        Connection conn = getConnection();
        try {
            // set the _poolUuid to the old pool uuid in case it's not set.
            _poolUuid = getPoolUuid();
            
            // Connect and find out about the new connection to the new pool.
            Connection poolConn = _connPool.connect(address, username, password, _wait);
            Map<Pool, Pool.Record> pools = Pool.getAllRecords(poolConn);
            Pool.Record pr = pools.values().iterator().next();
            
            // Now join it.
            String masterAddr = pr.master.getAddress(poolConn);
            Pool.join(conn, masterAddr, username, password);
            disconnected();
            
            // Set the pool uuid now to the newest pool.
            _poolUuid = pr.uuid;
            
            return true;
        } catch (JoiningHostCannotContainSharedSrs e) {
            s_logger.warn("Unable to allow host " + _hostUuid + " to join pool " + address, e);
            return false;
        } catch (XenAPIException e) {
            s_logger.warn("Unable to allow host " + _hostUuid + " to join pool " + address, e);
            return false;
        } catch (XmlRpcException e) {
            s_logger.warn("Unable to allow host " + _hostUuid + " to join pool " + address, e);
            return false;
        }
    }

    public boolean leavePool() {
        Connection conn = getConnection();
        try {
            Host host = Host.getByUuid(conn, _hostUuid);
            Pool.eject(conn, host);
            return true;
        } catch (XenAPIException e) {
            s_logger.warn("Unable to eject host " + _hostUuid, e);
            return false;
        } catch (XmlRpcException e) {
            s_logger.warn("Unable to eject host " + _hostUuid, e);
            return false;
        }
    }

    protected StartAnswer execute(StartCommand cmd) {
        State state = State.Stopped;
        Connection conn = getConnection();
        VM vm = null;
        try {
            synchronized (_vms) {
                _vms.put(cmd.getVmName(), State.Starting);
            }

            List<VolumeVO> vols = cmd.getVolumes();

            List<Ternary<SR, VDI, VolumeVO>> mounts = null;
            mounts = mount(vols);

            vm = getVMTemplate(conn, cmd);

            long memsize = cmd.getRamSize() * 1024L * 1024L;
            vm.setMemoryDynamicMax(conn, memsize);
            vm.setMemoryDynamicMin(conn, memsize);

            vm.setMemoryStaticMax(conn, memsize);
            vm.setMemoryStaticMin(conn, memsize);

            vm.setVCPUsAtStartup(conn, (long) cmd.getCpu());
            vm.setVCPUsMax(conn, (long) cmd.getCpu());

            Host host = Host.getByUuid(conn, _hostUuid);
            vm.setAffinity(conn, host);

            Map<String, String> vcpuparam = new HashMap<String, String>();

            vcpuparam.put("weight", Integer.toString(cmd.getCpuWeight()));
            vcpuparam.put("cap", Integer.toString(cmd.getUtilization()));
            vm.setVCPUsParams(conn, vcpuparam);

            vm.setIsATemplate(conn, false);

            boolean bootFromISO = cmd.getBootFromISO();

            /* create root VBD */
            VBD.Record vbdr = new VBD.Record();
            Ternary<SR, VDI, VolumeVO> mount = mounts.get(0);
            vbdr.VM = vm;
            vbdr.VDI = mount.second();
            vbdr.bootable = !bootFromISO;
            vbdr.userdevice = "0";
            vbdr.mode = Types.VbdMode.RW;
            vbdr.type = Types.VbdType.DISK;
            VBD.create(conn, vbdr);

            /* determine available slots to attach data volumes to */
            List<String> availableSlots = new ArrayList<String>();
            availableSlots.add("1");
            availableSlots.add("2");
            availableSlots.add("4");
            availableSlots.add("5");
            availableSlots.add("6");
            availableSlots.add("7");

            /* create data VBDs */
            for (int i = 1; i < mounts.size(); i++) {
                String userDevice = availableSlots.get(0);
                mount = mounts.get(i);
                VDI vdi = mount.second();
                vdi.setNameLabel(conn, cmd.getVmName() + "-DATA");
                vbdr.VM = vm;
                vbdr.VDI = mount.second();
                vbdr.bootable = false;
                vbdr.userdevice = userDevice;
                vbdr.mode = Types.VbdMode.RW;
                vbdr.type = Types.VbdType.DISK;
                vbdr.unpluggable = true;
                VBD.create(conn, vbdr);
                availableSlots.remove(userDevice);
            }

            /* create CD-ROM VBD */
            VBD.Record cdromVBDR = new VBD.Record();
            cdromVBDR.VM = vm;
            cdromVBDR.empty = true;
            cdromVBDR.bootable = bootFromISO;
            cdromVBDR.userdevice = "3";
            cdromVBDR.mode = Types.VbdMode.RO;
            cdromVBDR.type = Types.VbdType.CD;
            VBD cdromVBD = VBD.create(conn, cdromVBDR);

            /* insert the ISO VDI if isoPath is not null */
            String isopath = cmd.getISOPath();
            if (isopath != null) {
                int index = isopath.lastIndexOf("/");

                String mountpoint = isopath.substring(0, index);

                SR isosr = getIsoSRbyMountPoint(mountpoint, true);

                isosr.scan(conn);

                String isoname = isopath.substring(index + 1);

                VDI isovdi = getVDIbyLocationandSR(isoname, isosr);

                if (isovdi == null) {
                    String msg = " can not find ISO " + cmd.getISOPath();
                    s_logger.warn(msg);
                    return new StartAnswer(cmd, msg);
                } else {
                    cdromVBD.insert(conn, isovdi);
                }

            }

            createVIF(conn, vm, cmd.getGuestMacAddress(), cmd.getGuestNetworkId(), "0");

            if (cmd.getExternalMacAddress() != null && cmd.getExternalVlan() != null) {
                createVIF(conn, vm, cmd.getExternalMacAddress(), cmd.getExternalVlan(), "1");
            }

            /* set action after crash as destroy */
            vm.setActionsAfterCrash(conn, Types.OnCrashBehaviour.DESTROY);

            vm.start(conn, false, true);

            state = State.Running;
            return new StartAnswer(cmd);

        } catch (Exception e) {
            String msg = "Exception caught while starting VM due to message:" + e.getMessage()
            + " String:" + e.toString();
            s_logger.warn(msg, e);
            if( vm != null) {
                try {
                    vm.hardShutdown(conn);
                } catch (Exception e1) {
                    
                }
            }
            return new StartAnswer(cmd, msg);
        } finally {
            try {
                if (vm != null && vm.getPowerState(conn) == VmPowerState.HALTED) {
                    vm.destroy(conn);
                    state = State.Stopped;
                }
            } catch (Exception e1) {
                String msg = "VM destroy failed due to " + e1.toString();
                s_logger.warn(msg, e1);
            }
            synchronized (_vms) {
                _vms.put(cmd.getVmName(), state);
            }

        }
    }

    private void createVIF(Connection conn, VM vm, String mac, String vlanTag, String devNum) throws BadServerResponse, XenAPIException, XmlRpcException {
        VIF.Record vifr = new VIF.Record();
        vifr.VM = vm;
        vifr.device = devNum;
        vifr.MAC = mac;
        vifr.network = createVlanNetwork(Long.parseLong(vlanTag), _privateNic);

        VIF.create(conn, vifr);
    }

    protected StopAnswer execute(final StopCommand cmd) {
        String vmName = cmd.getVmName();
        try {
            Connection conn = getConnection();

            Set<VM> vms = VM.getByNameLabel(conn, vmName);

            if (vms.size() == 0) {
                s_logger.warn("VM does not exist on XenServer" + _hostUuid);
                synchronized(_vms) {
                    _vms.remove(vmName);
                }
                return new StopAnswer(cmd, "VM does not exist", 0, 0L, 0L);
            }
            Long bytesSent = 0L;
            Long bytesRcvd = 0L;
            for (VM vm : vms) {

                if (vm.getIsControlDomain(conn)) {
                    s_logger.warn("Tring to Shutdown control domain");
                    throw new VmopsRuntimeException("Tring to Shutdown control domain");
                }
                State state = null;
                synchronized (_vms) {
                    state = _vms.get(vmName);
                    _vms.put(vmName, State.Stopping);
                }
                try {
                    if (vm.getPowerState(conn) == VmPowerState.RUNNING && vm.getResidentOn(conn).getUuid(conn).equals(_hostUuid)) {
                        /* when stop a vm, set affinity to current xenserver */
                        vm.setAffinity(conn, vm.getResidentOn(conn));
                        try {
                            if(VirtualMachineName.isValidRouterName(vmName)){
                                long[] stats = getNetworkStats(vmName);
                                bytesSent = stats[0];
                                bytesRcvd = stats[1];
                            }
                            vm.cleanShutdown(conn);
                        } catch (XenAPIException e) {
                            s_logger.debug("Do Not support Clean Shutdown, fall back to hard Shutdown: " + e.toString());
                            try {
                                vm.hardShutdown(conn);
                            } catch (XenAPIException e1) {
                                String msg = "Hard Shutdown failed due to " + e1.toString();
                                s_logger.warn(msg, e1);
                                return new StopAnswer(cmd, msg);
                            } catch (XmlRpcException e1) {
                                String msg = "Hard Shutdown failed due to " + e1.getMessage();
                                s_logger.warn(msg, e1);
                                return new StopAnswer(cmd, msg);
                            }
                        } catch (XmlRpcException e) {
                            String msg = "Clean Shutdown failed due to " + e.getMessage();
                            s_logger.warn(msg, e);
                            return new StopAnswer(cmd, msg);
                        }
                    }
                } catch (Exception e) {
                    String msg = "Catch exception " + e.getClass().toString() + " when stop VM:" + cmd.getVmName();
                    s_logger.debug(msg);
                } finally {

                    try {
                        if (vm.getPowerState(conn) == VmPowerState.HALTED) {
                            vm.destroy(conn);
                            state = State.Stopped;
                            if(VirtualMachineName.isValidRouterName(vmName)){
                                synchronized (_domrIPMap) {
                                    _domrIPMap.remove(vmName);
                                }
                            }
                        }
                    } catch (XmlRpcException e) {
                        String msg = "VM destroy failed in Stop Command due to " + e.getMessage();
                        s_logger.warn(msg, e);
                    } catch (XenAPIException e) {
                        String msg = "VM destroy failed in Stop Command due to " + e.toString();
                        s_logger.warn(msg, e);
                    } finally {
                        synchronized (_vms) {
                            _vms.put(vmName, state);
                        }
                    }
                }
            }
            return new StopAnswer(cmd, "Stop VM " + cmd.getVmName() + " Succeed", 0, bytesSent, bytesRcvd);
        } catch (XenAPIException e) {
            String msg = "Stop Vm " + cmd.getVmName() + " fail due to " + e.toString();
            s_logger.warn(msg, e);
            return new StopAnswer(cmd, msg);
        } catch (XmlRpcException e) {
            String msg = "Stop Vm " + cmd.getVmName() + " fail due to " + e.getMessage();
            s_logger.warn(msg, e);
            return new StopAnswer(cmd, msg);
        }
    }

    protected String connect(final String vmName, final String ipAddress, final int port) {
        for (int i = 0; i <= _retry; i++) {
            try {
                Connection conn = getConnection();

                Set<VM> vms = VM.getByNameLabel(conn, vmName);
                if( vms.size() < 1 ) {
                    String msg = "VM " + vmName + " is not running";
                    s_logger.warn(msg);
                    return msg;
                }
            } catch ( Exception e ) {
                String msg = "VM.getByNameLabel " + vmName + " failed due to " + e.toString();
                s_logger.warn(msg, e);
                return msg;
            }
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Trying to connect to " + ipAddress);
            }
            if (pingdomr(ipAddress, Integer.toString(port)))
                return null;
            try {
                Thread.sleep(_sleep);
            } catch (final InterruptedException e) {
            }
        }

        s_logger.debug("Unable to logon to " + ipAddress);

        return "Unable to connect";
    }

    protected String connect(final String vmname, final String ipAddress) {
        return connect(vmname, ipAddress, 3922);
    }

    protected StartRouterAnswer execute(StartRouterCommand cmd) {
        VM vm = null;
        Connection conn = getConnection();
        State state = State.Stopped;
        try {
            synchronized (_vms) {
                _vms.put(cmd.getVmName(), State.Starting);
            }

            List<VolumeVO> vols = cmd.getVolumes();

            final DomainRouter router = cmd.getRouter();
            List<Ternary<SR, VDI, VolumeVO>> mounts = null;

            mounts = mount(vols);

            assert mounts.size() == 1 : "Routers should have only 1 partition but we actuall have " + mounts.size();

            Ternary<SR, VDI, VolumeVO> mount = mounts.get(0);

            if (!patchDomr(mount.second(), cmd.getVmName())) {
                String msg = "patch domr failed";
                s_logger.warn(msg);
                return new StartRouterAnswer(cmd, msg);
            }

            Set<VM> templates = VM.getByNameLabel(conn, "CentOS 5.3");
            assert templates.size() == 1 : "Should only have 1 template but found " + templates.size();

            VM template = templates.iterator().next();

            vm = template.createClone(conn, cmd.getVmName());

            vm.removeFromOtherConfig(conn, "disks");

            vm.setPVBootloader(conn, "pygrub");

            long memsize = router.getRamSize() * 1024L * 1024L;
            vm.setMemoryDynamicMax(conn, memsize);
            vm.setMemoryDynamicMin(conn, memsize);

            vm.setMemoryStaticMax(conn, memsize);
            vm.setMemoryStaticMin(conn, memsize);

            vm.setVCPUsAtStartup(conn, 1L);

            Host host = Host.getByUuid(conn, _hostUuid);
            vm.setAffinity(conn, host);

            vm.setIsATemplate(conn, false);
            /* create VBD */
            VBD.Record vbdr = new VBD.Record();

            vbdr.VM = vm;
            vbdr.VDI = mount.second();
            vbdr.bootable = true;
            vbdr.userdevice = "0";
            vbdr.mode = Types.VbdMode.RW;
            vbdr.type = Types.VbdType.DISK;

            VBD.create(conn, vbdr);

            /* create VIF0 */
            VIF.Record vifr = new VIF.Record();

            vifr.VM = vm;
            vifr.device = "0";
            vifr.MAC = router.getGuestMacAddress();

            String tag = router.getVnet();

            vifr.network = createVlanNetwork(Long.parseLong(tag), _privateNic);

            VIF.create(conn, vifr);

            /* create VIF1 */
            vifr.VM = vm;
            vifr.device = "1";
            vifr.MAC = router.getPrivateMacAddress();
            vifr.network = getNetwork(_privateNic);

            VIF.create(conn, vifr);

            /* create VIF2 */

            vifr.VM = vm;
            vifr.device = "2";
            vifr.MAC = router.getPublicMacAddress();

            if ("untagged".equalsIgnoreCase(router.getVlanId())) {
                vifr.network = getNetwork(_publicNic);
            } else {
                vifr.network = createVlanNetwork(Long.parseLong(router.getVlanId()), _publicNic);
            }

            VIF.create(conn, vifr);

            /* set up PV dom argument */
            String pvargs = vm.getPVArgs(conn);
            pvargs = pvargs + cmd.getBootArgs();
            s_logger.debug("PV args for router are " + pvargs);
            vm.setPVArgs(conn, pvargs);

            /* destroy domr console */
            Set<Console> consoles = vm.getRecord(conn).consoles;

            for (Console console : consoles) {
                console.destroy(conn);
            }

            /* set action after crash as destroy */
            vm.setActionsAfterCrash(conn, Types.OnCrashBehaviour.DESTROY);

            vm.start(conn, false, true);

            String result = connect(cmd.getVmName(), router.getPrivateIpAddress());
            if (result != null) {
                s_logger.warn(" can not ping domr");
            }
            state = State.Running;
            networkUsage(router.getPrivateIpAddress(), "create");
            synchronized (_domrIPMap) {
                _domrIPMap.put(cmd.getVmName(), router.getPrivateIpAddress());
            }
            return new StartRouterAnswer(cmd);

        } catch (XenAPIException e) {
            String msg = "Exception caught while starting router: " + e.toString();
            s_logger.warn(msg, e);
            return new StartRouterAnswer(cmd, msg);
        } catch (Exception e) {
            String msg = "Exception caught while starting router: " + e.getMessage();
            s_logger.warn(msg, e);
            return new StartRouterAnswer(cmd, msg);
        } finally {
            try {
                if (vm != null && vm.getPowerState(conn) == VmPowerState.HALTED) {
                    vm.destroy(conn);
                    state = State.Stopped;
                }
            } catch (XmlRpcException e1) {
                String msg = "VM destroy failed due to " + e1.getMessage();
                s_logger.warn(msg, e1);
            } catch (XenAPIException e1) {
                String msg = "VM destroy failed due to " + e1.toString();
                s_logger.warn(msg, e1);
            }
            synchronized (_vms) {
                _vms.put(cmd.getVmName(), state);
            }
        }
    }
    
    protected String startSystemVM(String vmName, String vlanId, List<VolumeVO> vols, String bootArgs, String privateIp, String privateMacAddr, String publicIp, String publicMacAddr, int cmdPort, long ramSize) {

        VM vm = null;
        Connection conn = getConnection();
        State state = State.Stopped;
        try {
            synchronized (_vms) {
                _vms.put(vmName, State.Starting);
            }

            List<Ternary<SR, VDI, VolumeVO>> mounts = null;
            mounts = mount(vols);

            assert mounts.size() == 1 : "System VMs should have only 1 partition but we actually have " + mounts.size();

            Ternary<SR, VDI, VolumeVO> mount = mounts.get(0);

            if (!patchDomp(mount.second(), vmName)) { //FIXME make this nonspecific
                String msg = "patch system vm failed";
                s_logger.warn(msg);
                return  msg;
            }

            Set<VM> templates = VM.getByNameLabel(conn, "CentOS 5.3");
            assert templates.size() == 1 : "Should only have 1 template but found " + templates.size();

            VM template = templates.iterator().next();

            vm = template.createClone(conn, vmName);

            vm.removeFromOtherConfig(conn, "disks");

            vm.setPVBootloader(conn, "pygrub");

            long memsize = ramSize * 1024L * 1024L;
            vm.setMemoryDynamicMax(conn, memsize);
            vm.setMemoryDynamicMin(conn, memsize);

            vm.setMemoryStaticMax(conn, memsize);
            vm.setMemoryStaticMin(conn, memsize);

            vm.setVCPUsAtStartup(conn, 1L);

            Host host = Host.getByUuid(conn, _hostUuid);
            vm.setAffinity(conn, host);

            vm.setIsATemplate(conn, false);
            /* create VBD */
            VBD.Record vbdr = new VBD.Record();

            vbdr.VM = vm;
            vbdr.VDI = mount.second();
            vbdr.bootable = true;
            vbdr.userdevice = "0";
            vbdr.mode = Types.VbdMode.RW;
            vbdr.type = Types.VbdType.DISK;

            VBD.create(conn, vbdr);

            VIF.Record vifr = new VIF.Record();

            vifr.VM = vm;
            vifr.device = "0";
            vifr.MAC = privateMacAddr;
            vifr.network = getNetwork(_privateNic);
            VIF.create(conn, vifr);

            /* create VIF1 */
            vifr.VM = vm;
            vifr.device = "1";
            vifr.MAC = privateMacAddr;
            vifr.network = getNetwork(_privateNic);

            VIF.create(conn, vifr);

            /* create VIF2 */
            vifr.VM = vm;
            vifr.device = "2";
            vifr.MAC = publicMacAddr;
            vifr.network = getNetwork(_publicNic);
            if ("untagged".equalsIgnoreCase(vlanId)) {
                vifr.network = getNetwork(_publicNic);
            } else {
                vifr.network = createVlanNetwork(Long.parseLong(vlanId), _publicNic);
            }

            VIF.create(conn, vifr);

            /* set up PV dom argument */
            String pvargs = vm.getPVArgs(conn);
            pvargs = pvargs + bootArgs;

            pvargs += " zone=" + _dcId;
            pvargs += " pod=" + _pod;

            if (s_logger.isInfoEnabled())
                s_logger.info("PV args for system vm are " + pvargs);
            vm.setPVArgs(conn, pvargs);

            /* destroy  console */
            Set<Console> consoles = vm.getRecord(conn).consoles;

            for (Console console : consoles) {
                console.destroy(conn);
            }

            /* set action after crash as destroy */
            vm.setActionsAfterCrash(conn, Types.OnCrashBehaviour.DESTROY);

            vm.start(conn, false, true);

            if (s_logger.isInfoEnabled())
                s_logger.info("Ping system vm command port, " + privateIp + ":" + cmdPort);

            state = State.Running;
            String result = connect(vmName, privateIp, cmdPort);
            if (result != null) {
                s_logger.warn(" can not ping system vm " + vmName);

            } else {
                if (s_logger.isInfoEnabled())
                    s_logger.info("Ping system vm command port succeeded for vm " + vmName);
            }
            return null;

        } catch (XenAPIException e) {
            String msg = "Exception caught while starting system vm  " + vmName + " " + e.toString();
            s_logger.warn(msg, e);
            return msg;
        } catch (Exception e) {
            String msg = "Exception caught while starting system vm " + vmName + " " + e.getMessage();
            s_logger.warn(msg, e);
            return msg;
        } finally {
            try {
                if (vm != null && vm.getPowerState(conn) == VmPowerState.HALTED) {
                    vm.destroy(conn);
                    state = State.Stopped;
                }
            } catch (XmlRpcException e1) {
                String msg = "VM destroy failed due to " + e1.getMessage();
                s_logger.warn(msg, e1);
            } catch (XenAPIException e1) {
                String msg = "VM destroy failed due to " + e1.toString();
                s_logger.warn(msg, e1);
            }
            synchronized (_vms) {
                _vms.put(vmName, state);
            }
        }
    
    }

    // TODO : need to refactor it to reuse code with StartRouter
    protected Answer execute(final StartConsoleProxyCommand cmd) {
        VM vm = null;
        Connection conn = getConnection();
        State state = State.Stopped;
        try {
            synchronized (_vms) {
                _vms.put(cmd.getVmName(), State.Starting);
            }

            List<VolumeVO> vols = cmd.getVolumes();

            final ConsoleProxyVO proxy = cmd.getProxy();
            List<Ternary<SR, VDI, VolumeVO>> mounts = null;
            mounts = mount(vols);

            assert mounts.size() == 1 : "Routers should have only 1 partition but we actuall have " + mounts.size();

            Ternary<SR, VDI, VolumeVO> mount = mounts.get(0);

            if (!patchDomp(mount.second(), cmd.getVmName())) {
                String msg = "patch domp failed";
                s_logger.warn(msg);
                return new StartConsoleProxyAnswer(cmd, msg);
            }

            Set<VM> templates = VM.getByNameLabel(conn, "CentOS 5.3");
            assert templates.size() == 1 : "Should only have 1 template but found " + templates.size();

            VM template = templates.iterator().next();

            vm = template.createClone(conn, cmd.getVmName());

            vm.removeFromOtherConfig(conn, "disks");

            vm.setPVBootloader(conn, "pygrub");

            long memsize = proxy.getRamSize() * 1024L * 1024L;
            vm.setMemoryDynamicMax(conn, memsize);
            vm.setMemoryDynamicMin(conn, memsize);

            vm.setMemoryStaticMax(conn, memsize);
            vm.setMemoryStaticMin(conn, memsize);

            vm.setVCPUsAtStartup(conn, 1L);

            Host host = Host.getByUuid(conn, _hostUuid);
            vm.setAffinity(conn, host);

            vm.setIsATemplate(conn, false);
            /* create VBD */
            VBD.Record vbdr = new VBD.Record();

            vbdr.VM = vm;
            vbdr.VDI = mount.second();
            vbdr.bootable = true;
            vbdr.userdevice = "0";
            vbdr.mode = Types.VbdMode.RW;
            vbdr.type = Types.VbdType.DISK;

            VBD.create(conn, vbdr);

            VIF.Record vifr = new VIF.Record();

            vifr.VM = vm;
            vifr.device = "0";
            vifr.MAC = proxy.getPrivateMacAddress();
            vifr.network = getNetwork(_privateNic);
            VIF.create(conn, vifr);

            /* create VIF1 */
            vifr.VM = vm;
            vifr.device = "1";
            vifr.MAC = proxy.getPrivateMacAddress();
            vifr.network = getNetwork(_privateNic);

            VIF.create(conn, vifr);

            /* create VIF2 */
            vifr.VM = vm;
            vifr.device = "2";
            vifr.MAC = proxy.getPublicMacAddress();
            vifr.network = getNetwork(_publicNic);
            if ("untagged".equalsIgnoreCase(proxy.getVlanId())) {
                vifr.network = getNetwork(_publicNic);
            } else {
                vifr.network = createVlanNetwork(Long.parseLong(proxy.getVlanId()), _publicNic);
            }

            VIF.create(conn, vifr);

            /* set up PV dom argument */
            String pvargs = vm.getPVArgs(conn);
            pvargs = pvargs + cmd.getBootArgs();

            pvargs += " zone=" + _dcId;
            pvargs += " pod=" + _pod;
            pvargs += " guid=Proxy." + cmd.getProxy().getId();
            pvargs += " proxy_vm=" + cmd.getProxy().getId();

            if (s_logger.isInfoEnabled())
                s_logger.info("PV args for console proxy are " + pvargs);
            vm.setPVArgs(conn, pvargs);

            /* destroy domr console */
            Set<Console> consoles = vm.getRecord(conn).consoles;

            for (Console console : consoles) {
                console.destroy(conn);
            }

            /* set action after crash as destroy */
            vm.setActionsAfterCrash(conn, Types.OnCrashBehaviour.DESTROY);

            vm.start(conn, false, true);

            if (s_logger.isInfoEnabled())
                s_logger.info("Ping console proxy command port, " + proxy.getPrivateIpAddress() + ":" + cmd.getProxyCmdPort());

            state = State.Running;
            String result = connect(cmd.getVmName(), proxy.getPrivateIpAddress(), cmd.getProxyCmdPort());
            if (result != null) {
                s_logger.warn(" can not ping domp");

            } else {
                if (s_logger.isInfoEnabled())
                    s_logger.info("Ping console proxy command port succeeded.");
            }
            return new StartConsoleProxyAnswer(cmd);

        } catch (XenAPIException e) {
            String msg = "Exception caught while starting console proxy: " + e.toString();
            s_logger.warn(msg, e);
            return new StartConsoleProxyAnswer(cmd, msg);
        } catch (Exception e) {
            String msg = "Exception caught while starting console proxy: " + e.getMessage();
            s_logger.warn(msg, e);
            return new StartConsoleProxyAnswer(cmd, msg);
        } finally {
            try {
                if (vm != null && vm.getPowerState(conn) == VmPowerState.HALTED) {
                    vm.destroy(conn);
                    state = State.Stopped;
                }
            } catch (XmlRpcException e1) {
                String msg = "VM destroy failed due to " + e1.getMessage();
                s_logger.warn(msg, e1);
            } catch (XenAPIException e1) {
                String msg = "VM destroy failed due to " + e1.toString();
                s_logger.warn(msg, e1);
            }
            synchronized (_vms) {
                _vms.put(cmd.getVmName(), state);
            }
        }
    }

    private boolean patchDomr(VDI vdi, String vmname) {
        return patchSpecialVM(vdi, vmname, "domr");
    }

    private boolean patchDomp(VDI vdi, String vmname) {
        return patchSpecialVM(vdi, vmname, "domp");
    }

    private String getUnusedDeviceNum(VM vm) {
        // Figure out the disk number to attach the VM to
        List<String> availableSlots = new ArrayList<String>();
        availableSlots.add("1");
        availableSlots.add("2");
        availableSlots.add("4");
        availableSlots.add("5");
        availableSlots.add("6");
        availableSlots.add("7");
        try {
            Connection conn = getConnection();
            Set<VBD> currentVBDs = vm.getVBDs(conn);
            for (VBD vbd : currentVBDs) {
                VBD.Record vbdr = vbd.getRecord(conn);
                String userDevice = vbdr.userdevice;
                if (availableSlots.contains(userDevice))
                    availableSlots.remove(userDevice);
            }

            if (availableSlots.size() == 0)
                throw new VmopsRuntimeException("Could not find an available slot in VM with name: " + vm.getNameLabel(conn) + " to attach a new disk.");

            return availableSlots.get(0);
        } catch (XmlRpcException e) {
            String msg = "Catch XmlRpcException due to: " + e.getMessage();
            s_logger.warn(msg, e);
        } catch (XenAPIException e) {
            String msg = "Catch XenAPIException due to: " + e.toString();
            s_logger.warn(msg, e);
        }
        throw new VmopsRuntimeException("Could not find an available slot in VM with name to attach a new disk.");
    }

    private boolean patchSpecialVM(VDI vdi, String vmname, String vmtype) {
        // patch special vm here, domr, domp
        VBD vbd = null;
        Connection conn = getConnection();
        try {

            Set<VM> vms = VM.getAll(conn);

            for (VM vm : vms) {
                VM.Record vmrec = vm.getRecord(conn);
                if (vmrec.isControlDomain && vmrec.residentOn.getUuid(conn).equals(_hostUuid)) {

                    /* create VBD */
                    VBD.Record vbdr = new VBD.Record();
                    vbdr.VM = vm;
                    vbdr.VDI = vdi;
                    vbdr.bootable = false;
                    vbdr.userdevice = getUnusedDeviceNum(vm);
                    vbdr.unpluggable = true;
                    vbdr.mode = Types.VbdMode.RW;
                    vbdr.type = Types.VbdType.DISK;

                    vbd = VBD.create(conn, vbdr);

                    vbd.plug(conn);

                    String device = vbd.getDevice(conn);

                    return patchspecialvm(vmname, device, vmtype);
                }
            }
        } catch (XmlRpcException e) {
            String msg = "VM destroy failed due to " + e.getMessage();
            s_logger.warn(msg, e);
        } catch (XenAPIException e) {
            String msg = "VM destroy failed due to " + e.toString();
            s_logger.warn(msg, e);
        } finally {
            if (vbd != null) {
                try {
                    if (vbd.getCurrentlyAttached(conn)) {
                        vbd.unplug(conn);
                    }
                    vbd.destroy(conn);
                } catch (XmlRpcException e) {
                    String msg = "Catch XmlRpcException due to " + e.getMessage();
                    s_logger.warn(msg, e);
                } catch (XenAPIException e) {
                    String msg = "Catch XenAPIException due to " + e.toString();
                    s_logger.warn(msg, e);
                }

            }
        }
        return false;
    }

    private boolean patchspecialvm(String vmname, String device, String vmtype) {
        String result = callHostPlugin("patchdomr", "vmname", vmname, "vmtype", vmtype, "device", "/dev/" + device);
        if (result == null || result.isEmpty())
            return false;
        return true;
    }

    private String callHostPlugin(String cmd, String... params) {
        String argString = "";
        try {
            Connection conn = getConnection();
            Host host = Host.getByUuid(conn, _hostUuid);
            Map<String, String> args = new HashMap<String, String>();
            for (int i = 0; i < params.length; i += 2) {
                args.put(params[i], params[i + 1]);
            }
            
            for (Map.Entry<String, String> arg : args.entrySet()) {
                argString = arg.getKey() + ": " + arg.getValue() + ", ";
            }
            
            String result = host.callPlugin(conn, "vmops", cmd, args);
            return result.replace("\n", "");
        } catch (XenAPIException e) {
            s_logger.warn("callHostPlugin failed for cmd: " + cmd + " with args " + argString + " due to " + e.toString());
        } catch (XmlRpcException e) {
            s_logger.debug("callHostPlugin failed for cmd: " + cmd + " with args " + argString + " due to " + e.getMessage());
        }
        return null;
    }

    private boolean setIptables() {
        String result = callHostPlugin("setIptables");
        if (result == null || result.isEmpty())
            return false;
        return true;
    }
    
    private boolean getNetworkInfo() {
        Connection conn = getConnection();
        String localGateway, privateNic;
        Host myself;
        try {
            myself = Host.getByUuid(conn, _hostUuid);
            privateNic = callHostPlugin("getnetwork", "mgmtIP", myself.getAddress(conn));
            if (privateNic == null || privateNic.isEmpty()) {
                return false;
            }
        } catch (XenAPIException e) {
            s_logger.warn("Unable to get network info due to " + e.toString());
            return false;
        } catch (XmlRpcException e) {
            s_logger.debug("Unable to get network info due to " + e.getMessage());
            return false;
        }

        _privateNic = privateNic;
        if (_publicNic == null) {
            _publicNic = _privateNic;
        }
        return true;
    }

    protected List<Ternary<SR, VDI, VolumeVO>> mount(List<VolumeVO> vos) {
        ArrayList<Ternary<SR, VDI, VolumeVO>> mounts = new ArrayList<Ternary<SR, VDI, VolumeVO>>(vos.size());

        for (VolumeVO vol : vos) {
            String vdiuuid = vol.getPath();
            SR sr = null;
            VDI vdi = null;
            // Look up the VDI
            vdi = getVDIbyUuid(vdiuuid);

            Ternary<SR, VDI, VolumeVO> ter = new Ternary<SR, VDI, VolumeVO>(sr, vdi, vol);
            mounts.add(ter);
        }
        return mounts;
    }

    protected Network getNetwork(String name) throws BadServerResponse, XenAPIException, XmlRpcException {
        Connection conn = getConnection();

        Set<Network> networks = Network.getByNameLabel(conn, name);
        if (networks.size() > 0) {
            assert networks.size() == 1 : "How did we find more than one network with this name label" + name + "?  Strange....";
            return networks.iterator().next(); // Found it.
        }

        return null;
    }

    protected Network createVlanNetwork(long tag, String nic) throws BadServerResponse, XenAPIException, XmlRpcException {
        // In XenServer, vlan is added by
        // 1. creating a network.
        // 2. creating a vlan associating network with the pif.
        // We always create
        // 1. a network with VLAN[vlan id in decimal]
        // 2. a vlan associating the network created with the pif to private
        // network.
        try {
            Connection conn = getConnection();
            String name = "VLAN" + Long.toString(tag);

            Network vlanNetwork = getNetwork(name);
            if (vlanNetwork == null) { // Can't find it, then create it.
                Network.Record nwr = new Network.Record();
                nwr.nameLabel = name;
                nwr.bridge = name;
                vlanNetwork = Network.create(conn, nwr);
            }

            Network privateNetwork = getNetwork(nic);
            Set<PIF> pifs = privateNetwork.getPIFs(conn);
            PIF.Record myPif = null;
            PIF myPif2 = null;
            for (PIF pif : pifs) {
                PIF.Record rec = pif.getRecord(conn);
                if (!isRefNull(rec.host) && rec.host.getUuid(conn).equals(_hostUuid)) {
                    myPif = rec;
                    myPif2 = pif;
                    break;
                }
            }

            /* create VLAN */
            pifs = PIF.getAll(conn);
            for (PIF pif : pifs) {
                PIF.Record pif_rec = pif.getRecord(conn);
                if (pif_rec.device.equals(myPif.device) && pif_rec.VLAN == tag && !isRefNull(pif_rec.host) && pif_rec.host.getUuid(conn).equals(_hostUuid)) {
                    return vlanNetwork;
                }
            }

            VLAN.create(conn, myPif2, tag, vlanNetwork);
            return vlanNetwork;
        } catch (XenAPIException e) {
            s_logger.warn("Catch XenAPIException due to " + e.toString(), e);
        } catch (final XmlRpcException e) {
            s_logger.warn("Catch XenAPIException due to " + e.getMessage(), e);
        }
        return null;
    }

    private SR getLocalLVMSR() {
        Connection conn = getConnection();

        try {
            Map<SR, SR.Record> map = SR.getAllRecords(conn);
            for (Map.Entry<SR, SR.Record> entry : map.entrySet()) {
                SR.Record srRec = entry.getValue();
                if (srRec.type.equals(SRType.LVM.toString())) {
                    Set<PBD> pbds = srRec.PBDs;
                    if (pbds == null) {
                        continue;
                    }
                    for (PBD pbd : pbds) {
                        Host host = pbd.getHost(conn);
                        if (!isRefNull(host) && host.getUuid(conn).equals(_hostUuid)) {
                            return entry.getKey();
                        }
                    }
                }
            }
        } catch (XenAPIException e) {
            String msg = "Unable to get local LVMSR in host:" + _hostUuid + e.toString();
            s_logger.warn(msg);
        } catch (XmlRpcException e) {
            String msg = "Unable to get local LVMSR in host:" + _hostUuid + e.getCause();
            s_logger.warn(msg);
        }
        return null;

    }

    private StartupStorageCommand initializeLocalSR() {

        SR lvmsr = getLocalLVMSR();
        if (lvmsr == null) {
            return null;
        }
        try {
            Connection conn = getConnection();
            String lvmuuid = lvmsr.getUuid(conn);
            lvmsr.setNameLabel(conn, lvmuuid);
            String name = "VMOps local storage pool in host : " + _hostUuid;
            lvmsr.setNameDescription(conn, name);
            long cap = lvmsr.getPhysicalSize(conn);
            long avail = cap - lvmsr.getPhysicalUtilisation(conn);
            Host host = Host.getByUuid(conn, _hostUuid);
            String address = host.getAddress(conn);
            StoragePoolInfo pInfo = new StoragePoolInfo(name, lvmuuid, address, SRType.LVM.toString(), SRType.LVM.toString(), StoragePoolType.LVM, cap, avail);
            StartupStorageCommand cmd = new StartupStorageCommand();
            cmd.setPoolInfo(pInfo);
            cmd.setGuid(_hostUuid);
            cmd.setResourceType(StorageResourceType.STORAGE_POOL);
            return cmd;
        } catch (XenAPIException e) {
            String msg = "build startupstoragecommand err in host:" + _hostUuid + e.toString();
            s_logger.warn(msg);
        } catch (XmlRpcException e) {
            String msg = "build startupstoragecommand err in host:" + _hostUuid + e.getMessage();
            s_logger.warn(msg);
        }
        return null;

    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        try {
            final HashMap<String, State> newStates = sync();
            if (newStates == null) {
                return null;
            }
            return new PingRoutingCommand(getType(), id, newStates);
        } catch (Exception e) {
            s_logger.warn("Unable to get current status", e);
            return null;
        }
    }

    @Override
    public Type getType() {
        return com.vmops.host.Host.Type.Routing;
    }

    private void getPVISO(StartupStorageCommand sscmd) {
        Connection conn = getConnection();
        try {
            Set<VDI> vids = VDI.getByNameLabel(conn, "xs-tools.iso");
            if (vids.isEmpty())
                return;
            VDI pvISO = vids.iterator().next();
            String uuid = pvISO.getUuid(conn);
            Map<String, TemplateInfo> pvISOtmlt = new HashMap<String, TemplateInfo>();
            TemplateInfo tmplt = new TemplateInfo("xs-tools.iso", uuid, 0, true);
            pvISOtmlt.put("xs-tools", tmplt);
            sscmd.setTemplateInfo(pvISOtmlt);
        } catch (XenAPIException e) {
            s_logger.debug("Can't get xs-tools.iso: " + e.toString());
        } catch (XmlRpcException e) {
            s_logger.debug("Can't get xs-tools.iso: " + e.toString());
        }
    }

    @Override
    public StartupCommand[] initialize() {
        disconnected();
        setupServer();

        StartupRoutingCommand cmd = new StartupRoutingCommand();
        fillHostInfo(cmd);

        cleanupDiskMounts();

        Map<String, State> changes = null;
        synchronized (_vms) {
            _vms.clear();
            changes = sync();
        }

        synchronized (_domrIPMap) {
            _domrIPMap.clear();
            if(changes != null){
                for (final Map.Entry<String, State> entry : changes.entrySet()) {
                    final String vm = entry.getKey();
                    State state = entry.getValue();
                    if(VirtualMachineName.isValidRouterName(vm) && (state == State.Running)){
                        syncDomRIPMap(vm);
                    }
                }
            }
        }
        
        cmd.setHypervisorType(HypervisorType.XenServer);
        cmd.setChanges(changes);

        StartupStorageCommand sscmd = initializeLocalSR();

        if (sscmd != null) {
            /* report pv driver iso */
            getPVISO(sscmd);
            return new StartupCommand[] { cmd, sscmd };
        }
        
        _poolUuid = getPoolUuid();

        return new StartupCommand[] { cmd };
    }
    
    private String getPoolUuid() {
        Connection conn = getConnection();
        try {
        Map<Pool, Pool.Record> pools = Pool.getAllRecords(conn);
        assert (pools.size() == 1) : "Tell me how pool size can be " + pools.size();
        Pool.Record rec = pools.values().iterator().next();
        return rec.uuid;
        } catch(XenAPIException e) {
            throw new VmopsRuntimeException("Unable to get pool ", e);
        } catch(XmlRpcException e) {
            throw new VmopsRuntimeException("Unable to get pool ", e);
        }
    }


    protected void setupServer() {
        Connection conn = getConnection();

        String version = XenServerResource.class.getPackage().getImplementationVersion();

        try {
            Host host = Host.getByUuid(conn, _hostUuid);
            /* enable host in case it is disabled somehow */
            host.enable(conn);
            /* push patches to XenServer */
            Host.Record hr = host.getRecord(conn);

            Iterator<String> it = hr.tags.iterator();

            while (it.hasNext()) {
                String tag = it.next();
                if (tag.startsWith("vmops-version-")) {
                    if (tag.equals("vmops-version-" + version)) {
                        s_logger.info(logX(host, "Host " + hr.address + " is already setup."));
                        getNetworkInfo();
                        return;
                    } else {
                        it.remove();
                    }
                }
            }

            com.trilead.ssh2.Connection sshConnection = new com.trilead.ssh2.Connection(hr.address, 22);
            try {
                sshConnection.connect(null, 60000, 60000);
                if (!sshConnection.authenticateWithPassword(_username, _password)) {
                    throw new VmopsRuntimeException("Unable to authenticate");
                }

                SCPClient scp = new SCPClient(sshConnection);
                File file = new File(_patchPath);

                Properties props = new Properties();
                props.load(new FileInputStream(file));

                String path = _patchPath.substring(0, _patchPath.lastIndexOf(File.separator) + 1);
                for (Map.Entry<Object, Object> entry : props.entrySet()) {
                    String f = path.concat((String) entry.getKey());
                    String d = (String) entry.getValue();

                    if (!new File(f).exists()) {
                        s_logger.warn("We cannot locate " + f);
                        continue;
                    }
                    if (f.endsWith("id_rsa")) {
                        scp.put(f, d, "0600");
                    } else {
                        scp.put(f, d, "0755");
                    }
                    s_logger.info("Copied " + f + " to " + d + " on " + hr.address);
                }

            } catch (IOException e) {
                throw new VmopsRuntimeException("Unable to setup the server correctly", e);
            } finally {
                sshConnection.close();
            }

            if (!setIptables()) {
                s_logger.warn("set xenserver Iptable failed");
            }

            getNetworkInfo();

            hr.tags.add("vmops-version-" + version);
            host.setTags(conn, hr.tags);
        } catch (XenAPIException e) {
            String msg = "Xen setup failed due to " + e.toString();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException("Unable to get host information " + e.toString(), e);
        } catch (XmlRpcException e) {
            String msg = "Xen setup failed due to " + e.getMessage();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException("Unable to get host information ", e);
        }
    }

    private SR getSRByNameLabelandHost(String name) throws BadServerResponse, XenAPIException, XmlRpcException {
        Connection conn = getConnection();
        Set<SR> srs = SR.getByNameLabel(conn, name);
        SR ressr = null;
        for (SR sr : srs) {
            Set<PBD> pbds;
            pbds = sr.getPBDs(conn);
            for (PBD pbd : pbds) {
                PBD.Record pbdr = pbd.getRecord(conn);
                if (pbdr.host != null && pbdr.host.getUuid(conn).equals(_hostUuid)) {
                    if (!pbdr.currentlyAttached) {
                        pbd.plug(conn);
                    }
                    ressr = sr;
                    break;
                }
            }
        }
        return ressr;
    }
    
    private SR forgetSRByNameLabelandHost(String name) throws BadServerResponse, XenAPIException, XmlRpcException {
        Connection conn = getConnection();
        Set<SR> srs = SR.getByNameLabel(conn, name);
        SR ressr = null;
        for (SR sr : srs) {
            Set<PBD> pbds;
            pbds = sr.getPBDs(conn);
            for (PBD pbd : pbds) {
                PBD.Record pbdr = pbd.getRecord(conn);
                if (pbdr.host != null && pbdr.host.getUuid(conn).equals(_hostUuid)) {
                    if (pbdr.currentlyAttached) {
                        pbd.unplug(conn);
                    }
                    ressr = sr;
                    break;
                }
            }
        }
        if (ressr != null) {
            ressr.forget(conn);
        }
        return ressr;
    }

    protected GetStorageStatsAnswer execute(final GetStorageStatsCommand cmd) {

        try {
            Connection conn = getConnection();
            Set<SR> srs = SR.getByNameLabel(conn, cmd.getStorageId());

            if (srs.size() != 1) {
                String msg = "There are " + srs.size() + " storageid: " + cmd.getStorageId();
                s_logger.warn(msg);
                return new GetStorageStatsAnswer(cmd, msg);
            }

            SR sr = srs.iterator().next();

            sr.scan(conn);
            long capacity = sr.getPhysicalSize(conn);
            long used = sr.getPhysicalUtilisation(conn);
            return new GetStorageStatsAnswer(cmd, capacity, used);
        } catch (XenAPIException e) {
            String msg = "GetStorageStats Exception:" + e.toString() + "host:" + _hostUuid + "storageid: " + cmd.getStorageId();
            s_logger.warn(msg);
            return new GetStorageStatsAnswer(cmd, msg);
        } catch (XmlRpcException e) {
            String msg = "GetStorageStats Exception:" + e.getMessage() + "host:" + _hostUuid + "storageid: " + cmd.getStorageId();
            s_logger.warn(msg);
            return new GetStorageStatsAnswer(cmd, msg);
        }
    }

    protected Answer execute(ModifyStoragePoolCommand cmd) {
        StoragePoolVO pool = cmd.getPool();
        try {

            SR sr = getStorageRepository(pool);

            Connection conn = getConnection();
            sr.setNameLabel(conn, pool.getUuid());
            sr.setNameDescription(conn, pool.getName());
            sr.scan(conn);
            long capacity = sr.getPhysicalSize(conn);
            long available = capacity - sr.getPhysicalUtilisation(conn);
            Map<String, TemplateInfo> tInfo = new HashMap<String, TemplateInfo>();
            ModifyStoragePoolAnswer answer = new ModifyStoragePoolAnswer(cmd, capacity, available, tInfo);
            return answer;
        } catch (XenAPIException e) {
            String msg = "ModifyStoragePoolCommand XenAPIException:" + e.toString() + " host:" + _hostUuid + " pool: " + pool.getName() + pool.getHostAddress() + pool.getPath();
            s_logger.warn(msg, e);
            return new Answer(cmd, false, msg);
        } catch (Exception e) {
            String msg = "ModifyStoragePoolCommand XenAPIException:" + e.getMessage() + " host:" + _hostUuid + " pool: " + pool.getName() + pool.getHostAddress() + pool.getPath();
            s_logger.warn(msg, e);
            return new Answer(cmd, false, msg);
        }

    }

    public Connection getConnection() {
        return _connPool.connect(_hostUuid, _hostUrl, _username, _password, _wait);
    }

    protected void fillHostInfo(StartupRoutingCommand cmd) {
        long speed = 0;
        int cpus = 0;
        long ram = 0;

        Connection conn = getConnection();

        long dom0Ram = 0;
        final StringBuilder caps = new StringBuilder();
        try {

            Host host = Host.getByUuid(conn, _hostUuid);
            Host.Record hr = host.getRecord(conn);

            Network network = getNetwork(_privateNic);
            if (network == null) {
                throw new VmopsRuntimeException("Unable to get private nic: " + _privateNic);
            }
            network = getNetwork(_publicNic);
            if (network == null) {
                throw new VmopsRuntimeException("Unable to get public nic: " + _publicNic);
            }
            Map<String, String> details = cmd.getHostDetails();
            if (details == null) {
                details = new HashMap<String, String>();
            }
            details.put("private.network.device", _privateNic);
            details.put("public.network.device", _publicNic);
            cmd.setHostDetails(details);
            cmd.setPrivateIpAddress(hr.address);
            cmd.setStorageIpAddress(hr.address);
            cmd.setName(hr.nameLabel);
            cmd.setGuid(_hostUuid);
            cmd.setDataCenter(Long.toString(_dcId));
            for (final String cap : hr.capabilities) {
                if (cap.length() > 0) {
                    caps.append(cap).append(" , ");
                }
            }
            if (caps.length() > 0) {
                caps.delete(caps.length() - 3, caps.length());
            }
            cmd.setCaps(caps.toString());

            Set<HostCpu> hcs = host.getHostCPUs(conn);
            cpus = hcs.size();
            for (final HostCpu hc : hcs) {
                speed = hc.getSpeed(conn);
            }
            cmd.setSpeed(speed);
            cmd.setCpus(cpus);

            long free = 0;
            HostMetrics hm = host.getMetrics(conn);
            HostMetrics.Record hmr = hm.getRecord(conn);
            ram = hmr.memoryTotal;
            free = hmr.memoryFree;

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Total Ram: " + ram + " Free Ram: " + free);
            }

            Set<VM> vms = host.getResidentVMs(conn);
            for (VM vm : vms) {
                final VM.Record record = vm.getRecord(conn);
                if (record.powerState == Types.VmPowerState.RUNNING && !record.isControlDomain) {
                    free += record.memoryDynamicMin;
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Adding memory " + record.memoryDynamicMin + " used by vm: " + record.nameLabel);
                    }
                }
            }
            dom0Ram = ram - free;
            cmd.setMemory(ram);
            cmd.setDom0MinMemory(dom0Ram);

            Set<PIF> pifs = host.getPIFs(conn);
            for (PIF pif : pifs) {
                PIF.Record record = pif.getRecord(conn);
                if (hr.address.equals(record.IP)) {
                    cmd.setPrivateMacAddress(record.MAC);
                    cmd.setStorageMacAddress(record.MAC);
                    cmd.setPrivateNetmask(record.netmask);
                    cmd.setStorageNetmask(record.netmask);
                }
            }

            Map<String, String> configs = hr.otherConfig;
            cmd.setIqn(configs.get("iscsi_iqn"));

            Map<Pool, Pool.Record> pools = Pool.getAllRecords(conn);
            assert pools.size() == 1 : "Pool should have been 1 right? .... no....it's" + pools.size();

            cmd.setPod(_pod);
            cmd.setVersion(XenServerResource.class.getPackage().getImplementationVersion());

        } catch (final XmlRpcException e) {
            throw new VmopsRuntimeException("XML RPC Exception" + e.getMessage(), e);
        } catch (XenAPIException e) {
            throw new VmopsRuntimeException("XenAPIException" + e.toString(), e);
        }
    }

    public XenServerResource() {
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        _name = name;
        _hostUuid = (String) params.get("guid");
        try {
            _dcId = Long.parseLong((String) params.get("zone"));
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Unable to get the zone " + params.get("zone"));
        }
        _name = _hostUuid;
        _hostUrl = (String) params.get("url");
        _username = (String) params.get("username");
        _password = (String) params.get("password");
        _pod = (String) params.get("pod");
        _privateNic = (String) params.get("private.network.device");
        _publicNic = (String) params.get("public.network.device");

        String value = (String) params.get("wait");
        _wait = NumbersUtil.parseInt(value, 1800 * 1000);

        if (_pod == null) {
            throw new ConfigurationException("Unable to get the pod");
        }

        if (_hostUrl == null) {
            throw new ConfigurationException("Unable to get the host url");
        }

        if (_username == null) {
            throw new ConfigurationException("Unable to get the username");
        }

        if (_password == null) {
            throw new ConfigurationException("Unable to get the password");
        }

        if (_hostUuid == null) {
            throw new ConfigurationException("Unable to get the uuid");
        }

        params.put("domr.scripts.dir", "scripts/network/domr");

        synchronized (XenServerResource.class) {
            if (s_vrr == null) {
                s_vrr = new VirtualRoutingResource();
                s_vrr.configure("Virtual Routing Resource", params);
            }
        }

        String patchPath = (String) params.get("xenserver.patch.dir");
        if (patchPath == null) {
            patchPath = "scripts/vm/hypervisor/xenserver/patch";
        }

        _patchPath = Script.findScript(patchPath, "patch");
        if (_patchPath == null) {
            throw new ConfigurationException("Unable to find all of patch files for xenserver");
        }

        _connPool = XenServerConnectionPool.getInstance();

        return true;
    }

    protected void addVolumes(final List<VolumeVO> vols, StoragePoolVO pool, VDI vdi, Volume.VolumeType type) {

        Connection conn = getConnection();

        VDI.Record vdir;
        try {
            vdir = vdi.getRecord(conn);

            VolumeVO vol = new VolumeVO(null, vdir.nameLabel, -1, -1, -1, -1, -1, new Long(-1), pool.getUuid(), vdir.uuid, vdir.virtualSize, type);
            vol.setNameLabel(vdir.nameLabel);
            vols.add(vol);
        } catch (XenAPIException e) {
            throw new VmopsRuntimeException("Exception " + e.toString(), e);
        } catch (Exception e) {
            throw new VmopsRuntimeException("There is no Local storage for this xenserver");
        }

    }

    private SR getSRbyNLandPool(StoragePoolVO pool) {
        try {
            Connection conn = getConnection();

            Set<SR> srs = SR.getByNameLabel(conn, pool.getUuid());
            if (srs.size() > 1) {
                String message = "There are more than one SR for one pool uuid";
                s_logger.warn(message);
                return null;
            } else if (srs.size() == 1) {
                return srs.iterator().next();
            } else {
                if (pool.getPoolType() == StoragePool.StoragePoolType.NetworkFilesystem) {
                    return getNfsSR(pool);

                } else if (pool.getPoolType() == StoragePool.StoragePoolType.IscsiLUN) {
                    return getIscsiSR(pool);

                } else {
                    String msg = "not support pool type " + pool.getPoolType().name();
                    s_logger.warn(msg);
                    return null;
                }
            }
        } catch (XenAPIException e) {
            String msg = "getSRbyNLandPool failed" + e.toString();
            s_logger.warn(msg, e);
        } catch (XmlRpcException e) {
            String msg = "getSRbyNLandPool failed" + e.getMessage();
            s_logger.warn(msg, e);
        }
        return null;

    }

    void destroyVDI(VDI vdi) {
        try {
            Connection conn = getConnection();
            vdi.destroy(conn);

        } catch (Exception e) {
            String msg = "destroy VDI failed due to " + e.toString();
            s_logger.warn(msg);
        }
    }

    protected CreateAnswer execute(final CreateCommand cmd) {
        VDI rootvdi = null;
        VDI datavdi = null;
        try {
            Connection conn = getConnection();

            List<VolumeVO> vols = new ArrayList<VolumeVO>();

            StoragePoolVO pool = cmd.getPool();

            SR poolsr = getSRbyNLandPool(pool);
            if (poolsr == null) {
                String msg = "can not find storage pool in host " + _hostUuid + " with name " + pool.getUuid();
                s_logger.warn(msg);
                return new CreateAnswer(cmd, msg);

            }

            String tmpltname = cmd.getTemplatePath();

            if (tmpltname == null) {
                // create blank root virtual disk

                if (cmd.getRootDiskSizeByte() > 0) {
                    VDI.Record vdir = new VDI.Record();
                    vdir.nameLabel = cmd.getVmName() + "-ROOT";
                    vdir.SR = poolsr;
                    vdir.type = Types.VdiType.USER;
                    vdir.virtualSize = cmd.getRootDiskSizeByte();

                    rootvdi = VDI.create(conn, vdir);
                    addVolumes(vols, pool, rootvdi, Volume.VolumeType.ROOT);
                }

                if (cmd.getDataDiskSizeByte() > 0) {
                    VDI.Record vdir = new VDI.Record();
                    vdir.nameLabel = cmd.getVmName() + "-DATA";
                    vdir.SR = poolsr;
                    vdir.type = Types.VdiType.USER;
                    vdir.virtualSize = cmd.getDataDiskSizeByte();

                    datavdi = VDI.create(conn, vdir);
                    addVolumes(vols, pool, datavdi, Volume.VolumeType.DATADISK);
                }

            } else {
                VDI tmpltvdi = null;

                tmpltvdi = getVDIbyUuid(tmpltname);

                synchronized (this.getClass()) {
                    rootvdi = tmpltvdi.createClone(conn, new HashMap<String, String>());
                }

                rootvdi.setNameLabel(conn, cmd.getVmName() + "-ROOT");

                addVolumes(vols, pool, rootvdi, Volume.VolumeType.ROOT);

                if (cmd.getDataDiskSizeByte() > 0) {
                    VDI.Record vdir = new VDI.Record();
                    vdir.nameLabel = cmd.getVmName() + "-DATA";
                    vdir.SR = poolsr;
                    vdir.type = Types.VdiType.USER;
                    vdir.virtualSize = cmd.getDataDiskSizeByte();
                    datavdi = VDI.create(conn, vdir);
                    addVolumes(vols, pool, datavdi, Volume.VolumeType.DATADISK);
                }
            }

            for (VolumeVO vol : vols) {
                vol.setStorageResourceType(getStorageResourceType());
            }

            return new CreateAnswer(cmd, vols);
        } catch (XenAPIException e) {
            String msg = "create vm failed due to " + e.toString();
            s_logger.warn(msg, e);
            if (rootvdi != null) {
                destroyVDI(rootvdi);
            }
            if (datavdi != null) {
                destroyVDI(datavdi);
            }
            return new CreateAnswer(cmd, msg);
        } catch (Exception e) {
            String msg = "create vm failed due to " + e.getMessage();
            s_logger.warn(msg, e);
            if (rootvdi != null) {
                destroyVDI(rootvdi);
            }
            if (datavdi != null) {
                destroyVDI(datavdi);
            }
            return new CreateAnswer(cmd, msg);
        }
    }

    private SR getNfsSRbyMountPoint(String mountpoint, boolean shared) {
        Connection conn = getConnection();

        try {
            URI uri = new URI(mountpoint);
            String path = uri.getPath();
            path = path.replace("//", "/");
            Map<SR, SR.Record> srs = SR.getAllRecords(conn);
            for (Map.Entry<SR, SR.Record> entry : srs.entrySet()) {
                SR.Record record = entry.getValue();
                if (SRType.NFS.equals(record.type) && record.contentType.equals("user")) {
                    Set<PBD> pbds = record.PBDs;
                    for (PBD pbd : pbds) {
                        Host host = pbd.getHost(conn);
                        if(isRefNull(host))
                            break;
                        Map<String, String> dconfig = pbd.getDeviceConfig(conn);
                        if (host.equals(Host.getByUuid(conn, _hostUuid))
                                && uri.getHost().equals(dconfig.get("server"))
                                && path.equals(dconfig.get("serverpath"))) {
                            SR sr = entry.getKey();
                            if (s_logger.isDebugEnabled()) {
                                s_logger.debug(logX(sr, "Found SR for " + mountpoint + " UUID=" + record.uuid));
                            }
                            return sr;
                        }
                    }
                }
            }
            return createNfsSRbyURI(uri, shared);
        } catch (XenAPIException e) {
            String msg = "Can not create second storage SR, mountpoint:" + mountpoint + ":" + e.toString();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        } catch (Exception e) {
            String msg = "Can not create second storage SR, mountpoint:" + mountpoint + e.getMessage();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        }
    }

    private SR getIsoSRbyMountPoint(String mountpoint, boolean shared) {
        Connection conn = getConnection();

        try {
            URI uri = new URI(mountpoint);
            String location = uri.getHost() + ":" + uri.getPath();
            Set<SR> srs = SR.getAll(conn);
            
            for (SR sr : srs) {
                SR.Record record = sr.getRecord(conn);
                if (SRType.ISO.equals(record.type) && record.contentType.equals("user")) {
                    Set<PBD> pbds = record.PBDs;
                    for (PBD pbd : pbds) {
                        Host host = pbd.getHost(conn);
                        if(isRefNull(host))
                            break;
                        Map<String, String> dconfig = pbd.getDeviceConfig(conn);
                        if (host.equals(Host.getByUuid(conn, _hostUuid))
                                && location.equals(dconfig.get("location"))) {
                            if (s_logger.isDebugEnabled()) {
                                s_logger.debug(logX(sr, "Found SR for " + mountpoint + " UUID=" + record.uuid));
                            }
                            return sr;
                        }
                        break;
                    }
                }
            }
            return createIsoSRbyURI(uri, shared);
        } catch (XenAPIException e) {
            String msg = "getIsoSRbyMountPoint failed, mountpoint:" + mountpoint + " due to " + e.toString();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        } catch (Exception e) {
            String msg = "getIsoSRbyMountPoint failed, mountpoint:" + mountpoint + " due to " + e.getMessage();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        }
    }

    private SR createNfsSRbyURI(URI uri, boolean shared) {
        try {
            Connection conn = getConnection();
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Creating a " + (shared ? "shared SR for " : "not shared SR for ") + uri);
            }

            Map<String, String> deviceConfig = new HashMap<String, String>();
            String path = uri.getPath();
            path = path.replace("//", "/");
            deviceConfig.put("server", uri.getHost());
            deviceConfig.put("serverpath", path);

            Host host = Host.getByUuid(conn, _hostUuid);

            SR sr = SR.create(conn, host, deviceConfig, new Long(0), "", uri.getHost() + uri.getPath(), SRType.NFS.toString(), "user", shared, new HashMap<String, String>());
            sr.setNameLabel(conn, uri.getHost() + ":" + path);
            if (s_logger.isDebugEnabled()) {
                s_logger.debug(logX(sr, "Created a SR; UUID is " + sr.getUuid(conn)));
            }
            sr.scan(conn);
            return sr;
        } catch (XenAPIException e) {
            String msg = "Can not create second storage SR mountpoint: " + uri.getHost() + uri.getPath() + " due to " + e.toString();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        } catch (Exception e) {
            String msg = "Can not create second storage SR mountpoint: " + uri.getHost() + uri.getPath() + " due to " + e.getMessage();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        }
    }

    private SR createIsoSRbyURI(URI uri, boolean shared) {
        try {
            Connection conn = getConnection();

            Map<String, String> deviceConfig = new HashMap<String, String>();
            String path = uri.getPath();
            path = path.replace("//", "/");
            deviceConfig.put("location", uri.getHost() + ":" + uri.getPath());
            Host host = Host.getByUuid(conn, _hostUuid);
            SR sr = SR.create(conn, host, deviceConfig, new Long(0), uri.getHost() + uri.getPath(), "iso", "iso", "iso", shared, new HashMap<String, String>());
            sr.setNameLabel(conn, deviceConfig.get("location"));
            sr.scan(conn);
            return sr;
        } catch (XenAPIException e) {
            String msg = "createIsoSRbyURI failed! mountpoint: " + uri.getHost() + uri.getPath() + " due to " + e.toString();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        } catch (Exception e) {
            String msg = "createIsoSRbyURI failed! mountpoint: " + uri.getHost() + uri.getPath() + " due to " + e.getMessage();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        }
    }

    private VDI getVDIbyLocationandSR(String loc, SR sr) {
        Connection conn = getConnection();
        try {
            Set<VDI> vdis = sr.getVDIs(conn);
            for (VDI vdi : vdis) {
                if (vdi.getLocation(conn).startsWith(loc)) {
                    return vdi;
                }
            }

            String msg = "can not getVDIbyLocationandSR " + loc;
            s_logger.warn(msg);
            return null;
        } catch (XenAPIException e) {
            String msg = "getVDIbyLocationandSR exception " + loc + " due to " + e.toString();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        } catch (Exception e) {
            String msg = "getVDIbyLocationandSR exception " + loc + " due to " + e.getMessage();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        }

    }

    private VDI getVDIbyUuid(String uuid) {
        try {
            Connection conn = getConnection();
            return VDI.getByUuid(conn, uuid);
        } catch (XenAPIException e) {
            String msg = "VDI getByUuid failed " + uuid + " due to " + e.toString();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        } catch (Exception e) {
            String msg = "VDI getByUuid failed " + uuid + " due to " + e.getMessage();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        }

    }

    private SR getIscsiSR(StoragePoolVO pool) {
        Connection conn = getConnection();

        Map<String, String> deviceConfig = new HashMap<String, String>();
        try {
            String target = pool.getHostAddress();
            String tmp[] = pool.getPath().split("/");
            String targetiqn = tmp[1];
            String scsiid = tmp[2];

            Set<SR> srs = SR.getAll(conn);
            for (SR sr : srs) {
                if (!sr.getType(conn).equalsIgnoreCase("lvmoiscsi"))
                    continue;

                Set<PBD> pbds = sr.getPBDs(conn);
                if (pbds.isEmpty())
                    continue;

                PBD pbd = pbds.iterator().next();

                Map<String, String> dc = pbd.getDeviceConfig(conn);

                if (dc == null)
                    continue;

                if (dc.get("target") == null)
                    continue;

                if (dc.get("targetIQN") == null)
                    continue;

                if (dc.get("SCSIid") == null)
                    continue;

                if (target.equals(dc.get("target")) && targetiqn.equals(dc.get("targetIQN")) && scsiid.equals(dc.get("SCSIid"))) {
                    return sr;
                }

            }
            deviceConfig.put("target", target);
            deviceConfig.put("targetIQN", targetiqn);
            deviceConfig.put("SCSIid", scsiid);

            Host host = Host.getByUuid(conn, _hostUuid);
            SR sr = SR.create(conn, host, deviceConfig, new Long(0), pool.getUuid(), pool.getName(), "lvmoiscsi", "user", true, new HashMap<String, String>());
            sr.scan(conn);
            return sr;

        } catch (XenAPIException e) {
            String msg = "Unable to create Iscsi SR  " + deviceConfig + " due to  " + e.toString();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        } catch (Exception e) {
            String msg = "Unable to create Iscsi SR  " + deviceConfig + " due to  " + e.getMessage();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        }
    }

    private SR getNfsSR(StoragePoolVO pool) {
        Connection conn = getConnection();

        Map<String, String> deviceConfig = new HashMap<String, String>();
        try {

            String server = pool.getHostAddress();
            String serverpath = pool.getPath();
            serverpath = serverpath.replace("//", "/");
            Set<SR> srs = SR.getAll(conn);
            for (SR sr : srs) {
                if (!sr.getType(conn).equalsIgnoreCase(SRType.NFS.toString()))
                    continue;

                Set<PBD> pbds = sr.getPBDs(conn);
                if (pbds.isEmpty())
                    continue;

                PBD pbd = pbds.iterator().next();

                Map<String, String> dc = pbd.getDeviceConfig(conn);

                if (dc == null)
                    continue;

                if (dc.get("server") == null)
                    continue;

                if (dc.get("serverpath") == null)
                    continue;

                if (server.equals(dc.get("server")) && serverpath.equals(dc.get("serverpath"))) {
                    return sr;
                }

            }

            deviceConfig.put("server", server);
            deviceConfig.put("serverpath", serverpath);
            Host host = Host.getByUuid(conn, _hostUuid);
            SR sr = SR.create(conn, host, deviceConfig, new Long(0), pool.getUuid(), pool.getName(), SRType.NFS.toString(), "user", true, new HashMap<String, String>());
            sr.scan(conn);
            return sr;

        } catch (XenAPIException e) {
            String msg = "Unable to create Iscsi SR  " + deviceConfig + " due to  " + e.toString();
            s_logger.warn(msg, e);
            throw new VmopsRuntimeException(msg, e);
        } catch (Exception e) {
            String msg = "Unable to create Iscsi SR  " + deviceConfig + " due to  " + e.getMessage();
            s_logger.warn(msg);
            throw new VmopsRuntimeException(msg, e);
        }
    }

    protected Answer execute(DestroyCommand cmd) {
        List<VolumeVO> vols;
        vols = cmd.getVolumes();
        try {
            Connection conn = getConnection();
            for (VolumeVO vol : vols) {
                // Look up the VDI
                String volumeUUID = vol.getPath();
                VDI vdi = getVDIbyUuid(volumeUUID);
                vdi.destroy(conn);
            }

            return new Answer(cmd, true, "Success");
        } catch (Exception e) {
            String msg = "Destroy vm failed " + " due to  " + e.getMessage() + e.toString();
            s_logger.warn(msg, e);
            return new Answer(cmd, true, msg);
        }
    }

    protected ShareAnswer execute(final ShareCommand cmd) {
        return new ShareAnswer(cmd, new HashMap<String, Integer>());
    }

    protected ManageVolumeAnswer execute(final ManageVolumeCommand cmd) {
        boolean add = cmd.getAdd();
        String srName = cmd.getFolder();
        String volumeUUID = cmd.getPath();
        String volumeNameLabel = cmd.getNameLabel();
        StoragePoolVO pool = cmd.getPool();
        s_logger.debug("Trying to do Volume operation add: " + add + " srName: " + srName + " volumeUUID " + volumeUUID + " volumeName " + volumeNameLabel + " storagePool "
                + pool.getName());

        String errorMsg;
        if (add) {
            errorMsg = "Failed to create volume";
        } else {
            errorMsg = "Failed to delete volume";
        }

        // Make sure the storage pool is valid
        if (pool.getUuid().isEmpty()) {
            return new ManageVolumeAnswer(cmd, false, "Pool doesn't have pool uuid", null, null, null);
        }
        VDI vdi = null;

        Connection conn = getConnection();
        try {
            if (add) {
                // Find a storage repository on the pool
                SR vmsr = getStorageRepository(pool);

                // Create a new data volume
                VDI.Record vdir = new VDI.Record();
                vdir.SR = vmsr;
                vdir.type = Types.VdiType.USER;
                vdir.virtualSize = cmd.getDiskSizeByte();
                vdir.nameLabel = "detached";
                vdi = VDI.create(conn, vdir);

                // Determine the UUID and size of the created data volume
                vdir = vdi.getRecord(conn);
                String uuid = vdir.uuid;
                long createdSize = vdir.virtualSize;

                // Determine the name of the SR
                srName = vmsr.getNameLabel(conn);

                return new ManageVolumeAnswer(cmd, true, null, createdSize, srName, uuid);
            } else {
                // Look up the VDI
                vdi = getVDIbyUuid(volumeUUID);

                // Destroy the VDI
                vdi.destroy(conn);

                return new ManageVolumeAnswer(cmd, true, null, null, null, null);
            }
        } catch (XenAPIException e) {
            if (add && vdi != null) {
                destroyVDI(vdi);
            }
            s_logger.warn(errorMsg + ": " + e.toString(), e);
            return new ManageVolumeAnswer(cmd, false, e.toString(), null, null, null);
        } catch (Exception e) {
            s_logger.warn(errorMsg + ": " + e.toString(), e);
            return new ManageVolumeAnswer(cmd, false, e.getMessage(), null, null, null);
        }
    }

    protected Answer execute(final AttachDiskCommand cmd) {
        boolean attach = cmd.getAttach();
        String vmName = cmd.getVmName();
        String volumeUUID = cmd.getVolumePath();

        String errorMsg;
        if (attach) {
            errorMsg = "Failed to attach volume";
        } else {
            errorMsg = "Failed to detach volume";
        }

        Connection conn = getConnection();
        try {
            // Look up the VDI
            VDI vdi = getVDIbyUuid(volumeUUID);
            // Look up the VM
            VM vm = getVM(conn, vmName);
            /* For HVM guest, if no pv driver installed, no attach/detach */
            boolean isHVM;
            if (vm.getPVBootloader(conn).equalsIgnoreCase(""))
                isHVM = true;
            else
                isHVM = false;
            VMGuestMetrics vgm = vm.getGuestMetrics(conn);
            boolean pvDrvInstalled = false;
            if (!isRefNull(vgm) && vgm.getPVDriversUpToDate(conn)) {
                pvDrvInstalled = true;
            }
            if (isHVM && !pvDrvInstalled) {
                s_logger.warn(errorMsg + ": You attempted an operation on a VM which requires PV drivers to be installed but the drivers were not detected");
                return new Answer(cmd, false, "No PV drivers installed in VM, please install it by attaching xen-pv-drv.iso");
            }
            if (attach) {
                // Figure out the disk number to attach the VM to
                String diskNumber = getUnusedDeviceNum(vm);

                // Create a new VBD
                VBD.Record vbdr = new VBD.Record();
                vbdr.VM = vm;
                vbdr.VDI = vdi;
                vbdr.bootable = false;
                vbdr.userdevice = diskNumber;
                vbdr.mode = Types.VbdMode.RW;
                vbdr.type = Types.VbdType.DISK;
                vbdr.unpluggable = true;
                VBD vbd = VBD.create(conn, vbdr);

                // Attach the VBD to the VM
                vbd.plug(conn);

                // Update the VDI's label to include the VM name
                vdi.setNameLabel(conn, vmName + "-DATA");

                return new Answer(cmd);
            } else {
                // Look up all VBDs for this VDI
                Set<VBD> vbds = vdi.getVBDs(conn);

                // Detach each VBD from its VM, and then destroy it
                for (VBD vbd : vbds) {
                    vbd.unplug(conn);
                    vbd.destroy(conn);
                }

                // Update the VDI's label to be "detached"
                vdi.setNameLabel(conn, "detached");

                return new Answer(cmd);
            }
        } catch (XenAPIException e) {
            s_logger.warn(errorMsg + ": " + e.toString(), e);
            return new Answer(cmd, false, e.toString());
        } catch (Exception e) {
            s_logger.warn(errorMsg + ": " + e.toString(), e);
            return new Answer(cmd, false, e.getMessage());
        }

    }

    protected Answer execute(final AttachIsoCommand cmd) {
        boolean attach = cmd.isAttach();
        String vmName = cmd.getVmName();
        String isoURL = cmd.getIsoPath();

        String errorMsg;
        if (attach) {
            errorMsg = "Failed to attach ISO";
        } else {
            errorMsg = "Failed to detach ISO";
        }

        Connection conn = getConnection();
        try {
            if (attach) {
                VBD isoVBD = null;

                // Find the VM
                VM vm = getVM(conn, vmName);

                // Find the ISO VDI
                VDI isoVDI = getIsoVDIByURL(conn, isoURL);

                // Find the VM's CD-ROM VBD
                Set<VBD> vbds = vm.getVBDs(conn);
                for (VBD vbd : vbds) {
                    String userDevice = vbd.getUserdevice(conn);
                    Types.VbdType type = vbd.getType(conn);

                    if (userDevice.equals("3") && type == Types.VbdType.CD) {
                        isoVBD = vbd;
                        break;
                    }
                }

                if (isoVBD == null) {
                    throw new VmopsRuntimeException("Unable to find CD-ROM VBD for VM: " + vmName);
                } else {
                    // If an ISO is already inserted, eject it
                    if (isoVBD.getEmpty(conn) == false) {
                        isoVBD.eject(conn);
                    }

                    // Insert the new ISO
                    isoVBD.insert(conn, isoVDI);
                }

                return new Answer(cmd);
            } else {
                // Find the VM
                VM vm = getVM(conn, vmName);
                String vmUUID = vm.getUuid(conn);

                // Find the ISO VDI
                VDI isoVDI = getIsoVDIByURL(conn, isoURL);
                
                SR sr = isoVDI.getSR(conn);

                // Look up all VBDs for this VDI
                Set<VBD> vbds = isoVDI.getVBDs(conn);

                // Iterate through VBDs, and if the VBD belongs the VM, eject the ISO from it
                for (VBD vbd : vbds) {
                    VM vbdVM = vbd.getVM(conn);
                    String vbdVmUUID = vbdVM.getUuid(conn);

                    if (vbdVmUUID.equals(vmUUID)) {
                        // If an ISO is already inserted, eject it
                        if (!vbd.getEmpty(conn)) {
                            vbd.eject(conn);
                        }

                        break;
                    }
                }
                
                return new Answer(cmd);
            }
        } catch (XenAPIException e) {
            s_logger.warn(errorMsg + ": " + e.toString(), e);
            return new Answer(cmd, false, e.toString());
        } catch (Exception e) {
            s_logger.warn(errorMsg + ": " + e.toString(), e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    protected ManageSnapshotAnswer execute(final ManageSnapshotCommand cmd) {

        long snapshotId = cmd.getSnapshotId();
        String snapshotName = cmd.getSnapshotName();

        Connection conn = getConnection();
        try {
            if (cmd.getCommandSwitch().equals(ManageSnapshotCommand.CREATE_SNAPSHOT)) {
                // Look up the volume
                String volumeUUID = cmd.getVolumePath();
                VDI volume = getVDIbyUuid(volumeUUID);

                // Create a snapshot
                VDI snapshot = volume.snapshot(conn, new HashMap<String, String>());

                if (snapshotName != null) {
                    snapshot.setNameLabel(conn, snapshotName);
                }

                // Determine the UUID of the snapshot
                VDI.Record vdir = snapshot.getRecord(conn);
                String snapshotUUID = vdir.uuid;

                return new ManageSnapshotAnswer(cmd, snapshotId, snapshotUUID, true, null);
            } else if (cmd.getCommandSwitch().equals(ManageSnapshotCommand.DESTROY_SNAPSHOT)) {
                // Look up the snapshot
                String snapshotUUID = cmd.getSnapshotPath();
                VDI snapshot = getVDIbyUuid(snapshotUUID);

                snapshot.destroy(conn);
                return new ManageSnapshotAnswer(cmd, snapshotId, null, true, null);
            } else {
                return new ManageSnapshotAnswer(cmd, snapshotId, null, false, "Unsupported snapshot command.");
            }
        } catch (XenAPIException e) {
            s_logger.warn("ManageSnapshot Failed due to " + e.toString(), e);
            return new ManageSnapshotAnswer(cmd, snapshotId, null, false, e.toString());
        } catch (Exception e) {
            s_logger.warn("ManageSnapshot Failed due to " + e.getMessage(), e);
            return new ManageSnapshotAnswer(cmd, snapshotId, null, false, e.getMessage());
        }

    }

    protected CreatePrivateTemplateAnswer execute(final CreatePrivateTemplateCommand cmd) {
        String secondaryStorageURL = cmd.getSecondaryStorageURL();
        String snapshotUUID = cmd.getSnapshotPath();
        String userSpecifiedName = cmd.getTemplateName();

        Connection conn = getConnection();
        try {
            // Look up the VDI
            VDI snapshot = getVDIbyUuid(snapshotUUID);

            // Copy the snapshot to secondary storage
            SR secondaryStorage = getNfsSRbyMountPoint(secondaryStorageURL + "/template", false);
            VDI privateTemplate = snapshot.copy(conn, secondaryStorage);

            if (userSpecifiedName != null) {
                privateTemplate.setNameLabel(conn, userSpecifiedName);
            }

            // Determine the URL of the private template
            VDI.Record vdir = privateTemplate.getRecord(conn);
            String privateTemplateURL = "template/" + vdir.uuid + ".vhd";

            return new CreatePrivateTemplateAnswer(cmd, true, null, privateTemplateURL);

        } catch (XenAPIException e) {
            s_logger.warn("CreatePrivateTemplate Failed due to " + e.toString(), e);
            return new CreatePrivateTemplateAnswer(cmd, false, e.toString(), null);
        } catch (Exception e) {
            s_logger.warn("CreatePrivateTemplate Failed due to " + e.getMessage(), e);
            return new CreatePrivateTemplateAnswer(cmd, false, e.getMessage(), null);
        }
    }

    protected BackupSnapshotAnswer execute(final BackupSnapshotCommand cmd) {
        String primaryStorageNameLabel = cmd.getPrimaryStoragePoolUuid();
        String snapshotUuid = cmd.getSnapshotUuid(); // not null: Precondition.
        String volumeName = cmd.getVolumeName();
        String secondaryStoragePoolURL = cmd.getSecondaryStoragePoolURL();
        String lastBackedUpSnapshotUuid = cmd.getLastBackedUpSnapshotUuid();
        String prevSnapshotUuid = cmd.getPrevSnapshotUuid();
        boolean isFirstSnapshotOfRootVolume = cmd.isFirstSnapshotOfRootVolume();

        String details = null;
        boolean success = false;
        String backupSnapshotName = null;
        try {
            Connection conn = getConnection();
            SR primaryStorageSR = getSRByNameLabelandHost(primaryStorageNameLabel);
            String primaryStorageUuid = primaryStorageSR.getUuid(conn);
            String secondaryStorageMountPath = getNFSStorageMountPath(secondaryStoragePoolURL);
            
            if (secondaryStorageMountPath == null) {
                details = "Couldn't backup snapshot because the URL passed: " + secondaryStoragePoolURL + " is invalid.";
            }
            else {
                backupSnapshotName = backupSnapshot(primaryStorageUuid, snapshotUuid, volumeName, secondaryStorageMountPath, lastBackedUpSnapshotUuid, isFirstSnapshotOfRootVolume);
                success = (backupSnapshotName != null);
            }
            
            if (!success) {
                // destroy the VDI corresponding to the current snapshotId too
                // And hope that XenServer coalesces back the base copy (which we couldn't backup)
                // to it's parent.
                // Else further snapshots will be corrupted.
                // snapshotUuid is not null.
                details = "Couldn't backup the snapshot " + snapshotUuid + " to secondary storage. Destroying it on primary storage.";
                destroySnapshotOnPrimaryStorage(snapshotUuid);
                
            }
            else if (prevSnapshotUuid != null) {
                // Destroy the previous snapshot, if it exists.
                // We destroy the previous snapshot only if the current snapshot backup succeeds.
                // The aim is to keep the VDI of the last 'successful' snapshot so that it doesn't get merged with the new one
                // and muddle the vhd chain on the secondary storage.
                details = "Successfully backedUp the snapshot " + snapshotUuid + " to secondary storage.";
                destroySnapshotOnPrimaryStorage(prevSnapshotUuid);
            }

        } catch (XenAPIException e) {
            details = "BackupSnapshot Failed due to " + e.toString();
            s_logger.warn(details, e);
        } catch (Exception e) {
            details = "BackupSnapshot Failed due to " + e.getMessage();
            s_logger.warn(details, e);
        }
        
        s_logger.debug(details);
        return new BackupSnapshotAnswer(cmd, success, details, backupSnapshotName);
    }

    protected CreateVolumeFromSnapshotAnswer execute(final CreateVolumeFromSnapshotCommand cmd) {
        String primaryStorageNameLabel = cmd.getPrimaryStoragePoolUuid();
        String volumeName = cmd.getVolumeName();
        String secondaryStoragePoolURL = cmd.getSecondaryStoragePoolName();
        String backedUpSnapshotUuid = cmd.getBackedUpSnapshotUuid();
        String templateUUID = cmd.getTemplateUUID();
        
        // By default, assume the command has failed and set the params to be
        // passed to CreateVolumeFromSnapshotAnswer appropriately
        boolean result = false;
        // Generic error message.
        String details = "Failed to create volume from snapshot for volume: " + volumeName + " with backupUuid: " + backedUpSnapshotUuid;
        String newVdiUUID = null;
        try {
            Connection conn = getConnection();
            SR primaryStorageSR = getSRByNameLabelandHost(primaryStorageNameLabel);
            String primaryStorageSRUUID = primaryStorageSR.getUuid(conn);

            String rootVdiUUID = null;
            if (templateUUID != null) {
                VDI rootVDI = null;
                VDI tmpltvdi = getVDIbyUuid(templateUUID);
    
                synchronized (this.getClass()) {
                    rootVDI = tmpltvdi.createClone(conn, new HashMap<String, String>());
                }
    
                rootVDI.setNameLabel(conn, volumeName);
                rootVdiUUID = rootVDI.getUuid(conn);
            }

            String secondaryStorageMountPath = getNFSStorageMountPath(secondaryStoragePoolURL);

            if (secondaryStorageMountPath == null) {
                details += " because the URL passed: " + secondaryStoragePoolURL + " is invalid.";
                return new CreateVolumeFromSnapshotAnswer(cmd, result, details, newVdiUUID);
            }
            String vdiUUID = createVolumeFromSnapshot(primaryStorageSRUUID, primaryStorageNameLabel, volumeName, secondaryStorageMountPath, backedUpSnapshotUuid);
            if (vdiUUID == null) {
                details += " because the vmops plugin on XenServer failed at some point";
            }
            else {
                String mountPointOfTemporaryDirOnSecondaryStorage = secondaryStoragePoolURL + File.separator + "snapshots" + File.separator + volumeName + "_temp";
                URI uri = new URI(mountPointOfTemporaryDirOnSecondaryStorage);
                // No need to check if the SR already exists. It's a temporary SR destroyed when this method exits.
                // And two createVolumeFromSnapshot operations cannot proceed at the same time.
                SR temporarySROnSecondaryStorage = createNfsSRbyURI(uri, false);
                if (temporarySROnSecondaryStorage == null) {
                    details += "because SR couldn't be created on " + mountPointOfTemporaryDirOnSecondaryStorage;
                }
                else {
                    s_logger.debug("Successfully created temporary SR on secondary storage " + temporarySROnSecondaryStorage.getNameLabel(conn) + "with uuid " + temporarySROnSecondaryStorage.getUuid(conn) + " and scanned it");
                    // createNFSSRbyURI also scans the SR and introduces the VDI
                    
                    VDI vdi = getVDIbyUuid(vdiUUID);
                    
                    if (vdi != null) {
                       s_logger.debug("Successfully created VDI on secondary storage SR " + temporarySROnSecondaryStorage.getNameLabel(conn) + " with uuid " + vdiUUID);
                       s_logger.debug("Copying VDI from secondary to primary");
                       VDI vdiOnPrimaryStorage = vdi.copy(conn, primaryStorageSR);
                       // vdi.copy introduces the vdi into the database. Don't need to do a scan on the primary storage.
                       
                       // Whether the vdi was copied successfully or not, we need to destroy the temporary SR created.
                       // This will unmount the temp dir
                       // Then delete the contents of the temp dir
                       String tempSRNameLabel = secondaryStorageMountPath + File.separator + volumeName + "_temp";
                       SR temporarySR = forgetSRByNameLabelandHost(tempSRNameLabel);
                       if (temporarySR == null) {
                           // Do we have to raise a more severe alarm?
                           s_logger.error("Could not destroy the temporary SR mounted on " + tempSRNameLabel);
                       }
                       if (vdiOnPrimaryStorage != null) {
                           newVdiUUID = vdiOnPrimaryStorage.getUuid(conn);
                           s_logger.debug("Successfully copied and introduced VDI on primary storage with path " + vdiOnPrimaryStorage.getLocation(conn) + " and uuid " + newVdiUUID);
                           if (rootVdiUUID != null) {
                               boolean stitched = stitchSnapshotDeltaToTemplate(primaryStorageSRUUID, rootVdiUUID, newVdiUUID);
                               if (stitched) {
                                   result = true;
                                   details = null;
                               }
                               else {
                                   details += " because the newly cloned root VDI " + rootVdiUUID + " and the snapshot VDI " + newVdiUUID + " couldn't be stitched together";
                                   // result is false, set resultObject to null.
                                   newVdiUUID = null;
                               }
                           }
                           else {
                               // Creating volume of data disk. No stitching needs to be done. Volume has been successfully created.  
                               result = true;
                               details = null;
                           }
                       }
                       else {
                           details += ". Could not copy the vdi " + vdi.getUuid(conn) + " to primary storage";
                       }
                    }
                    else {
                        details += ". Could not scan and introduce vdi with uuid: " + vdiUUID;
                    }
                }
            }
        } catch (XenAPIException e) {
            details += " due to " + e.toString();
            s_logger.warn(details, e);
        } catch (Exception e) {
            details += " due to " + e.getMessage();
            s_logger.warn(details, e);
        }
        if (!result) {
            // Is this logged at a higher level?
            s_logger.error(details);
        }
        
        // In all cases return something.
        return new CreateVolumeFromSnapshotAnswer(cmd, result, details, newVdiUUID);
    }

    

    protected DeleteSnapshotBackupAnswer execute(final DeleteSnapshotBackupCommand cmd) {
        String volumeName = cmd.getVolumeName();
        String secondaryStoragePoolURL = cmd.getSecondaryStoragePoolURL();
        String backupUUID = cmd.getBackupUUID();
        String childUUID = cmd.getChildUUID();

        String secondaryStorageMountPath = getNFSStorageMountPath(secondaryStoragePoolURL);
        String details = null;
        boolean success = false;
        if (secondaryStorageMountPath == null) {
            details = "Couldn't delete snapshot because the URL passed: " + secondaryStoragePoolURL + " is invalid.";
        }
        else {
            details = deleteSnapshotBackup(volumeName, secondaryStorageMountPath, backupUUID, childUUID);
            success = (details != null && details.equals("1"));
            if (success) {
                s_logger.debug("Successfully deleted snapshot backup " + backupUUID);
            }
        }

        return new DeleteSnapshotBackupAnswer(cmd, success, details);
    }

    private VM getVM(Connection conn, String vmName) {
        // Look up VMs with the specified name
        Set<VM> vms;
        try {
            vms = VM.getByNameLabel(conn, vmName);
        } catch (XenAPIException e) {
            throw new VmopsRuntimeException("Unable to get " + vmName + ": " + e.toString(), e);
        } catch (Exception e) {
            throw new VmopsRuntimeException("Unable to get " + vmName + ": " + e.getMessage(), e);
        }

        // If there are no VMs, throw an exception
        if (vms.size() == 0)
            throw new VmopsRuntimeException("VM with name: " + vmName + " does not exist.");

        // If there is more than one VM, print a warning
        if (vms.size() > 1)
            s_logger.warn("Found " + vms.size() + " VMs with name: " + vmName);

        // Return the first VM in the set
        return vms.iterator().next();
    }

    private VDI getIsoVDIByURL(Connection conn, String isoURL) {
        SR isoSR = null;
        String mountpoint = null;
        if (isoURL.startsWith("xs-tools")) {
            try {
                Set<VDI> vdis = VDI.getByNameLabel(conn, isoURL);
                if (vdis.isEmpty()) {
                    throw new VmopsRuntimeException("Could not find ISO with URL: " + isoURL);
                }
                return vdis.iterator().next();

            } catch (XenAPIException e) {
                throw new VmopsRuntimeException("Unable to get pv iso: " + isoURL + " due to " + e.toString());
            } catch (Exception e) {
                throw new VmopsRuntimeException("Unable to get pv iso: " + isoURL + " due to " + e.toString());
            }
        }

        int index = isoURL.lastIndexOf("/");
        mountpoint = isoURL.substring(0, index);
        isoSR = getIsoSRbyMountPoint(mountpoint, true);

        try {
            isoSR.scan(conn);
        } catch (XenAPIException e) {
            throw new VmopsRuntimeException("Unable to scan " + mountpoint + " due to " + e.toString(), e);
        } catch (Exception e) {
            throw new VmopsRuntimeException("Unable to scan " + mountpoint + " due to " + e.getMessage(), e);
        }

        String isoName = isoURL.substring(index + 1);

        VDI isoVDI = getVDIbyLocationandSR(isoName, isoSR);

        if (isoVDI != null) {
            return isoVDI;
        } else {
            throw new VmopsRuntimeException("Could not find ISO with URL: " + isoURL);
        }
    }

    private SR getStorageRepository(StoragePoolVO pool) {
        Set<SR> srs;
        try {
            Connection conn = getConnection();
            srs = SR.getByNameLabel(conn, pool.getUuid());
        } catch (XenAPIException e) {
            throw new VmopsRuntimeException("Unable to get SR " + pool.getUuid() + " due to " + e.toString(), e);
        } catch (Exception e) {
            throw new VmopsRuntimeException("Unable to get SR " + pool.getUuid() + " due to " + e.getMessage(), e);
        }

        if (srs.size() > 1) {
            throw new VmopsRuntimeException("More than one storage repository was found for pool with uuid: " + pool.getUuid());
        } else if (srs.size() == 1) {
            return srs.iterator().next();
        } else {
            if (pool.getPoolType() == StoragePool.StoragePoolType.NetworkFilesystem)
                return getNfsSR(pool);
            else if (pool.getPoolType() == StoragePool.StoragePoolType.IscsiLUN)
                return getIscsiSR(pool);
            else
                throw new VmopsRuntimeException("The pool type: " + pool.getPoolType().name() + " is not supported.");
        }
    }

    protected Answer execute(final CheckConsoleProxyLoadCommand cmd) {
        return executeProxyLoadScan(cmd, cmd.getProxyVmId(), cmd.getProxyVmName(), cmd.getProxyManagementIp(), cmd.getProxyCmdPort());
    }

    protected Answer execute(final WatchConsoleProxyLoadCommand cmd) {
        return executeProxyLoadScan(cmd, cmd.getProxyVmId(), cmd.getProxyVmName(), cmd.getProxyManagementIp(), cmd.getProxyCmdPort());
    }

    private Answer executeProxyLoadScan(final Command cmd, final long proxyVmId, final String proxyVmName, final String proxyManagementIp, final int cmdPort) {
        String result = null;

        final StringBuffer sb = new StringBuffer();
        sb.append("http://").append(proxyManagementIp).append(":" + cmdPort).append("/cmd/getstatus");

        boolean success = true;
        try {
            final URL url = new URL(sb.toString());
            final URLConnection conn = url.openConnection();

            // setting TIMEOUTs to avoid possible waiting until death situations
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            final InputStream is = conn.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            final StringBuilder sb2 = new StringBuilder();
            String line = null;
            try {
                while ((line = reader.readLine()) != null)
                    sb2.append(line + "\n");
                result = sb2.toString();
            } catch (final IOException e) {
                success = false;
            } finally {
                try {
                    is.close();
                } catch (final IOException e) {
                    s_logger.warn("Exception when closing , console proxy address : " + proxyManagementIp);
                    success = false;
                }
            }
        } catch (final IOException e) {
            s_logger.warn("Unable to open console proxy command port url, console proxy address : " + proxyManagementIp);
            success = false;
        }

        return new ConsoleProxyLoadAnswer(cmd, proxyVmId, proxyVmName, success, result);
    }

    // Each argument is put in a separate line for readability.
    // Using more lines does not harm the environment.
    private String backupSnapshot(String primaryStorageSRUuid,
                                  String snapshotUuid,
                                  String volumeName,
                                  String secondaryStorageMountPath,
                                  String lastBackedUpSnapshotUuid,
                                  Boolean isFirstSnapshotOfRootVolume)
    {
        String backupSnapshotUuid = null;

        // Each argument is put in a separate line for readability.
        // Using more lines does not harm the environment.
        String results = callHostPlugin("backupSnapshot",
                                        "primaryStorageSRUuid",
                                        primaryStorageSRUuid,
                                        "snapshotUuid",
                                        snapshotUuid,
                                        "volumeName",
                                        volumeName,
                                        "secondaryStorageMountPath",
                                        secondaryStorageMountPath,
                                        "lastBackedUpSnapshotUuid",
                                        lastBackedUpSnapshotUuid,
                                        "isFirstSnapshotOfRootVolume",
                                        isFirstSnapshotOfRootVolume.toString());

        if (results == null || results.isEmpty()) {
            // errString is already logged.
            return null;
        }

        String[] tmp = results.split("#");
        String status = tmp[0];
        backupSnapshotUuid = tmp[1];
        s_logger.debug("callHostPlugin returned status: " + status + " backupSnapshot: " + backupSnapshotUuid);

        // status == "1" if and only if backupSnapshotUuid != null
        // So we don't rely on status value but return backupSnapshotUuid as an indicator of success.
        String failureString = "Could not copy " + snapshotUuid + " from primary storage " + primaryStorageSRUuid + " to secondary storage " + secondaryStorageMountPath;
        if (status != null && status.equalsIgnoreCase("1") && backupSnapshotUuid != null) {
            s_logger.debug("Successfully copied base copy of snapshot " + snapshotUuid + " to secondary storage with uuid " + backupSnapshotUuid);
        } else {
            s_logger.debug(failureString + ". Failed with status " + status + " with backupSnapshotUuid " + backupSnapshotUuid);
        }

        return backupSnapshotUuid;
    }

    private boolean destroySnapshotOnPrimaryStorage(String snapshotUuid) {
        // Precondition prevSnapshotUuid != null

        try {
            Connection conn = getConnection();
            VDI prevSnapshot = getVDIbyUuid(snapshotUuid);
            prevSnapshot.destroy(conn);
            s_logger.debug("Successfully destroyed snapshot " + snapshotUuid + " on primary storage");
            return true;
        } catch (XenAPIException e) {
            String msg = "Destroy snapshot on primary storage " + snapshotUuid + " failed due to " + e.toString();
            s_logger.error(msg, e);
        } catch (Exception e) {
            String msg = "Destroy snapshot on primary storage " + snapshotUuid + " failed due to " + e.getMessage();
            s_logger.warn(msg, e);
        }

        return false;
    }

    private String deleteSnapshotBackup(String volumeName, String secondaryStorageMountPath, String backupUUID, String childUUID) {

        String result = callHostPlugin("deleteSnapshotBackup", "backupUUID", backupUUID, "childUUID", childUUID, "volumeName", volumeName, "secondaryStorageMountPath",
                secondaryStorageMountPath);

        return result;
    }

    private String createVolumeFromSnapshot(String primaryStorageSRUUID,
                                            String primaryStorageSRNameLabel,
                                            String volumeName,
                                            String secondaryStorageMountPath,
                                            String backedUpSnapshotUuid)
    {
        String vdiUUID = null;

        String failureString = "Could not create volume from " + backedUpSnapshotUuid;
        String results = callHostPlugin("createVolumeFromSnapshot",
                                        "primaryStorageSRUuid",
                                        primaryStorageSRUUID,
                                        "volumeName",
                                        volumeName,
                                        "secondaryStorageMountPath",
                                        secondaryStorageMountPath,
                                        "backedUpSnapshotUuid",
                                        backedUpSnapshotUuid);
        

        if (results == null || results.isEmpty()) {
            // Command threw an exception which has already been logged.
            return null;
        }
        String[] tmp = results.split("#");
        String status = tmp[0];
        vdiUUID = tmp[1];
        // status == "1" if and only if vdiUUID != null
        // So we don't rely on status value but return vdiUUID as an indicator of success.
    
        if (status != null && status.equalsIgnoreCase("1") && vdiUUID != null) {
            s_logger.debug("Successfully created vhd file with all data on secondary storage : " + vdiUUID);
        } else {
            s_logger.debug(failureString + ". Failed with status " + status + " with vdiUuid " + vdiUUID);
        }
        return vdiUUID;
        
    }

    private boolean stitchSnapshotDeltaToTemplate(String primaryStorageSRUuid,
                                                  String rootVdiUUID,
                                                  String newVdiUUID)
    {
        boolean stitched = false;
        String results =  callHostPlugin("stitchSnapshotDeltaToTemplate",
                                         "primaryStorageSRUuid",
                                         primaryStorageSRUuid,
                                         "rootVdiUUID",
                                         rootVdiUUID,
                                         "snapshotVdiUUID",
                                         newVdiUUID);
        
        if (results != null && !results.isEmpty()) {
            String[] tmp = results.split("#");
            String status = tmp[0];
            if (status != null && status.equalsIgnoreCase("1")) {
                s_logger.debug("Successfully stitched rootVdi: " + rootVdiUUID + " and snapshotVdi: " + newVdiUUID);
                stitched = true;
            } else {
                s_logger.debug("Could not stitch rootVdi: " + rootVdiUUID + " and snapshotVdi: " + newVdiUUID);
            }
        }
        
        return stitched;
    }
    
    private String getNFSStorageMountPath(String secondaryStoragePoolURL) {
        URI uri = null;
        try {
            uri = new URI(secondaryStoragePoolURL);
            
        } catch (URISyntaxException e) {
            s_logger.error("Given URL: " + secondaryStoragePoolURL + " is not valid " + e.getMessage(), e);
            return null;
        }
        String secondaryStorageMountPath = uri.getHost() + ":" + uri.getPath() + File.separator + "snapshots";
        return secondaryStorageMountPath;
    }
    
    private void syncDomRIPMap(String vm){
        //VM is a DomR, get its IP and add to domR-IP map
        Connection conn = getConnection();
        VM vm1 = getVM(conn, vm);
        try {
            String pvargs = vm1.getPVArgs(conn);
            if(pvargs != null){
                pvargs = pvargs.replaceAll(" ", "\n");
                Properties pvargsProps = new Properties();
                pvargsProps.load(new StringReader(pvargs));
                String ip = pvargsProps.getProperty("eth1ip");
                if(ip != null){
                    _domrIPMap.put(vm, ip);
                }
            }
        } catch (BadServerResponse e) {
            String msg = "Unable to update domR IP map due to: " + e.toString();
            s_logger.warn(msg, e);
        } catch (XenAPIException e) {
            String msg = "Unable to update domR IP map due to: " + e.toString();
            s_logger.warn(msg, e);
        } catch (XmlRpcException e) {
            String msg = "Unable to update domR IP map due to: " + e.toString();
            s_logger.warn(msg, e);
        } catch (IOException e) {
            String msg = "Unable to update domR IP map due to: " + e.toString();
            s_logger.warn(msg, e);
        }
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        disconnected();
        return true;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public IAgentControl getAgentControl() {
        return _agentControl;
    }

    @Override
    public void setAgentControl(IAgentControl agentControl) {
        _agentControl = agentControl;
    }
}
