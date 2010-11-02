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
 
 function afterLoadPodJSP($midmenuItem1) {
    hideMiddleMenu();	
          	
    initAddHostButton($("#midmenu_add_link"), "pod_page"); 
    initAddPrimaryStorageButton($("#midmenu_add2_link"), "pod_page");  
           
    initDialog("dialog_add_host");
    initDialog("dialog_add_pool");
    
    // if hypervisor is KVM, limit the server option to NFS for now
    if (getHypervisorType() == 'kvm') 
	    $("#dialog_add_pool").find("#add_pool_protocol").empty().html('<option value="nfs">NFS</option>');	
    bindEventHandlerToDialogAddPool();	 
    	    
	podJsonToRightPanel($midmenuItem1);     	
}

function podJsonToRightPanel($leftmenuItem1) {	 
    podJsonToDetailsTab($leftmenuItem1);
}

function podJsonToDetailsTab($leftmenuItem1) {	
    var jsonObj = $leftmenuItem1.data("jsonObj");     
    var $detailsTab = $("#tab_content_details");   
    $detailsTab.data("jsonObj", jsonObj);           
    $detailsTab.find("#id").text(fromdb(jsonObj.id));
    $detailsTab.find("#name").text(fromdb(jsonObj.name));
    $detailsTab.find("#cidr").text(fromdb(jsonObj.cidr));        
    $detailsTab.find("#ipRange").text(getIpRange(jsonObj.startip, jsonObj.endip));
    $detailsTab.find("#gateway").text(fromdb(jsonObj.gateway));  
    
    //actions ***   
    var $actionLink = $detailsTab.find("#action_link"); 
    $actionLink.bind("mouseover", function(event) {	    
        $(this).find("#action_menu").show();    
        return false;
    });
    $actionLink.bind("mouseout", function(event) {       
        $(this).find("#action_menu").hide();    
        return false;
    });	  
    var $actionMenu = $detailsTab.find("#action_link #action_menu");
    $actionMenu.find("#action_list").empty();   
    buildActionLinkForDetailsTab("Delete Pod", podActionMap, $actionMenu, $leftmenuItem1, $detailsTab);  
}	

function podJsonClearRightPanel(jsonObj) {	 
    podJsonClearDetailsTab(jsonObj);
}

function podJsonClearDetailsTab(jsonObj) {	    
    var $detailsTab = $("#tab_content_details");       
    $detailsTab.find("#id").text("");
    $detailsTab.find("#name").text("");
    $detailsTab.find("#cidr").text("");        
    $detailsTab.find("#ipRange").text("");
    $detailsTab.find("#gateway").text("");  
    
    //if (getDirectAttachUntaggedEnabled() == "true") 
	//	$("#submenu_content_zones #action_add_directip_vlan").data("type", "pod").data("id", obj.id).data("name", obj.name).data("zoneid", obj.zoneid).show();		
}
	
function getIpRange(startip, endip) {
    var ipRange = "";
	if (startip != null && startip.length > 0) {
		ipRange = startip;
	}
	if (endip != null && endip.length > 0) {
		ipRange = ipRange + " - " + endip;
	}		
	return ipRange;
}	

