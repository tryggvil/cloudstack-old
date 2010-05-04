function showHostsTab() {
	mainContainer.load("content/tab_hosts.html", function() {		
	    var rIndex = 0;	
		var sIndex = 0;
		var pIndex = 0;
		
		// Dialog Setup
		if (getHypervisorType() != "kvm") {
			$("#host_action_new_routing").show();
			activateDialog($("#dialog_add_routing").dialog({ 
				autoOpen: false,
				modal: true,
				zIndex: 2000
			}));
			$.ajax({
				data: "command=listZones&available=true&response=json",
				dataType: "json",
				success: function(json) {
					var zones = json.listzonesresponse.zone;
					var zoneSelect = $("#dialog_add_routing #host_zone").empty();								
					if (zones != null && zones.length > 0) {
						for (var i = 0; i < zones.length; i++) 
							zoneSelect.append("<option value='" + zones[i].id + "'>" + zones[i].name + "</option>"); 				    
					}
					$("#dialog_add_routing #host_zone").change();
				}
			});
			$("#dialog_add_routing #host_zone").bind("change", function(event) {
				var zoneId = $(this).val();
				$.ajax({
					data: "command=listPods&zoneId="+zoneId+"&response=json",
					dataType: "json",
					async: false,
					success: function(json) {
						var pods = json.listpodsresponse.pod;
						var podSelect = $("#dialog_add_routing #host_pod").empty();	
						if (pods != null && pods.length > 0) {
							for (var i = 0; i < pods.length; i++) {
								podSelect.append("<option value='" + pods[i].id + "'>" + pods[i].name + "</option>"); 
							}
						}
						$("#dialog_add_routing #host_pod").change();
					}
				});
			});
		}
		activateDialog($("#dialog_update_os").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		$.ajax({
			data: "command=listOSCategories&response=json",
			dataType: "json",
			success: function(json) {
				var categories = json.listoscategoriesresponse.oscategory;
				var select = $("#dialog_update_os #host_os");								
				if (categories != null && categories.length > 0) {
					for (var i = 0; i < categories.length; i++) 
						select.append("<option value='" + categories[i].id + "'>" + categories[i].name + "</option>"); 				    
				}
			}
		});
		
		// Routing Template Setup
		var routingTemplate = $("#routing_template");
		routingTemplate.bind("mouseenter", function(event) {
			$(this).find("#grid_links_container").show();
			return false;
		});
		routingTemplate.bind("mouseleave", function(event) {
			$(this).find("#grid_links_container").hide();
			return false;
		});
		
		routingTemplate.bind("click", function(event) {
			var template = $(this);
			var link = $(event.target);
			var linkAction = link.attr("id");
			var hostId = template.data("hostId");
			var hostName = template.data("hostName");
			switch (linkAction) {
				case "host_action_details" :
					var expanded = link.data("expanded");
					if (expanded == null || expanded == false) {
						var index = 0;
						$.ajax({
							cache: false,
							data: "command=listVirtualMachines&hostId="+hostId+"&response=json",
							dataType: "json",
							success: function(json) {
								var instances = json.listvirtualmachinesresponse.virtualmachine;
								if (instances != null && instances.length > 0) {
									var grid = template.find("#detail_container").empty();
									var detailTemplate = $("#routing_detail_template");
									for (var i = 0; i < instances.length; i++) {
										var detail = detailTemplate.clone(true).attr("id","vm"+instances[i].id);
										if (index++ % 2 == 0) {
											detail.addClass("hostadmin_showdetails_row_even");
										} else {
											detail.addClass("hostadmin_showdetails_row_odd");
										}
										detail.find("#detail_type").text("VM");
										detail.find("#detail_name").text(instances[i].name);
										detail.find("#detail_ip").text(instances[i].ipaddress);
										detail.find("#detail_service").text(instances[i].serviceofferingname);
										detail.find("#detail_owner").text(instances[i].account);
										
										var created = new Date();
										created.setISO8601(instances[i].created);
										var showDate = created.format("m/d/Y H:i:s");
										detail.find("#detail_created").text(showDate);
										grid.append(detail.show());
									}
								}
								template.find("#host_action_details_container img").attr("src", "images/details_uparrow.jpg");
								template.find("#host_action_details_container a").text("Hide Details");
								template.find("#host_detail_panel").slideDown("slow");
								link.data("expanded", true);
							}
						});
					} else {
						template.find("#host_action_details_container img").attr("src", "images/details_downarrow.jpg");
						template.find("#host_action_details_container a").text("Show Details");
						template.find("#host_detail_panel").slideUp("slow");
						link.data("expanded", false);
					}
					break;
				case "host_action_enable_maint" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you enable maintenance for host: <b>"+hostName+"</b>.  Enabling maintenance mode will cause a live migration of all running instances on this host to any available host.  An alert will be sent to the admin when this process has been completed.</p>")
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
						"Confirm": function() { 
							var dialogBox = $(this);
							$.ajax({
								data: "command=prepareHostForMaintenance&id="+hostId+"&response=json",
								dataType: "json",
								success: function(json) {
									dialogBox.dialog("close");
									
									template.find(".row_loading").show();
									template.find(".loading_animationcontainer .loading_animationtext").text("Preparing...");
									template.find(".loading_animationcontainer").show();
									template.fadeIn("slow");
									template.find(".continue_button").data("hostId", hostId).unbind("click").bind("click", function(event) {
										event.preventDefault();
										var template = $("#host"+$(this).data("hostId"));
										template.find(".loading_animationcontainer").hide();
										template.find(".loadingmessage_container").fadeOut("slow");
										template.find(".row_loading").fadeOut("slow");
									});
									var timerKey = "host"+hostId;
									$("body").everyTime(
										15000, // Migration could possibly take a while
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.preparehostformaintenanceresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														$("body").stopTime(timerKey);
														if (result.jobstatus == 1) {
															// Succeeded
															template.find("#host_state_bar").removeClass("yellow_statusbar green_statusbar red_statusbar").addClass("grey_statusbar");
															template.find("#routing_state").text("Maintenance").removeClass("grid_runningtitles grid_stoppedtitles").addClass("grid_celltitles ");
															template.find(".loadingmessage_container .loadingmessage_top p").html("Your host has been successfully prepared for maintenance.");
															template.find(".loadingmessage_container").fadeIn("slow");
															template.find(".grid_links").find("#host_action_reconnect_container, #host_action_enable_maint_container").hide();
															template.find(".grid_links").find("#host_action_remove_container, #host_action_cancel_maint_container").show();
														} else if (result.jobstatus == 2) {
															// Failed
															template.find("#host_state_bar").removeClass("yellow_statusbar green_statusbar red_statusbar").addClass("grey_statusbar");
															template.find("#routing_state").text("ErrorInMaintenance").removeClass("grid_runningtitles grid_stoppedtitles").addClass("grid_celltitles ");
															template.find(".loadingmessage_container .loadingmessage_top p").text("We were unable to successfully prepare your host for maintenance.  Please check your logs for more info.");
															template.find(".loadingmessage_container").fadeIn("slow");
															template.find(".grid_links").find("#host_action_reconnect_container, #host_action_remove_container").hide();
															template.find(".grid_links").find("#host_action_enable_maint_container, #host_action_cancel_maint_container").show();
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
					break;
				case "host_action_cancel_maint" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to cancel maintenance for host: <b>"+hostName+"</b>. </p>")
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
						"Confirm": function() { 
							var dialogBox = $(this);
							$.ajax({
								data: "command=cancelHostMaintenance&id="+hostId+"&response=json",
								dataType: "json",
								success: function(json) {
									dialogBox.dialog("close");
									
									template.find(".row_loading").show();
									template.find(".loading_animationcontainer .loading_animationtext").text("Cancelling...");
									template.find(".loading_animationcontainer").show();
									template.fadeIn("slow");
									template.find(".continue_button").data("hostId", hostId).unbind("click").bind("click", function(event) {
										event.preventDefault();
										var template = $("#host"+$(this).data("hostId"));
										template.find(".loading_animationcontainer").hide();
										template.find(".loadingmessage_container").fadeOut("slow");
										template.find(".row_loading").fadeOut("slow");
									});
									var timerKey = "host"+hostId;
									$("body").everyTime(
										5000,
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.cancelhostmaintenanceresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														$("body").stopTime(timerKey);
														if (result.jobstatus == 1) {
															// Succeeded
															template.find("#host_state_bar").removeClass("yellow_statusbar green_statusbar red_statusbar").addClass("grey_statusbar");
															template.find("#routing_state").text("Disconnected").removeClass("grid_runningtitles grid_stoppedtitles").addClass("grid_celltitles ");
															template.find(".loadingmessage_container .loadingmessage_top p").html("The maintenance process for this host has been successfully cancelled.");
															template.find(".loadingmessage_container").fadeIn("slow");
														} else if (result.jobstatus == 2) {
															// Failed
															template.find("#host_state_bar").removeClass("yellow_statusbar green_statusbar red_statusbar").addClass("grey_statusbar");
															template.find("#routing_state").text("Disconnected").removeClass("grid_runningtitles grid_stoppedtitles").addClass("grid_celltitles ");
															template.find(".loadingmessage_container .loadingmessage_top p").text("We were unable to cancel your maintenance process.  Please try again.");
															template.find(".loadingmessage_container").fadeIn("slow");
														}
														template.find(".grid_links").find("#host_action_reconnect_container, #host_action_enable_maint_container, #host_action_cancel_maint_container, #host_action_remove_container").hide();
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
					break;
				case "host_action_reconnect" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to force a reconnection for host: <b>"+hostName+"</b>. </p>")
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
						"Confirm": function() { 
							var dialogBox = $(this);
							$.ajax({
								data: "command=reconnectHost&id="+hostId+"&response=json",
								dataType: "json",
								success: function(json) {
									dialogBox.dialog("close");
									
									template.find(".row_loading").show();
									template.find(".loading_animationcontainer .loading_animationtext").text("Reconnecting...");
									template.find(".loading_animationcontainer").show();
									template.fadeIn("slow");
									template.find(".continue_button").data("hostId", hostId).unbind("click").bind("click", function(event) {
										event.preventDefault();
										var template = $("#host"+$(this).data("hostId"));
										template.find(".loading_animationcontainer").hide();
										template.find(".loadingmessage_container").fadeOut("slow");
										template.find(".row_loading").fadeOut("slow");
									});
									var timerKey = "host"+hostId;
									$("body").everyTime(
										5000,
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.reconnecthostresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														$("body").stopTime(timerKey);
														if (result.jobstatus == 1) {
															// Succeeded
															template.find("#host_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar");
															template.find("#routing_state").text("Up").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
															template.find(".loadingmessage_container .loadingmessage_top p").html("Your host has been successfully reconnected.");
															template.find(".loadingmessage_container").fadeIn("slow");
															template.find(".grid_links").find("#host_action_reconnect_container, #host_action_enable_maint_container").show();
															template.find(".grid_links").find("#host_action_remove_container, #host_action_cancel_maint_container").hide();
														} else if (result.jobstatus == 2) {
															// Failed
															template.find("#host_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar");
															template.find("#routing_state").text("Disconnected").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
															template.find(".loadingmessage_container .loadingmessage_top p").text("We were unable to reconnect your host.  Please try again.");
															template.find(".loadingmessage_container").fadeIn("slow");
															template.find(".grid_links").find("#host_action_reconnect_container, #host_action_enable_maint_container, #host_action_cancel_maint_container, #host_action_remove_container").hide();
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
					break;
				case "host_action_remove" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to remove this host: <b>"+hostName+"</b> from the management server. </p>")
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
						"Confirm": function() { 
							var dialogBox = $(this);
							$.ajax({
								data: "command=deleteHost&id="+hostId+"&response=json",
								dataType: "json",
								success: function(json) {
									dialogBox.dialog("close");
									template.slideUp("slow", function() {
										$(this).remove();
									});
								}
							});
						} 
					}).dialog("open");
					break;
				case "host_action_update_os" :
					$("#dialog_update_os #host_os").val(template.data("osId"));
					$("#dialog_update_os")
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
						"Update": function() { 
							var dialogBox = $(this);
							var osId = $("#dialog_update_os #host_os").val();
							var osName = $("#dialog_update_os #host_os option:selected").text();
							var category = "";
							if (osId.length > 0) {
								category = "&osCategoryId="+osId;
							}
							$.ajax({
								data: "command=updateHost&id="+hostId+category+"&response=json",
								dataType: "json",
								success: function(json) {
									template.find("#routing_os").text(osName);
									template.data("osId", osId);
									dialogBox.dialog("close");
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
	
		// FUNCTION: Routing JSON to Template
		function routingJSONToTemplate(json, template) {
		    template.attr("id", "host"+json.id);
		
			if (index++ % 2 == 0) {
				template.find("#row_container").addClass("row_even");
			} else {
				template.find("#row_container").addClass("row_odd");
			}
			template.data("hostId", json.id).data("hostName", json.name);
			if (json.hypervisor == "KVM") {
				template.find("#routing_hypervisor img").attr("src", "images/KVM_icon.gif");
			} else {
				// default to XEN for now
				template.find("#routing_hypervisor img").attr("src", "images/XEN_icon.gif");
			}
			template.find("#routing_id").text(json.id);
			template.find("#routing_name").text(json.name);
			template.find("#routing_ip").text(json.ipaddress);
			template.find("#routing_version").text(json.version);
			template.find("#routing_mgmt").text(json.managementserverid);
			template.find("#routing_os").text(json.oscategoryname);
			template.data("osId", json.oscategoryid);
			if (json.disconnected != null && json.disconnected.length > 0) {
				var disconnected = new Date();
				disconnected.setISO8601(json.disconnected);
				var showDate = disconnected.format("m/d/Y H:i:s");
				template.find("#routing_disconnected").text(showDate);
			}
			
			var statHtml = "<div class='hostcpu_icon'></div><p><strong> CPU Total:</strong> " +json.cpunumber+ " x " + convertHz(json.cpuspeed)+" | <strong>CPU Allocated:</strong> " + json.cpuallocated + " | <span class='host_statisticspanel_green'> <strong>CPU Used:</strong> " + json.cpuused + "</span></p>";
			template.find("#host_cpu_stat").html(statHtml);
			statHtml = "<div class='hostmemory_icon'></div><p><strong> MEM Total:</strong> " +convertBytes(json.memorytotal)+" | <strong>MEM Allocated:</strong> " + convertBytes(json.memoryallocated) + " | <span class='host_statisticspanel_green'> <strong>MEM Used:</strong> " + json.memoryused + "</span></p>";
			template.find("#host_mem_stat").html(statHtml);
			
			// State
			if (json.state == 'Up' || json.state == "Connecting") {
				template.find("#host_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar ");
				template.find("#routing_state").text(json.state).removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
				template.find(".grid_links").find("#host_action_cancel_maint_container, #host_action_remove_container").hide();
			} else if (json.state == 'Down' || json.state == "Alert") {
				template.find("#host_state_bar").removeClass("yellow_statusbar grey_statusbar green_statusbar").addClass("red_statusbar");
				template.find("#routing_state").text(json.state).removeClass("grid_celltitles grid_runningtitles").addClass("grid_stoppedtitles");
				
				if (json.state == "Alert") {
					template.find(".grid_links").find("#host_action_reconnect_container, #host_action_enable_maint_container, #host_action_cancel_maint_container, #host_action_remove_container").hide();
				} else {
					template.find(".grid_links").find("#host_action_reconnect_container, #host_action_cancel_maint_container").hide();
				}
			} else {
				template.find("#host_state_bar").removeClass("yellow_statusbar green_statusbar red_statusbar").addClass("grey_statusbar");
				template.find("#routing_state").text(json.state).removeClass("grid_runningtitles grid_stoppedtitles").addClass("grid_celltitles ");
				
				if (json.state == "ErrorInMaintenance") {
					template.find(".grid_links").find("#host_action_reconnect_container, #host_action_remove_container").hide();
				} else if (json.state == "PrepareForMaintenance") {
					template.find(".grid_links").find("#host_action_reconnect_container, #host_action_enable_maint_container, #host_action_remove_container").hide();
				} else if (json.state == "Maintenance") {
					template.find(".grid_links").find("#host_action_reconnect_container, #host_action_enable_maint_container").hide();
				} else if (json.state == "Disconnected") {
					template.find(".grid_links").find("#host_action_reconnect_container, #host_action_enable_maint_container, #host_action_cancel_maint_container, #host_action_remove_container").hide();
				} else {
					alert("Unsupported Host State: " + json.state);
				}
			} 
		}
		
		// Add New Routing Host
		if (getHypervisorType() != "kvm") {
			$("#host_action_new_routing").bind("click", function(event) {
			$("#dialog_add_routing")
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Add": function() { 
					// validate values
					var isValid = true;									
					isValid &= validateString("Host name", $("#host_hostname"), $("#host_hostname_errormsg"));
					isValid &= validateString("User name", $("#host_username"), $("#host_username_errormsg"));
					isValid &= validateString("Password", $("#host_password"), $("#host_password_errormsg"));		
					if (!isValid) return;
								
			        var hostname = trim($("#host_hostname").val());
					var username = trim($("#host_username").val());
					var password = trim($("#host_password").val());
					var zoneId = $("#dialog_add_routing #host_zone").val();
					var podId = $("#dialog_add_routing #host_pod").val();
					
					var url;					
					if(hostname.indexOf("http://")==-1)
					    url = "http://" + hostname;
					else
					    url = hostname;
										
					var dialogBox = $(this);
					dialogBox.dialog("close");
					$.ajax({
						data: "command=addHost&zoneId="+zoneId+"&podId="+podId+"&url="+encodeURIComponent(url)+"&username="+encodeURIComponent(username)+"&password="+encodeURIComponent(password)+"&response=json",
						dataType: "json",
						success: function(json) {
							var hosts = json.addhostresponse.host;
							// For now, we'll just refresh since this call doesn't return any hosts for me to append.							
							listHosts();
						}
					});
				} 
			}).dialog("open");
			return false;
			});
		}
				
		function listHosts() {			
		    var submenuContent = $("#submenu_content_routing");			    
		               			
            var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();	
			    var state = submenuContent.find("#advanced_search #adv_search_state").val();
			    var zone = submenuContent.find("#advanced_search #adv_search_zone").val();
			    var pod = submenuContent.find("#advanced_search #adv_search_pod").val();			    
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));				
				if (state!=null && state.length > 0) 
					moreCriteria.push("&state="+state);		
			    if (zone!=null && zone.length > 0) 
					moreCriteria.push("&zoneId="+zone);		
			    if (pod!=null && pod.length > 0) 
					moreCriteria.push("&podId="+pod);					
				commandString = "command=listHosts&page=" + currentPage + moreCriteria.join("") + "&type=Routing&response=json";   //moreCriteria.join("")
			} else {          
				var searchInput = submenuContent.find("#search_input").val();            
				if (searchInput != null && searchInput.length > 0) 
					commandString = "command=listHosts&page=" + currentPage + "&keyword=" + searchInput + "&type=Routing&response=json";
				else
					commandString = "command=listHosts&page=" + currentPage + "&type=Routing&response=json";
			}
            
             //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listhostsresponse", "host", $("#routing_template"), routingJSONToTemplate);         
		};				
		
		submenuContentEventBinder($("#submenu_content_routing"), listHosts);
		
		currentPage = 1;	
		listHosts();
	});
}