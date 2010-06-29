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

// Version: @VERSION@

function showInstancesTab(p_domainId, p_account) {
	// Manage VM Tab
	mainContainer.load("content/tab_instances.html", function() {
		// Submenus change based on role
		if (isUser()) {
			$("#submenu_links, #submenu_routers, #submenu_console").hide();
		} else if (isDomainAdmin()) {
			$("#submenu_console").hide();
		}
				
		var vIndex = 0;
		var vmPopup = $("#vmpopup");
		var currentPageInPopupStep2 =1;
		var filterInPopupStep2;  //filterInPopupStep2 will be set to "featured" when new VM dialog box opens
		
		activateDialog($("#dialog_change_service_offering").dialog({ 
			width: 600,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_create_template").dialog({
			width: 400,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_change_group").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_change_name").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_attach_iso").dialog({ 
			width: 600,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_t_and_c").dialog({ 
			width: 600,
			autoOpen: false,
			modal: true,
			zIndex: 2000,
			buttons: { "OK": function() { $(this).dialog("close"); } }
		}));
		
		$("#t_and_c").click(function(event) {
			$("#dialog_t_and_c").dialog("open");
			return false;
		});
		
		// Security Groups Dialog setup --------------------
		var availableSG = null;
		function addSGToSelect(appliedSG) {
			var availableSelect = $("#dialog_apply_security_groups #selectable_available").empty();
			if (availableSG != null && availableSG.length > 0) {
				for (var i = 0; i < availableSG.length; i++) {
					var available = true;
					if (appliedSG != null && appliedSG.length > 0) {
						for (var j = 0; j < appliedSG.length ; j++) {
							if (availableSG[i].id == appliedSG[j].id) {
								available = false;
								break;
							}
						}
					}
					if (available) {
						var group = "<li id='"+availableSG[i].id+ "'>" + sanitizeXSS(availableSG[i].name) + "</li>";
						availableSelect.append(group);
					}
				}
			}
			var appliedSelect = $("#dialog_apply_security_groups #selectable_applied").empty();
			if (appliedSG != null && appliedSG.length > 0) {
				for (var j = 0; j < appliedSG.length ; j++) {
					var group = "<li id='"+appliedSG[j].id+ "'>" + sanitizeXSS(appliedSG[j].name) + "</li>";
					appliedSelect.append(group);
				}
			}
		}
		activateDialog($("#dialog_apply_security_groups").dialog({ 
			width: 550,
			autoOpen: false,
			modal: true,
			zIndex: 2000,
			buttons: {				
				"Apply": function() { 
					var dialogBox = $(this);
					var appliedSG = null;
					var publicIp = $("#apply_sg_public_ip").val();
					var vmId = $("#apply_sg_public_ip").data("vmId");
					
					dialogBox.find(".selectable_commentbox").hide();
					dialogBox.find(".selectable_errorbox").hide();
					
					$("#dialog_apply_security_groups #selectable_applied").children().each(function(index) {
						if (appliedSG == null) {
							appliedSG = $(this).attr("id");
						} else {
							appliedSG = appliedSG + "," + $(this).attr("id");
						}
					});
					if (appliedSG == null) appliedSG = "0";
					dialogBox.find(".selectable_commentbox .selectable_loader").show();
					dialogBox.find(".selectable_commentbox").fadeIn("slow");
					dialogBox.find(".selectable_commentbox strong").text("Please wait while we apply your Port Forwarding Services...");
					$.ajax({
						data: "command=assignPortForwardingService&ids="+appliedSG+"&publicip="+publicIp+"&virtualmachineid="+vmId+"&response=json",
						dataType: "json",
						success: function(json) {
							dialogBox.everyTime(2000, function() {
								$.ajax({
									data: "command=queryAsyncJobResult&jobId="+json.assignportforwardingserviceresponse.jobid+"&response=json",
									dataType: "json",
									success: function(json) {
										var result = json.queryasyncjobresultresponse;
										if (result.jobstatus == 0) {
											return; //Job has not completed
										} else {
											dialogBox.stopTime();
											if (result.jobstatus == 1) {
												// Succeeded
												dialogBox.find(".selectable_commentbox .selectable_loader").hide();
												dialogBox.find(".selectable_commentbox strong").text("Your Port Forwarding Services have been successfully applied.");
											} else if (result.jobstatus == 2) {
												// Failed
												dialogBox.find(".selectable_commentbox").hide();
												dialogBox.find(".selectable_errorbox #apply_error_text").text(result.jobresult);
												dialogBox.find(".selectable_errorbox").fadeIn("slow");
												$("#apply_sg_public_ip").change();
											}
										}
									},
									error: function(XMLHttpResponse) {
										dialogBox.stopTime();
										handleError(XMLHttpResponse);
									}
								});
							}, 0);
						}
					});
				},				
				"Close": function() {
					var dialogBox = $(this);
					dialogBox.find(".selectable_commentbox, .selectable_errorbox").hide();
					dialogBox.dialog("close"); 
				}				
			}
		}));
		$("#dialog_apply_security_groups #selectable_available, #dialog_apply_security_groups #selectable_applied").selectable();
		$("#dialog_apply_security_groups #move_right").unbind("click").bind("click", function(event) {
			$("#dialog_apply_security_groups #selectable_available .ui-selected").each(function(index) {
				$(this).fadeOut("fast", function() {
					$(this).detach().appendTo("#dialog_apply_security_groups #selectable_applied").fadeIn("fast");
				});
			});
		});
		$("#dialog_apply_security_groups #move_left").unbind("click").bind("click", function(event) {
			$("#dialog_apply_security_groups #selectable_applied .ui-selected").each(function(index) {
				$(this).fadeOut("fast", function() {
					$(this).detach().appendTo("#dialog_apply_security_groups #selectable_available").fadeIn("fast");
				});
			});
		});
		$("#apply_sg_public_ip").change(function() {
			var publicIp = $(this).val();
			var vmId = $(this).data("vmId");
			var appliedSG = null;
			// Get all the groups applied to this VM
			$.ajax({
				data: "command=listPortForwardingServicesByVm&ipaddress="+publicIp+"&virtualmachineid="+vmId+"&response=json",
				dataType: "json",
				success: function(json) {
					var appliedSG = json.listportforwardingservicesbyvmresponse.portforwardingservice;
					addSGToSelect(appliedSG);
				}
			});
		});
		// End Security Groups Dialog setup ------------------
		
		// VM Instance Template Setup
		var vmInstanceTemplate = $("#vm_instance_template");
		
		// FUNCTION: Sets up the thumbnail effect
		function enableConsoleHover(vmTemplate) {
			var offset = vmTemplate.offset();
			var imgUrl = vmTemplate.data("imgUrl");
			var index = 0;
			if (imgUrl != null) {
				var time = new Date();
				$("#spopup .console_box0").css("background", "url("+imgUrl+"&t="+time.getTime()+")");
				$("#spopup .console_box1").css("background", "url("+imgUrl+"&t="+time.getTime()+")");
				vmTemplate.everyTime(2000, function() {
					var time = new Date();
					if ((index % 2) == 0) {
						$("#spopup .console_box0").hide().css("background", "url("+imgUrl+"&t="+time.getTime()+")");
						$("#spopup .console_box1").show();
					} else {
						$("#spopup .console_box1").hide().css("background", "url("+imgUrl+"&t="+time.getTime()+")");
						$("#spopup .console_box0").show();
					}
					index++;
				}, 0);
			} 
			$("#spopup").css("top", (offset.top - 210) + "px").css("left", offset.left + "px").show();
		}
		vmInstanceTemplate.find("#vm_action_view_console").bind("mouseover", function(event) {
			enableConsoleHover($(this));
		});
		vmInstanceTemplate.find("#vm_action_view_console").bind("mouseout", function(event) {
			$(this).stopTime();
			$("#spopup").hide();
		});
		
		function showInstanceLoading(vmInstance, actionText) {
			vmInstance.find("#instance_loading_overlay").show();
			vmInstance.find("#vm_instance_menu").hide();
			vmInstance.find("#vm_loading_text").text(actionText);
			vmInstance.find("#vm_loading_container").fadeIn("slow");
		}
		
		function hideInstanceLoading(vmInstance) {
			vmInstance.find("#instance_loading_overlay").hide();
			vmInstance.find("#vm_loading_container").hide();
			vmInstance.find("#vm_instance_menu").fadeIn("slow");
		}
		
		vmInstanceTemplate.bind("click", function(event) {
			var vmInstance = $(this);
			var link = $(event.target);
			var linkAction = link.attr("id");
			var vmId = vmInstance.data("id");
			var vmName = vmInstance.data("name");				
			var vmState = vmInstance.data("state");
			var timerKey = "vm"+vmId;
			
			var closeActions = false;
			if (link.hasClass("vmaction_links_off")) {
				return false;
			} else if (link.hasClass("vmaction_links_on")) {
				closeActions = true;
			}
			switch (linkAction) {
				case "vm_action_start" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to start your virtual machine: <b>"+vmName+"</b></p>")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 
							$(this).dialog("close"); 
							showInstanceLoading(vmInstance, "Starting...");
							vmInstance.find("#vm_state_bar").removeClass("admin_vmred_arrow admin_vmgreen_arrow").addClass("admin_vmgrey_arrow");
							vmInstance.find("#vm_state").text("Starting").removeClass("grid_stoppedtitles grid_runningtitles").addClass("grid_celltitles");
							vmInstance.find("#vm_action_volumes").removeClass().addClass("vm_botactionslinks_down").data("expanded", false);
							vmInstance.find("#volume_detail_panel").slideUp("slow");
							$.ajax({
								data: "command=startVirtualMachine&id="+vmId+"&response=json",
								dataType: "json",
								success: function(json) {
									vmInstance.fadeIn("slow");
									$("body").everyTime(
										10000,
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.startvirtualmachineresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														if (vmInstance != null) {
															$("body").stopTime(timerKey);
															vmInstance.find("#vm_loading_container").hide();
															if (result.jobstatus == 1) {
																// Succeeded
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").html("Your instance has been successfully started.");
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
																vmInstance.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmred_arrow").addClass("admin_vmgreen_arrow");
																
																vmInstance.find("#vm_state").text(result.virtualmachine[0].state).removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
																vmInstance.data("state", result.virtualmachine[0].state);
																																											
																if (result.virtualmachine[0].hostname != undefined) {
					                                                vmInstance.find("#vm_host").html("<strong>Host:</strong> " + sanitizeXSS(result.virtualmachine[0].hostname));
				                                                } else {
				                                                    vmInstance.find("#vm_host").html("<strong>Host:</strong> ");
				                                                }	
																																
																vmInstance.find("#vm_action_start, #vm_action_reset_password, #vm_action_change_service").removeClass().addClass("vmaction_links_off");
																vmInstance.find("#vm_action_stop, #vm_action_reboot").removeClass().addClass("vmaction_links_on");
																
																
																// Console Proxy UI
																vmInstance.find("#vm_action_view_console").data("imgUrl", "console?cmd=thumbnail&vm=" + result.virtualmachine[0].id + "&w=144&h=110");
																vmInstance.find("#vm_action_view_console").data("proxyUrl", "console?cmd=access&vm=" + result.virtualmachine[0].id + "&t=" + result.virtualmachine[0].displayname).data("vmId",result.virtualmachine[0].id).click(function(event) {
																	event.preventDefault();
																	window.open($(this).data("proxyUrl"),$(this).data("vmId"),"width=820,height=640,resizable=yes,menubar=no,status=no,scrollbars=no,toolbar=no,location=no");
																});
																vmInstance.find("#vm_action_view_console").bind("mouseover", function(event) {
																	enableConsoleHover($(this));
																});
															} else if (result.jobstatus == 2) {
																// Failed
																vmInstance.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmgreen_arrow").addClass("admin_vmred_arrow");
																vmInstance.find("#vm_state").text("Stopped").removeClass("grid_celltitles grid_runningtitles").addClass("grid_stoppedtitles");
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").text("Unable to start your instance due to the error: " + result.jobresult);
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
															}
														}
													}
												},
												error: function(XMLHttpResponse) {
													hideInstanceLoading(vmInstance);
													$("body").stopTime(timerKey);
													handleError(XMLHttpResponse);
												}
											});
										},
										0
									);
								},
								error: function(XMLHttpResponse) {
									hideInstanceLoading(vmInstance);
									handleError(XMLHttpResponse);
								}
							});
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_stop" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to stop your virtual machine: <b>"+vmName+"</b></p>")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 
							$(this).dialog("close");
							showInstanceLoading(vmInstance, "Stopping...");
							vmInstance.find("#vm_state_bar").removeClass("admin_vmred_arrow admin_vmgreen_arrow").addClass("admin_vmgrey_arrow");
							vmInstance.find("#vm_state").text("Stopping").removeClass("grid_stoppedtitles grid_runningtitles").addClass("grid_celltitles");
							vmInstance.find("#vm_action_volumes").removeClass().addClass("vm_botactionslinks_down").data("expanded", false);
							vmInstance.find("#volume_detail_panel").slideUp("slow");
							$.ajax({
								data: "command=stopVirtualMachine&id="+vmId+"&response=json",
								dataType: "json",
								success: function(json) {
									$("body").everyTime(
										10000,
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.stopvirtualmachineresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														if (vmInstance != null) {
															$("body").stopTime(timerKey);
															vmInstance.find("#vm_loading_container").hide();
															if (result.jobstatus == 1) {
																// Succeeded
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").html("Your instance has been successfully stopped.");
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
																vmInstance.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmgreen_arrow").addClass("admin_vmred_arrow");
																
																vmInstance.find("#vm_state").text(result.virtualmachine[0].state).removeClass("grid_celltitles grid_runningtitles").addClass("grid_stoppedtitles");
																vmInstance.data("state", result.virtualmachine[0].state);
																																
																if (result.virtualmachine[0].hostname != undefined) {
					                                                vmInstance.find("#vm_host").html("<strong>Host:</strong> " + sanitizeXSS(result.virtualmachine[0].hostname));
				                                                } else {
				                                                    vmInstance.find("#vm_host").html("<strong>Host:</strong> ");
				                                                }																	
																
																vmInstance.find("#vm_action_start, #vm_action_reset_password, #vm_action_change_service").removeClass().addClass("vmaction_links_on");
																vmInstance.find("#vm_action_stop, #vm_action_reboot").removeClass().addClass("vmaction_links_off");
																vmInstance.find("#vm_action_view_console").unbind("mouseover click");
																
															} else if (result.jobstatus == 2) {
																// Failed
																vmInstance.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmred_arrow").addClass("admin_vmgreen_arrow");
																vmInstance.find("#vm_state").text("Running").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").text("Unable to stop your instance due to the error: " + result.jobresult);
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
															}
														}
													}
												},
												error: function(XMLHttpResponse) {
													hideInstanceLoading(vmInstance);
													$("body").stopTime(timerKey);
													handleError(XMLHttpResponse);
												}
											});
										},
										0
									);
								},
								error: function(XMLHttpResponse) {
									hideInstanceLoading(vmInstance);
									handleError(XMLHttpResponse);
								}
							});
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						}
					}).dialog("open");
					break;
				case "vm_action_reboot" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to reboot your virtual machine: <b>"+vmName+"</b></p>")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 
							$(this).dialog("close");
							showInstanceLoading(vmInstance, "Rebooting...");
							vmInstance.find("#vm_state_bar").removeClass("admin_vmred_arrow admin_vmgreen_arrow").addClass("admin_vmgrey_arrow");
							vmInstance.find("#vm_state").text("Rebooting").removeClass("grid_stoppedtitles grid_runningtitles").addClass("grid_celltitles");
							$.ajax({
								data: "command=rebootVirtualMachine&id="+vmId+"&response=json",
								dataType: "json",
								success: function(json) {
									$("body").everyTime(
										10000,
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.rebootvirtualmachineresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														if (vmInstance != null) {
															$("body").stopTime(timerKey);
															vmInstance.find("#vm_loading_container").hide();
															if (result.jobstatus == 1) {
																// Succeeded
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").html("Your instance has been successfully rebooted.");
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
																vmInstance.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmred_arrow").addClass("admin_vmgreen_arrow");
																vmInstance.find("#vm_state").text("Running").removeClass("grid_stoppedtitles grid_celltitles").addClass("grid_runningtitles");
															} else if (result.jobstatus == 2) {
																// Failed
																vmInstance.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmred_arrow").addClass("admin_vmgreen_arrow");
																vmInstance.find("#vm_state").text("Running").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").text("Unable to reboot your instance due to the error: " + result.jobresult);
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
															}
														}
													}
												},
												error: function(XMLHttpResponse) {
													hideInstanceLoading(vmInstance);
													$("body").stopTime(timerKey);
													handleError(XMLHttpResponse);
												}
											});
										},
										0
									);
								},
								error: function(XMLHttpResponse) {
									hideInstanceLoading(vmInstance);
									handleError(XMLHttpResponse);
								}
							});
						},
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_destroy" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to destroy your virtual machine: <b>"+vmName+"</b>.  Destroying your virtual machine would include deleting the ROOT volume and all attached data disk volumes.</p>")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 
							$(this).dialog("close");
							showInstanceLoading(vmInstance, "Destroying...");
							vmInstance.find("#vm_state_bar").removeClass("admin_vmred_arrow admin_vmgreen_arrow").addClass("admin_vmgrey_arrow");
							vmInstance.find("#vm_state").text("Destroying").removeClass("grid_stoppedtitles grid_runningtitles").addClass("grid_celltitles");
							vmInstance.find("#vm_action_volumes").removeClass().addClass("vm_botactionslinks_down").data("expanded", false);
							vmInstance.find("#volume_detail_panel").slideUp("slow");
							$.ajax({
								data: "command=destroyVirtualMachine&id="+vmId+"&response=json",
								dataType: "json",
								success: function(json) {
									$("body").everyTime(
										10000,
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.destroyvirtualmachineresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														if (vmInstance != null) {
															$("body").stopTime(timerKey);
															vmInstance.find("#vm_loading_container").hide();
															if (result.jobstatus == 1) {
																// Succeeded
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").html("Your instance has been successfully destroyed.");
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
																if (isAdmin()) {
																	vmInstance.find("#vm_state_bar").removeClass("admin_vmred_arrow admin_vmgreen_arrow").addClass("admin_vmgrey_arrow");
																	
																	//No embedded object is returned. So, hardcoding state as "Destroyed".
																	vmInstance.find("#vm_state").text("Destroyed").removeClass("grid_stoppedtitles grid_runningtitles").addClass("grid_celltitles");
																	vmInstance.data("state", "Destroyed"); 
																	
																	vmInstance.find("#vm_host").html("<strong>Host:</strong>");
																	
																	vmInstance.find("#vm_action_restore").show();
																	vmInstance.find("#vm_action_volumes, #vm_actions").hide();
																} else {
																	vmInstance.find(".continue_button").unbind("click").bind("click", function(event) {
																		$(this).parents(".loadingmessage_container").hide().prevAll(".row_loading").hide();
																		vmInstance.fadeOut("slow", function(event) {
																			$(this).remove();
																		});
																	});
																}
															} else if (result.jobstatus == 2) {
																// Failed
																if (vmState == 'Running') {
																	vmInstance.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmred_arrow").addClass("admin_vmgreen_arrow");
																	vmInstance.find("#vm_state").text("Running").removeClass("grid_stoppedtitles grid_celltitles").addClass("grid_runningtitles");
																} else {
																	vmInstance.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmgreen_arrow").addClass("admin_vmred_arrow");
																	vmInstance.find("#vm_state").text(vmState).removeClass("grid_runningtitles grid_celltitles").addClass("grid_stoppedtitles");
																}
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").text("Unable to destroy your instance due to the error: " + result.jobresult);
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
															}
														}
													}
												},
												error: function(XMLHttpResponse) {
													hideInstanceLoading(vmInstance);
													$("body").stopTime(timerKey);
													handleError(XMLHttpResponse);
												}
											});
										},
										0
									);
								},
								error: function(XMLHttpResponse) {
									hideInstanceLoading(vmInstance);
									handleError(XMLHttpResponse);
								}
							});
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_reset_password" :
					if(vmState != "Stopped") {
						$("#dialog_alert").html("<p><b>"+vmName+"</b> needs to be stopped before you can reset your password.</p>")
						$("#dialog_alert").dialog("open");
						return false;
					}
					if($(this).data("passwordEnabled") != "true") {
						$("#dialog_alert").html("<p><b>"+vmName+"</b> is not using a template that has the password reset feature enabled.  If you have forgotten your root password, please contact support.</p>")
						$("#dialog_alert").dialog("open");
						return false;
					}
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to change the ROOT password for your virtual machine: <b>"+vmName+"</b></p>")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 
							$(this).dialog("close"); 
							showInstanceLoading(vmInstance, "Resetting password...");
							$.ajax({
								data: "command=resetPasswordForVirtualMachine&id="+vmId+"&response=json",
								dataType: "json",
								success: function(json) {
									vmInstance.fadeIn("slow");
									$("body").everyTime(
										10000,
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.resetpasswordforvirtualmachineresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														if (vmInstance != null) {
															$("body").stopTime(timerKey);
															vmInstance.find("#vm_loading_container").hide();
															if (result.jobstatus == 1) {
																// Succeeded
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").html("Your password has been successfully resetted.  Your new password is : <b>" + result.virtualmachine[0].password + "</b> .  Please reboot your virtual instance for the new password to take effect.");
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
															} else if (result.jobstatus == 2) {
																// Failed
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").text("Unable to reset your password.  Please try again or contact support.");
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
															}
														}
													}
												},
												error: function(XMLHttpResponse) {
													hideInstanceLoading(vmInstance);
													$("body").stopTime(timerKey);
													handleError(XMLHttpResponse);
												}
											});
										},
										0
									);
								},
								error: function(XMLHttpResponse) {
									hideInstanceLoading(vmInstance);
									handleError(XMLHttpResponse);
								}
							});
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_change_service" :
					if(vmState != "Stopped") {
						$("#dialog_alert").html("<p><b>"+vmName+"</b> needs to be stopped before you can change its service.</p>")
						$("#dialog_alert").dialog("open");
						return false;
					}
					
					$("#dialog_change_service_offering").find("#change_vm_name").text(vmName);
					$.ajax({
						data: "command=listServiceOfferings&VirtualMachineId="+vmId+"&response=json",
						dataType: "json",
						success: function(json) {
							var offerings = json.listserviceofferingsresponse.serviceoffering;
							var offeringSelect = $("#dialog_change_service_offering #change_service_offerings").empty();
							
							if (offerings != null && offerings.length > 0) {
								for (var i = 0; i < offerings.length; i++) {
									var option = $("<option value='" + offerings[i].id + "'>" + sanitizeXSS(offerings[i].displaytext) + "</option>").data("name", offerings[i].name);
									offeringSelect.append(option); 
								}
							} 
						}
					});
					
					$("#dialog_change_service_offering")
					.dialog('option', 'buttons', { 						
						"Change": function() { 
							$(this).dialog("close"); 
							$.ajax({
								data: "command=changeServiceForVirtualMachine&id="+vmId+"&serviceOfferingId="+$("#dialog_change_service_offering #change_service_offerings").val()+"&response=json",
								dataType: "json",
								success: function(json) {
									vmInstance.find("#vm_loading_container").hide();
									vmInstance.find(".row_loading").show();
									vmInstance.find(".loadingmessage_container .loadingmessage_top p").html("Your virtual instance has been upgraded.  Please restart your virtual instance for the new service offering to take effect.");
									vmInstance.find(".loadingmessage_container").fadeIn("slow");
									vmInstance.find("#vm_service").text(($("#dialog_change_service_offering #change_service_offerings :selected").data("name")));
								}
							});
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_apply_security" :
					$("#dialog_apply_security_groups").find("#security_group_vm_name").text(vmName);
					$("#dialog_apply_security_groups #apply_sg_public_ip").data("vmId", vmId);

					var params = "";
					if (isAdmin()) {
						params = "&domainId="+vmInstance.data("domainId")+"&account="+vmInstance.data("account")+"&zoneid="+vmInstance.data("zoneId");
					}
					
					// Get public ips
					$.ajax({
						data: "command=listPublicIpAddresses&response=json"+params,
						dataType: "json",
						success: function(json) {
							var addressesJSON = json.listpublicipaddressesresponse.publicipaddress;
							var ipSelect = $("#dialog_apply_security_groups #apply_sg_public_ip").empty();
							if (addressesJSON != null && addressesJSON.length > 0 ) {
								for (var i = 0; i < addressesJSON.length; i++) {
									ipSelect.append("<option value='" + addressesJSON[i].ipaddress + "'>" + addressesJSON[i].ipaddress + "</option>"); 
								}
							} else {
								ipSelect.append("<option value='none'>No Public IPs</option>"); 
							}
						}
					});
					
					if (isAdmin()) {
						params = "&domainId="+vmInstance.data("domainId")+"&account="+vmInstance.data("account");
					}
					
					// Get list of available groups
					$.ajax({
						data: "command=listPortForwardingServices&response=json"+params,
						dataType: "json",
						success: function(json) {
							availableSG = json.listportforwardingservicesresponse.portforwardingservice;
							$("#dialog_apply_security_groups #apply_sg_public_ip").change();
							$("#dialog_apply_security_groups").dialog("open");
						}
					});
					break;
				case "vm_action_change_group" :
					$("#dialog_change_group").find("#vm_name").text(vmName);								
					$("#dialog_change_group").find("#change_group_name").val((vmInstance.data("group")==null)?"":vmInstance.data("group"));										
					$("#dialog_change_group")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 											
							// validate values
					        var isValid = true;					
					        isValid &= validateString("Group", $("#change_group_name"), $("#change_group_name_errormsg"), true); //group name is optional								
					        if (!isValid) return;						
							
							var group = trim($("#change_group_name").val());
							var vmInstance = $("#vm"+vmId);
							$.ajax({
								data: "command=updateVirtualMachine&id="+vmId+"&group="+encodeURIComponent(group)+"&response=json",
								dataType: "json",
								success: function(json) {
									vmInstance.find("#vm_group").text(group);
								},
								error: function(XMLHttpResponse) {
									handleError(XMLHttpResponse);
								}
							});
							$(this).dialog("close"); 
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_change_name" :
					$("#dialog_change_name").find("#vm_name").text(vmName);
					$("#dialog_change_name")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 												
							// validate values
					        var isValid = true;					
					        isValid &= validateString("Name", $("#change_instance_name"), $("#change_instance_name_errormsg"));								
					        if (!isValid) return;								
							
							var name = trim($("#change_instance_name").val());
							
							$.ajax({
								data: "command=updateVirtualMachine&id="+vmId+"&displayName="+encodeURIComponent(name)+"&response=json",
								dataType: "json",
								success: function(json) {
									if (isAdmin()) {
										var systemName = vmInstance.data("systemName");
										name = systemName + "(" + name + ")";
										vmInstance.find("#vm_name").text(name);
									} else {
										vmInstance.find("#vm_name").text(name);
									}
									vmInstance.data("name", name);
								},
								error: function(XMLHttpResponse) {
									handleError(XMLHttpResponse);
								}
							});
							$(this).dialog("close"); 
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_ha" :
					var enable = true;
					var message = "<p>Please confirm you want to enable HA for your virtual machine: <b>"+vmName+"</b>.  Once HA is enabled, your Virtual Instance will be automatically restarted in the event it is detected to have failed.</p>";
					if (vmInstance.data("ha") == 'true') {
						enable = false;
						message = "<p>Please confirm you want to disable HA for your virtual machine: <b>"+vmName+"</b>.  Once HA is disabled, your Virtual Instance will no longer be be automatically restarted in the event of a failure.</p>";
					}
					$("#dialog_confirmation")
					.html(message)
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 
							$(this).dialog("close"); 
							$.ajax({
								data: "command=updateVirtualMachine&id="+vmId+"&haenable="+enable+"&response=json",
								dataType: "json",
								success: function(json) {
									if (enable) {
										vmInstance.find("#vm_ha").html("<strong>HA:</strong> Enabled");
										vmInstance.find("#vm_action_ha").text("Disable HA");
										vmInstance.data("ha", "true");
									} else {
										vmInstance.find("#vm_ha").html("<strong>HA:</strong> Disabled");
										vmInstance.find("#vm_action_ha").text("Enable HA");
										vmInstance.data("ha", "false");
									}
								}
							});
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_restore" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to restore the virtual machine: <b>"+vmName+"</b>.</p>")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 
							$(this).dialog("close"); 
							$.ajax({
								data: "command=recoverVirtualMachine&id="+vmId+"&response=json",
								dataType: "json",
								success: function(json) {
									vmInstance.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmgreen_arrow").addClass("admin_vmred_arrow");
									vmInstance.find("#vm_state").text("Stopped").removeClass("grid_celltitles grid_runningtitles").addClass("grid_stoppedtitles");
									vmInstance.find("#vm_action_restore").hide();
									vmInstance.find("#vm_action_volumes, #vm_actions").show();
									vmInstance.find("#vm_action_start, #vm_action_reset_password, #vm_action_change_service").removeClass().addClass("vmaction_links_on");
									if (vmInstance.data("isoId") != null) {
										vmInstance.find("#vm_action_detach_iso").removeClass().addClass("vmaction_links_on");
										vmInstance.find("#vm_action_attach_iso").removeClass().addClass("vmaction_links_off");
									} else {
										vmInstance.find("#vm_action_detach_iso").removeClass().addClass("vmaction_links_off");
										vmInstance.find("#vm_action_attach_iso").removeClass().addClass("vmaction_links_on");
									}
									vmInstance.find("#vm_action_stop, #vm_action_reboot").removeClass().addClass("vmaction_links_off");
									vmInstance.data("state", "Stopped");
								}
							});
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_attach_iso" :
					$.ajax({
						data: "command=listIsos&isReady=true&response=json",
						dataType: "json",
						async: false,
						success: function(json) {
							var isos = json.listisosresponse.iso;
							var isoSelect = $("#dialog_attach_iso #attach_iso_select");
							if (isos != null && isos.length > 0) {
								isoSelect.empty();
								for (var i = 0; i < isos.length; i++) {
									isoSelect.append("<option value='"+isos[i].id+"'>"+sanitizeXSS(isos[i].displaytext)+"</option>");;
								}
							}
						}
					});
					$("#dialog_attach_iso").find("#vm_name").text(vmName);
					$("#dialog_attach_iso")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 
							$(this).dialog("close");
							var isoId = $("#dialog_attach_iso #attach_iso_select").val();
							if (isoId == "none") {
								$("#dialog_alert").html("<p>There is no ISO file to attach to the virtual machine.</p>")
								$("#dialog_alert").dialog("open");
								return false;
							}
							
							showInstanceLoading(vmInstance, "Attaching ISO...");
							$.ajax({
								data: "command=attachIso&virtualmachineid="+vmId+"&id="+isoId+"&response=json",
								dataType: "json",
								success: function(json) {
									$("body").everyTime(
										5000,
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.attachisoresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														if (vmInstance != null) {
															$("body").stopTime(timerKey);
															vmInstance.find("#vm_loading_container").hide();
															if (result.jobstatus == 1) {
																// Succeeded
																vmInstance.find("#iso_state").removeClass().addClass("vmiso_on");
																vmInstance.data("isoId", isoId);
																vmInstance.find("#vm_action_detach_iso").removeClass().addClass("vmaction_links_on");
																vmInstance.find("#vm_action_attach_iso").removeClass().addClass("vmaction_links_off");
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").html("Your ISO has been successfully attached.");
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
															} else if (result.jobstatus == 2) {
																// Failed
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").text("We were unable to attach the ISO to your VM.  Please contact support or try again.");
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
															}
														}
													}
												},
												error: function(XMLHttpResponse) {
													hideInstanceLoading(vmInstance);
													$("body").stopTime(timerKey);
													handleError(XMLHttpResponse);
												}
											});
										},
										0
									);
								},
								error: function(XMLHttpResponse) {
									hideInstanceLoading(vmInstance);
									handleError(XMLHttpResponse);
								}
							});
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_detach_iso" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to detach an ISO from the virtual machine: <b>"+vmName+"</b>.</p>")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 
							$(this).dialog("close");
							showInstanceLoading(vmInstance, "Detaching ISO...");
							$.ajax({
								data: "command=detachIso&virtualmachineid="+vmId+"&response=json",
								dataType: "json",
								success: function(json) {
									$("body").everyTime(
										5000,
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.detachisoresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														if (vmInstance != null) {
															$("body").stopTime(timerKey);
															vmInstance.find("#vm_loading_container").hide();
															if (result.jobstatus == 1) {
																// Succeeded
																vmInstance.find("#iso_state").removeClass().addClass("vmiso_off");
																vmInstance.data("isoId", null);
																vmInstance.find("#vm_action_detach_iso").removeClass().addClass("vmaction_links_off");
																vmInstance.find("#vm_action_attach_iso").removeClass().addClass("vmaction_links_on");
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").html("You have successfully detached your ISO.");
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
															} else if (result.jobstatus == 2) {
																// Failed
																vmInstance.find(".loadingmessage_container .loadingmessage_top p").text(result.jobresult);
																vmInstance.find(".loadingmessage_container").fadeIn("slow");
															}
														}
													}
												},
												error: function(XMLHttpResponse) {
													hideInstanceLoading(vmInstance);
													$("body").stopTime(timerKey);
													handleError(XMLHttpResponse);
												}
											});
										},
										0
									);
								},
								error: function(XMLHttpResponse) {
									hideInstanceLoading(vmInstance);
									handleError(XMLHttpResponse);
								}
							});
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				case "vm_action_volumes" :
					var expanded = link.data("expanded");
					if (expanded == null || expanded == false) {
						var index = 0;
						$.ajax({
							cache: false,
							data: "command=listVolumes&virtualMachineId="+vmId+"&response=json",
							dataType: "json",
							success: function(json) {
								var volumes = json.listvolumesresponse.volume;
								if (volumes != null && volumes.length > 0) {
									var grid = vmInstance.find("#detail_container").empty();
									var detailTemplate = $("#volume_detail_template");
									for (var i = 0; i < volumes.length; i++) {
										var detail = detailTemplate.clone(true).attr("id","volume"+volumes[i].id);
										if (vIndex++ % 2 == 0) {
											detail.addClass("hostadmin_showdetails_row_even");
										} else {
											detail.addClass("hostadmin_showdetails_row_odd");
										}
										detail.find("#detail_id").text(volumes[i].id);										
										detail.data("volumeId", volumes[i].id).data("vmState", volumes[i].vmstate).data("vmName", volumes[i].vmname);										
										detail.find("#detail_name").text(volumes[i].name);
										if (volumes[i].storagetype == "shared") {
											detail.find("#detail_type").text(volumes[i].type + " (shared storage)");
										} else {
											detail.find("#detail_type").text(volumes[i].type + " (local storage)");
										}
										
										detail.find("#detail_size").text((volumes[i].size == "0") ? "" : convertBytes(volumes[i].size));										
										setDateField(volumes[i].created, detail.find("#detail_created"));
																				
										grid.append(detail.show());
																													
										if(volumes[i].type=="ROOT") {
											if (volumes[i].vmstate == "Stopped") {
												detail.find("#volume_action_detach_disk, #volume_acton_separator").hide();
											} else {
												detail.find("#volume_action_detach_disk, #volume_acton_separator, #volume_action_create_template").hide();
											}
										} else {
											if (volumes[i].vmstate != "Stopped") {
												detail.find("#volume_acton_separator, #volume_action_create_template").hide();
											}
										}
									}
								}
								link.removeClass().addClass("vm_botactionslinks_up");
								vmInstance.find("#volume_detail_panel").slideDown("slow");
								
								link.data("expanded", true);
							}
						});
					} else {
						link.removeClass().addClass("vm_botactionslinks_down");
						vmInstance.find("#volume_detail_panel").slideUp("slow");
						link.data("expanded", false);
					}
					break;
				case "vm_actions" :
					vmInstance.find("#vm_actions_container").slideDown("fast");
					break;
				case "vm_actions_close" :
					vmInstance.find("#vm_actions_container").hide();
					break;
				case "vm_action_continue" :
					hideInstanceLoading(vmInstance);
					vmInstance.find(".loadingmessage_container").fadeOut("slow");
					vmInstance.find(".row_loading").fadeOut("slow");
					break;
				default:
					break;
			}
			if (closeActions) {
				vmInstance.find("#vm_actions_container").hide();
			}	
			return false;
		});	
		
		// FUNCTION: Parses the JSON object for VM Instances and applies it to the vm template
		function vmJSONToTemplate(instanceJSON, instanceTemplate) {
		    instanceTemplate.attr("id","vm"+instanceJSON.id);  
		    
			// Setup			
			var vmName = getVmName(instanceJSON.name, instanceJSON.displayname);
						
			instanceTemplate.data("id", instanceJSON.id)
				.data("systemName", sanitizeXSS(instanceJSON.name))
				.data("name", sanitizeXSS(vmName))				
				.data("passwordEnabled", instanceJSON.passwordenabled)
				.data("domainId", instanceJSON.domainid)
				.data("account", sanitizeXSS(instanceJSON.account))
				.data("zoneId", sanitizeXSS(instanceJSON.zoneid))
				.data("state", instanceJSON.state)
				.data("ha", instanceJSON.haenable);
			instanceTemplate.data("group", sanitizeXSS(instanceJSON.group));	
				
			if (instanceJSON.isoId != undefined && instanceJSON.isoid.length > 0) {
				instanceTemplate.data("isoId", instanceJSON.isoid);
			}
			instanceTemplate.find("#vm_actions").data("id", instanceJSON.id);
			
			// Populate the template
			instanceTemplate.find("#vm_name").html("<strong>Name:</strong> " + sanitizeXSS(vmName));
			instanceTemplate.find("#vm_ip_address").html("<strong>IP Address:</strong> " + instanceJSON.ipaddress);
			instanceTemplate.find("#vm_zone").html("<strong>Zone:</strong> " + sanitizeXSS(instanceJSON.zonename));
			instanceTemplate.find("#vm_template").html("<strong>Template:</strong> " + sanitizeXSS(instanceJSON.templatename));
			instanceTemplate.find("#vm_service").html("<strong>Service:</strong> " + sanitizeXSS(instanceJSON.serviceofferingname));
			if (instanceJSON.haenable =='true') {
				instanceTemplate.find("#vm_ha").html("<strong>HA:</strong> Enabled");
				instanceTemplate.find("#vm_action_ha").text("Disable HA");
			} else {
				instanceTemplate.find("#vm_ha").html("<strong>HA:</strong> Disabled");
				instanceTemplate.find("#vm_action_ha").text("Enable HA");
			}
			
			setDateField(instanceJSON.created, instanceTemplate.find("#vm_created"), "<strong>Created:</strong> ");
						
			instanceTemplate.find("#vm_account").html("<strong>Account:</strong> " + sanitizeXSS(instanceJSON.account));
			instanceTemplate.find("#vm_domain").html("<strong>Domain:</strong> " + sanitizeXSS(instanceJSON.domain));
			if (isAdmin()) {
				if (instanceJSON.hostname != undefined) {
					instanceTemplate.find("#vm_host").html("<strong>Host:</strong> " + sanitizeXSS(instanceJSON.hostname));
				} else {
				    instanceTemplate.find("#vm_host").html("<strong>Host:</strong> ");
				}				
			}
			if (instanceJSON.group != undefined) {
				instanceTemplate.find("#vm_group").text(instanceJSON.group);
			} else {
			    instanceTemplate.find("#vm_group").text("No Group");
			}
			
			// Show State of the VM
			if (instanceJSON.state == 'Destroyed') {
				instanceTemplate.find("#vm_state_bar").removeClass("admin_vmred_arrow admin_vmgreen_arrow").addClass("admin_vmgrey_arrow");
				instanceTemplate.find("#vm_state").text(instanceJSON.state).removeClass("grid_stoppedtitles grid_runningtitles").addClass("grid_celltitles");
				instanceTemplate.find("#vm_action_restore").show();
				instanceTemplate.find("#vm_action_volumes, #vm_actions").hide();
				instanceTemplate.find("#vm_action_view_console").unbind("mouseover");
			} else if (instanceJSON.state == 'Running') {
				instanceTemplate.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmred_arrow").addClass("admin_vmgreen_arrow");
				instanceTemplate.find("#vm_state").text(instanceJSON.state).removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
				instanceTemplate.find("#vm_action_view_console").data("imgUrl", "console?cmd=thumbnail&vm=" + instanceJSON.id + "&w=144&h=110");

				// Console Proxy UI
				instanceTemplate.find("#vm_action_view_console").data("proxyUrl", "console?cmd=access&vm=" + instanceJSON.id + "&t=" + instanceJSON.displayname).data("vmId",instanceJSON.id).click(function(event) {
					event.preventDefault();
					window.open($(this).data("proxyUrl"),$(this).data("vmId"),"width=820,height=640,resizable=yes,menubar=no,status=no,scrollbars=no,toolbar=no,location=no");
				});
				
				// Enable/Disable actions
				instanceTemplate.find("#vm_action_start, #vm_action_reset_password, #vm_action_change_service").removeClass().addClass("vmaction_links_off");
				if (instanceJSON.isoid != undefined && instanceJSON.isoid.length > 0) {
					instanceTemplate.find("#vm_action_attach_iso").removeClass().addClass("vmaction_links_off");
				} else {
					instanceTemplate.find("#vm_action_detach_iso").removeClass().addClass("vmaction_links_off");
				}
			} else {
				if (instanceJSON.state == 'Stopped') {
					instanceTemplate.find("#vm_state_bar").removeClass("admin_vmgrey_arrow admin_vmgreen_arrow").addClass("admin_vmred_arrow");
					instanceTemplate.find("#vm_state").text(instanceJSON.state).removeClass("grid_celltitles grid_runningtitles").addClass("grid_stoppedtitles");
					instanceTemplate.find("#vm_action_stop, #vm_action_reboot").removeClass().addClass("vmaction_links_off");
					if (instanceJSON.isoid != undefined && instanceJSON.isoid.length > 0) {
						instanceTemplate.find("#vm_action_attach_iso").removeClass().addClass("vmaction_links_off");
					} else {
						instanceTemplate.find("#vm_action_detach_iso").removeClass().addClass("vmaction_links_off");
					}
				} else {
					instanceTemplate.find("#vm_state_bar").removeClass("admin_vmred_arrow admin_vmgreen_arrow").addClass("admin_vmgrey_arrow");
					instanceTemplate.find("#vm_state").text(instanceJSON.state).removeClass("grid_stoppedtitles grid_runningtitles").addClass("grid_celltitles");
					instanceTemplate.find("#vm_action_start, #vm_action_stop, #vm_action_reboot, #vm_action_attach_iso, #vm_action_detach_iso, #vm_action_reset_password, #vm_action_change_service, #vm_action_apply_security").removeClass().addClass("vmaction_links_off");
					if(instanceJSON.state == 'Creating')
					    instanceTemplate.find("#vm_action_destroy").hide();
				}
				instanceTemplate.find("#vm_action_view_console").unbind("mouseover");
			}
			
			// Show ISO state
			if (instanceJSON.isoid != undefined && instanceJSON.isoid.length > 0) {
				instanceTemplate.find("#iso_state").removeClass().addClass("vmiso_on");
			}
		}
		
		// Add New Wizard Setup
		var currentStep = 1;
		$(".add_newvmbutton").bind("click", function(event) {
			// Wizard Step 1 - list zones
			$.ajax({
				data: "command=listZones&available=true&response=json",
				dataType: "json",
				success: function(json) {
					var zones = json.listzonesresponse.zone;
					var zoneSelect = $("#wizard_zone").empty();	
					if (zones != null && zones.length > 0) {
						for (var i = 0; i < zones.length; i++) {
							zoneSelect.append("<option value='" + zones[i].id + "'>" + sanitizeXSS(zones[i].name) + "</option>"); 
						}
					}
				}
			});
			
			filterInPopupStep2 = "featured";	
			$("#vmpopup").fadeIn("slow");
			$("#overlay_black").show();
		});
		
		function vmWizardClose() {
			currentStep = 1;
			$("#vmpopup").hide();
			$("#overlay_black").hide();
			vmPopup.find("#step1").show().nextAll().hide();
			vmPopup.find(".rev_wizmid_actionback").hide();
			vmPopup.find(".rev_wizmid_actionnext").show();
			vmPopup.find("#wizard_message").hide();
			$("#wiz_featured").removeClass().addClass("rev_wizmid_selectedtempbut");
			$("#wiz_my, #wiz_community, #wiz_blank").removeClass().addClass("rev_wizmid_nonselectedtempbut");
		}
		
		vmPopup.find("#vm_wizard_close").bind("click", function(event) {
			vmWizardClose();
			return false;
		});
				
		vmPopup.find("#step2 #wiz_message_continue").bind("click", function(event) {		    
			vmPopup.find("#step2 #wiz_message").hide();
			return false;
		});
		
		vmPopup.find("#step3 #wiz_message_continue").bind("click", function(event) {		    
			vmPopup.find("#step3 #wiz_message").hide();
			return false;
		});
		
		function getIconForOS(osType) {
			if (osType == null || osType.length == 0) {
				return "";
			} else {
				if (osType.match("^CentOS") != null) {
					return "rev_wiztemo_centosicons";
				} else if (osType.match("^Windows") != null) {
					return "rev_wiztemo_windowsicons";
				} else {
					return "rev_wiztemo_linuxicons";
				}
			}
		}
		
		//vm wizard search and pagination
		vmPopup.find("#search_button").bind("click", function(event) {	              
            currentPageInPopupStep2 = 1;           	        	
            listTemplatesInPopupStep2();  
            return false;   //event.preventDefault() + event.stopPropagation() 
        });
    	
	    vmPopup.find("#search_input").bind("keypress", function(event) {		        
            if(event.keyCode == keycode_Enter) {                	        
	            vmPopup.find("#search_button").click();	
	            return false;   //event.preventDefault() + event.stopPropagation() 		     
	        }		    
        });   
				
		vmPopup.find("#nextPage").bind("click", function(event){	            
            currentPageInPopupStep2++;        
            listTemplatesInPopupStep2(); 
            return false;   //event.preventDefault() + event.stopPropagation() 
        });		
        
        vmPopup.find("#prevPage").bind("click", function(event){	                 
            currentPageInPopupStep2--;	              	    
            listTemplatesInPopupStep2(); 
            return false;   //event.preventDefault() + event.stopPropagation() 
        });	
							
		var vmPopupStep2PageSize = 11; //max number of templates each page in step2 of New VM wizard is 11 
		function listTemplatesInPopupStep2() {		
		    var container = vmPopup.find("#template_container");	 		    	
			   
		    var commandString;    		  	   
	        var searchInput = vmPopup.find("#search_input").val();   
	        if (filterInPopupStep2 != "blank") {      
                if (searchInput != null && searchInput.length > 0)                 
                    commandString = "command=listTemplates&templatefilter="+filterInPopupStep2+"&zoneid="+wizardSelectedZone+"&keyword="+searchInput+"&page="+currentPageInPopupStep2+"&response=json"; 
                else
                    commandString = "command=listTemplates&templatefilter="+filterInPopupStep2+"&zoneid="+wizardSelectedZone+"&page="+currentPageInPopupStep2+"&response=json";           		    		
			} else {
			    if (searchInput != null && searchInput.length > 0)                 
                    commandString = "command=listIsos&isReady=true&bootable=true&zoneid="+wizardSelectedZone+"&keyword="+searchInput+"&page="+currentPageInPopupStep2+"&response=json";  
                else
                    commandString = "command=listIsos&isReady=true&bootable=true&zoneid="+wizardSelectedZone+"&page="+currentPageInPopupStep2+"&response=json";  
			}
			
			var loading = vmPopup.find("#wiz_template_loading").show();				
			if(currentPageInPopupStep2==1)
                vmPopup.find("#prevPage").hide();
            else 
	            vmPopup.find("#prevPage").show();  		
			
			$.ajax({
				data: commandString,
				dataType: "json",
				async: false,
				success: function(json) {
				    var items;
				    if (filterInPopupStep2 != "blank")
					    items = json.listtemplatesresponse.template;
					else
					    items = json.listisosresponse.iso;
					loading.hide();
					container.empty();
					if (items != null && items.length > 0) {
						var first = true;
						for (var i = 0; i < items.length; i++) {
							var divClass = "rev_wiztemplistbox";
							if (first) {
								divClass = "rev_wiztemplistbox_selected";
								first = false;
							}

							var html = '<div class="'+divClass+'" id="'+items[i].id+'">'
										  +'<div class="'+getIconForOS(items[i].ostypename)+'"></div>'
										  +'<div class="rev_wiztemp_listtext">'+sanitizeXSS(items[i].displaytext)+'</div>'
										  +'<div class="rev_wiztemp_ownertext">'+sanitizeXSS(items[i].account)+'</div>'
									  +'</div>';
							container.append(html);
						}						
						if(items.length < vmPopupStep2PageSize)
			                vmPopup.find("#nextPage").hide();
			            else
			                vmPopup.find("#nextPage").show();
			        
					} else {
					    var msg;
					    if (filterInPopupStep2 != "blank")
					        msg = "No templates available";
					    else
					        msg = "No ISOs available";					    
						var html = '<div class="rev_wiztemplistbox" id="-2">'
									  +'<div></div>'
									  +'<div class="rev_wiztemp_listtext">'+msg+'</div>'
								  +'</div>';
						container.append(html);						
						vmPopup.find("#nextPage").hide();
					}
				}
			});
		}
				
		vmPopup.find("#template_container").bind("click", function(event) {
			var container = $(this);
			var target = $(event.target);
			var parent = target.parent();
			if (parent.hasClass("rev_wiztemplistbox_selected") || parent.hasClass("rev_wiztemplistbox")) {
				target = parent;
			}
			if (target.attr("id") != "-2") {
				if (target.hasClass("rev_wiztemplistbox")) {
					container.find(".rev_wiztemplistbox_selected").removeClass().addClass("rev_wiztemplistbox");
					target.removeClass().addClass("rev_wiztemplistbox_selected");
				} else if (target.hasClass("rev_wiztemplistbox_selected")) {
					target.removeClass().addClass("rev_wiztemplistbox");
				}
			}
		});
        
        var wizardSelectedZone;		
		vmPopup.find(".rev_wizmid_actionnext").bind("click", function(event) {
			event.preventDefault();
			event.stopPropagation();
			
			var thisDialog = vmPopup;
			
			if (currentStep == 1) {			    
			    // validate values
				var isValid = true;		
				isValid &= validateString("Name", thisDialog.find("#wizard_vm_name"), thisDialog.find("#wizard_vm_name_errormsg"), true);
				isValid &= validateString("Group", thisDialog.find("#wizard_vm_group"), thisDialog.find("#wizard_vm_group_errormsg"), true);				
				if (!isValid) return;		
			}
			
			// prevent a person from moving on if no templates are selected
			if (currentStep == 2 && vmPopup.find("#step2 #template_container .rev_wiztemplistbox_selected").length == 0) {
				$("#step2 #wiz_message").show();
				return false;
			}
			
			if(currentStep ==3) {
			    // prevent a person from moving on if no service offering is selected
			    if(vmPopup.find("#step3 #wizard_service_offering li").length == 0) {
			        $("#step3 #wiz_message #wiz_message_text").text("Please select a service offering to continue");
			        $("#step3 #wiz_message").show();
				    return false;
				}
				
				// prevent a person from moving on if no disk offering is selected
				if ($("#wiz_blank").hasClass("rev_wizmid_selectedtempbut")) {  //check root disk offering 				    
				    if(vmPopup.find("#step3 #wizard_root_disk_offering li").length == 0) {
			            $("#step3 #wiz_message #wiz_message_text").text("Please select a root disk offering to continue");
			            $("#step3 #wiz_message").show();
				        return false;
				    }				    
				}
				else {  //check data disk offering				
				    if(vmPopup.find("#step3 #wizard_data_disk_offering li").length == 0) {
				        $("#step3 #wiz_message #wiz_message_text").text("Please select a data disk offering to continue");
			            $("#step3 #wiz_message").show();
				        return false;
				    }				
				}
			}
			
			vmPopup.find("#step" + currentStep).hide().next().show();
			currentStep++;
			
			// Wizard Step 2 - list templates
			if (currentStep == 2) {
				wizardSelectedZone = $("#wizard_zone").val();
				
				// Make a check to see if a zone has been selected
				
				// Show default featured templates
				listTemplatesInPopupStep2();
				
				// Setup the left template filters
				currentPageInPopupStep2 = 1;				
				$("#wiz_template_filter").unbind("click").bind("click", function(event) {
					var container = $(this);
					var target = $(event.target);
					var targetId = target.attr("id");
					filterInPopupStep2 = "featured";
					switch (targetId) {
						case "wiz_featured":
						    vmPopup.find("#search_input").val("");  
						    currentPageInPopupStep2 = 1;
							filterInPopupStep2 = "featured";
							container.find("#wiz_featured").removeClass().addClass("rev_wizmid_selectedtempbut");
							container.find("#wiz_my, #wiz_community, #wiz_blank").removeClass().addClass("rev_wizmid_nonselectedtempbut");
							break;
						case "wiz_my":
						    vmPopup.find("#search_input").val("");  
						    currentPageInPopupStep2 = 1;
							container.find("#wiz_my").removeClass().addClass("rev_wizmid_selectedtempbut");
							container.find("#wiz_featured, #wiz_community, #wiz_blank").removeClass().addClass("rev_wizmid_nonselectedtempbut");
							filterInPopupStep2 = "selfexecutable";
							break;	
						case "wiz_community":
						    vmPopup.find("#search_input").val("");  
						    currentPageInPopupStep2 = 1;
							container.find("#wiz_community").removeClass().addClass("rev_wizmid_selectedtempbut");
							container.find("#wiz_my, #wiz_featured, #wiz_blank").removeClass().addClass("rev_wizmid_nonselectedtempbut");
							filterInPopupStep2 = "community";
							break;
						case "wiz_blank":
						    vmPopup.find("#search_input").val("");  
						    currentPageInPopupStep2 = 1;
							container.find("#wiz_blank").removeClass().addClass("rev_wizmid_selectedtempbut");
							container.find("#wiz_my, #wiz_community, #wiz_featured").removeClass().addClass("rev_wizmid_nonselectedtempbut");
							filterInPopupStep2 = "blank";
							break;
					}
					listTemplatesInPopupStep2();
					return false;
				});
			}

			// Wizard Step 3 - list service offerings
			if (currentStep == 3) {
				vmPopup.find("#wiz_message").hide();
				var serviceOfferingSelected = false;
			    var diskOfferingSelected = false;
				$.ajax({
					data: "command=listServiceOfferings&response=json",
					dataType: "json",
					async: false,
					success: function(json) {
						var offerings = json.listserviceofferingsresponse.serviceoffering;
						$("#wizard_service_offering").empty();	

						var first = true;
						if (offerings != null && offerings.length > 0) {
						    serviceOfferingSelected = true;
							for (var i = 0; i < offerings.length; i++) {
								var checked = "checked";
								if (first == false) checked = "";
								var html = 
									"<li>"
										+"<input class='radio' type='radio' name='service' id='service' value='"+offerings[i].id+"'" + checked + "/>"
										+"<label style='width:500px;font-size:11px;' for='service'>"+sanitizeXSS(offerings[i].displaytext)+"</label>"
								   +"</li>";
								$("#wizard_service_offering").append(html);
								first = false;
							}
							//Safari and Chrome are not smart enough to make checkbox checked if html markup is appended by JQuery.append(). So, the following 2 lines are added.		
						    var html_all = $("#wizard_service_offering").html();        
                            $("#wizard_service_offering").html(html_all); 
						}
						
						$.ajax({
							data: "command=listDiskOfferings&domainid=1&response=json",
							dataType: "json",
							async: false,
							success: function(json) {
								var offerings = json.listdiskofferingsresponse.diskoffering;
								$("#wizard_root_disk_offering, #wizard_data_disk_offering").empty();
								var showRootDisk = false;
								if ($("#wiz_blank").hasClass("rev_wizmid_selectedtempbut")) {
									$("#wizard_root_disk_offering_title").show();
									$("#wizard_data_disk_offering_title").hide();
									showRootDisk = true;
								} else {
									$("#wizard_root_disk_offering_title").hide();
									$("#wizard_data_disk_offering_title").show();
								}
								var first = true;
								if (offerings != null && offerings.length > 0) {
								    diskOfferingSelected = true;
									for (var i = 0; i < offerings.length; i++) {
										var checked = "checked";
										if (first == false) checked = "";
										if (showRootDisk) {
											var html = 
												"<li>"
													+"<input class='radio' type='radio' name='rootdisk' id='rootdisk' value='"+offerings[i].id+"'" + checked + "/>"
													+"<label style='width:500px;font-size:11px;' for='disk'>"+sanitizeXSS(offerings[i].displaytext)+"</label>"
											   +"</li>";
											$("#wizard_root_disk_offering").append(html);
										} else {
											var html = 
											"<li>"
												+"<input class='radio' type='radio' name='datadisk' id='datadisk' value='"+offerings[i].id+"'" + checked + "/>"
												+"<label style='width:500px;font-size:11px;' for='disk'>"+sanitizeXSS(offerings[i].displaytext)+"</label>"
										   +"</li>";
											$("#wizard_data_disk_offering").append(html);
										}
										first = false;
									}
									//Safari and Chrome are not smart enough to make checkbox checked if html markup is appended by JQuery.append(). So, the following 2 lines are added.		
						            var html_all = $("#wizard_root_disk_offering").html();        
                                    $("#wizard_root_disk_offering").html(html_all); 
						            
						            var html_all = $("#wizard_data_disk_offering").html();        
                                    $("#wizard_data_disk_offering").html(html_all); 
								}
							}
						});
					}
				});
			}
			
			// Wizard Step 4 - Review all data
			if (currentStep == 4) {
				if ($("#wiz_blank").hasClass("rev_wizmid_selectedtempbut")) {
					$("#wizard_review_root_disk_offering").text($("#wizard_root_disk_offering input[name=rootdisk]:checked").next().text());
					$("#wizard_review_root_disk_offering_p").show();
					$("#wizard_review_iso").text($("#step2 .rev_wiztemplistbox_selected .rev_wiztemp_listtext").text());
					$("#wizard_review_iso_p").show();
					$("#wizard_review_data_disk_offering_p").hide();
					$("#wizard_review_template").text("Blank Template");
				} else {
					$("#wizard_review_template").text($("#step2 .rev_wiztemplistbox_selected .rev_wiztemp_listtext").text());
					$("#wizard_review_data_disk_offering_p").show();
					$("#wizard_review_data_disk_offering").text($("#wizard_data_disk_offering input[name=datadisk]:checked").next().text());
					$("#wizard_review_root_disk_offering_p").hide();
					$("#wizard_review_iso_p").hide();
				}	
								
				$("#wizard_review_service_offering").text($("#wizard_service_offering input[name=service]:checked").next().text());
				$("#wizard_review_zone").text($("#wizard_zone option:selected").text());
				$("#wizard_review_name").text($("#wizard_vm_name").val());
				$("#wizard_review_group").text($("#wizard_vm_group").val());
			}
			
			if (currentStep > 4) {
				// Create a new VM!!!!
				var soId = $("#wizard_service_offering input[name=service]:checked").val();
				var doId = $("#wizard_data_disk_offering input[name=datadisk]:checked").val();
				
				var tId = $("#step2 .rev_wiztemplistbox_selected").attr("id");
				var zId = $("#wizard_zone").val();
				var name = trim($("#wizard_vm_name").val());
				var group = trim($("#wizard_vm_group").val());
				var diskOffering = null;
				
				var template = "&templateId="+tId;
				if ($("#wiz_blank").hasClass("rev_wizmid_selectedtempbut")) {
					diskOffering = "&diskOfferingId="+$("#wizard_root_disk_offering input[name=rootdisk]:checked").val();
				} else {
					diskOffering = "&diskOfferingId="+doId;
				}
				if (group != null && group.length > 0) {
					group = "&group="+encodeURIComponent(group);
				} else {
					group = "";
				}
				if (name != null && name.length > 0) {
					name = "&displayname="+encodeURIComponent(name);
				} else {
					name = "";
				}
				vmWizardClose();
				
				var vmInstance = vmInstanceTemplate.clone(true);
				// Add it to the DOM
				showInstanceLoading(vmInstance, "Creating...");
				vmInstance.find("#vm_state_bar").removeClass("admin_vmred_arrow admin_vmgreen_arrow").addClass("admin_vmgrey_arrow");
				vmInstance.find("#vm_state").text("Creating").removeClass("grid_stoppedtitles grid_runningtitles").addClass("grid_celltitles");
				vmInstance.fadeIn("slow");
				$("#submenu_content_vms #grid_content").prepend(vmInstance);
				
				$.ajax({
					data: "command=deployVirtualMachine&zoneId="+zId+"&serviceOfferingId="+soId+diskOffering+template+group+name+"&response=json",
					dataType: "json",
					success: function(json) {
						var jobId = json.deployvirtualmachineresponse.jobid;
						vmInstance.attr("id","vmNew"+jobId).data("jobId", jobId);
						var timerKey = "vmNew"+jobId;
						
						// Process the async job
						$("body").everyTime(
							10000,
							timerKey,
							function() {
								$.ajax({
									data: "command=queryAsyncJobResult&jobId="+jobId+"&response=json",
									dataType: "json",
									success: function(json) {
										var result = json.queryasyncjobresultresponse;
										if (result.jobstatus == 0) {
											return; //Job has not completed
										} else {
											$("body").stopTime(timerKey);
											//vmInstance.find(".loading_animationcontainer").hide();
											vmInstance.find("#vm_loading_container").hide();
											if (result.jobstatus == 1) {
												// Succeeded
												vmJSONToTemplate(result.virtualmachine[0], vmInstance);
												if (result.virtualmachine[0].passwordenabled == 'true') {
													vmInstance.find(".loadingmessage_container .loadingmessage_top p").html("Your instance has been successfully created.  Your new password is : <b>" + result.virtualmachine[0].password + "</b> .  Please change it as soon as you log into your new instance");
												} else {
													vmInstance.find(".loadingmessage_container .loadingmessage_top p").html("Your instance has been successfully created.");
												}
												vmInstance.find(".loadingmessage_container").fadeIn("slow");
												vmInstance.attr("id", "vm" + result.virtualmachine[0].id);
												vmInstance.find("#vm_state_bar").removeClass("admin_vmred_arrow admin_vmgrey_arrow").addClass("admin_vmgreen_arrow");
												vmInstance.find("#vm_state").text("Running").removeClass("grid_stoppedtitles grid_celltitles").addClass("grid_runningtitles");
												changeGridRowsTotal($("#grid_rows_total"), 1); 
											} else if (result.jobstatus == 2) {
												// Failed
												vmInstance.find(".loadingmessage_container .loadingmessage_top p").text("Unable to create your new instance due to the error: " + result.jobresult);
												vmInstance.find(".loadingmessage_container").fadeIn("slow");
												vmInstance.find(".continue_button").data("jobId", result.jobid).unbind("click").bind("click", function(event) {
													event.preventDefault();
													var deadVM = $("#vmNew"+$(this).data("jobId"));
													deadVM.slideUp("slow", function() {
														$(this).remove();
													});
												});
											}
										}
									},
									error: function(XMLHttpResponse) {
										$("body").stopTime(timerKey);
										handleError(XMLHttpResponse);
									}
								});
							},
							0
						);
					}
				});
			} else {
				vmPopup.find(".rev_wizmid_actionback").show();
			}
		});
		
		vmPopup.find(".rev_wizmid_actionback").bind("click", function(event) {		
			vmPopup.find("#step" + currentStep).hide().prev().show();
			currentStep--;
			if (currentStep == 1) {
				vmPopup.find(".rev_wizmid_actionback").hide();
			}
			return false; //event.preventDefault() + event.stopPropagation()
		});
						
		var currentSubMenu = $("#submenu_vms");
		$("#submenu_vms").bind("click", function(event) {				
			event.preventDefault();
			
			$(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);
			var submenuContent = $("#submenu_content_vms").show();
			$("#submenu_content_console, #submenu_content_routers, #submenu_content_snapshots").hide();
						
			// Major HACK here.  I am reusing the disk header as the account header.
			if (isAdmin()) {
				$("#vm_disk_header").text("Account");
				$("#vm_group_header").text("Host");
				submenuContent.find("#adv_search_pod_li, #adv_search_domain_li, #adv_search_account_li").show();				
			}
			
			// Setup VM Page by listing User's VMs			
			currentPage = 1;
			listVirtualMachines();
		});
		
		function listVirtualMachines() {	
		    var submenuContent = $("#submenu_content_vms");  			  
                      		     
            var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();	
			    var state = submenuContent.find("#advanced_search #adv_search_state").val();
			    var zone = submenuContent.find("#advanced_search #adv_search_zone").val();
			    var pod = submenuContent.find("#advanced_search #adv_search_pod").val();
			    var domainId = submenuContent.find("#advanced_search #adv_search_domain").val();
			    var account = submenuContent.find("#advanced_search #adv_search_account").val();
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));				
				if (state!=null && state.length > 0) 
					moreCriteria.push("&state="+state);		
			    if (zone!=null && zone.length > 0) 
					moreCriteria.push("&zoneid="+zone);		
			    if (domainId!=null && domainId.length > 0) 
					moreCriteria.push("&domainid="+domainId);		
			    if (pod!=null && pod.length > 0) 
					moreCriteria.push("&podId="+pod);		
				if (account!=null && account.length > 0) 
					moreCriteria.push("&account="+account);		       
				commandString = "command=listVirtualMachines&page="+currentPage+moreCriteria.join("")+"&response=json";
			} else {     			    		
			    var searchInput = submenuContent.find("#search_input").val();	 
		        if (searchInput != null && searchInput.length > 0) {
		            commandString = "command=listVirtualMachines&page="+currentPage+"&keyword="+searchInput+"&response=json";
		        }
		        else {		            
		            var moreCriteria = [];	
		            // "p_domainId!=null" and "p_account!=null" means redirected from "VMs" link on Accounts page to here(Instances page) 	
			        if(p_domainId!=null && p_domainId.length > 0)
			            moreCriteria.push("&domainid="+p_domainId);	                 
                    if (p_account!=null && p_account.length > 0) 
					    moreCriteria.push("&account="+p_account);
		            commandString = "command=listVirtualMachines&page="+currentPage+moreCriteria.join("")+"&response=json";
		        }
			}
			
			//listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listvirtualmachinesresponse", "virtualmachine", vmInstanceTemplate, vmJSONToTemplate);           
		}
		
		submenuContentEventBinder($("#submenu_content_vms"), listVirtualMachines);
		
		        //*** router_template event handler (begin) ******************************************************************    	
	    $("#router_template").bind("mouseenter", function(event) {
		    $(this).find("#grid_links_container").show();
		    return false;
	    });
	    $("#router_template").bind("mouseleave", function(event) {
		    $(this).find("#grid_links_container").hide();
		    return false;
	    });

        $("#router_template").bind("click", function(event) {               
		    var template = $(this);
		    var link = $(event.target);
		    var linkAction = link.attr("id");
		    var id = template.data("routerId");
		    var name = template.data("routerName");
		    switch (linkAction) {
			    case "router_action_start" :
				    $("#dialog_confirmation")
				    .html("<p>Please confirm you want to start the router: <b>"+name+"</b></p>")
				    .dialog('option', 'buttons', { 					    
					    "Confirm": function() { 
						    var dialogBox = $(this);
						    $.ajax({
							    data: "command=startRouter&id="+id+"&response=json",
							    dataType: "json",
							    success: function(json) {
								    dialogBox.dialog("close");
									
								    template.find(".row_loading").show();
								    template.find(".loading_animationcontainer .loading_animationtext").text("Starting...");
								    template.find(".loading_animationcontainer").show();
								    template.fadeIn("slow");
								    var that = template; //"that" is a closure and will be used in callback function.
								    template.find(".continue_button").data("id", id).unbind("click").bind("click", function(event) {
									    event.preventDefault();									    
									    that.find(".loading_animationcontainer").hide();
									    that.find(".loadingmessage_container").fadeOut("slow");
									    that.find(".row_loading").fadeOut("slow");
								    });
								    var timerKey = "router"+id;
								    $("body").everyTime(
									    10000, 
									    timerKey,
									    function() {										      
										    $.ajax({
											    data: "command=queryAsyncJobResult&jobId="+json.startrouterresponse.jobid+"&response=json",
											    dataType: "json",
											    success: function(json) {
												    var result = json.queryasyncjobresultresponse;
												    if (result.jobstatus == 0) {
													    return; //Job has not completed
												    } else {
													    $("body").stopTime(timerKey);
													    if (result.jobstatus == 1) {
														    // Succeeded
														    var json = result.router[0];
														    template.find("#router_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar");
														    template.find("#router_state").text("Running").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
														    template.find(".grid_links").find("#router_action_stop_container, #router_action_view_console_container, #router_action_reboot_container").show();
														    template.find(".grid_links").find("#router_action_start_container").hide();														    														    
														    routerJSONToTemplate(json, template, true);
														    template.find(".loadingmessage_container .loadingmessage_top p").html("Your router has been successfully started.");
														    template.find(".loadingmessage_container").fadeIn("slow");														   
														    template.find("#router_action_view_console").data("proxyUrl", "console?cmd=access&vm=" + json.id + "&t=" + json.displayname).data("vmId", json.id).click(function(event) {
																event.preventDefault();
																window.open($(this).data("proxyUrl"),$(this).data("vmId"),"width=820,height=640,resizable=yes,menubar=no,status=no,scrollbars=no,toolbar=no,location=no");
															});
													    } else if (result.jobstatus == 2) {
														    // Failed
														    template.find("#router_state_bar").removeClass("yellow_statusbar green_statusbar grey_statusbar").addClass("red_statusbar");
														    template.find("#router_state").text("Stopped").removeClass("grid_runningtitles grid_celltitles").addClass("grid_stoppedtitles");
														    template.find(".grid_links").find("#router_action_start_container").show();
														    template.find(".grid_links").find("#router_action_stop_container, #router_action_view_console_container, #router_action_reboot_container").hide();
														    template.find(".loadingmessage_container .loadingmessage_top p").text("We were unable to start the router.  Please check your logs for more info.");
														    template.find(".loadingmessage_container").fadeIn("slow");
													    }
												    }
											    },
											    error: function(XMLHttpResponse) {
												    $("body").stopTime(timerKey);
												    handleError(XMLHttpResponse);
											    }
										    });
									    },
									    0
								    );
							    }
						    });
					    }, 
					    "Cancel": function() { 
						    $(this).dialog("close"); 
					    } 
				    }).dialog("open");
				    break;
			    case "router_action_stop" :
				    $("#dialog_confirmation")
				    .html("<p>Please confirm you want to stop the router: <b>"+name+"</b></p>")
				    .dialog('option', 'buttons', { 					    
					    "Confirm": function() { 
						    var dialogBox = $(this);
						    $.ajax({
							    data: "command=stopRouter&id="+id+"&response=json",
							    dataType: "json",
							    success: function(json) {
								    dialogBox.dialog("close");
									
								    template.find(".row_loading").show();
								    template.find(".loading_animationcontainer .loading_animationtext").text("Stopping...");
								    template.find(".loading_animationcontainer").show();
								    template.fadeIn("slow");
								    var that = template; //"that" is a closure and will be used in callback function.
								    template.find(".continue_button").data("id", id).unbind("click").bind("click", function(event) {
									    event.preventDefault();									   
									    that.find(".loading_animationcontainer").hide();
									    that.find(".loadingmessage_container").fadeOut("slow");
									    that.find(".row_loading").fadeOut("slow");
								    });
								    var timerKey = "router"+id;
								    $("body").everyTime(
									    10000, 
									    timerKey,
									    function() {										     
										    $.ajax({
											    data: "command=queryAsyncJobResult&jobId="+json.stoprouterresponse.jobid+"&response=json",
											    dataType: "json",
											    success: function(json) {
												    var result = json.queryasyncjobresultresponse;
												    if (result.jobstatus == 0) {
													    return; //Job has not completed
												    } else {
													    $("body").stopTime(timerKey);
													    if (result.jobstatus == 1) {
														    // Succeeded
														    var json = result.router[0];
														    template.find("#router_state_bar").removeClass("yellow_statusbar green_statusbar grey_statusbar").addClass("red_statusbar");
														    template.find("#router_state").text("Stopped").removeClass("grid_runningtitles grid_celltitles").addClass("grid_stoppedtitles");
														    template.find(".grid_links").find("#router_action_start_container").show();
														    template.find(".grid_links").find("#router_action_stop_container, #router_action_view_console_container, #router_action_reboot_container").hide();														    
														    routerJSONToTemplate(json, template, true);
														    template.find(".loadingmessage_container .loadingmessage_top p").html("Your router has been successfully stopped.");
														    template.find(".loadingmessage_container").fadeIn("slow");
														    template.find("#router_action_view_console").unbind("click");
													    } else if (result.jobstatus == 2) {
														    // Failed
														    template.find("#router_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar");
														    template.find("#router_state").text("Running").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
														    template.find(".grid_links").find("#router_action_stop_container, #router_action_view_console_container, #router_action_reboot_container").show();
														    template.find(".grid_links").find("#router_action_start_container").hide();
														    template.find(".loadingmessage_container .loadingmessage_top p").text("We were unable to stop the router.  Please check your logs for more info.");
														    template.find(".loadingmessage_container").fadeIn("slow");
													    }
												    }
											    },
											    error: function(XMLHttpResponse) {
												    $("body").stopTime(timerKey);
												    handleError(XMLHttpResponse);
											    }
										    });
									    },
									    0
								    );
							    }
						    });
					    }, 
					    "Cancel": function() { 
						    $(this).dialog("close"); 
					    } 
				    }).dialog("open");
				    break;
			    case "router_action_reboot" :
				    $("#dialog_confirmation")
				    .html("<p>Please confirm you want to reboot the router: <b>"+name+"</b></p>")
				    .dialog('option', 'buttons', { 					    
					    "Confirm": function() { 
						    var dialogBox = $(this);
						    $.ajax({
							    data: "command=rebootRouter&id="+id+"&response=json",
							    dataType: "json",
							    success: function(json) {
								    dialogBox.dialog("close");
									
								    template.find(".row_loading").show();
								    template.find(".loading_animationcontainer .loading_animationtext").text("Rebooting...");
								    template.find(".loading_animationcontainer").show();
								    template.fadeIn("slow");
								    var that = template; //"that" is a closure and will be used in callback function.
								    template.find(".continue_button").data("id", id).unbind("click").bind("click", function(event) {
									    event.preventDefault();									   
									    that.find(".loading_animationcontainer").hide();
									    that.find(".loadingmessage_container").fadeOut("slow");
									    that.find(".row_loading").fadeOut("slow");
								    });
								    var timerKey = "router"+id;
								    $("body").everyTime(
									    10000, 
									    timerKey,
									    function() {										      
										    $.ajax({
											    data: "command=queryAsyncJobResult&jobId="+json.rebootrouterresponse.jobid+"&response=json",
											    dataType: "json",
											    success: function(json) {
												    var result = json.queryasyncjobresultresponse;
												    if (result.jobstatus == 0) {
													    return; //Job has not completed
												    } else {
													    $("body").stopTime(timerKey);
													    if (result.jobstatus == 1) {
														    // Succeeded
														    template.find("#router_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar");
														    template.find("#router_state").text("Running").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
														    template.find(".grid_links").find("#routerr_action_stop_container, #router_action_reboot_container").show();
														    template.find(".grid_links").find("#router_action_start_container").hide();
														    routerJSONToTemplate(result.router[0], template, true);
														    template.find(".loadingmessage_container .loadingmessage_top p").html("Your router has been successfully rebooted.");
														    template.find(".loadingmessage_container").fadeIn("slow");
													    } else if (result.jobstatus == 2) {
														    // Failed
														    template.find("#router_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar");
														    template.find("#router_state").text("Running").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
														    template.find(".grid_links").find("#router_action_stop_container, #router_action_view_console_container, #router_action_reboot_container").show();
														    template.find(".grid_links").find("#router_action_start_container").hide();
														    template.find(".loadingmessage_container .loadingmessage_top p").text("We were unable to reboot the router.  Please check your logs for more info.");
														    template.find(".loadingmessage_container").fadeIn("slow");
													    }
												    }
											    },
											    error: function(XMLHttpResponse) {
												    $("body").stopTime(timerKey);
												    handleError(XMLHttpResponse);
											    }
										    });
									    },
									    0
								    );
							    }
						    });
					    }, 
					    "Cancel": function() { 
						    $(this).dialog("close"); 
					    } 
				    }).dialog("open");
				    break;
			    default :
				    break;
		    }
		    return false;
	    });
        //*** router_template event handler (end) ********************************************************************  
		
		//*** console_template event handler (begin) ******************************************************************    	
	    $("#console_template").bind("mouseenter", function(event) {
		    $(this).find("#grid_links_container").show();
		    return false;
	    });
	    $("#console_template").bind("mouseleave", function(event) {
		    $(this).find("#grid_links_container").hide();
		    return false;
	    });

        $("#console_template").bind("click", function(event) {               
		    var template = $(this);
		    var link = $(event.target);
		    var linkAction = link.attr("id");
		    var id = template.data("consoleId");
		    var name = template.data("consoleName");
		    switch (linkAction) {
			    case "console_action_start" :
				    $("#dialog_confirmation")
				    .html("<p>Please confirm you want to start the system VM: <b>"+name+"</b></p>")
				    .dialog('option', 'buttons', { 					    
					    "Confirm": function() { 
						    var dialogBox = $(this);
						    $.ajax({
							    data: "command=startSystemVm&id="+id+"&response=json",
							    dataType: "json",
							    success: function(json) {
								    dialogBox.dialog("close");
									
								    template.find(".row_loading").show();
								    template.find(".loading_animationcontainer .loading_animationtext").text("Starting...");
								    template.find(".loading_animationcontainer").show();
								    template.fadeIn("slow");
								    var that = template; //"that" is a closure and will be used in callback function.
								    template.find(".continue_button").data("id", id).unbind("click").bind("click", function(event) {
									    event.preventDefault();									    
									    that.find(".loading_animationcontainer").hide();
									    that.find(".loadingmessage_container").fadeOut("slow");
									    that.find(".row_loading").fadeOut("slow");
								    });
								    var timerKey = "console"+id;
								    $("body").everyTime(
									    10000, 
									    timerKey,
									    function() {										      
										    $.ajax({
											    data: "command=queryAsyncJobResult&jobId="+json["startsystemvmresponse"].jobid+"&response=json",
											    dataType: "json",
											    success: function(json) {
												    var result = json.queryasyncjobresultresponse;
												    if (result.jobstatus == 0) {
													    return; //Job has not completed
												    } else {
													    $("body").stopTime(timerKey);
													    if (result.jobstatus == 1) {
														    // Succeeded
														    var json = result.systemvm[0];
														    template.find("#console_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar");
														    template.find("#console_state").text("Running").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
														    template.find(".grid_links").find("#console_action_stop_container, #console_action_view_console_container, #console_action_reboot_container").show();
														    template.find(".grid_links").find("#console_action_start_container").hide();														    
														    consoleJSONToTemplate(json, template, true);
														    template.find(".loadingmessage_container .loadingmessage_top p").html("Your system vm has been successfully started.");
														    template.find(".loadingmessage_container").fadeIn("slow");											    
														    template.find("#console_action_view_console").data("proxyUrl", "console?cmd=access&vm=" + json.id + "&t=" + json.displayname).data("vmId",json.id).click(function(event) {
																event.preventDefault();
																window.open($(this).data("proxyUrl"),$(this).data("vmId"),"width=820,height=640,resizable=yes,menubar=no,status=no,scrollbars=no,toolbar=no,location=no");
															});														    
													    } else if (result.jobstatus == 2) {
														    // Failed
														    template.find("#console_state_bar").removeClass("yellow_statusbar green_statusbar grey_statusbar").addClass("red_statusbar");
														    template.find("#console_state").text("Stopped").removeClass("grid_runningtitles grid_celltitles").addClass("grid_stoppedtitles");
														    template.find(".grid_links").find("#console_action_start_container").show();
														    template.find(".grid_links").find("#console_action_stop_container, #console_action_view_console_container, #console_action_reboot_container").hide();
														    template.find(".loadingmessage_container .loadingmessage_top p").text("We were unable to start the console.  Please check your logs for more info.");
														    template.find(".loadingmessage_container").fadeIn("slow");
													    }
												    }
											    },
											    error: function(XMLHttpResponse) {
												    $("body").stopTime(timerKey);
												    handleError(XMLHttpResponse);
											    }
										    });
									    },
									    0
								    );
							    }
						    });
					    }, 
					    "Cancel": function() { 
						    $(this).dialog("close"); 
					    } 
				    }).dialog("open");
				    break;
			    case "console_action_stop" :
				    $("#dialog_confirmation")
				    .html("<p>Please confirm you want to stop the system VM: <b>"+name+"</b></p>")
				    .dialog('option', 'buttons', { 					    
					    "Confirm": function() { 
						    var dialogBox = $(this);
						    $.ajax({
							    data: "command=stopSystemVm&id="+id+"&response=json",
							    dataType: "json",
							    success: function(json) {
								    dialogBox.dialog("close");
									
								    template.find(".row_loading").show();
								    template.find(".loading_animationcontainer .loading_animationtext").text("Stopping...");
								    template.find(".loading_animationcontainer").show();
								    template.fadeIn("slow");
								    var that = template; //"that" is a closure and will be used in callback function.
								    template.find(".continue_button").data("id", id).unbind("click").bind("click", function(event) {
									    event.preventDefault();									    
									    that.find(".loading_animationcontainer").hide();
									    that.find(".loadingmessage_container").fadeOut("slow");
									    that.find(".row_loading").fadeOut("slow");
								    });
								    var timerKey = "console"+id;
								    $("body").everyTime(
									    10000, 
									    timerKey,
									    function() {										     
										    $.ajax({
											    data: "command=queryAsyncJobResult&jobId="+json["stopsystemvmresponse"].jobid+"&response=json",
											    dataType: "json",
											    success: function(json) {
												    var result = json.queryasyncjobresultresponse;
												    if (result.jobstatus == 0) {
													    return; //Job has not completed
												    } else {
													    $("body").stopTime(timerKey);
													    if (result.jobstatus == 1) {
														    // Succeeded
														    template.find("#console_state_bar").removeClass("yellow_statusbar green_statusbar grey_statusbar").addClass("red_statusbar");
														    template.find("#console_state").text("Stopped").removeClass("grid_runningtitles grid_celltitles").addClass("grid_stoppedtitles");
														    template.find(".grid_links").find("#console_action_start_container").show();
														    template.find(".grid_links").find("#console_action_stop_container, #console_action_view_console_container, #console_action_reboot_container").hide();
														    consoleJSONToTemplate(result.systemvm[0], template, true);
														    template.find(".loadingmessage_container .loadingmessage_top p").html("Your system vm has been successfully stopped.");
														    template.find(".loadingmessage_container").fadeIn("slow");
														    template.find("#console_action_view_console").unbind("click");
													    } else if (result.jobstatus == 2) {
														    // Failed
														    template.find("#console_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar");
														    template.find("#console_state").text("Running").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
														    template.find(".grid_links").find("#console_action_stop_container, #console_action_view_console_container, #console_action_reboot_container").show();
														    template.find(".grid_links").find("#console_action_start_container").hide();
														    template.find(".loadingmessage_container .loadingmessage_top p").text("We were unable to stop the console.  Please check your logs for more info.");
														    template.find(".loadingmessage_container").fadeIn("slow");
													    }
												    }
											    },
											    error: function(XMLHttpResponse) {
												    $("body").stopTime(timerKey);
												    handleError(XMLHttpResponse);
											    }
										    });
									    },
									    0
								    );
							    }
						    });
					    }, 
					    "Cancel": function() { 
						    $(this).dialog("close"); 
					    } 
				    }).dialog("open");
				    break;
			    case "console_action_reboot" :
				    $("#dialog_confirmation")
				    .html("<p>Please confirm you want to reboot the system VM: <b>"+name+"</b></p>")
				    .dialog('option', 'buttons', { 					    
					    "Confirm": function() { 
						    var dialogBox = $(this);
						    $.ajax({
							    data: "command=rebootSystemVm&id="+id+"&response=json",
							    dataType: "json",
							    success: function(json) {
								    dialogBox.dialog("close");
									
								    template.find(".row_loading").show();
								    template.find(".loading_animationcontainer .loading_animationtext").text("Rebooting...");
								    template.find(".loading_animationcontainer").show();
								    template.fadeIn("slow");
								    var that = template; //"that" is a closure and will be used in callback function.
								    template.find(".continue_button").data("id", id).unbind("click").bind("click", function(event) {
									    event.preventDefault();									    
									    that.find(".loading_animationcontainer").hide();
									    that.find(".loadingmessage_container").fadeOut("slow");
									    that.find(".row_loading").fadeOut("slow");
								    });
								    var timerKey = "console"+id;
								    $("body").everyTime(
									    10000, 
									    timerKey,
									    function() {										      
										    $.ajax({
											    data: "command=queryAsyncJobResult&jobId="+json["rebootsystemvmresponse"].jobid+"&response=json",
											    dataType: "json",
											    success: function(json) {
												    var result = json.queryasyncjobresultresponse;
												    if (result.jobstatus == 0) {
													    return; //Job has not completed
												    } else {
													    $("body").stopTime(timerKey);
													    if (result.jobstatus == 1) {
														    // Succeeded
														    template.find("#console_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar");
														    template.find("#console_state").text("Running").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
														    template.find(".grid_links").find("#consoler_action_stop_container, #console_action_reboot_container").show();
														    template.find(".grid_links").find("#console_action_start_container").hide();
														    consoleJSONToTemplate(result.systemvm[0], template, true);
														    template.find(".loadingmessage_container .loadingmessage_top p").html("Your system vm has been successfully rebooted.");
														    template.find(".loadingmessage_container").fadeIn("slow");
													    } else if (result.jobstatus == 2) {
														    // Failed
														    template.find("#console_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar");
														    template.find("#console_state").text("Running").removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
														    template.find(".grid_links").find("#console_action_stop_container, #console_action_view_console_container, #console_action_reboot_container").show();
														    template.find(".grid_links").find("#console_action_start_container").hide();
														    template.find(".loadingmessage_container .loadingmessage_top p").text("We were unable to reboot the console.  Please check your logs for more info.");
														    template.find(".loadingmessage_container").fadeIn("slow");
													    }
												    }
											    },
											    error: function(XMLHttpResponse) {
												    $("body").stopTime(timerKey);
												    handleError(XMLHttpResponse);
											    }
										    });
									    },
									    0
								    );
							    }
						    });
					    }, 
					    "Cancel": function() { 
						    $(this).dialog("close"); 
					    } 
				    }).dialog("open");
				    break;
			    default :
				    break;
		    }
		    return false;
	    });
        //*** console_template event handler (end) ********************************************************************    	
		
		//routers page	
		function listRouters() {	
		    var submenuContent = $("#submenu_content_routers");	          	       
	               		        	       
            var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();	
			    var state = submenuContent.find("#advanced_search #adv_search_state").val();
			    var zone = submenuContent.find("#advanced_search #adv_search_zone").val();
			    var pod = submenuContent.find("#advanced_search #adv_search_pod").val();
			    var domainId = submenuContent.find("#advanced_search #adv_search_domain").val();
			    var account = submenuContent.find("#advanced_search #adv_search_account").val();
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));				
				if (state!=null && state.length > 0) 
					moreCriteria.push("&state="+state);		
			    if (zone!=null && zone.length > 0) 
					moreCriteria.push("&zoneId="+zone);		
			    if (pod!=null && pod.length > 0) 
					moreCriteria.push("&podId="+pod);
				if (domainId!=null && domainId.length > 0) 
					moreCriteria.push("&domainid="+domainId);			
				if (account!=null && account.length > 0) 
					moreCriteria.push("&account="+account);	
				commandString = "command=listRouters&page="+currentPage+moreCriteria.join("")+"&response=json";
			} else {              
	            var searchInput = submenuContent.find("#search_input").val();            
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listRouters&page="+currentPage+"&keyword="+searchInput+"&response=json";
                else
                    commandString = "command=listRouters&page="+currentPage+"&response=json";
            }                
           
            //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listroutersresponse", "router", $("#router_template"), routerJSONToTemplate);    
		}
		
		function routerJSONToTemplate(json, template, refresh) {
		    template.data("routerId", json.id).data("routerName", json.name).attr("id", "router"+json.id);
		
			if (index % 2 == 0) {
				template.addClass("row_odd");
			} else {
				template.addClass("row_even");
			}

            template.find("#router_zonename").text(json.zonename);
			template.find("#router_name").text(json.name);
			template.find("#router_public_ip").text(json.publicip);
			template.find("#router_private_ip").text(json.privateip);
			if(json.hostname)
				template.find("#router_host").text(json.hostname);
			else
				template.find("#router_host").text("");
			template.find("#router_domain").text(json.networkdomain);
			template.find("#router_owner").text(json.account);
			
			setDateField(json.created, template.find("#router_created"));
						
			// State
			if (json.state == 'Running') {
				template.find("#router_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar ");
				template.find("#router_state").text(json.state).removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
				template.find(".grid_links").find("#router_action_start_container").hide();							
			    // Console Proxy UI
				template.find("#router_action_view_console").data("proxyUrl", "console?cmd=access&vm=" + json.id + "&t=" + json.displayname).data("vmId",json.id).click(function(event) {
					event.preventDefault();
					window.open($(this).data("proxyUrl"),$(this).data("vmId"),"width=820,height=640,resizable=yes,menubar=no,status=no,scrollbars=no,toolbar=no,location=no");
				});				
			} else if (json.state == 'Stopped') {
				template.find("#router_state_bar").removeClass("yellow_statusbar grey_statusbar green_statusbar").addClass("red_statusbar");
				template.find("#router_state").text(json.state).removeClass("grid_celltitles grid_runningtitles").addClass("grid_stoppedtitles");
				template.find(".grid_links").find("#router_action_stop_container, #router_action_view_console_container, #router_action_reboot_container").hide();
			} else {
				template.find("#router_state_bar").removeClass("yellow_statusbar green_statusbar red_statusbar").addClass("grey_statusbar");
				template.find("#router_state").text(json.state).removeClass("grid_runningtitles grid_stoppedtitles").addClass("grid_celltitles");
				template.find(".grid_links").find("#router_action_start_container, #router_action_stop_container, #router_action_view_console_container, #router_action_reboot_container").hide();
			} 
		}
				
		$("#submenu_routers").bind("click", function(event) {	   
			event.preventDefault();
			
			$(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);
			var submenuContent = $("#submenu_content_routers").show();
			$("#submenu_content_vms, #submenu_content_console, #submenu_content_snapshots").hide();
						
			if (isAdmin()) 				
				submenuContent.find("#adv_search_pod_li, #adv_search_domain_li, #adv_search_account_li").show();   						
						
			currentPage = 1;
			listRouters();
		});			
        	
        submenuContentEventBinder($("#submenu_content_routers"), listRouters);	

		//console proxy	
		function listConsoleProxies() {		 
		    var submenuContent = $("#submenu_content_console");   
		        	        	
        	var commandString;    	
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();	
			    var state = submenuContent.find("#advanced_search #adv_search_state").val();
			    var zone = submenuContent.find("#advanced_search #adv_search_zone").val();
			    var pod = submenuContent.find("#advanced_search #adv_search_pod").val();
			    var domainId = submenuContent.find("#advanced_search #adv_search_domain").val();
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));				
				if (state!=null && state.length > 0) 
					moreCriteria.push("&state="+state);		
			    if (zone!=null && zone.length > 0) 
					moreCriteria.push("&zoneId="+zone);		
			    if (pod!=null && pod.length > 0) 
					moreCriteria.push("&podId="+pod);
				if (domainId!=null && domainId.length > 0) 
					moreCriteria.push("&domainid="+domainId);			
				commandString = "command=listSystemVms&page="+currentPage+moreCriteria.join("")+"&response=json";     
			} else {                      	
		        var searchInput = submenuContent.find("#search_input").val();            
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listSystemVms&page="+currentPage+"&keyword="+searchInput+"&response=json"
                else
                    commandString = "command=listSystemVms&page="+currentPage+"&response=json";      
            }                               
                        
            //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listsystemvmsresponse", "systemvm", $("#console_template"), consoleJSONToTemplate);                
		}
		
		function consoleJSONToTemplate(json, template, refresh) {
			if(!refresh) {
	            if (index++ % 2 == 0) {
					template.addClass("row_odd");
				} else {
					template.addClass("row_even");
				}				
			}
		    
		    template.data("consoleId", json.id).data("consoleName", json.name).attr("id", "console"+json.id);	
			template.find("#console_type").text(json.systemvmtype);	  
		    template.find("#console_name").text(json.name);	  
			template.find("#console_zone").text(json.zonename);
		    template.find("#console_active_session").text(json.activeviewersessions);	 
		    template.find("#console_public_ip").text(json.publicip);
		    if(json.privateip)
		    	template.find("#console_private_ip").text(json.privateip);
		    else
		    	template.find("#console_private_ip").text("");
		    if(json.hostname)
		    	template.find("#console_host").text(json.hostname);
		    else
		    	template.find("#console_host").text("");
		    template.find("#console_gateway").text(json.gateway); 
		    
		    setDateField(json.created, template.find("#console_created"));
		   			
			// State			
			if (json.state == 'Running') {
				template.find("#console_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar ");
				template.find("#console_state").text(json.state).removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
				template.find(".grid_links").find("#console_action_start_container").hide();				
				// Console Proxy UI
				template.find("#console_action_view_console").data("proxyUrl", "console?cmd=access&vm=" + json.id + "&t=" + json.displayname).data("vmId", json.id).click(function(event) {
					event.preventDefault();
					window.open($(this).data("proxyUrl"),$(this).data("vmId"),"width=820,height=640,resizable=yes,menubar=no,status=no,scrollbars=no,toolbar=no,location=no");
				});
				
			} else if (json.state == 'Stopped') {
				template.find("#console_state_bar").removeClass("yellow_statusbar grey_statusbar green_statusbar").addClass("red_statusbar");
				template.find("#console_state").text(json.state).removeClass("grid_celltitles grid_runningtitles").addClass("grid_stoppedtitles");
				template.find(".grid_links").find("#console_action_stop_container, #console_action_view_console_container, #console_action_reboot_container").hide();
			} else {
				template.find("#console_state_bar").removeClass("yellow_statusbar green_statusbar red_statusbar").addClass("grey_statusbar");
				template.find("#console_state").text(json.state).removeClass("grid_runningtitles grid_stoppedtitles").addClass("grid_celltitles");
				template.find(".grid_links").find("#console_action_start_container, #console_action_stop_container, #console_action_view_console_container, #console_action_reboot_container").hide();
			} 
	    }
		
		// CONSOLE PROXY SUBMENU
		$("#submenu_console").bind("click", function(event) {
			event.preventDefault();
			$(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);
			var submenuContent = $("#submenu_content_console").show();
			$("#submenu_content_vms, #submenu_content_routers, #submenu_content_snapshots").hide();
						
			if (isAdmin())				
				submenuContent.find("#adv_search_pod_li #adv_search_domain_li").show();  		
						
			currentPage = 1;
			listConsoleProxies();
		});  
	    	
	    submenuContentEventBinder($("#submenu_content_console"), listConsoleProxies);	
	    	    				      
		activateDialog($("#dialog_detach_volume").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));		
		
        $("#volume_detail_template").bind("click", function(event) {            
			var template = $(this);
			var link = $(event.target);
			var linkAction = link.attr("id");
			var volumeId = template.data("volumeId");
			var volumeName = template.data("volumeName");
			var vmState = template.data("vmState");
			var vmName = template.data("vmName");
			var timerKey = "volume"+volumeId;
			switch (linkAction) {				
				case "volume_action_detach_disk" :
					$("#dialog_confirmation")
					.html("<p>Please confirm you want to detach the volume.  If you are detaching a disk volume from a Windows based virtual machine, you will need to reboot the instance for the settings to take effect.</p>")
					.dialog('option', 'buttons', { 						
						"Confirm": function() { 
							$(this).dialog("close"); 
							template.find(".adding_loading .adding_text").text("Detaching...");
							template.find(".adding_loading").show();
							template.find("#volume_body").hide();
							$.ajax({
								data: "command=detachVolume&id="+volumeId+"&response=json",
								dataType: "json",
								success: function(json) {							                				                
									$("body").everyTime(5000, timerKey, function() {
										$.ajax({
											data: "command=queryAsyncJobResult&jobId="+json.detachvolumeresponse.jobid+"&response=json",
											dataType: "json",
											success: function(json) {									                
												var result = json.queryasyncjobresultresponse;										           
												if (result.jobstatus == 0) {
													return; //Job has not completed
												} else {
													$("body").stopTime(timerKey);
													if (result.jobstatus == 1) {
														// Succeeded
														template.slideUp("slow", function() {
															$(this).remove();
														});
														
													} else if (result.jobstatus == 2) {
														// Failed
														template.find(".adding_loading").hide();
														template.find("#volume_body").show();
														$("#dialog_alert").html("<p>" + sanitizeXSS(result.jobresult) + "</p>").dialog("open");
													}
												}
											},
											error: function(XMLHttpResponse) {
												$("body").stopTime(timerKey);
												handleError(XMLHttpResponse);
											}
										});
									}, 0);
								}
							});			
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						}
					}).dialog("open");
					break;
				case "volume_action_create_template" :
					if(vmState != "Stopped") {
						$("#dialog_alert").html("<p><b>"+vmName+"</b> needs to be stopped before you can create a template of this disk volume.</p>")
						$("#dialog_alert").dialog("open");
						return false;
					}
					$("#dialog_create_template").find("#volume_name").text(volumeName);
					$("#dialog_create_template")
					.dialog('option', 'buttons', { 						
						"Create": function() { 							
							// validate values
					        var isValid = true;					
					        isValid &= validateString("Name", $("#create_template_name"), $("#create_template_name_errormsg"));
        					isValid &= validateString("Display Text", $("#create_template_desc"), $("#create_template_desc_errormsg"));			
					        if (!isValid) return;		
					        
					        var name = trim($("#create_template_name").val());
							var desc = trim($("#create_template_desc").val());
							var osType = $("#create_template_os_type").val();					
							var isPublic = $("#create_template_public").val();
                            var password = $("#create_template_password").val();				
							
							$(this).dialog("close"); 
							template.find(".adding_loading .adding_text").text("Creating Template...");
							template.find(".adding_loading").show();
							template.find("#volume_body").hide();
							$.ajax({
								data: "command=createTemplate&volumeId="+volumeId+"&name="+encodeURIComponent(name)+"&displayText="+encodeURIComponent(desc)+"&osTypeId="+osType+"&isPublic="+isPublic+"&passwordEnabled="+password+"&response=json",
								dataType: "json",
								success: function(json) {
									$("body").everyTime(
										30000, // This is templates..it could take hours
										timerKey,
										function() {
											$.ajax({
												data: "command=queryAsyncJobResult&jobId="+json.createtemplateresponse.jobid+"&response=json",
												dataType: "json",
												success: function(json) {
													var result = json.queryasyncjobresultresponse;
													if (result.jobstatus == 0) {
														return; //Job has not completed
													} else {
														$("body").stopTime(timerKey);
														template.find(".adding_loading").hide();
														template.find("#volume_body").show();
														if (result.jobstatus == 1) {
															$("#dialog_info").html("<p>Private template: " + name + " has been successfully created</p>").dialog("open");
														} else if (result.jobstatus == 2) {
															$("#dialog_alert").html("<p>" + result.jobresult + "</p>").dialog("open");
														}
													}
												},
												error: function(XMLHttpResponse) {
													template.find(".adding_loading").hide();
													template.find("#volume_body").show();
													$("body").stopTime(timerKey);
													handleError(XMLHttpResponse);
												}
											});
										},
										0
									);
								},
								error: function(XMLHttpResponse) {
									template.find(".adding_loading").hide();
									template.find("#volume_body").show();
									handleError(XMLHttpResponse);
								}
							});
						}, 
						"Cancel": function() { 
							$(this).dialog("close"); 
						} 
					}).dialog("open");
					break;
				default :
					break;
			}
			return false;
		});   
	
		$.ajax({
			data: "command=listOsTypes&response=json",
			dataType: "json",
			success: function(json) {
				types = json.listostypesresponse.ostype;
				if (types != null && types.length > 0) {
					var select = $("#dialog_create_template #create_template_os_type").empty();
					for (var i = 0; i < types.length; i++) {
						select.append("<option value='" + types[i].id + "'>" + types[i].description + "</option>");
					}
				}	
			}
		});
		
		$("#submenu_vms").click();
	});
}