function initAddHostButton($midmenuAddLink1, currentPageInRightPanel) {
    $midmenuAddLink1.find("#label").text("Add Host"); 
    $midmenuAddLink1.show();
    $midmenuAddLink1.unbind("click").bind("click", function(event) {     
        dialogAddHost = $("#dialog_add_host");      
        dialogAddHost.find("#info_container").hide();    
        dialogAddHost.find("#new_cluster_name").val("");
        
        var zoneId, podId;        
        if(currentPageInRightPanel == "pod_page") {
            var podObj = $("#tab_content_details").data("jsonObj");   
            zoneId = podObj.zoneid;
            podId = podObj.id;
            dialogAddHost.find("#zone_name").text(fromdb(podObj.zonename));  
            dialogAddHost.find("#pod_name").text(fromdb(podObj.name)); 
        }
        else if(currentPageInRightPanel == "host_page") {
            var hostObj = $("#tab_content_details").data("jsonObj");  
            zoneId = hostObj.zoneid;
            podId = hostObj.podid; 
            dialogAddHost.find("#zone_name").text(fromdb(hostObj.zonename));  
            dialogAddHost.find("#pod_name").text(fromdb(hostObj.podname)); 
        }
        else if(currentPageInRightPanel == "primarystorage_page") {
            var primarystorageObj = $("#tab_content_details").data("jsonObj");   
            zoneId = primarystorageObj.zoneid;
            podId = primarystorageObj.podid;           
            dialogAddHost.find("#zone_name").text(fromdb(primarystorageObj.zonename));  
            dialogAddHost.find("#pod_name").text(fromdb(primarystorageObj.podname)); 
        }
                                  
        $.ajax({
	       data: createURL("command=listClusters&podid="+podId+maxPageSize),
            dataType: "json",
            success: function(json) {			            
                var items = json.listclustersresponse.cluster;
                var clusterSelect = dialogAddHost.find("#cluster_select").empty();		
                if(items != null && items.length > 0) {			                
                    for(var i=0; i<items.length; i++) 			                    
                        clusterSelect.append("<option value='" + items[i].id + "'>" + fromdb(items[i].name) + "</option>");		      
                    dialogAddHost.find("input[value=existing_cluster_radio]").attr("checked", true);
                }
                else {
				    clusterSelect.append("<option value='-1'>None Available</option>");
                    dialogAddHost.find("input[value=new_cluster_radio]").attr("checked", true);
                }
            }
        });           
	        	    
        dialogAddHost
        .dialog('option', 'buttons', { 				
	        "Add": function() { 
	            var $thisDialog = $(this);		            
	            			   
		        var clusterRadio = $thisDialog.find("input[name=cluster]:checked").val();				
			
		        // validate values
		        var isValid = true;									
		        isValid &= validateString("Host name", $thisDialog.find("#host_hostname"), $thisDialog.find("#host_hostname_errormsg"));
		        isValid &= validateString("User name", $thisDialog.find("#host_username"), $thisDialog.find("#host_username_errormsg"));
		        isValid &= validateString("Password", $thisDialog.find("#host_password"), $thisDialog.find("#host_password_errormsg"));						
		        if (!isValid) 
		            return;
		            
				//$thisDialog.dialog("close");   //only close dialog when this action succeeds		
				$thisDialog.find("#spinning_wheel").show() 				
				
		        var array1 = [];    
		        array1.push("&zoneId="+zoneId);
		        array1.push("&podId="+podId);
						      
		        var username = trim($thisDialog.find("#host_username").val());
		        array1.push("&username="+encodeURIComponent(username));
				
		        var password = trim($thisDialog.find("#host_password").val());
		        array1.push("&password="+encodeURIComponent(password));
					
				var newClusterName, existingClusterId;							
			    if(clusterRadio == "new_cluster_radio") {
		            newClusterName = trim($thisDialog.find("#new_cluster_name").val());
		            array1.push("&clustername="+todb(newClusterName));				    
		        }
		        else if(clusterRadio == "existing_cluster_radio") {			            
		            existingClusterId = $thisDialog.find("#cluster_select").val();
				    // We will default to no cluster if someone selects Join Cluster with no cluster available.
				    if (existingClusterId != '-1') {
					    array1.push("&clusterid="+existingClusterId);
				    }
		        }				
				
		        var hostname = trim($thisDialog.find("#host_hostname").val());
		        var url;					
		        if(hostname.indexOf("http://")==-1)
		            url = "http://" + todb(hostname);
		        else
		            url = hostname;
		        array1.push("&url="+encodeURIComponent(url));
									
		        //var $midmenuItem1 = beforeAddingMidMenuItem() ;    				
		        
		        $.ajax({
			       data: createURL("command=addHost" + array1.join("")),
			        dataType: "json",
			        success: function(json) {
			            $thisDialog.find("#spinning_wheel").hide();
			            $thisDialog.dialog("close");
					
					    showMiddleMenu();
					    
					    /*
					    var $midmenuItem1 = $("#midmenu_item").clone();
                        $("#midmenu_container").append($midmenuItem1.fadeIn("slow"));
                        var items = json.addhostresponse.host;				            			      										   
					    hostToMidmenu(items[0], $midmenuItem1);
	                    bindClickToMidMenu($midmenuItem1, hostToRightPanel, hostGetMidmenuId); 
			           
                        if(items.length > 1) { 
                            for(var i=1; i<items.length; i++) {                                    
                                var $midmenuItem2 = $("#midmenu_item").clone();
                                hostToMidmenu(items[i], $midmenuItem2);
                                bindClickToMidMenu($midmenuItem2, hostToRightPanel, hostGetMidmenuId); 
                                $("#midmenu_container").append($midmenuItem2.fadeIn("slow"));                                   
                            }	
                        }   
                        */                             
                        
                        clickClusterNodeAfterAddHost(clusterRadio, podId, newClusterName, existingClusterId, $thisDialog);                                  
			        },			
                    error: function(XMLHttpResponse) {	
                        clickClusterNodeAfterAddHost(clusterRadio, podId, newClusterName, existingClusterId, $thisDialog);                                  
                        handleErrorInDialog(XMLHttpResponse, $thisDialog);					    
                    }				
		        });
	        }, 
	        "Cancel": function() { 
		        $(this).dialog("close"); 
	        } 
        }).dialog("open");            
        return false;
    });        
}

