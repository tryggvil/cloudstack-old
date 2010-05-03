function showNetworkingTab() {
	// Manage Networking 
	mainContainer.load("content/tab_networking.html", function() {
		// Sub Menus				
		var sgRuleIndex = 0;
		
		// Basic setup for networking tab
		$.ajax({
			data: "command=listZones&available=true&response=json",
			dataType: "json",
			success: function(json) {
				var zones = json.listzonesresponse.zone;
				var zoneSelect = $("#dialog_acquire_public_ip #acquire_zone").empty();	
				if (zones != null && zones.length > 0) {	
				    for (var i = 0; i < zones.length; i++) {
					    zoneSelect.append("<option value='" + zones[i].id + "'>" + zones[i].name + "</option>"); 
				    }
			    }
			}
		});
		
		//FUNCTION : Parses IP JSON to template
		function ipJSONToTemplate(addressJSON, ipTemplate) {
		    ipTemplate.attr("id","ip"+addressJSON.ipaddress);
		
			if (index++ % 2 == 0) 
			    ipTemplate.addClass("smallrow_even");
			else 
				ipTemplate.addClass("smallrow_odd");			

            var submenuContent = $("#submenu_content_public_ips");
            
			// Populate the template
			var allocated = new Date();
			if(addressJSON.allocated != null)
			    allocated.setISO8601(addressJSON.allocated);
			var showDate = allocated.format("m/d/Y H:i:s");
			ipTemplate.find("#ip_address").text(addressJSON.ipaddress);
			ipTemplate.find("#ip_zone").text(addressJSON.zonename);
			ipTemplate.find("#ip_source_nat").text(((addressJSON.issourcenat == "true") ? "Yes" : "No"));
			ipTemplate.find("#ip_allocated").text(showDate);
			ipTemplate.find("#ip_vlan").text(addressJSON.vlanname);
			ipTemplate.find("#ip_domain").text(addressJSON.domain);
			ipTemplate.find("#ip_account").text(addressJSON.account);
			if (addressJSON.issourcenat != "true") {
				ipTemplate.find("#ip_release a").data("ipAddress", addressJSON.ipaddress).bind("click", function(event) {
					event.preventDefault();
					var ipLink = $(this);
					var ipAddress = ipLink.data("ipAddress");
					$.ajax({
						data: "command=disassociateIpAddress&ipAddress="+ipAddress+"&response=json",
						dataType: "json",
						success: function(json) {
							ipTemplate.slideUp("slow", function() { $(this).remove() });
							changeGridRowsTotal(submenuContent.find("#grid_rows_total"), -1);
						}
					});
				});
				ipTemplate.find("#ip_release").show();
			} 
		}
	
		//FUNCTION : Parses Security Group JSON to template
		function sgJSONToTemplate(sgJSON, sgTemplate) {
		    sgTemplate.attr("id","sg"+sgJSON.id);
		    
			if (index++ % 2 == 0) {
				sgTemplate.addClass("smallrow_even");
			} else {
				sgTemplate.addClass("smallrow_odd");
			}

			// Populate the template
			sgTemplate.find("#sg_name").text(sgJSON.name);
			sgTemplate.find("#sg_desc").text(sgJSON.description);
			sgTemplate.find("#sg_account").text(sgJSON.account);
			
			// Clicking on a row will open up the right Rules Section
			sgTemplate.data("sgId", sgJSON.id).data("sgName", sgJSON.name).bind("click", function(event) {
				var sgId = $(this).data("sgId");
				var sgName = $(this).data("sgName");
				$("#submenu_content_security_groups .net_displaytitlebox h2").text(sgName);
				$("#submenu_content_security_groups .add_rules").data("sgId", sgId).show();
				$("#submenu_content_security_groups .net_displaybox_mid").show();
				
				// Load the grid
				$.ajax({
					data: "command=listNetworkRules&securitygroupId="+sgId+"&response=json",
					dataType: "json",
					success: function(json) {
						var rules = json.listnetworkrulesresponse.networkrule;
						$("#submenu_content_security_groups #display_gridcontent").empty();
						if (rules != null && rules.length > 0) {
							for (var i = 0; i < rules.length; i++) {
								var rule = rules[i];
								var sgRuleTemplate = $("#sg_rule_template").clone(true).attr("id","sg"+rule.id);
								sgRuleJSONToTemplate(sgRuleTemplate, rule);
								$("#submenu_content_security_groups #display_gridcontent").append(sgRuleTemplate.show());
							}
						}
					}
				});
			});
			
			// Deletion
			sgTemplate.find("#sg_delete").data("sgId", sgJSON.id).data("sgName", sgJSON.name).bind("click", function(event) {
				var sgId = $(this).data("sgId");
				var sgName = $(this).data("sgName");
				var submenuContent = $("#submenu_content_security_groups");
				
				$("#dialog_confirmation")
				.html("<p>Please confirm you want to delete your security group <b>"+sgName+"</b>.</p>")
				.dialog('option', 'buttons', { 
					"Cancel": function() { 
						$(this).dialog("close"); 
					},
					"Confirm": function() { 
						var dialogBox = $(this);
						$(this).dialog("close");
						$.ajax({
							data: "command=deleteSecurityGroup&id="+sgId+"&response=json",
							dataType: "json",
							success: function(json) {
								submenuContent.find(".net_displaytitlebox h2").text("Click on a Security Group to see more details");
								submenuContent.find(".add_rules").data("sgId", sgId).hide();
								submenuContent.find(".net_displaybox_mid").hide();
								submenuContent.find("#sg"+sgId).slideUp("slow", function() { $(this).remove() });
								changeGridRowsTotal(submenuContent.find("#grid_rows_total"), -1);
							}
						});
					} 
				}).dialog("open");
			});
		}
		
		//FUNCTION : Parses Security Group Rule JSON to template
		function sgRuleJSONToTemplate(sgRuleTemplate, sgRuleJSON) {
			if (sgRuleIndex++ % 2 == 0) {
				sgRuleTemplate.addClass("display_roweven");
			} else {
				sgRuleTemplate.addClass("display_rowodd");
			}

			// Populate the template
			sgRuleTemplate.find("#sg_rule_public_port").text(sgRuleJSON.publicport);
			sgRuleTemplate.find("#sg_rule_private_port").text(sgRuleJSON.privateport);
			sgRuleTemplate.find("#sg_rule_protocol").text(sgRuleJSON.protocol);
			
			// delete action
			sgRuleTemplate.find("#sg_rule_delete").data("sgId",sgRuleJSON.id).data("publicPort",sgRuleJSON.publicport).data("privatePort",sgRuleJSON.privateport).data("protocol",sgRuleJSON.protocol).bind("click", function(event) {
				var sgRule = $(this);
				var sgId = sgRule.data("sgId");
				var publicPort = sgRule.data("publicPort");
				var privatePort = sgRule.data("privatePort");
				var protocol = sgRule.data("protocol");
				var timerKey = "sgRule"+sgId;
				$("#dialog_confirmation")
				.html("<p>Please confirm you want to delete the following security group rule.<br/><br/><b>Public Port:  "+publicPort+"<br/>Private Port:  "+privatePort+"<br/>Protocol:  "+protocol+"</b></p>")
				.dialog('option', 'buttons', { 
					"Cancel": function() { 
						$(this).dialog("close"); 
					},
					"Confirm": function() { 
						var dialogBox = $(this);
						$(this).dialog("close");
						sgRuleTemplate.find("#sg_body").hide();
						sgRuleTemplate.find(".display_rowloading p").text("Deleting...");
						sgRuleTemplate.find(".display_rowloading").fadeIn("slow");
						$.ajax({
							data: "command=deleteNetworkRule&id="+sgId+"&response=json",
							dataType: "json",
							success: function(json) {
								var nwJSON = json.deletenetworkruleresponse;
								$("body").everyTime(
									5000,
									timerKey,
									function() {
										$.ajax({
											data: "command=queryAsyncJobResult&jobId="+nwJSON.jobid+"&response=json",
											dataType: "json",
											success: function(json) {
												var result = json.queryasyncjobresultresponse;
												if (result.jobstatus == 0) {
													return; //Job has not completed
												} else {
													$("body").stopTime(timerKey);
													if (result.jobstatus == 1) { // Succeeded
														sgRuleTemplate.find(".display_rowloading").hide();
														sgRuleTemplate.find("#sg_body").fadeIn("slow");
														sgRuleTemplate.slideUp("slow", function() {
															$(this).remove();
														});
													} else if (result.jobstatus == 2) { // Failed
														sgRuleTemplate.find(".display_rowloading").hide();
														sgRuleTemplate.find("#sg_body").fadeIn("slow");
														$("#dialog_error").html("<p style='color:red'>We were unable to delete the security group rule: <br/><br/><b>Public Port:  "+publicPort+"<br/>Private Port:  "+privatePort+"<br/>Protocol:  "+protocol+"</b></p>").dialog("open");
													}
												}
											},
											error: function(XMLHttpRequest) {
												$("body").stopTime(timerKey);
												handleError(XMLHttpRequest);
											}
										});
									},
									0
								);
							}
						});
					} 
				}).dialog("open");
			});
		}
		
		var lbInstanceTemplate = $("#lb_instance_template");
		lbInstanceTemplate.find(".load_closebutton").unbind("click").bind("click", function(event) {
			event.preventDefault();
			var lbInstanceId = $(this).data("lbInstanceId");
			var vmId = $(this).data("vmId");
			var vmName = $(this).data("vmName");
			var timerKey = "lbInstance"+vmId;
			var lbInstance = $("#submenu_content_load_balancer_policies #lbInstance"+vmId);
			$("#dialog_confirmation")
			.html("<p>Please confirm you want to remove the following Virtual Instance," + vmName + ", from your load balancer policy.</p>")
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Confirm": function() { 
					var dialogBox = $(this);
					$(this).dialog("close");
					lbInstance.find(".load_adding_text").html("Removing &hellip;");
					lbInstance.find(".load_loadingvm").fadeIn("slow");
					lbInstance.find(".load_workingvm").hide();
					$.ajax({
						data: "command=removeFromLoadBalancer&id="+lbInstanceId+"&virtualmachineid="+vmId+"&response=json",
						dataType: "json",
						success: function(json) {
							var lbJSON = json.removefromloadbalancerresponse;
							$("body").everyTime(
								5000,
								timerKey,
								function() {
									$.ajax({
										data: "command=queryAsyncJobResult&jobId="+lbJSON.jobid+"&response=json",
										dataType: "json",
										success: function(json) {
											var result = json.queryasyncjobresultresponse;
											if (result.jobstatus == 0) {
												return; //Job has not completed
											} else {
												$("body").stopTime(timerKey);
												if (result.jobstatus == 1) { // Succeeded
													lbInstance.fadeOut("slow", function(event) {
														$(this).remove();
													});
												} else if (result.jobstatus == 2) { // Failed
													lbInstance.find(".load_loadingvm").hide();
													lbInstance.find(".load_workingvm").fadeIn("slow");
													$("#dialog_error").html("<p style='color:red'>We were unable to remove the Virtual Instance: "+vmName + " from your load balancer policy.  Please try again.").dialog("open");
												}
											}
										},
										error: function(XMLHttpRequest) {
											$("body").stopTime(timerKey);
											handleError(XMLHttpRequest);
										}
									});
								},
								0
							);
						}
					});
				} 
			}).dialog("open");
		});
		
		//FUNCTION : Parses Load Balancer JSON to template
		function lbJSONToTemplate(lbJSON, lbTemplate) {
		    lbTemplate.attr("id","lb"+lbJSON.id);
		
			if (index++ % 2 == 0) {
				lbTemplate.addClass("smallrow_even");
			} else {
				lbTemplate.addClass("smallrow_odd");
			}

			// Populate the template
			lbTemplate.find("#lb_name").text(lbJSON.name);
			lbTemplate.find("#lb_public_ip").text(lbJSON.publicip);
			lbTemplate.find("#lb_public_port").text(lbJSON.publicport);
			lbTemplate.find("#lb_private_port").text(lbJSON.privateport);
			lbTemplate.find("#lb_algorithm").text(lbJSON.algorithm);
			lbTemplate.find("#lb_account").text(lbJSON.account);

			// Clicking on a row will open up the right Load Balancer Instance section
			lbTemplate.data("lbId", lbJSON.id).data("lbName", lbJSON.name).data("lbPublicIp", lbJSON.publicip).bind("click", function(event) {
				var lbId = $(this).data("lbId");
				var lbName = $(this).data("lbName");
				$("#submenu_content_load_balancer_policies .net_displaytitlebox h2").text(lbName);
				$("#submenu_content_load_balancer_policies .add_rules").data("lbId", lbId).show();
				$("#submenu_content_load_balancer_policies #lb_instance_new").data("lbId", lbId);
				$("#submenu_content_load_balancer_policies .net_displaybox_mid").show();
				$("#submenu_content_load_balancer_policies #lb_rule_ip").text($(this).data("lbPublicIp"));
				
				// Load the grid
				$.ajax({
					cache: false,
					data: "command=listLoadBalancerInstances&id="+lbId+"&response=json",
					dataType: "json",
					success: function(json) {
						var instances = json.listloadbalancerinstancesresponse.loadbalancerinstance;
						var lbInstanceContent = $("#submenu_content_load_balancer_policies #load_vmlist_container").empty();
						if (instances != null && instances.length > 0) {
							$("#lb_instance_new").hide();
							for (var i = 0; i < instances.length; i++) {
								lbInstance = lbInstanceTemplate.clone(true).attr("id", "lbInstance"+instances[i].id);
								lbInstance.find(".loadingvmtext").html("<p>"+instances[i].name+"<br/>"+instances[i].privateip+"</p>");
								lbInstance.find(".load_closebutton").data("lbInstanceId", lbId).data("vmId", instances[i].id).data("vmName",instances[i].name);
								lbInstance.find(".load_workingvm").show();
								lbInstanceContent.append(lbInstance.show());
							}
						} else {
							$("#lb_instance_new").show();
						}
					}
				});
			});
			
			// Deletion
			lbTemplate.find("#lb_delete").data("lbId", lbJSON.id).data("lbName", lbJSON.name).bind("click", function(event) {
				var lbId = $(this).data("lbId");
				var lbName = $(this).data("lbName");
				var lbRow = $("#lb"+lbId);
				var timerKey = "lbRule"+lbId;
				$("#dialog_confirmation")
				.html("<p>Please confirm you want to delete your load balancer policy: <b>"+lbName+"</b>.</p>")
				.dialog('option', 'buttons', { 
					"Cancel": function() { 
						$(this).dialog("close"); 
					},
					"Confirm": function() { 
						var dialogBox = $(this);
						$(this).dialog("close");						
						var submenuContent = $("#submenu_content_load_balancer_policies");						
						lbRow.find(".adding_loading").show();
						lbRow.find("#lb_body").hide();
						$.ajax({
							data: "command=deleteLoadBalancer&id="+lbId+"&response=json",
							dataType: "json",
							success: function(json) {
								var lbJSON = json.deleteloadbalancerresponse;
								$("body").everyTime(
									5000,
									timerKey,
									function() {
										$.ajax({
											data: "command=queryAsyncJobResult&jobId="+lbJSON.jobid+"&response=json",
											dataType: "json",
											success: function(json) {
												var result = json.queryasyncjobresultresponse;
												if (result.jobstatus == 0) {
													return; //Job has not completed
												} else {
													$("body").stopTime(timerKey);
													if (result.jobstatus == 1) { // Succeeded
														submenuContent.find(".net_displaytitlebox h2").text("Click on a Load Balancer to see more details");
														submenuContent.find(".add_rules").hide();
														submenuContent.find(".net_displaybox_mid").hide();
														lbRow.slideUp("slow", function() {
															$(this).remove();
															changeGridRowsTotal(submenuContent.find("#grid_rows_total"), -1);
														});
													} else if (result.jobstatus == 2) { // Failed
														lbRow.find(".adding_loading").hide();
														lbRow.find("#sg_body").fadeIn("slow");
													}
												}
											},
											error: function(XMLHttpRequest) {
												$("body").stopTime(timerKey);
												handleError(XMLHttpRequest);
											}
										});
									},
									0
								);
							}
						});
					} 
				}).dialog("open");
			});
		}
		
		// Dialog Setup
		activateDialog($("#dialog_acquire_public_ip").dialog({ 
			width: 325,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		activateDialog($("#dialog_add_security_groups").dialog({ 
			width: 325,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		activateDialog($("#dialog_add_security_group_rule").dialog({ 
			width: 325,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		activateDialog($("#dialog_add_lb_policy").dialog({ 
			width: 325,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		activateDialog($("#dialog_add_vm_to_lb").dialog({ 
			width: 325,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		// Add Public IP Dialog
		$(".add_publicipbutton").bind("click", function(event) {
			event.preventDefault();
			var submenuContent = $("#submenu_content_public_ips");
			$("#dialog_acquire_public_ip").dialog("option", "buttons", {
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Acquire": function() { 
					var dialogBox = $(this);
					var zId = dialogBox.find("#acquire_zone").val();
					dialogBox.dialog("close");
					$.ajax({
						data: "command=associateIpAddress&zoneid="+zId+"&response=json",
						dataType: "json",
						success: function(json) {
							var ipJSON = json.associateipaddressresponse.publicipaddress[0];
							var ipTemplate = $("#ip_template").clone(true).attr("id","ip"+ipJSON.ipaddress);
							ipJSONToTemplate(ipJSON, ipTemplate);
							submenuContent.find("#grid_content").prepend(ipTemplate.slideDown("slow"));
							changeGridRowsTotal(submenuContent.find("#grid_rows_total"), 1);
						}
					});
				}
			});
			$("#dialog_acquire_public_ip").dialog("open");
		});
		
		// Add a Security Group Rule Dialog
		$("#submenu_content_security_groups .add_rules").bind("click", function(event) {
			event.preventDefault();
			$("#security_group_public_port").val("");
			$("#security_group_private_port").val("");
			$("#security_group_protocol").val("");
			
			var sgId = $(this).data("sgId");
			$("#dialog_add_security_group_rule").dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Add": function() { 					
					// validate values
					var isValid = true;							
					isValid &= validateNumber("Public Port", $("#security_group_public_port"), $("#security_group_public_port_errormsg"), 1, 65535);
					isValid &= validateNumber("Private Port", $("#security_group_private_port"), $("#security_group_private_port_errormsg"), 1, 65535);				
					if (!isValid) return;
					
					var publicPort = trim($("#security_group_public_port").val());
					var privatePort = trim($("#security_group_private_port").val());
					var protocol = $("#security_group_protocol").val();
										
					var dialogBox = $(this);		
					$.ajax({
						data: "command=createNetworkRule&publicport="+publicPort+"&privateport="+privatePort+"&protocol="+protocol+"&securitygroupid="+sgId+"&response=json",
						dataType: "json",
						success: function(json) {
							var sgRuleJSON = json.createnetworkruleresponse;
							var timerKey = "sg"+sgRuleJSON.jobid;
							var sgRuleTemplate = $("#sg_rule_template").clone(true).attr("id","sg"+sgRuleJSON.jobid);
							sgRuleTemplate.find(".display_rowloading p").text("Adding...");
							sgRuleTemplate.find(".display_rowloading").show();
							sgRuleTemplate.find("#sg_body").hide();
							$("#submenu_content_security_groups #display_gridcontent").prepend(sgRuleTemplate.fadeIn("slow"));
							$("body").everyTime(
								5000,
								timerKey,
								function() {
									$.ajax({
										data: "command=queryAsyncJobResult&jobId="+sgRuleJSON.jobid+"&response=json",
										dataType: "json",
										success: function(json) {
											var result = json.queryasyncjobresultresponse;
											if (result.jobstatus == 0) {
												return; //Job has not completed
											} else {
												$("body").stopTime(timerKey);
												if (result.jobstatus == 1) { // Succeeded
													sgRuleTemplate.find(".display_rowloading").hide();
													sgRuleJSONToTemplate(sgRuleTemplate, result.networkrule[0]);
													sgRuleTemplate.find("#sg_body").fadeIn("slow");
												} else if (result.jobstatus == 2) { // Failed
													sgRuleTemplate.fadeOut("slow", function() {
														$(this).remove();
													});
												}
											}
										},
										error: function(XMLHttpRequest) {
											$("body").stopTime(timerKey);
											handleError(XMLHttpRequest);
										}
									});
								},
								0
							);
						}
					});
					dialogBox.dialog("close");
				}
			}).dialog("open");
		});

		// Add Security Group Dialog
		$(".add_sgroupbutton").bind("click", function(event) {
			event.preventDefault();			
			var submenuContent = $("#submenu_content_security_groups");			
			$("#security_group_name").val("");
			$("#security_group_desc").val("");
			$("#dialog_add_security_groups").dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Add": function() { 					
					// validate values
					var isValid = true;
					isValid &= validateString("Name", $("#security_group_name"), $("#security_group_name_errormsg"));
					isValid &= validateString("Description", $("#security_group_desc"), $("#security_group_desc_errormsg"));					
					if (!isValid) return;	
					
					var name = trim($("#security_group_name").val());
					var desc = trim($("#security_group_desc").val());
					
					var dialogBox = $(this);				
					$.ajax({
						data: "command=createSecurityGroup&name="+encodeURIComponent(name)+"&description="+encodeURIComponent(desc)+"&response=json",
						dataType: "json",
						success: function(json) {
							var sgJSON = json.createsecuritygroupresponse.securitygroup[0];
							var sgTemplate = $("#sg_template").clone(true).attr("id","sg"+sgJSON.id);
							sgJSONToTemplate(sgJSON, sgTemplate);
							submenuContent.find("#grid_content").prepend(sgTemplate.fadeIn("slow"));
							changeGridRowsTotal(submenuContent.find("#grid_rows_total"), 1);
							dialogBox.dialog("close");
						}
					});
					dialogBox.dialog("close");
				}
			}).dialog("open");
		});
		
		// Add Load Balancer Policy Dialog
		$(".add_loadbalancerbutton").bind("click", function(event) {
			event.preventDefault();
			$("#lb_form_name").val("");
			$("#lb_form_public_port").val("");
			$("#lb_form_private_port").val("");
			//$("#lb_form_algorithm").val("");
			
			$.ajax({
				data: "command=listPublicIpAddresses&response=json",
				dataType: "json",
				success: function(json) {
					var addressesJSON = json.listpublicipaddressesresponse.publicipaddress;
					var ipSelect = $("#lb_form_public_ip").empty();
					if (addressesJSON != null && addressesJSON.length > 0 ) {
						for (var i = 0; i < addressesJSON.length; i++) {
							ipSelect.append("<option value='" + addressesJSON[i].ipaddress + "'>" + addressesJSON[i].ipaddress + "</option>"); 
						}
					} else {
						ipSelect.append("<option value='none'>No Public IPs</option>"); 
					}
				}
			});
				
			$("#dialog_add_lb_policy").dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Add": function() { 					
					// validate values
					var isValid = true;					
					isValid &= validateString("Name", $("#lb_form_name"), $("#lb_form_name_errormsg"));
					isValid &= validateNumber("Public Port", $("#lb_form_public_port"), $("#lb_form_public_port_errormsg"), 1, 65535);
					isValid &= validateNumber("Private Port", $("#lb_form_private_port"), $("#lb_form_private_port_errormsg"), 1, 65535);				
					if (!isValid) return;
					
					var submenuContent = $("#submenu_content_load_balancer_policies");
					
					var name = trim($("#lb_form_name").val());
					var ip = $("#lb_form_public_ip").val();
					var publicPort = trim($("#lb_form_public_port").val());
					var privatePort = trim($("#lb_form_private_port").val());
					var algorithm = $("#lb_form_algorithm").val();
					
					var dialogBox = $(this);
					$.ajax({
						data: "command=createLoadBalancer&name="+encodeURIComponent(name)+"&ipaddress="+ip+"&publicport="+publicPort+"&privateport="+privatePort+"&algorithm="+algorithm+"&response=json",
						dataType: "json",
						success: function(json) {
							var lbJSON = json.createloadbalancerresponse.loadbalancer[0];
							var lbTemplate = $("#lb_template").clone(true).attr("id","lb"+lbJSON.id);
							lbJSONToTemplate(lbJSON, lbTemplate);
							submenuContent.find("#grid_content").prepend(lbTemplate.fadeIn("slow"));
							changeGridRowsTotal(submenuContent.find("#grid_rows_total"), 1);
						}
					});
					dialogBox.dialog("close");
				}
			}).dialog("open");
		});
		
		// Add a VM to a Load Balancer Dialog
		$("#submenu_content_load_balancer_policies .add_rules, #submenu_content_load_balancer_policies #lb_instance_new").bind("click", function(event) {
			event.preventDefault();
			var lbId = $(this).data("lbId");
			
			// Load the select box with the VMs that haven't been applied a LB rule to.
			$.ajax({
				cache: false,
				data: "command=listLoadBalancerInstances&id="+lbId+"&applied=false&response=json",
				dataType: "json",
				success: function(json) {
					var instances = json.listloadbalancerinstancesresponse.loadbalancerinstance;
					var vmSelect = $("#vm_to_lb").empty();
					if (instances != null && instances.length > 0) {
						for (var i = 0; i < instances.length; i++) {
							html = $("<option value='" + instances[i].id + "'>" + instances[i].name + "</option>")
							html.data("vmIp", instances[i].privateip);
							vmSelect.append(html); 
						}
					} else {
						vmSelect.append("<option value='none'>None Available</option>");
					}
				}
			});
			
			var lbId = $(this).data("lbId");
			$("#dialog_add_vm_to_lb").dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Add": function() { 
					var dialogBox = $(this);
					var selected = $("#vm_to_lb");
					var vmId = selected.val();
					var vmName = selected.find(":selected").text();
					var vmIp = selected.find(":selected").data("vmIp");
					
					$.ajax({
						data: "command=assignToLoadBalancer&id="+lbId+"&virtualmachineid="+vmId+"&response=json",
						dataType: "json",
						success: function(json) {
							var lbInstanceJSON = json.assigntoloadbalancerresponse;
							$("#lb_instance_new").hide();
							var lbInstanceContent = $("#submenu_content_load_balancer_policies #load_vmlist_container");
							var lbInstanceNew = $("#lb_instance_template").clone(true).attr("id", "lbInstanceNew"+lbInstanceJSON.jobid);
							lbInstanceNew.find(".loadingvmtext").html("<p>"+vmName+"<br/>"+vmIp+"</p>");
							lbInstanceNew.find(".load_closebutton").data("lbInstanceId", lbId).data("vmId", vmId).data("vmName",vmName);
							lbInstanceNew.find(".load_loadingvm").show();
							lbInstanceNew.find(".load_workingvm").hide();
							lbInstanceContent.append(lbInstanceNew.fadeIn("slow"));
							var timerKey = "lbInstanceNew"+lbInstanceJSON.jobid;
							dialogBox.dialog("close");
							$("body").everyTime(
								5000,
								timerKey,
								function() {
									$.ajax({
										data: "command=queryAsyncJobResult&jobId="+lbInstanceJSON.jobid+"&response=json",
										dataType: "json",
										success: function(json) {
											var result = json.queryasyncjobresultresponse;
											if (result.jobstatus == 0) {
												return; //Job has not completed
											} else {
												$("body").stopTime(timerKey);
												if (result.jobstatus == 1) { // Succeeded
													lbInstanceNew.attr("id", "lbInstance" + vmId);
													lbInstanceNew.find(".load_loadingvm").hide();
													lbInstanceNew.find(".load_workingvm").fadeIn();
												} else if (result.jobstatus == 2) { // Failed
													$("#dialog_error").html("<p style='color:red'><b>Operation error:</b></p><br/><p style='color:red'>"+ result.jobresult+"</p>").dialog("open");
													lbInstanceNew.find(".load_loadingvm").hide();
													$("#lb_instance_new").show();
												}
											}
										},
										error: function(XMLHttpRequest) {
											$("body").stopTime(timerKey);
											handleError(XMLHttpRequest);
										}
									});
								},
								0
							);
						}
					});
				}
			}).dialog("open");
		});
		
		// SubMenu Links				
		var currentSubMenu = $("#submenu_public_ips");
		var gridContainer = $("#submenu_content_public_ips #grid_content");
		
		function listPublicIpAddresses() {	
		    var submenuContent = $("#submenu_content_public_ips");
			    		 
        	var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");   	              
			if (advanced != null && advanced) {					   
			    var zone = submenuContent.find("#advanced_search #adv_search_zone").val();			  
			    var account = submenuContent.find("#advanced_search #adv_search_account").val();
			    var moreCriteria = [];					
			    if (zone!=null && zone.length > 0) 
					moreCriteria.push("&zoneId="+zone);	 
				if (account!=null && account.length > 0) 
					moreCriteria.push("&account="+account);		
				commandString = "command=listPublicIpAddresses&page="+currentPage+moreCriteria.join("")+"&response=json";       
			} else {              
        	    var searchInput = submenuContent.find("#search_input").val();            
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listPublicIpAddresses&page="+currentPage+"&keyword="+searchInput+"&response=json"
                else
                    commandString = "command=listPublicIpAddresses&page="+currentPage+"&response=json";    
            }
		
		    //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listpublicipaddressesresponse", "publicipaddress", $("#ip_template"), ipJSONToTemplate);   		
		}
		
		submenuContentEventBinder($("#submenu_content_public_ips"), listPublicIpAddresses);
		
		$("#submenu_public_ips").bind("click", function(event) {				
			event.preventDefault();
			
			$(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);
			var submenuContent = $("#submenu_content_public_ips").show();			
			$("#submenu_content_security_groups").hide();
			$("#submenu_content_load_balancer_policies").hide();			
			
			if (isAdmin()) {
				$("#ip_account_header, #ip_account_container, #ip_vlan_header, #ip_vlan_container, #ip_domain_header, #ip_domain_container").show();
				$("#ip_release_header").removeClass("ip_gridheader_cell2").addClass("ip_gridheader_cell3");
				$("#ip_release_container").removeClass("ip_gridrow_cell2").addClass("ip_gridrow_cell3");				
				submenuContent.find("#adv_search_account_li").show();  
			}
			
			currentPage = 1;  
			listPublicIpAddresses();			
		});				
		
		//Security Groups						 
		function listSecurityGroups() {		 
		    var submenuContent = $("#submenu_content_security_groups");
		   
	        var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();				   
			    var account = submenuContent.find("#advanced_search #adv_search_account").val();
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));					
				if (account!=null && account.length > 0) 
					moreCriteria.push("&account="+account);		
				commandString = "command=listSecurityGroups&page="+currentPage+moreCriteria.join("")+"&response=json";   
			} else {         
	            var searchInput = submenuContent.find("#search_input").val();            
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listSecurityGroups&page="+currentPage+"&keyword="+searchInput+"&response=json";
                else
                    commandString = "command=listSecurityGroups&page="+currentPage+"&response=json";
            }
	        
	        //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listsecuritygroupsresponse", "securitygroup", $("#sg_template"), sgJSONToTemplate);    	        
		}
		
		submenuContentEventBinder($("#submenu_content_security_groups"), listSecurityGroups);
		
		$("#submenu_security_groups").bind("click", function(event) {		
			event.preventDefault();
			
			$(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);		
			var submenuContent = $("#submenu_content_security_groups").show();
			$("#submenu_content_public_ips").hide();
			$("#submenu_content_load_balancer_policies").hide();			
			
			if (isAdmin()) {
				$("#sg_account_container, #sg_rule_account_header").show();
				$("#sg_desc_container").removeClass("net_row_cell2").addClass("net_row_cell4");
				$("#sg_rule_desc_header").removeClass("netgridheader_cell2").addClass("netgridheader_cell4");				
				submenuContent.find("#adv_search_account_li").show();  
			}
			
			currentPage = 1;  
			listSecurityGroups();
		});
				
		// Load Balancer Policies		
		var lbTemplate = $("#lb_template");
				
		function listLoadBalancers() {	
		    var submenuContent = $("#submenu_content_load_balancer_policies");
		    
	        var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();				   
			    var account = submenuContent.find("#advanced_search #adv_search_account").val();
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));			
				if (account!=null && account.length > 0) 
					moreCriteria.push("&account="+account);		
				commandString = "command=listLoadBalancers&page="+currentPage+moreCriteria.join("")+"&response=json";  
			} else {          
	            var searchInput = submenuContent.find("#search_input").val();            
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listLoadBalancers&page="+currentPage+"&keyword="+searchInput+"&response=json"
                else
                    commandString = "command=listLoadBalancers&page="+currentPage+"&response=json";
            }
	        
	        //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listloadbalancersresponse", "loadbalancer", lbTemplate, lbJSONToTemplate);   
		}
		
		submenuContentEventBinder($("#submenu_content_load_balancer_policies"), listLoadBalancers);
		
		$("#submenu_load_balancer_policies").bind("click", function(event) {	
			event.preventDefault();
			
			$(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);			
			var submenuContent = $("#submenu_content_load_balancer_policies").show();
			$("#submenu_content_public_ips").hide();
			$("#submenu_content_security_groups").hide();
			
			if (isAdmin()) {
				$("#lb_account_header, #lb_account_container").show();
				$("#lb_name_header").removeClass("loadgridheader_cell1").addClass("loadgridheader_cell4");
				$("#lb_name_container").removeClass("load_row_cell1").addClass("load_row_cell4");				
				submenuContent.find("#adv_search_account_li").show();   
			}	
			
			currentPage = 1;
			listLoadBalancers();		
		});		
				
		$("#submenu_public_ips").click();
	});
}