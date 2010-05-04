function showConfigurationTab() {
	var forceLogout = true;  // We force a logout only if the user has first added a POD for the very first time

	// Manage Events 
	mainContainer.load("content/tab_configuration.html", function() {
		var currentSubMenu = $("#submenu_global");
		
		activateDialog($("#dialog_edit_global").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		$("#global_template").bind("click", function(event) {
			var template = $(this);
			var link = $(event.target);
			var linkAction = link.attr("id");
			var name = template.data("name");
			switch (linkAction) {
				case "global_action_edit" :
					$("#edit_global_name").text(name);
					$("#edit_global_value").val(template.find("#global_value").text());
					
					var confirmEditGlobalDialog = function() { 					    
						// validate values
				        var isValid = true;					
				        isValid &= validateString("Value", dialogBox2.find("#edit_global_value"), dialogBox2.find("#edit_global_value_errormsg"));					
				        if (!isValid) return;
						
						var value = trim(dialogBox2.find("#edit_global_value").val());
						
						dialogBox2.dialog("close");
						$.ajax({
							data: "command=updateConfiguration&name="+encodeURIComponent(name)+"&value="+encodeURIComponent(value)+"&response=json",
							dataType: "json",
							success: function(json) {
								template.find("#global_value").text(value);
								$("#dialog_alert").html("<p><b>PLEASE RESTART YOUR MGMT SERVER!!</b><br/><b>PLEASE RESTART YOUR MGMT SERVER!!</b><br/><br/>You have successfully change a global configuration value.  Please <b>RESTART</b> your management server for your new settings to take effect.  Refer to the install guide for instructions on how to restart the mgmt server.</p>").dialog("open");
							}
						});
			        }
					
					var dialogBox2 = $("#dialog_edit_global")
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
						"Confirm": confirmEditGlobalDialog						
					}).dialog("open");
					
				   $("#dialog_edit_global").bind("keypress", function(event) {	
				       if(event.keyCode == 13) {			       
				           confirmEditGlobalDialog();				           
				           return false;
				       }
				   });
			}
			return false;
		});
		
		function globalJSONToTemplate(json, template) {
		    template.data("name", json.name).attr("id", "global_"+json.name);
		    (index++ % 2 == 0)? template.addClass("smallrow_even"): template.addClass("smallrow_odd");		
			template.find("#global_name").text(json.name);
			template.find("#global_value").text(json.value);
			template.find("#global_desc").text(json.description);
		}
						
		function listConfigurations() {		 
		    var submenuContent = $("#submenu_content_global");
		   
        	var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();				   
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));					
				commandString = "command=listConfigurations&page="+currentPage+moreCriteria.join("")+"&response=json";  
			} else {          
                var searchInput = submenuContent.find("#search_input").val();            
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listConfigurations&page="+currentPage+"&keyword="+searchInput+"&response=json";
                else
                    commandString = "command=listConfigurations&page="+currentPage+"&response=json";
            }
        	 
        	//listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listconfigurationsresponse", "configuration", $("#global_template"), globalJSONToTemplate);     
		}
		
		submenuContentEventBinder($("#submenu_content_global"), listConfigurations);
		
		$("#submenu_global").bind("click", function(event) {			        
		    event.preventDefault();	            
			
			$(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);
			var submenuContent = $("#submenu_content_global").show();
			$("#submenu_content_zones, #submenu_content_service, #submenu_content_disk").hide();			
			
			currentPage = 1;
			listConfigurations();
		});
		
		
		//zone	
		var rightPanel = $("#submenu_content_zones #right_panel_detail_title");
		var rightContent = $("#submenu_content_zones #right_panel_detail_content");
					
		function zoneObjectToRightPanel(obj) {
	        rightPanel.html("<strong>Zone:</strong> "+obj.name);					
			var rightContentHtml = 
				"<p><span>ZONE:</span> "+obj.name+"</p>"
				+ "<p><span>DNS 1:</span> "+obj.dns1+"</p>"
				+ "<p><span>DNS 2:</span> "+((obj.dns2 == null) ? "" : obj.dns2) +"</p>";
			if (getNetworkType() != "vnet") {
				rightContentHtml += "<p><span>VLAN:</span> "+((obj.vlan == null) ? "" : obj.vlan) +"</p>";
			}
			
			rightContent.data("id", obj.id).html(rightContentHtml);		
			
			$("#submenu_content_zones #action_edit_pod").hide();			
			
			var buttons = $("#submenu_content_zones #action_delete, #submenu_content_zones #action_edit_zone, #submenu_content_zones #action_add_pod, #submenu_content_zones #action_add_publicip_vlan").data("type", "zone").show();			
			buttons.data("id", obj.id);			
			buttons.data("name", obj.name);		    
		    buttons.data("dns1", obj.dns1);
		    buttons.data("dns2", obj.dns2);
		    buttons.data("vlan", obj.vlan);			   
		}	
		
		function podObjectToRightPanel(obj) {		    
			rightPanel.html("<strong>Pod:</strong> " + obj.name);
			var rightContentHtml = 
				"<p><span>POD:</span> "+obj.name+"</p>"
				+ "<p><span>CIDR:</span> "+obj.cidr+"</p>"
				+ "<p><span>IP Range:</span> "+obj.ipRange+"</p>";
			rightContent.data("id", obj.id).html(rightContentHtml);
			$("#submenu_content_zones #action_edit_zone, #submenu_content_zones #action_add_pod, #submenu_content_zones #action_add_publicip_vlan").hide();
			var buttons = $("#submenu_content_zones #action_delete, #submenu_content_zones #action_edit_pod").data("type", "pod").show();
			buttons.data("id", obj.id);
			buttons.data("name", obj.name);
			buttons.data("cidr", obj.cidr);
			buttons.data("startip", obj.startip);	
			buttons.data("endip", obj.endip);	
			buttons.data("ipRange", obj.ipRange);	
		}
		
		$("#submenu_content_zones #action_delete").bind("click", function(event) {
			var deleteButton = $(this);
		
			var confirmMessage = null;
			var id = deleteButton.data("id");
			var type = deleteButton.data("type");
			var command = null;
			if (type == "zone") {
				confirmMessage = "Please confirm you want to delete the zone : <b>" + deleteButton.data("name") +"</b>";
				command = "deleteZone";
			} else if (type == "pod") {
				confirmMessage = "Please confirm you want to delete the pod : <b>" + deleteButton.data("name") + "</b>";
				command = "deletePod"
			} else {
				confirmMessage = "Please confirm you want to delete the public vlan ip range : <b>" + deleteButton.data("name") + "</b>";
				command = "deleteVlanIpRange";
			}
		
			$("#dialog_confirmation")
			.html(confirmMessage)
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Confirm": function() { 
					$(this).dialog("close"); 
					
					$.ajax({
						data: "command="+command+"&id="+id+"&response=json",
						dataType: "json",
						success: function(json) {
							var target = null;
							if (type == "zone") {
								target = $("#submenu_content_zones #zone_" + id);
							} else if (type == "pod") {
								target = $("#submenu_content_zones #pod_" + id);
							} else {
								target = $("#submenu_content_zones #publicip_range_" + id);
							}
							target.fadeOut("slow", function() {
								$(this).remove();
							});
							rightPanel.empty();
							rightContent.empty();
							$("#submenu_content_zones #action_delete").hide();
						}
					});
					
				} 
			}).dialog("open");
		});
		
		$("#submenu_content_zones #action_edit_zone").bind("click", function(event) {            
			var id = $(this).data("id");
			
			var dialogEditZone = $("#dialog_edit_zone");			
			dialogEditZone.find("#edit_zone_name").val($(this).data("name"));
			dialogEditZone.find("#edit_zone_dns1").val($(this).data("dns1"));
			dialogEditZone.find("#edit_zone_dns2").val($(this).data("dns2"));
			
			// If the network type is vnet, don't show any vlan stuff.
			if (getNetworkType() != "vnet") {
				dialogEditZone.find("#edit_zone_startvlan").val("");
				dialogEditZone.find("#edit_zone_endvlan").val("");
				var vlan = $(this).data("vlan");
				if(vlan != null) {
					if(vlan.indexOf("-")!==-1) {
						var startVlan = vlan.substring(0, vlan.indexOf("-"));
						var endVlan = vlan.substring((vlan.indexOf("-")+1));		    
						dialogEditZone.find("#edit_zone_startvlan").val(startVlan);
						dialogEditZone.find("#edit_zone_endvlan").val(endVlan);
					}
					else {
						dialogEditZone.find("#edit_zone_startvlan").val(vlan);			        
					}
				}
			}
			
			dialogEditZone
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Change": function() { 					
					// validate values
					var isValid = true;					
					isValid &= validateString("Name", dialogEditZone.find("#edit_zone_name"), dialogEditZone.find("#edit_zone_name_errormsg"));
					isValid &= validateIp("DNS 1", dialogEditZone.find("#edit_zone_dns1"), dialogEditZone.find("#edit_zone_dns1_errormsg"));	    //DNS 1 is required
					isValid &= validateIp("DNS 2", dialogEditZone.find("#edit_zone_dns2"), dialogEditZone.find("#edit_zone_dns2_errormsg"), true);	//DNS 2 is optional	
					if (getNetworkType() != "vnet") {
						isValid &= validateString("Zone VLAN Range", dialogEditZone.find("#edit_zone_startvlan"), dialogEditZone.find("#edit_zone_startvlan_errormsg"));
					}
					if (!isValid) return;							
					
					var name = trim(dialogEditZone.find("#edit_zone_name").val());
					var dns1 = trim(dialogEditZone.find("#edit_zone_dns1").val());
					var dns2 = trim(dialogEditZone.find("#edit_zone_dns2").val());
					
					var vlan = "";
					var vlanParam = "";
					if (getNetworkType() != "vnet") {
						var vlanStart = trim(dialogEditZone.find("#edit_zone_startvlan").val());
						var vlanEnd = trim(dialogEditZone.find("#edit_zone_endvlan").val());
						vlan = vlanStart;
						if (vlanEnd != null && vlanEnd.length > 0) {
							vlan = encodeURIComponent(vlan + "-" + vlanEnd);
							vlanParam = "&vlan=" + vlan;
						} else {
							vlanParam = "&vlan=" + encodeURIComponent(vlan);
						}
					}
					
					var moreCriteria = [];	
					if (dns2 != "") 
						moreCriteria.push("&dns2="+encodeURIComponent(dns2));					 
					
					$(this).dialog("close"); 
					
					var template = $("#zone_"+id); 
					var loadingImg = template.find(".adding_loading").find(".adding_text").text("Updating zone....");										
					var row_container = template.find("#row_container");									            
		            loadingImg.show();  
                    row_container.hide();             
			        template.fadeIn("slow");				        		
					
					$.ajax({
						data: "command=updateZone&id="+id+"&name="+encodeURIComponent(name)+"&dns1="+encodeURIComponent(dns1)+moreCriteria.join("")+vlanParam+"&response=json",
						dataType: "json",
						success: function(json) {	
						    var obj = {"id": id, "name": name, "dns1": dns1, "dns2": dns2, "vlan": vlan};
					        zoneObjectToRightPanel(obj);						
							var zoneName = $("#zone_"+id).find("#zone_name").text(name);		
							zoneName.data("id", id).data("name", name).data("dns1", dns1);
							if (dns2 != undefined) {
								zoneName.data("dns2", dns2);
							}
							if (vlan != undefined) {
								zoneName.data("vlan", vlan);
							}	
							loadingImg.hide(); 								                            
                            row_container.show();      
						},
					    error: function(XMLHttpRequest) {
					        handleError(XMLHttpRequest);				    
						    template.slideUp("slow", function() {
								$(this).remove();
							});
					    }
					});
				} 
			}).dialog("open");			
			
		});
		
		$("#submenu_content_zones #action_add_pod").bind("click", function(event) {
			var id = $(this).data("id");
			
			$("#dialog_add_pod").find("#add_pod_zone_name").text(name);
			$("#dialog_add_pod #add_pod_name, #dialog_add_pod #add_pod_cidr, #dialog_add_pod #add_pod_startip, #dialog_add_pod #add_pod_endip").val("");
			
			$("#dialog_add_pod")
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Add": function() {					
					// validate values
					var isValid = true;					
					isValid &= validateString("Name", $(this).find("#add_pod_name"), $(this).find("#add_pod_name_errormsg"));
					isValid &= validateCIDR("CIDR", $(this).find("#add_pod_cidr"), $(this).find("#add_pod_cidr_errormsg"));	
					isValid &= validateIp("Start IP Range", $(this).find("#add_pod_startip"), $(this).find("#add_pod_startip_errormsg"));  //required
					isValid &= validateIp("End IP Range", $(this).find("#add_pod_endip"), $(this).find("#add_pod_endip_errormsg"), true);  //optional
					if (!isValid) return;			

                    var name = trim($(this).find("#add_pod_name").val());
					var cidr = trim($(this).find("#add_pod_cidr").val());
					var startip = trim($(this).find("#add_pod_startip").val());
					var endip = trim($(this).find("#add_pod_endip").val());					

					if (endip != null) {
						endip = "&endIp="+encodeURIComponent(endip);
					} else {
						endip = "";
					}
					
					$(this).dialog("close"); 
					
					var template = $("#pod_template").clone(true);
					var loadingImg = template.find(".adding_loading");										
					var row_container = template.find("#row_container");
					
					$("#zone_"+id+" #zone_content").show();	
					$("#zone_" + id + " #pods_container").prepend(template.show());						
					$("#zone_" + id + " #zone_expand").removeClass().addClass("zonetree_openarrows");									            
		            loadingImg.show();  
                    row_container.hide();             
			        template.fadeIn("slow");
					
					$.ajax({
						data: "command=createPod&zoneId="+id+"&name="+encodeURIComponent(name)+"&cidr="+encodeURIComponent(cidr)+"&startIp="+encodeURIComponent(startip)+endip+"&response=json",
						dataType: "json",
						success: function(json) {
							var pod = json.createpodresponse;
							template.attr("id", "pod_"+pod.id);
							podJSONToTemplate(pod, template);
							loadingImg.hide(); 								                            
                            row_container.show();
							if (forceLogout) {
								$("#dialog_confirmation")
									.html("<p>You have successfully added your first Zone and Pod.  After clicking 'Ok', this UI will automatically refresh to give you access to the rest of cloud features.</p>")
									.dialog('option', 'buttons', { 
										"OK": function() { 
											var dialogBox = $(this);
											$(this).dialog("close");
											window.location.reload();
										} 
									}).dialog("open");
							}
						},
					    error: function(XMLHttpRequest) {	
					        handleError(XMLHttpRequest);			    
						    template.slideUp("slow", function() {
								$(this).remove();
							});
					    }
					});					
				} 
			}).dialog("open");
		});
		
		$("#submenu_content_zones #action_edit_pod").bind("click", function(event) {             
			var id = $(this).data("id");	
			var dialogEditPod = $("#dialog_edit_pod");					
			dialogEditPod.find("#edit_pod_name").val($(this).data("name"));
			dialogEditPod.find("#edit_pod_cidr").val($(this).data("cidr"));								
			dialogEditPod.find("#edit_pod_startip").val($(this).data("startip")); 
			dialogEditPod.find("#edit_pod_endip").val($(this).data("endip"));   
						
			dialogEditPod
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Change": function() { 					    				
				    // validate values
					var isValid = true;					
					isValid &= validateString("Name", dialogEditPod.find("#edit_pod_name"), dialogEditPod.find("#edit_pod_name_errormsg"));
					isValid &= validateCIDR("CIDR", dialogEditPod.find("#edit_pod_cidr"), dialogEditPod.find("#edit_pod_cidr_errormsg"));	
					isValid &= validateIp("Start IP Range", dialogEditPod.find("#edit_pod_startip"), dialogEditPod.find("#edit_pod_startip_errormsg"));  //required
					isValid &= validateIp("End IP Range", dialogEditPod.find("#edit_pod_endip"), dialogEditPod.find("#edit_pod_endip_errormsg"), true);  //optional
					if (!isValid) return;			

                    var name = trim(dialogEditPod.find("#edit_pod_name").val());
					var cidr = trim(dialogEditPod.find("#edit_pod_cidr").val());
					var startip = trim(dialogEditPod.find("#edit_pod_startip").val());
					var endip = trim(dialogEditPod.find("#edit_pod_endip").val());					

                    var moreCriteria = [];	
					if (endip != null && endip.length > 0) {
						moreCriteria.push("&endIp="+encodeURIComponent(endip));
					} 
					
					$(this).dialog("close"); 
					
					var template = $("#pod_"+id); //?????
					var loadingImg = template.find(".adding_loading");										
					var row_container = template.find("#row_container");
													            
		            loadingImg.show();  
                    row_container.hide();             
			        template.fadeIn("slow");
					
					$.ajax({
						data: "command=updatePod&id="+id+"&name="+encodeURIComponent(name)+"&cidr="+encodeURIComponent(cidr)+"&startIp="+encodeURIComponent(startip)+moreCriteria.join("")+"&response=json",
						dataType: "json",
						success: function(json) {						    
						    var ipRange = getIpRange(startip, endip);							   
							var obj = {"id": id, "name": name, "cidr": cidr, "startip": startip, "endip": endip, "ipRange": ipRange};  //???
					        podObjectToRightPanel(obj);					
							var podName = $("#pod_"+id).find("#pod_name").text(name);
							podName.data("id", id).data("name", name).data("cidr", cidr).data("startip", startip).data("endip", endip).data("ipRange", ipRange);	
							loadingImg.hide(); 								                            
                            row_container.show();							
						},
					    error: function(XMLHttpRequest) {	
					        handleError(XMLHttpRequest);			    
						    template.slideUp("slow", function() {
								$(this).remove();
							});
					    }
					});	
				}
			}).dialog("open");			
		});
				
		$("#submenu_content_zones #action_add_publicip_vlan").bind("click", function(event) {
			var id = $(this).data("id");
			$("#dialog_add_publicip_vlan #add_publicip_vlan_vlan_container").hide();
			$("#dialog_add_publicip_vlan #add_publicip_vlan_tagged, #dialog_add_publicip_vlan #add_publicip_vlan_vlan, #dialog_add_publicip_vlan #add_publicip_vlan_gateway, #dialog_add_publicip_vlan #add_publicip_vlan_netmask, #dialog_add_publicip_vlan #add_publicip_vlan_startip, #dialog_add_publicip_vlan #add_publicip_vlan_endip").val("");
			$("#dialog_add_publicip_vlan").find("#add_publicip_vlan_zone_name").text(name);
			
			$("#dialog_add_publicip_vlan")
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Add": function() { 					
					// validate values
					var isValid = true;					
					var isTagged = $(this).find("#add_publicip_vlan_tagged").val() == "tagged";
					if (isTagged) {
						isValid &= validateNumber("VLAN", $(this).find("#add_publicip_vlan_vlan"), $(this).find("#add_publicip_vlan_vlan_errormsg"), 2, 4095);
					}
					isValid &= validateIp("Gateway", $(this).find("#add_publicip_vlan_gateway"), $(this).find("#add_publicip_vlan_gateway_errormsg"));
					isValid &= validateIp("Netmask", $(this).find("#add_publicip_vlan_netmask"), $(this).find("#add_publicip_vlan_netmask_errormsg"));
					isValid &= validateIp("Start IP Range", $(this).find("#add_publicip_vlan_startip"), $(this).find("#add_publicip_vlan_startip_errormsg"));   //required
					isValid &= validateIp("End IP Range", $(this).find("#add_publicip_vlan_endip"), $(this).find("#add_publicip_vlan_endip_errormsg"), true);  //optional
					if (!isValid) return;							
					
					var vlan = trim($(this).find("#add_publicip_vlan_vlan").val());
					if (isTagged) {
						vlan = "&vlan="+vlan;
					} else {
						vlan = "&vlan=untagged";
					}
					var gateway = trim($(this).find("#add_publicip_vlan_gateway").val());
					var netmask = trim($(this).find("#add_publicip_vlan_netmask").val());
					var startip = trim($(this).find("#add_publicip_vlan_startip").val());
					var endip = trim($(this).find("#add_publicip_vlan_endip").val());					
					
					$(this).dialog("close"); 
										
					var template = $("#publicip_range_template").clone(true);
					var loadingImg = template.find(".adding_loading");										
					var row_container = template.find("#row_container");
					
					$("#zone_" + id + " #zone_content").show();	
					$("#zone_" + id + " #publicip_ranges_container").prepend(template.show());						
					$("#zone_" + id + " #zone_expand").removeClass().addClass("zonetree_openarrows");									            
		            loadingImg.show();  
                    row_container.hide();             
			        template.fadeIn("slow");					
					
					$.ajax({
						data: "command=createVlanIpRange&name=Public&zoneId="+id+vlan+"&gateway="+encodeURIComponent(gateway)+"&netmask="+encodeURIComponent(netmask)+"&startip="+encodeURIComponent(startip)+"&endip="+encodeURIComponent(endip)+"&response=json",
						dataType: "json",
						success: function(json) {
							var vlan = json.createvlaniprangeresponse;
							template.attr("id", "publicip_range_"+vlan.id);
							publipIpRangeJSONToTemplate(vlan, template);
							loadingImg.hide(); 								                            
                            row_container.show();    
						},
					    error: function(XMLHttpRequest) {	
					        handleError(XMLHttpRequest);			    
						    template.slideUp("slow", function() {
								$(this).remove();
							});
					    }
					});
					
				} 
			}).dialog("open");
		});
		
		$("#zone_template").bind("click", function(event) {
			var template = $(this);
			var target = $(event.target);
			var action = target.attr("id");
			var id = template.data("id");
			var name = template.data("name");
			
			switch (action) {
				case "zone_expand" :
					if (target.hasClass("zonetree_closedarrows")) {
						$("#zone_"+id+" #zone_content").show();
						target.removeClass().addClass("zonetree_openarrows");
					} else {
						$("#zone_"+id+" #zone_content").hide();
						target.removeClass().addClass("zonetree_closedarrows");
					}
					break;
				case "zone_name" :
					$("#submenu_content_zones .zonetree_firstlevel_selected").removeClass().addClass("zonetree_firstlevel");
					$("#submenu_content_zones .zonetree_secondlevel_selected").removeClass().addClass("zonetree_secondlevel");
					template.find(".zonetree_firstlevel").removeClass().addClass("zonetree_firstlevel_selected");
										
					var obj = {"id": target.data("id"), "name": target.data("name"), "dns1": target.data("dns1"), "dns2": target.data("dns2"), "vlan": target.data("vlan")};
					zoneObjectToRightPanel(obj);					
					
					break;
				case "pod_name" :
					$("#submenu_content_zones .zonetree_firstlevel_selected").removeClass().addClass("zonetree_firstlevel");
					$("#submenu_content_zones .zonetree_secondlevel_selected").removeClass().addClass("zonetree_secondlevel");
					target.parent(".zonetree_secondlevel").removeClass().addClass("zonetree_secondlevel_selected");
					
					var obj = {"id": target.data("id"), "name": target.data("name"), "cidr": target.data("cidr"), "startip": target.data("startip"), "endip": target.data("endip"), "ipRange": target.data("ipRange")};
					podObjectToRightPanel(obj);
					
					break;
				case "publicip_range_name" :
					$("#submenu_content_zones .zonetree_firstlevel_selected").removeClass().addClass("zonetree_firstlevel");
					$("#submenu_content_zones .zonetree_secondlevel_selected").removeClass().addClass("zonetree_secondlevel");
					target.parent(".zonetree_secondlevel").removeClass().addClass("zonetree_secondlevel_selected");
					
					rightPanel.html("<strong>Public VLAN IP Range</strong>");
					var rightContentHtml = 
						"<p><span>VLAN ID:</span> "+target.data("vlan")+"</p>"
						+ "<p><span>Gateway:</span> "+target.data("gateway")+"</p>"
						+ "<p><span>Netmask:</span> "+target.data("netmask")+"</p>"
						+ "<p><span>IP Range:</span> "+target.data("name")+"</p>";
					rightContent.data("id", target.data("id")).html(rightContentHtml);
					$("#submenu_content_zones #action_edit_zone, #submenu_content_zones #action_add_pod, #submenu_content_zones #action_add_publicip_vlan").hide();
					$("#submenu_content_zones #action_delete").data("id", target.data("id")).data("name", target.data("name")).data("type", "publicip_range").show();
					
					break;
				default:
					break;
			}
			return false;
		});
		
		function publipIpRangeJSONToTemplate(json, template) {
			template.data("id", json.id);
			var vlanName = json.id;
			var vlanDisplayName = vlanName;
			if (json.description != undefined) {
				if (json.description.indexOf("-") == -1) {
					vlanName = json.description;
					vlanDisplayName = vlanName;
				} else {
					var ranges = json.description.split("-");
					vlanName = ranges[0] + " -" + ranges[1];
					vlanDisplayName = ranges[0] + " - " + ranges[1];
				}
			}
			template.find("#publicip_range_name")
				.html(vlanName)
				.data("id", json.id)
				.data("name", vlanDisplayName)
				.data("vlan", json.vlan)
				.data("gateway", json.gateway)
				.data("netmask", json.netmask);
		}
		
		function getIpRange(startip, endip) {
		    var ipRange = "";
			if (startip != null && startip.length > 0) {
				ipRange = startip;
			}
			if (endip != null && endip.length > 0) {
				ipRange = ipRange + "-" + endip;
			}		
			return ipRange;
		}
		
		function podJSONToTemplate(json, template) {		    
			var ipRange = getIpRange(json.startip, json.endip);			
			template.data("id", json.id).data("name", json.name);
			
			var podName = template.find("#pod_name").text(json.name);
			podName.data("id", json.id)
			podName.data("name", json.name)
			podName.data("cidr", json.cidr)
			podName.data("startip", json.startip)
			podName.data("endip", json.endip)
			podName.data("ipRange", ipRange);				
		}
		
		function zoneJSONToTemplate(json, template) {
			template.data("id", json.id).data("name", json.name);
			template.find("#zone_name")
				.text(json.name)
				.data("id", json.id)
				.data("name", json.name)
				.data("dns1", json.dns1);
			if (json.dns2 != undefined) {
				template.find("#zone_name").data("dns2", json.dns2);
			}
			if (json.vlan != undefined) {
				template.find("#zone_name").data("vlan", json.vlan);
			}	
			
			$.ajax({
				data: "command=listPods&zoneId="+json.id+"&response=json",
				dataType: "json",
				success: function(json) {
					var pods = json.listpodsresponse.pod;
					var grid = template.find("#pods_container").empty();
					if (pods != null && pods.length > 0) {					    
						for (var i = 0; i < pods.length; i++) {
							var podTemplate = $("#pod_template").clone(true).attr("id", "pod_"+pods[i].id);
							podJSONToTemplate(pods[i], podTemplate);
							grid.append(podTemplate.show());
							forceLogout = false;
						}
					}
				}
			});
			
			$.ajax({
				data: "command=listVlanIpRanges&zoneId="+json.id+"&response=json",
				dataType: "json",
				success: function(json) {
					var ranges = json.listvlaniprangesresponse.vlaniprange;
					var grid = template.find("#publicip_ranges_container").empty();
					if (ranges != null && ranges.length > 0) {					    
						for (var i = 0; i < ranges.length; i++) {
							var rangeTemplate = $("#publicip_range_template").clone(true).attr("id", "publicip_range_"+ranges[i].id);
							publipIpRangeJSONToTemplate(ranges[i], rangeTemplate);
							grid.append(rangeTemplate.show());
						}
					}
				}
			});
			
		}
		// If the network type is vnet, don't show any vlan stuff.
		if (getNetworkType() == "vnet") {
			$("#dialog_edit_zone #edit_zone_container, #dialog_add_zone #add_zone_container").hide();
		}
		activateDialog($("#dialog_add_zone").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_edit_zone").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_add_pod").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_edit_pod").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_add_publicip_vlan").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		if (getNetworkType() != "vnet") {
			$("#dialog_add_publicip_vlan #add_publicip_vlan_tagged").change(function(event) {
				if ($(this).val() == "tagged") {
					$("#dialog_add_publicip_vlan #add_publicip_vlan_vlan_container").show();
				} else {
					$("#dialog_add_publicip_vlan #add_publicip_vlan_vlan_container").hide();
				}
				return false;
			});
		} else {
			$("#dialog_add_publicip_vlan #add_publicip_vlan_container").hide();
		}
		
		$("#action_add_zone").bind("click", function(event) {
			$("#dialog_add_zone #add_zone_name, #dialog_add_zone #add_zone_dns1, #dialog_add_zone #add_zone_dns2, #dialog_add_zone #add_zone_startvlan, #dialog_add_zone #add_zone_endvlan").val("");
			
			$("#dialog_add_zone")
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Add": function() { 					
					// validate values
					var isValid = true;					
					isValid &= validateString("Name", $(this).find("#add_zone_name"), $(this).find("#add_zone_name_errormsg"));
					isValid &= validateIp("DNS 1", $(this).find("#add_zone_dns1"), $(this).find("#add_zone_dns1_errormsg"));	    //DNS 1 is required
					isValid &= validateIp("DNS 2", $(this).find("#add_zone_dns2"), $(this).find("#add_zone_dns2_errormsg"), true);	//DNS 2 is optional	
					if (getNetworkType() != "vnet") {
						isValid &= validateString("Zone VLAN Range", $(this).find("#add_zone_startvlan"), $(this).find("#add_zone_startvlan_errormsg"));
					}
					if (!isValid) return;							
					
					var name = trim($(this).find("#add_zone_name").val());
					var dns1 = trim($(this).find("#add_zone_dns1").val());
					var dns2 = trim($(this).find("#add_zone_dns2").val());
					var vlan = "";
					if (getNetworkType() != "vnet") {
						var vlanStart = trim($(this).find("#add_zone_startvlan").val());	
						var vlanEnd = trim($(this).find("#add_zone_endvlan").val());
						vlan = vlanStart;
						if (vlanEnd != null && vlanEnd.length > 0) {
							vlan = "&vlan=" + encodeURIComponent(vlan + "-" + vlanEnd);
						} else {
							vlan = "&vlan=" + encodeURIComponent(vlan);
						}
					}
					
					if (dns2 != "") {
						dns2 = "&dns2="+encodeURIComponent(dns2);
					} else {
						dns2 = "";
					}
					
					$(this).dialog("close"); 
					
					var template = $("#zone_template").clone(true);
					var loadingImg = template.find(".adding_loading");										
					var row_container = template.find("#row_container");
					
					$("#submenu_content_zones #zones_container").prepend(template.show());					            
		            loadingImg.show();  
                    row_container.hide();             
			        template.fadeIn("slow");				        		
					
					$.ajax({
						data: "command=createZone&name="+encodeURIComponent(name)+"&dns1="+encodeURIComponent(dns1)+dns2+vlan+"&response=json",
						dataType: "json",
						success: function(json) {
							var zone = json.createzoneresponse;
							template.attr("id", "zone_"+zone.id);
							zoneJSONToTemplate(zone, template);							
							loadingImg.hide(); 								                            
                            row_container.show();      
						},
					    error: function(XMLHttpRequest) {
					        handleError(XMLHttpRequest);				    
						    template.slideUp("slow", function() {
								$(this).remove();
							});
					    }
					});
				} 
			}).dialog("open");
		});
		
		$("#submenu_zones").bind("click", function(event) {
			event.preventDefault();
			$(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);
			var container = $("#submenu_content_zones").show();
			$("#submenu_content_global, #submenu_content_service, #submenu_content_disk").hide();
			$.ajax({
				data: "command=listZones&available=true&response=json",
				dataType: "json",
				success: function(json) {
					var zones = json.listzonesresponse.zone;
					var grid = $("#submenu_content_zones #zones_container").empty();
					if (zones != null && zones.length > 0) {					    
						for (var i = 0; i < zones.length; i++) {
							var template = $("#zone_template").clone(true).attr("id", "zone_"+zones[i].id);
							zoneJSONToTemplate(zones[i], template);
							grid.append(template.show());
						}
					}
				}
			});
		});
		
		$("#service_template").bind("click", function(event) {
			var template = $(this);
			var link = $(event.target);
			var linkAction = link.attr("id");
			var svcId = template.data("svcId");
			var svcName = template.data("svcName");
			var submenuContent = $("#submenu_content_service");
			switch (linkAction) {
				case "service_action_edit" :
					var dialogEditService = $("#dialog_edit_service");
					
					dialogEditService.find("#edit_service_name").val(svcName);
					dialogEditService.find("#edit_service_display").val(template.find("#service_display").text());
					dialogEditService.find("#edit_service_name_display").text(svcName);
					
					dialogEditService
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
						"Confirm": function() { 							
							// validate values
					        var isValid = true;					
					        isValid &= validateString("Name", dialogEditService.find("#edit_service_name"), dialogEditService.find("#edit_service_name_errormsg"));
					        isValid &= validateString("Display Text", dialogEditService.find("#edit_service_display"), dialogEditService.find("#edit_service_display_errormsg"));											
					        if (!isValid) return;	
					
					        var name = trim(dialogEditService.find("#edit_service_name").val());
							var display = trim(dialogEditService.find("#edit_service_display").val());
							
							var dialogBox = $(this);					
							dialogBox.dialog("close");
							$.ajax({
								data: "command=updateServiceOffering&name="+encodeURIComponent(name)+"&displayText="+encodeURIComponent(display)+"&id="+svcId+"&response=json",
								dataType: "json",
								success: function(json) {
									template.find("#service_display").text(display);
									template.find("#service_name").text(name);
								}
							});
						} 
					}).dialog("open");
					break;
				case "service_action_delete" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to remove the service offering: <b>"+svcName+"</b> from the management server. </p>")
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
						"Confirm": function() { 
							var dialogBox = $(this);
							$.ajax({
								data: "command=deleteServiceOffering&id="+svcId+"&response=json",
								dataType: "json",
								success: function(json) {
									dialogBox.dialog("close");
									template.slideUp("slow", function() {
										$(this).remove();																			
										changeGridRowsTotal(submenuContent.find("#grid_rows_total"), -1);
									});
								}
							});
						} 
					}).dialog("open");
					break;
				default :
					break;
			}
			return false;
		});
				
		function serviceJSONToTemplate(json, template) {	
		    template.attr("id", "service_"+json.id);	   
			(index++ % 2 == 0)? template.addClass("smallrow_even"): template.addClass("smallrow_odd");	
			template.data("svcId", json.id).data("svcName", json.name);
			template.find("#service_id").text(json.id);
			template.find("#service_name").text(json.name);
			template.find("#service_display").text(json.displaytext);
			template.find("#service_storagetype").text(json.storagetype);
			template.find("#service_cpu").text(json.cpunumber + " x " + convertHz(json.cpuspeed));
			template.find("#service_memory").text(convertBytes(parseInt(json.memory)*1024*1024));
			
			var created = new Date();
			created.setISO8601(json.created);
			var showDate = created.format("m/d/Y H:i:s");
			template.find("#service_created").text(showDate);
		}
		
		activateDialog($("#dialog_add_service").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_edit_service").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_edit_disk").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		$("#service_add_service").bind("click", function(event) {
			var dialogAddService = $("#dialog_add_service");
			
			dialogAddService.find("#service_name").val("");
			dialogAddService.find("#service_display").val("");
			dialogAddService.find("#service_cpucore").val("");
			dialogAddService.find("#service_cpu").val("");
			dialogAddService.find("#service_memory").val("");
			var submenuContent = $("#submenu_content_service");
			
			dialogAddService
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Add": function() { 					
					// validate values
					var isValid = true;					
					isValid &= validateString("Name", dialogAddService.find("#add_service_name"), dialogAddService.find("#add_service_name_errormsg"));
					isValid &= validateString("Display Text", dialogAddService.find("#add_service_display"), dialogAddService.find("#add_service_display_errormsg"));
					isValid &= validateNumber("# of CPU Core", dialogAddService.find("#add_service_cpucore"), dialogAddService.find("#add_service_cpucore_errormsg"), 1, 1000);		
					isValid &= validateNumber("CPU", dialogAddService.find("#add_service_cpu"), dialogAddService.find("#add_service_cpu_errormsg"), 100, 100000);		
					isValid &= validateNumber("Memory", dialogAddService.find("#add_service_memory"), dialogAddService.find("#add_service_memory_errormsg"), 64, 1000000);							
					if (!isValid) return;										
					
					var name = trim(dialogAddService.find("#add_service_name").val());
					var display = trim(dialogAddService.find("#add_service_display").val());
					var storagetype = trim(dialogAddService.find("#add_service_storagetype").val());
					var core = trim(dialogAddService.find("#add_service_cpucore").val());
					var cpu = trim(dialogAddService.find("#add_service_cpu").val());
					var memory = trim(dialogAddService.find("#add_service_memory").val());
					
					var dialogBox = $(this);
					dialogBox.dialog("close");
					$.ajax({
						data: "command=createServiceOffering&name="+encodeURIComponent(name)+"&displayText="+encodeURIComponent(display)+"&storageType="+storagetype+"&cpuNumber="+core+"&cpuSpeed="+cpu+"&memory="+memory+"&response=json",
						dataType: "json",
						success: function(json) {
							var offering = json.createserviceofferingresponse;
							var template = $("#service_template").clone(true).attr("id", "service_"+offering.id);
							serviceJSONToTemplate(offering, template);
							$("#submenu_content_service #grid_content").append(template.fadeIn("slow"));
							changeGridRowsTotal(submenuContent.find("#grid_rows_total"), 1); 
						}
					});
				} 
			}).dialog("open");
			return false;
		});
		
		//add a new disk offering
		activateDialog($("#dialog_add_disk").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		$("#disk_add_disk").bind("click", function(event) {			   
			var dialogAddDisk = $("#dialog_add_disk");
			dialogAddDisk.find("#disk_name").val("");
			dialogAddDisk.find("#disk_description").val("");
			dialogAddDisk.find("#disk_disksize").val("");	
			var submenuContent = $("#submenu_content_disk");
					
			dialogAddDisk
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Add": function() { 					    		
					// validate values
					var isValid = true;					
					isValid &= validateString("Name", dialogAddDisk.find("#add_disk_name"), dialogAddDisk.find("#add_disk_name_errormsg"));
					isValid &= validateString("Description", dialogAddDisk.find("#add_disk_description"), dialogAddDisk.find("#add_disk_description_errormsg"));
					isValid &= validateNumber("Disk size", dialogAddDisk.find("#add_disk_disksize"), dialogAddDisk.find("#add_disk_disksize_errormsg"), 1, null); 	
					if (!isValid) return;										
										
					var name = trim(dialogAddDisk.find("#add_disk_name").val());
					var description = trim(dialogAddDisk.find("#add_disk_description").val());				
					var disksize = trim(dialogAddDisk.find("#add_disk_disksize").val());
					//var mirrored = trim(dialogAddDisk.find("#dialog_add_disk #add_disk_mirrored").val());
														
					var dialogBox = $(this);
					dialogBox.dialog("close");
					$.ajax({
						data: "command=createDiskOffering&name="+encodeURIComponent(name)+"&displaytext="+encodeURIComponent(description)+"&disksize="+disksize+"&isMirrored=false&response=json",
						dataType: "json",
						success: function(json) {						   
							var offering = json.creatediskofferingresponse;
							var template = $("#disk_template").clone(true).attr("id", "disk_"+offering.id);
							diskJSONToTemplate(offering, template);
							$("#submenu_content_disk #grid_content").append(template.fadeIn("slow"));
							changeGridRowsTotal(submenuContent.find("#grid_rows_total"), 1);
						}
					});
				} 
			}).dialog("open");			
			return false;
		});
				
		function listServiceOfferings() {	
		    var submenuContent = $("#submenu_content_service");
			
        	var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();				   
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));				
				commandString = "command=listServiceOfferings&page="+currentPage+moreCriteria.join("")+"&response=json";    
			} else {              
        	    var searchInput = submenuContent.find("#search_input").val();           	   
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listServiceOfferings&page="+currentPage +"&keyword="+searchInput+"&response=json";
                else
                    commandString = "command=listServiceOfferings&page="+currentPage+"&response=json"; 
            }   
        	
        	//listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listserviceofferingsresponse", "serviceoffering", $("#service_template"), serviceJSONToTemplate);          	
		}
			
		submenuContentEventBinder($("#submenu_content_service"), listServiceOfferings);	
				
		$("#submenu_service").bind("click", function(event) {
		    event.preventDefault();					   
			
			$(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);
			
			var submenuContent = $("#submenu_content_service").show();
			$("#submenu_content_zones, #submenu_content_global, #submenu_content_disk").hide();	
			
			currentPage = 1;							
			listServiceOfferings();   	
		});		
		
		//Disk Offering
		$("#disk_template").bind("click", function(event) {		   
			var template = $(this);
			var link = $(event.target);
			var linkAction = link.attr("id");
			var diskId = template.data("diskId");
			var diskName = template.data("diskName");
			var submenuContent = $("#submenu_content_disk");
			
			switch (linkAction) {	
			    case "disk_action_edit" :	
			        var dialogEditDisk = $("#dialog_edit_disk");		        
					dialogEditDisk.find("#edit_disk_name").val(template.find("#disk_name").text());
					dialogEditDisk.find("#edit_disk_display").val(template.find("#disk_description").text());		
								
					dialogEditDisk
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
						"Confirm": function() { 							
							// validate values
					        var isValid = true;						        			
					        isValid &= validateString("Name", dialogEditDisk.find("#edit_disk_name"), dialogEditDisk.find("#edit_disk_name_errormsg"));
					        isValid &= validateString("Display Text", dialogEditDisk.find("#edit_disk_display"), dialogEditDisk.find("#edit_disk_display_errormsg"));											
					        if (!isValid) return;	
					
					        var name = trim(dialogEditDisk.find("#edit_disk_name").val());
							var display = trim(dialogEditDisk.find("#edit_disk_display").val());
							
							var dialogBox = $(this);					
							dialogBox.dialog("close");
							$.ajax({
								data: "command=updateDiskOffering&name="+encodeURIComponent(name)+"&displayText="+encodeURIComponent(display)+"&id="+diskId+"&response=json",
								dataType: "json",
								success: function(json) {									   				    
									template.find("#disk_description").text(display);
									template.find("#disk_name").text(name);
								}
							});
						} 
					}).dialog("open");
					break;	
				case "disk_action_delete" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to remove the disk offering: <b>"+diskName+"</b> from the management server. </p>")
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
						"Confirm": function() { 
							var dialogBox = $(this);
							$.ajax({
								data: "command=deleteDiskOffering&id="+diskId+"&response=json",
								dataType: "json",
								success: function(json) {
									dialogBox.dialog("close");
									template.slideUp("slow", function() {
										$(this).remove();										
										changeGridRowsTotal(submenuContent.find("#grid_rows_total"), -1);
									});
								}
							});
						} 
					}).dialog("open");
					break;
				default :
					break;
			}
			return false;
		});
					
		function diskJSONToTemplate(json, template) {	
		    template.attr("id", "disk_"+json.id);	    
			if (index++ % 2 == 0) {
				template.addClass("smallrow_even");
			} else {
				template.addClass("smallrow_odd");
			}
			template.data("diskId", json.id).data("diskName", json.name);			
			template.find("#disk_id").text(json.id);			
			template.find("#disk_name").text(json.name);
			template.find("#disk_description").text(json.displaytext);
  		    template.find("#disk_disksize").text(json.disksize + " GB");
 			template.find("#disk_domain").text(json.domain); 			
 		    template.find("#disk_ismirrored").text(json.ismirrored);	
		}
			
		function listDiskOfferings() {		  
		    var submenuContent = $("#submenu_content_disk");
		
        	var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();				   
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));						
				commandString = "command=listDiskOfferings&page="+currentPage+moreCriteria.join("")+"&response=json";      //moreCriteria.join("")
			} else {              
        	    var searchInput = submenuContent.find("#search_input").val();            
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listDiskOfferings&page="+currentPage+"&keyword="+searchInput+"&response=json";                                
                else
                    commandString = "command=listDiskOfferings&page="+currentPage+"&response=json";    
            }
        	  
        	//listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listdiskofferingsresponse", "diskOffering", $("#disk_template"), diskJSONToTemplate);              	
		}		
		
		submenuContentEventBinder($("#submenu_content_disk"), listDiskOfferings);	
					
		$("#submenu_disk").bind("click", function(event) {	
		    event.preventDefault();	
		    	
			$(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);
			
			var submenuContent = $("#submenu_content_disk").show();
			$("#submenu_content_zones, #submenu_content_service, #submenu_content_global").hide();			
			
			currentPage=1;
			listDiskOfferings();
		});
				
		$("#submenu_global").click();
	});
}