function clickClusterNodeAfterAddHost(clusterRadio, podId, newClusterName, existingClusterId, $thisDialog) {
    if(clusterRadio == "new_cluster_radio") {    //*** new cluster ***                         
        refreshClusterUnderPod($("#pod_" + podId), newClusterName);  //this function will click the new cluster node                         
        $thisDialog.find("#new_cluster_name").val("");   
    }        
    else if(clusterRadio == "existing_cluster_radio") { //*** existing cluster ***     
        if (existingClusterId != null && existingClusterId != '-1') {
            $("#cluster_"+existingClusterId).find("#cluster_name").click();
        }    
    }         
}

function initAddPrimaryStorageButton($midmenuAddLink2, currentPageInRightPanel) {
    $midmenuAddLink2.find("#label").text("Add Primary Storage"); 
    $midmenuAddLink2.show();   
    $midmenuAddLink2.unbind("click").bind("click", function(event) {   
        dialogAddPool = $("#dialog_add_pool");  
        dialogAddPool.find("#info_container").hide();	
             
        var zoneId, podId;        
        if(currentPageInRightPanel == "pod_page") {
            var podObj = $("#tab_content_details").data("jsonObj");   
            var podObj = $("#tab_content_details").data("jsonObj");
            zoneId = podObj.zoneid;
            podId = podObj.id;
            dialogAddPool.find("#zone_name").text(fromdb(podObj.zonename));  
            dialogAddPool.find("#pod_name").text(fromdb(podObj.name)); 
        }
        else if(currentPageInRightPanel == "host_page") {
            var hostObj = $("#tab_content_details").data("jsonObj");  
            zoneId = hostObj.zoneid;
            podId = hostObj.podid; 
            dialogAddPool.find("#zone_name").text(fromdb(hostObj.zonename));  
            dialogAddPool.find("#pod_name").text(fromdb(hostObj.podname)); 
        }
        else if(currentPageInRightPanel == "primarystorage_page") {
            var primarystorageObj = $("#tab_content_details").data("jsonObj");   
            zoneId = primarystorageObj.zoneid;
            podId = primarystorageObj.podid;           
            dialogAddPool.find("#zone_name").text(fromdb(primarystorageObj.zonename));  
            dialogAddPool.find("#pod_name").text(fromdb(primarystorageObj.podname)); 
        }
                                             
        var clusterSelect = $("#dialog_add_pool").find("#pool_cluster").empty();			            
	    $.ajax({
		    data: createURL("command=listClusters&podid=" + podId),
	        dataType: "json",
	        success: function(json) {				                        
	            var items = json.listclustersresponse.cluster;
	            if(items != null && items.length > 0) {				                		                
	                for(var i=0; i<items.length; i++) 			                    
	                    clusterSelect.append("<option value='" + items[i].id + "'>" + fromdb(items[i].name) + "</option>");		                
	            }			            
	        }
	    });		   
        
        $("#dialog_add_pool")
	    .dialog('option', 'buttons', { 				    
		    "Add": function() { 	
		    	var $thisDialog = $(this);
		    	
			    // validate values
				var protocol = $thisDialog.find("#add_pool_protocol").val();
				
			    var isValid = true;						    
			    isValid &= validateDropDownBox("Cluster", $thisDialog.find("#pool_cluster"), $thisDialog.find("#pool_cluster_errormsg"), false);  //required, reset error text					    				
			    isValid &= validateString("Name", $thisDialog.find("#add_pool_name"), $thisDialog.find("#add_pool_name_errormsg"));
			    isValid &= validateString("Server", $thisDialog.find("#add_pool_nfs_server"), $thisDialog.find("#add_pool_nfs_server_errormsg"));	
				if (protocol == "nfs") {
					isValid &= validateString("Path", $thisDialog.find("#add_pool_path"), $thisDialog.find("#add_pool_path_errormsg"));	
				} else {
					isValid &= validateString("Target IQN", $thisDialog.find("#add_pool_iqn"), $thisDialog.find("#add_pool_iqn_errormsg"));	
					isValid &= validateString("LUN #", $thisDialog.find("#add_pool_lun"), $thisDialog.find("#add_pool_lun_errormsg"));	
				}
				isValid &= validateString("Tags", $thisDialog.find("#add_pool_tags"), $thisDialog.find("#add_pool_tags_errormsg"), true);	//optional
			    if (!isValid) 
			        return;
			        			    
				$thisDialog.find("#spinning_wheel").show()  
							
				var array1 = [];
				array1.push("&zoneId="+zoneId);
		        array1.push("&podId="+podId);
				
				var clusterId = $thisDialog.find("#pool_cluster").val();
			    array1.push("&clusterid="+clusterId);	
				
			    var name = trim($thisDialog.find("#add_pool_name").val());
			    array1.push("&name="+todb(name));
			    
			    var server = trim($thisDialog.find("#add_pool_nfs_server").val());						
				
				var url = null;
				if (protocol == "nfs") {
					var path = trim($thisDialog.find("#add_pool_path").val());
					if(path.substring(0,1)!="/")
						path = "/" + path; 
					url = nfsURL(server, path);
				} else {
					var iqn = trim($thisDialog.find("#add_pool_iqn").val());
					if(iqn.substring(0,1)!="/")
						iqn = "/" + iqn; 
					var lun = trim($thisDialog.find("#add_pool_lun").val());
					url = iscsiURL(server, iqn, lun);
				}
				array1.push("&url="+encodeURIComponent(url));
				
			    var tags = trim($thisDialog.find("#add_pool_tags").val());
				if(tags != null && tags.length > 0)
				    array1.push("&tags="+todb(tags));				    
			    
			    $.ajax({
				    data: createURL("command=createStoragePool" + array1.join("")),
				    dataType: "json",
				    success: function(json) {
				        $thisDialog.find("#spinning_wheel").hide();					       
				        $thisDialog.dialog("close");					
					    $("#cluster_"+clusterId).find("#cluster_name").click();
					    /*
					    var $midmenuItem1 = $("#midmenu_item").clone();
                        $("#midmenu_container").append($midmenuItem1.fadeIn("slow"));
				        var item = json.createstoragepoolresponse;				            			      										   
					    primarystorageToMidmenu(item, $midmenuItem1);
	                    bindClickToMidMenu($midmenuItem1, primarystorageToRightPanel, primarystorageGetMidmenuId);  
	                    */	                                               
				    },			
                    error: function(XMLHttpResponse) {	                                 
                        handleErrorInDialog(XMLHttpResponse, $thisDialog);	                        					    
                    }							    
			    });
		    }, 
		    "Cancel": function() { 
			    $(this).dialog("close"); 
		    } 
	    }).dialog("open");            
        return false;
    });             
}

function nfsURL(server, path) {
    var url;
    if(server.indexOf("://")==-1)
	    url = "nfs://" + server + path;
	else
	    url = server + path;
	return url;
}

function iscsiURL(server, iqn, lun) {
    var url;
    if(server.indexOf("://")==-1)
	    url = "iscsi://" + server + iqn + "/" + lun;
	else
	    url = server + iqn + "/" + lun;
	return url;
}

function bindEventHandlerToDialogAddPool() {    
    $("#dialog_add_pool").find("#add_pool_protocol").change(function(event) {
		if ($(this).val() == "iscsi") {
			$("#dialog_add_pool #add_pool_path_container").hide();
			$("#dialog_add_pool #add_pool_iqn_container, #dialog_add_pool #add_pool_lun_container").show();
		} else {
			$("#dialog_add_pool #add_pool_path_container").show();
			$("#dialog_add_pool #add_pool_iqn_container, #dialog_add_pool #add_pool_lun_container").hide();
		}
	});		
}

var podActionMap = {
    "Delete Pod": {  
        api: "deletePod",            
        isAsyncJob: false,        
        inProcessText: "Deleting Pod....",
        afterActionSeccessFn: function(json, $midmenuItem1, id) {       
            $midmenuItem1.slideUp(function() {
                $(this).remove();
            });
            clearRightPanel();
            podJsonClearRightPanel();
        }
    }
}
