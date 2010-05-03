function showStorageTab(domainId, targetTab) {      
    var currentSubMenu;
       		
    var populateZoneField = function(isAdmin) {         
        $.ajax({
		    data: "command=listZones&available=true&response=json",
		    dataType: "json",
		    success: function(json) {
			    var zones = json.listzonesresponse.zone;					    
			    if(isAdmin) {	
			        var poolZoneSelect = $("#dialog_add_pool #pool_zone").empty();		
			        var hostZoneSelect = $("#dialog_add_host #storage_zone").empty();	
			    }
			    var volumeZoneSelect = $("#dialog_add_volume #volume_zone").empty();			
			    if (zones != null && zones.length > 0) {
			        for (var i = 0; i < zones.length; i++) {	
			            if(isAdmin) {			
				            poolZoneSelect.append("<option value='" + zones[i].id + "'>" + zones[i].name + "</option>"); 
				            hostZoneSelect.append("<option value='" + zones[i].id + "'>" + zones[i].name + "</option>"); 
				        }
				        volumeZoneSelect.append("<option value='" + zones[i].id + "'>" + zones[i].name + "</option>"); 
			        }
			    }
				if (isAdmin) {
					poolZoneSelect.change();
				}
		    }
		});	
    }   
    
    var populateDiskOfferingField = function() {        
        $.ajax({
		    data: "command=listDiskOfferings&response=json",
		    dataType: "json",
		    success: function(json) {			    
		        var offerings = json.listdiskofferingsresponse.diskOffering;								
			    var volumeDiskOfferingSelect = $("#dialog_add_volume #volume_diskoffering").empty();	
			    if (offerings != null && offerings.length > 0) {								
			        if (offerings != null && offerings.length > 0) {
			            for (var i = 0; i < offerings.length; i++) 				
				            volumeDiskOfferingSelect.append("<option value='" + offerings[i].id + "'>" + offerings[i].displaytext + "</option>"); 		
				    }	
				}	
		    }
	    });		    
    }
    
    var populateVirtualMachineField = function(domainId, account) {        
	    $.ajax({
		    cache: false,
		    data: "command=listVirtualMachines&state=Running&domainid="+domainId+"&account="+account+"&response=json",
		    dataType: "json",
		    success: function(json) {			    
			    var instances = json.listvirtualmachinesresponse.virtualmachine;				
			    var volumeVmSelect = $("#dialog_attach_volume #volume_vm").empty();					
			    if (instances != null && instances.length > 0) {
				    for (var i = 0; i < instances.length; i++) {
					    volumeVmSelect.append("<option value='" + instances[i].id + "'>" + instances[i].name + "</option>"); 
				    }				    
			    }
				$.ajax({
					cache: false,
					data: "command=listVirtualMachines&state=Stopped&domainid="+domainId+"&account="+account+"&response=json",
					dataType: "json",
					success: function(json) {			    
						var instances = json.listvirtualmachinesresponse.virtualmachine;								
						if (instances != null && instances.length > 0) {
							for (var i = 0; i < instances.length; i++) {
								volumeVmSelect.append("<option value='" + instances[i].id + "'>" + instances[i].name + "</option>");
							}				    
						}
					}
				});
		    }
	    });
    }
    
    var initializeVolumeTab = function(isAdmin) {          
        // Add Volume Dialog (begin)
	    activateDialog($("#dialog_add_volume").dialog({ 
		    autoOpen: false,
		    modal: true,
		    zIndex: 2000
	    }));	
	     
	    $("#storage_action_new_volume").bind("click", function(event) {
		    $("#dialog_add_volume")
		    .dialog('option', 'buttons', { 
			    "Cancel": function() { 				        				        
				    $(this).dialog("close"); 
			    },
			    "Add": function() { 			            										
			        // validate values							
				    var isValid = true;									
				    isValid &= validateString("Name", $("#add_volume_name"), $("#add_volume_name_errormsg"));					
				    if (!isValid) return;
					
					var name = trim($("#add_volume_name").val());					
				    var zoneId = $("#dialog_add_volume #volume_zone").val();					    				
				    var diskofferingId = $("#dialog_add_volume #volume_diskoffering").val();		
				    
				    var submenuContent = $("#submenu_content_volume");						
				    var template = $("#volume_template").clone(true);	
				    beforeAddItem(submenuContent, template, $(this));									
					    					
				    $.ajax({
					    data: "command=createVolume&zoneId="+zoneId+"&name="+encodeURIComponent(name)+"&diskOfferingId="+diskofferingId+"&accountId="+"1"+"&response=json", 
					    dataType: "json",
					    success: function(json) {						        
					        var jobId = json.createvolumeresponse.jobid;
					        template.attr("id","volumeNew"+jobId).data("jobId", jobId);
					        var timerKey = "volume"+jobId;
								    
					        $("body").everyTime(2000, timerKey, function() {
							    $.ajax({
								    data: "command=queryAsyncJobResult&jobId="+json.createvolumeresponse.jobid+"&response=json",
								    dataType: "json",
								    success: function(json) {										       						   
									    var result = json.queryasyncjobresultresponse;
									    if (result.jobstatus == 0) {
										    return; //Job has not completed
									    } else {											    
										    $("body").stopTime(timerKey);
										    if (result.jobstatus == 1) {
											    // Succeeded	
											    volumeJSONToTemplate(result.volume[0], template);												    
											    afterAddItemSuccessfully(submenuContent, template);                                                                 
										    } else if (result.jobstatus == 2) {
											    $("#dialog_alert").html("<p>" + result.jobresult + "</p>").dialog("open");
											    template.slideUp("slow", function() {
													$(this).remove();
												});						    
										    }
									    }
								    },
								    error: function(XMLHttpRequest) {
									    $("body").stopTime(timerKey);
									    handleError(XMLHttpRequest);
									    template.slideUp("slow", function() {
											$(this).remove();
										});
								    }
							    });
						    }, 0);						    					
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
		    return false;
	    });
	    // Add Volume Dialog (end)
    
        function hideShowDetachAttachLinks(vmname, template) {              
	        var detachLink = template.find("#volume_action_detach_span");
	        var attachLink = template.find("#volume_action_attach_span");
	        
	        if (vmname=="none"||vmname==""||vmname==null)  {  //if NOT attached to a virtual machine, hide "detach" link, show "attach" link.  
	            detachLink.hide();
	            attachLink.show();	            
	        }        
	        else  { //if attached to a virtual machine, hide "attach" link, show "ditach" link. 
	            attachLink.hide();
	            detachLink.show();
	        }
        }  
    
        // FUNCTION: volume JSON to Template
	    function volumeJSONToTemplate(json, template) {		
	        template.attr("id", "volume"+json.id);   
		    if (index++ % 2 == 0) {
			    template.addClass("smallrow_even");
		    } else {
			    template.addClass("smallrow_odd");
		    }
		    template.data("volumeId", json.id);
		    template.data("vmname", json.vmname);	
			template.data("vmstate", json.vmstate);
		    template.data("domainId", json.domainid);	
		    template.data("account", json.account);	
			template.data("volumeName", json.name);
			template.data("vmid", json.virtualmachineid);
		    
		    template.find("#volume_id").text(json.id);
		    template.find("#volume_name").text(json.name);
		    template.find("#volume_account").text(json.account);
		    template.find("#volume_domain").text(json.domain);
		    template.find("#volume_hostname").text(json.storage);
		    template.find("#volume_path").text(json.path);
		    template.find("#volume_size").text((json.size == "0") ? "" : convertBytes(json.size));		    
		    template.find("#volume_type").text(json.type + " (" + json.storagetype + " storage)");
			if (json.virtualmachineid == undefined) {
				template.find("#volume_vmname").text("detached");
			} else {
				template.find("#volume_vmname").text(json.vmname + " (" + json.vmstate + ")");
			}
						
		    if (json.created != null && json.created.length > 0) {
			    var created = new Date();
			    created.setISO8601(json.created);
			    var showDate = created.format("m/d/Y H:i:s");
			    template.find("#volume_created").text(showDate);
		    }
		    		
			if(json.type=="ROOT") {
				if (json.virtualmachineid != undefined && json.vmstate == "Stopped") {
					template.find("#volume_action_create_template_span").show();
				}
			} else {
				// DataDisk
				if (json.virtualmachineid != undefined) {
					if (json.storagetype == "shared" && (json.vmstate == "Running" || json.vmstate == "Stopped")) {
						template.find("#volume_action_detach_span").show();
					}
					if (json.vmstate == "Stopped") {
						template.find("#volume_action_create_template_span").show();
					}
				} else {
					// Disk not attached
					template.find("#volume_action_create_template_span").show();
					if (json.storagetype == "shared") {
						template.find("#volume_action_attach_span, #volume_action_delete_span").show();
					}
				}
			}
	    }
	    	  
	    function listVolumes() {	 
	        var submenuContent = $("#submenu_content_volume");
	         
            var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();				    
			    var zone = submenuContent.find("#advanced_search #adv_search_zone").val();
			    var pod = submenuContent.find("#advanced_search #adv_search_pod").val();
			    var account = submenuContent.find("#advanced_search #adv_search_account").val();
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));				
			    if (zone!=null && zone.length > 0) 
					moreCriteria.push("&zoneId="+zone);		
			    if (pod!=null && pod.length > 0) 
					moreCriteria.push("&podId="+pod);	
				if (account!=null && account.length > 0) 
					moreCriteria.push("&account="+account);			
				commandString = "command=listVolumes&page=" + currentPage + moreCriteria.join("") + "&response=json";		
			} else {    
			     var moreCriteria = [];		
			    if(domainId!=null)
			        moreCriteria.push("&domainid="+domainId);				   			  
                var searchInput = submenuContent.find("#search_input").val();            
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listVolumes&page=" + currentPage + moreCriteria.join("") + "&keyword=" + searchInput + "&response=json"
                else
                    commandString = "command=listVolumes&page=" + currentPage + moreCriteria.join("") + "&response=json";		
            }
            	
            //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listvolumesresponse", "volume", $("#volume_template"), volumeJSONToTemplate);  
	    } 
	   
	    submenuContentEventBinder($("#submenu_content_volume"), listVolumes);
	   
	    
	    $("#submenu_volume").bind("click", function(event) {			        
		    event.preventDefault();
		  
		    currentSubMenu.addClass("submenu_links_off").removeClass("submenu_links_on");  		
		    $(this).addClass("submenu_links_on").removeClass("submenu_links_off");			    	    
		    currentSubMenu = $(this);
		    
		    $("#submenu_content_volume").show();
		    $("#submenu_content_pool").hide();
		    $("#submenu_content_storage").hide();  
		    $("#submenu_content_snapshot").hide(); 
		    
		    var submenuContent = $("#submenu_content_volume");
		    submenuContent.find("#adv_search_pod_li").show();   
		    submenuContent.find("#adv_search_account_li").show();   
		     
		    currentPage = 1;  			
		    listVolumes();
	    });   
		 
		//???
		function snapshotJSONToTemplate(json, template) {   
	        volumeSnapshotJSONToTemplate(json, template);
	    }
		    
        function listSnapshots() {      
            var submenuContent = $("#submenu_content_snapshot");
            
            var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    /*
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();	
			    var role = submenuContent.find("#advanced_search #adv_search_role").val();
			    */
			    var moreCriteria = [];			
			    /*					
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));				
				if (trim(role).length > 0) 
					moreCriteria.push("&snapshottype="+role);
			    */	
				commandString = "command=listSnapshots&page="+currentPage+moreCriteria.join("")+"&response=json";  
			} else {     
			    var moreCriteria = [];		
			    if(domainId!=null)
			        moreCriteria.push("&domainid="+domainId);			   
			    var searchInput = submenuContent.find("#search_input").val();         
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listSnapshots&page="+currentPage+moreCriteria.join("")+"&keyword="+searchInput+"&response=json"
                else
                    commandString = "command=listSnapshots&page="+currentPage+moreCriteria.join("")+"&response=json";          
            }   
            
            //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listsnapshotsresponse", "snapshot", $("#snapshot_template"), snapshotJSONToTemplate);    
	    }
	    
	    submenuContentEventBinder($("#submenu_content_snapshot"), listSnapshots);	   
	     
		$("#submenu_snapshot").bind("click", function(event) {			        
		    event.preventDefault();
		  
		    currentSubMenu.addClass("submenu_links_off").removeClass("submenu_links_on");  	
		    $(this).addClass("submenu_links_on").removeClass("submenu_links_off");			    		    
		    currentSubMenu = $(this);
		    
		    $("#submenu_content_snapshot").show();
		    $("#submenu_content_pool").hide();
		    $("#submenu_content_storage").hide();  
		    $("#submenu_content_volume").hide(); 
		    
		    var submenuContent = $("#submenu_content_snapshot");
		    		     
		    currentPage = 1;  			
		    listSnapshots();
	    });  	   
		//???
	
	    activateDialog($("#dialog_detach_volume").dialog({ 
		    autoOpen: false,
		    modal: true,
		    zIndex: 2000
	    }));	
		
	    activateDialog($("#dialog_attach_volume").dialog({ 
		    autoOpen: false,
		    modal: true,
		    zIndex: 2000
	    }));	
		
	    activateDialog($("#dialog_delete_volume").dialog({ 
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
		
		activateDialog($("#dialog_create_snapshot").dialog({ 
		    autoOpen: false,
		    modal: true,
		    zIndex: 2000
	    }));
		
		activateDialog($("#dialog_recurring_snapshot").dialog({ 
		    width: 772,
		    autoOpen: false,
		    modal: true,
		    zIndex: 2000
	    }));
		
		$.ajax({
			data: "command=listOSTypes&response=json",
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
			
		// *** recurring snapshot dialog - event binding (begin) ******************************	
		$("#dialog_recurring_snapshot").bind("click", function(event) {		    
		    var target = event.target.id;
		    var thisDialog = $(this);		   
		    var volumeId = thisDialog.data("volumeId");
		    var leftContainer = thisDialog.find(".dialog_snapshotleft");
			var rightContainer = thisDialog.find(".dialog_snapshotright");
			
			function clearFields() {			    
			    rightContainer.find("#edit_hour").val("");
		        rightContainer.find("#edit_minute").val("");
		        rightContainer.find("#edit_meridiem").val("AM");
		        rightContainer.find("#edit_max").val("");		        
		        rightContainer.find("#edit_day_of_week").val("");
		        rightContainer.find("#edit_day_of_month").val("");
			}
		    
		    if(target.indexOf("_edit_link")!=-1) {
		        rightContainer.show();			        
		        clearFields();
		    }	
		    else if(target.indexOf("_delete_link")!=-1) {  		       
		        clearFields();
		    }
		    
		    switch(target) {
		        case "hourly_edit_link":
		            $("#edit_interval_type").text("Hourly");
		            $("#edit_time_colon, #edit_hour_container, #edit_meridiem_container, #edit_day_of_week_container, #edit_day_of_month_container").hide(); 
		            $("#edit_minute_container").show();
		            break;
		        case "daily_edit_link":
		            $("#edit_interval_type").text("Daily");
		            $("#edit_day_of_week_container, #edit_day_of_month_container").hide(); 
		            $("#edit_time_colon, #edit_minute_container, #edit_hour_container, #edit_meridiem_container").show();
		            break;
		        case "weekly_edit_link":
		            $("#edit_interval_type").text("Weekly");
		            $("#edit_day_of_month_container").hide(); 
		            $("#edit_time_colon, #edit_minute_container, #edit_hour_container, #edit_meridiem_container, #edit_day_of_week_container").show();
		            break;
		        case "monthly_edit_link":
		            $("#edit_interval_type").text("Monthly");
		            $("#edit_day_of_week_container").hide(); 
		            $("#edit_time_colon, #edit_minute_container, #edit_hour_container, #edit_meridiem_container, #edit_day_of_month_container").show();
		            break;  
		        case "apply_button":		            
		            var intervalType = rightContainer.find("#edit_interval_type").text().toLowerCase();
		            var minute, hour12, hour24, meridiem, dayOfWeek, dayOfWeekString, dayOfMonth, schedule, max;   			                   
		            switch(intervalType) {
		                 case "hourly":
		                     minute = rightContainer.find("#edit_minute").val();		                     
		                     schedule = minute;		                    
		                     max = rightContainer.find("#edit_max").val();		                                 
		                     break;
		                 case "daily":
		                     minute = rightContainer.find("#edit_minute").val();		
		                     hour12 = rightContainer.find("#edit_hour").val();
		                     meridiem = rightContainer.find("#edit_meridiem").val();			                    
		                     if(meridiem=="AM")	 
		                         hour24 = hour12;
		                     else //meridiem=="PM"	 
		                         hour24 = (parseInt(hour12)+12).toString();                
		                     schedule = minute + ":" + hour24;		                    
		                     max = rightContainer.find("#edit_max").val();		
		                     break;
		                 case "weekly":
		                     minute = rightContainer.find("#edit_minute").val();		
		                     hour12 = rightContainer.find("#edit_hour").val();
		                     meridiem = rightContainer.find("#edit_meridiem").val();			                    
		                     if(meridiem=="AM")	 
		                         hour24 = hour12;
		                     else //meridiem=="PM"	 
		                         hour24 = (parseInt(hour12)+12).toString();    
		                     dayOfWeek = rightContainer.find("#edit_day_of_week").val();  
		                     dayOfWeekString = rightContainer.find("#edit_day_of_week option:selected").text();
		                     schedule = minute + ":" + hour24 + ":" + dayOfWeek;		                    
		                     max = rightContainer.find("#edit_max").val();	
		                     break;
		                 case "monthly":
		                     minute = rightContainer.find("#edit_minute").val();		
		                     hour12 = rightContainer.find("#edit_hour").val();
		                     meridiem = rightContainer.find("#edit_meridiem").val();			                    
		                     if(meridiem=="AM")	 
		                         hour24 = hour12;
		                     else //meridiem=="PM"	 
		                         hour24 = (parseInt(hour12)+12).toString();    
		                     dayOfMonth = rightContainer.find("#edit_day_of_month").val();  		                     
		                     schedule = minute + ":" + hour24 + ":" + dayOfMonth;		                    
		                     max = rightContainer.find("#edit_max").val();			                    
		                     break;		                
		            }	
		            
		            $.ajax({
                        data: "command=createSnapshotPolicy&intervaltype="+intervalType+"&schedule="+schedule+"&volumeid="+volumeId+"&maxsnaps="+max+"&response=json",
                        dataType: "json",                        
                        success: function(json) {	                                                                              
                            switch(intervalType) {
		                         case "hourly":
		                             leftContainer.find("#read_hourly_minute").text(minute);
                                     leftContainer.find("#read_hourly_max").text(max);	           
		                             break;
		                         case "daily":
		                             leftContainer.find("#read_daily_minute").text(minute);
		                             leftContainer.find("#read_daily_hour").text(hour12);
		                             leftContainer.find("#read_daily_meridiem").text(meridiem);
                                     leftContainer.find("#read_daily_max").text(max);	    
		                             break;
		                         case "weekly":
		                             leftContainer.find("#read_weekly_minute").text(minute);
		                             leftContainer.find("#read_weekly_hour").text(hour12);
		                             leftContainer.find("#read_weekly_meridiem").text(meridiem);
		                             leftContainer.find("#read_weekly_day_of_week").text(dayOfWeekString);
                                     leftContainer.find("#read_weekly_max").text(max);	    
		                             break;
		                         case "monthly":
		                             leftContainer.find("#read_monthly_minute").text(minute);
		                             leftContainer.find("#read_monthly_hour").text(hour12);
		                             leftContainer.find("#read_monthly_meridiem").text(meridiem);		                             
		                             leftContainer.find("#read_monthly_day_of_month").text(toDayOfMonthDesp(dayOfMonth));
                                     leftContainer.find("#read_monthly_max").text(max);	    
		                             break;
		                    }	                      
                            	    						
                        },
                        error: function(XMLHttpResponse) {                            					
	                        handleError(XMLHttpResponse);					
                        }
                    });	           
		                        
		            break;		            
		       
		    }
		    return false; //event.preventDefault() + event.stopPropagation()
		});	
		// *** recurring snapshot dialog - event binding (end) ******************************	
			
		// *** volume template - event binding (begin) ****************************************
		var volumeTemplate = $("#volume_template");
		
		/*
		volumeTemplate.bind("mouseenter", function(event) {
			$(this).find("#grid_links_container").show();
			return false;
		});
		
		volumeTemplate.bind("mouseleave", function(event) {
			$(this).find("#grid_links_container").hide();
			return false;
		});
		*/
		
	    volumeTemplate.bind("click", function(event) {			      
		    var template = $(this);
		    var link = $(event.target);
		    var linkAction = link.attr("id");
		    var volumeId = template.data("volumeId");
			var vmId = template.data("vmid");
		    var vmname = template.data("vmname");	
			var vmState = template.data("vmstate");
		    var domainId = template.data("domainId");
		    var account = template.data("account");
		    var volumeName = template.data("volumeName");
			var timerKey = "volume"+volumeId;	
			var submenuContent = $("#submenu_content_volume");		
					        
		    switch (linkAction) {						
			    case "volume_action_delete" : 	
                    //check if this volume is attached to a virtual machine. If yes, can't be deleted.						        		    		    		    			    
			        if(vmname != null && (vmname != "" || vmname != "none")) {  
				        $("#dialog_alert").html("<p>This volume is attached to virtual machine " + vmname + " and can't be deleted.</p>")
                        $("#dialog_alert").dialog("open");		        		        
			            return;
			        }					       		
   				        
				    $("#dialog_delete_volume")					
				    .dialog('option', 'buttons', { 
					    "Cancel": function() { 					        
						    $(this).dialog("close"); 
					    },
					    "Confirm": function() { 				    					            					            					            				        
							var volumeTemplate = $("#volume"+volumeId);	
							var loadingImg = volumeTemplate.find(".adding_loading");
							var rowContainer = volumeTemplate.find("#row_container");
							loadingImg.find(".adding_text").text("Deleting....");	
						    $(this).dialog("close");	
				            loadingImg.fadeIn("slow");
				            rowContainer.hide(); 
				                					            					        
						    $.ajax({
								data: "command=deleteVolume&id="+volumeId+"&response=json",
								dataType: "json",
								success: function(json) {							                    					                    				                				                
									$("body").everyTime(5000, timerKey, function() {
										$.ajax({
											data: "command=queryAsyncJobResult&jobId="+json.deletevolumeresponse.jobid+"&response=json",
											dataType: "json",
											success: function(json) {									                
												var result = json.queryasyncjobresultresponse;										           
												if (result.jobstatus == 0) {										                    
													return; //Job has not completed
												} else {
													$("body").stopTime(timerKey);
													if (result.jobstatus == 1) {
														// Succeeded			
														volumeTemplate.slideUp("slow", function(event) {								                                
															$(this).remove();
															changeGridRowsTotal(submenuContent.find("#grid_rows_total"), -1);      
														});							                                												            
													} else if (result.jobstatus == 2) {
														loadingImg.hide();
														rowContainer.hide(); 
														$("#dialog_alert").html("<p>" + result.jobresult + "</p>").dialog("open");
													}
												}
											},
											error: function(XMLHttpRequest) {
												$("body").stopTime(timerKey);										                
												loadingImg.hide(); 								                            
												rowContainer.show(); 
												handleError(XMLHttpRequest);
											}
										});
									}, 0);
								},
								error: function(XMLHttpResponse) {							                    			                    
									loadingImg.hide(); 								                            
									rowContainer.show(); 
									handleError(XMLHttpRequest);
								}
							});						
					    } 
				    }).dialog("open");
				    break;	
				    				
			    case "volume_action_detach" : 		   				        
				    $("#dialog_detach_volume")					
				    .dialog('option', 'buttons', { 
					    "Cancel": function() { 					        
						    $(this).dialog("close"); 
					    },
					    "Confirm": function() { 				    					            					            					            				        
							var loadingImg = template.find(".adding_loading");
							var rowContainer = template.find("#row_container");
							loadingImg.find(".adding_text").text("Detaching....");	
						    $(this).dialog("close");	
				            loadingImg.show();  
				            rowContainer.hide();
				            					            					        
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
														template.find("#volume_action_attach_span, #volume_action_delete_span, #volume_action_create_template_span").show();	
														template.find("#volume_action_detach_span").hide();																
														template.find("#volume_vmname").text("detached");
														template.data("vmid", null).data("vmname", null);
														loadingImg.hide(); 								                            
														rowContainer.show();   
													} else if (result.jobstatus == 2) {
														// Failed	
														loadingImg.hide(); 								                            
														rowContainer.show(); 	
														$("#dialog_alert").html("<p>" + result.jobresult + "</p>").dialog("open");
													}
												}
											},
											error: function(XMLHttpRequest) {
												$("body").stopTime(timerKey);										                
												loadingImg.hide(); 								                            
												rowContainer.show(); 
												handleError(XMLHttpRequest);
											}
										});
									}, 0);
								},
								error: function(XMLHttpResponse) {							                    			                    
									loadingImg.hide(); 								                            
									rowContainer.show(); 
									handleError(XMLHttpRequest);
								}
							});						
					    } 
				    }).dialog("open");
				    break;				
				    
			    case "volume_action_attach" : 			
			        populateVirtualMachineField(domainId, account);
			     		   				        
				    $("#dialog_attach_volume")					
				    .dialog('option', 'buttons', { 
					    "Cancel": function() { 					        
						    $(this).dialog("close"); 
					    },
					    "Confirm": function() { 
					        var virtualMachineId = $("#dialog_attach_volume #volume_vm").val();		
					        if(virtualMachineId==null)  {
					            $(this).dialog("close"); 
					            $("#dialog_alert").html("<p>Please attach volume to a valid virtual machine</p>").dialog("open");
					            return;					            
					        }		       
					    				    					            					            					            				        
							var loadingImg = template.find(".adding_loading");
							var rowContainer = template.find("#row_container");
							loadingImg.find(".adding_text").text("Attaching....");	
						    $(this).dialog("close");							    
						     
				            loadingImg.show();  
				            rowContainer.hide();	            
				      
				            var virtualMachineId = $("#dialog_attach_volume #volume_vm").val();		
						    $.ajax({
								data: "command=attachVolume&id="+volumeId+'&virtualMachineId='+virtualMachineId+"&response=json",
								dataType: "json",
								success: function(json) {							                    					                    				                				                
									$("body").everyTime(5000, timerKey, function() {
										$.ajax({
											data: "command=queryAsyncJobResult&jobId="+json.attachvolumeresponse.jobid+"&response=json",
											dataType: "json",
											success: function(json) {									                
												var result = json.queryasyncjobresultresponse;										           
												if (result.jobstatus == 0) {										                    
													return; //Job has not completed
												} else {
													$("body").stopTime(timerKey);
													if (result.jobstatus == 1) {
														// Succeeded
														if (result.vmstate == "Stopped") {
															template.find("#volume_action_attach_span, #volume_action_delete_span").hide();	
															template.find("#volume_action_detach_span, #volume_action_create_template_span").show();
														} else {
															template.find("#volume_action_attach_span, #volume_action_delete_span, #volume_action_create_template_span").hide();
															template.find("#volume_action_detach_span").show();
														}
														template.find("#volume_vmname").text(result.vmname + " (" + result.vmstate + ")");
														template.data("vmid", virtualMachineId).data("vmname", result.vmname);
														loadingImg.hide(); 								                            								                           							                            
														rowContainer.show(); 					                                           
													} else if (result.jobstatus == 2) {
														// Failed		
														loadingImg.hide(); 								                            
														rowContainer.show(); 												               										                
														$("#dialog_alert").html("<p>" + result.jobresult + "</p>").dialog("open");
													}
												}
											},
											error: function(XMLHttpRequest) {
												$("body").stopTime(timerKey);	
												loadingImg.hide(); 								                            
												rowContainer.show(); 
												handleError(XMLHttpRequest);
											}
										});
									}, 0);
								},
								error: function(XMLHttpRequest) {							                    			                    
									loadingImg.hide(); 								                            
									rowContainer.show(); 
									handleError(XMLHttpRequest);
								}
							});						
					    } 
				    }).dialog("open");
				    break;
				    
				case "volume_action_create_template" :
					if(vmId != null && vmState != "Stopped") {
						$("#dialog_alert").html("<p><b>"+vmname+"</b> needs to be stopped before you can create a template of this disk volume.</p>")
						$("#dialog_alert").dialog("open");
						return false;
					}
					$("#dialog_create_template").find("#volume_name").text(volumeName);
					$("#dialog_create_template")
					.dialog('option', 'buttons', { 
						"Cancel": function() { 
							$(this).dialog("close"); 
						},
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
												error: function(XMLHttpRequest) {
													template.find(".adding_loading").hide();
													template.find("#volume_body").show();
													$("body").stopTime(timerKey);
													handleError(XMLHttpRequest);
												}
											});
										},
										0
									);
								},
								error: function(XMLHttpRequest) {
									template.find(".adding_loading").hide();
									template.find("#volume_body").show();
									handleError(XMLHttpRequest);
								}
							});
						} 
					}).dialog("open");
					break;
					
			    case "volume_action_take_snapshot":	      	        
			        $("#dialog_create_snapshot")					
				    .dialog('option', 'buttons', { 
					    "Cancel": function() { 					        
						    $(this).dialog("close"); 
					    },
					    "Confirm": function() { 					        		    					            					            					            				        
							var volumeTemplate = $("#volume"+volumeId);								
							var loadingImg = volumeTemplate.find(".adding_loading");							
							var rowContainer = volumeTemplate.find("#volume_body");
							loadingImg.find(".adding_text").text("Taking snapshot....");	
						    $(this).dialog("close");					            			            
				            if(template.find("#volume_snapshot_detail_panel").css("display")=="block") //if volume's snapshot grid is poped down, close it.
							    template.find("#volume_action_snapshot_grid").click();		
							loadingImg.fadeIn("slow");
				            rowContainer.hide(); 									              					            					        
						    $.ajax({
								data: "command=createSnapshot&volumeid="+volumeId+"&response=json",
								dataType: "json",
								success: function(json) {							                    					                    				                				                
									$("body").everyTime(5000, timerKey, function() {									    
										$.ajax({
											data: "command=queryAsyncJobResult&jobId="+json.createsnapshotresponse.jobid+"&response=json",
											dataType: "json",
											success: function(json) {												    							                
												var result = json.queryasyncjobresultresponse;										           
												if (result.jobstatus == 0) {										                    
													return; //Job has not completed
												} else {
													$("body").stopTime(timerKey);													
													if (result.jobstatus == 1) {
														//Succeeded													
														template.find("#volume_action_snapshot_grid").click(); //pop down volume's snapshot grid																								
														loadingImg.hide();
														rowContainer.show(); 
														$("#dialog_info").html("<p>Taking snapshot successfully</p>").dialog("open");																		                                												            
													} else if (result.jobstatus == 2) {
														loadingImg.hide();
														rowContainer.show(); 
														$("#dialog_alert").html("<p>" + result.jobresult + "</p>").dialog("open");
													}
												}
											},
											error: function(XMLHttpResponse) {											    
												$("body").stopTime(timerKey);										                
												loadingImg.hide(); 								                            
												rowContainer.show(); 
												handleError(XMLHttpResponse);
											}
										});
									}, 0);
								},
								error: function(XMLHttpResponse) {									   			                    			                    
									loadingImg.hide(); 								                            
									rowContainer.show(); 
									handleError(XMLHttpResponse);
								}
							});						
					    } 
				    }).dialog("open");	       
			        break;     
			   
			   case "volume_action_recurring_snapshot": 
			        var dialogBox = $("#dialog_recurring_snapshot");   
			        			        	        
			        $.ajax({
	                    data: "command=listSnapshotPolicies&volumeid="+volumeId+"&response=json",
	                    dataType: "json",
	                    async: false,
	                    success: function(json) {		                                       
	                        var items = json.listsnapshotpoliciesresponse.snapshotpolicy;
	                        if(items!=null && items.length>0) {
                                for(var i=0; i<items.length; i++) {
                                    var item = items[i];
                                    switch(item.interval) {
                                        case "0": //hourly                                            
                                            dialogBox.find("#read_hourly_max").text(item.max);
                                            dialogBox.find("#read_hourly_minute").text(item.schedule);
                                            break;
                                        case "1": //daily
                                            $("#read_daily_max").text(item.max);
                                            var parts = item.schedule.split(":");
                                            dialogBox.find("#read_daily_minute").text(parts[0]);
                                            var hour12, meridiem;
                                            var hour24 = parts[1];
                                            if(hour24 < 12) {
                                                hour12 = hour24;  
                                                meridiem = "AM";                                               
                                            }   
                                            else {
                                                hour12 = hour24 - 12;
                                                meridiem = "PM"
                                            }
                                            dialogBox.find("#read_daily_hour").text(hour12);       
                                            dialogBox.find("#read_daily_meridiem").text(meridiem);                                      
                                            break;
                                        case "2": //weekly
                                            $("#read_weekly_max").text(item.max);
                                            var parts = item.schedule.split(":");
                                            dialogBox.find("#read_weekly_minute").text(parts[0]);
                                            var hour12, meridiem;
                                            var hour24 = parts[1];
                                            if(hour24 < 12) {
                                                hour12 = hour24;  
                                                meridiem = "AM";                                               
                                            }   
                                            else {
                                                hour12 = hour24 - 12;
                                                meridiem = "PM"
                                            }
                                            dialogBox.find("#read_weekly_hour").text(hour12);       
                                            dialogBox.find("#read_weekly_meridiem").text(meridiem);    
                                            dialogBox.find("#read_weekly_day_of_week").text(toDayOfWeekDesp(parts[2]));                                       
                                            break;
                                        case "3": //monthly
                                            $("#read_monthly_max").text(item.max);                                           
                                            var parts = item.schedule.split(":");
                                            dialogBox.find("#read_monthly_minute").text(parts[0]);
                                            var hour12, meridiem;
                                            var hour24 = parts[1];
                                            if(hour24 < 12) {
                                                hour12 = hour24;  
                                                meridiem = "AM";                                               
                                            }   
                                            else {
                                                hour12 = hour24 - 12;
                                                meridiem = "PM"
                                            }
                                            dialogBox.find("#read_monthly_hour").text(hour12);       
                                            dialogBox.find("#read_monthly_meridiem").text(meridiem);    
                                            dialogBox.find("#read_monthly_day_of_month").text(toDayOfMonthDesp(parts[2])); 
                                            break;
                                    }
                                }    
                            }              		    						
	                    },
		                error: function(XMLHttpResponse) {			                   					
			                handleError(XMLHttpResponse);					
		                }
                    });
			       	           			        
			        dialogBox
					.dialog('option', 'buttons', { 
						"Close": function() { 
							$(this).dialog("close"); 
						}
					}).dialog("open").data("volumeId", volumeId);
			        break;
			      
			   case "volume_action_snapshot_grid" :			        
					var expanded = link.data("expanded");
					if (expanded == null || expanded == false) {										
						$.ajax({
							cache: false,
							data: "command=listSnapshots&volumeid="+volumeId+"&response=json",
							dataType: "json",
							success: function(json) {							    
								var items = json.listsnapshotsresponse.snapshot;																						
								if (items != null && items.length > 0) {									    
								    var grid = template.find("#volume_snapshot_grid").empty();																	
									for (var i = 0; i < items.length; i++) {			
									    var newTemplate = $("#volume_snapshot_detail_template").clone(true);
				                        volumeSnapshotJSONToTemplate(items[i], newTemplate); 
				                        grid.append(newTemplate.show());																	
									}
								}
								link.removeClass().addClass("vm_botactionslinks_up");
								template.find("#volume_snapshot_detail_panel").slideDown("slow");
								
								link.data("expanded", true);
							}
						});
					} else {
						link.removeClass().addClass("vm_botactionslinks_down");
						template.find("#volume_snapshot_detail_panel").slideUp("slow");
						link.data("expanded", false);
					}
					break;			        
			        
			    default :
				    break;
		    }
		    return false;
	    });		
	    // *** volume template - event binding (end) ****************************************
				
		function volumeSnapshotJSONToTemplate(json, template) {			   
		    template.addClass("smallrow_even");		 
		   			      		    	    		    
		    template.attr("id", "volume_snapshot_"+json.id);	   
		    template.find("#id").text(json.id);
		    template.find("#name").text(json.name);
		    template.find("#path").text(json.path);		    
		    
		    var created = new Date();
			created.setISO8601(json.created);
			var showDate = created.format("m/d/Y H:i:s");
			template.find("#created").text(showDate);      
		}		
    }

        
	mainContainer.load("content/tab_storage.html", function() {		
	    if (isAdmin()) {  	   
            populateZoneField(true);
            populateDiskOfferingField();    		
    		
		    // *** Primary Storage (begin) ***
		    
		    function poolJSONToTemplate(json, template) {
		        template.attr("id", "pool"+json.id);
		    
			    if (index++ % 2 == 0) {
				    template.find("#row_container").addClass("smallrow_even");
			    } else {
				    template.find("#row_container").addClass("smallrow_odd");
			    }
    	
			    template.data("id", json.id).data("name", json.name);
			    template.find("#pool_id").text(json.id);
			    template.find("#pool_name").text(json.name);
			    template.find("#pool_zone").text(json.zonename);
			    template.find("#pool_pod").text(json.podname);
			    template.find("#pool_ip").text(json.ipaddress);
			    template.find("#pool_path").text(json.path);
			    var statHtml = "<strong> Disk Total:</strong> " +convertBytes(json.disksizetotal)+" | <strong>Disk Allocated:</strong> " + convertBytes(json.disksizeallocated);
			    template.find("#pool_statistics").html(statHtml);
    			
			    var created = new Date();
			    created.setISO8601(json.created);
			    var showDate = created.format("m/d/Y H:i:s");
			    template.find("#pool_created").text(showDate);
			    /*
			    var statHtml = "<div class='hostcpu_icon'></div><p><strong> Disk Total:</strong> " +convertBytes(json.disksizetotal)+" | <strong>Disk Allocated:</strong> " + json.disksizeallocated + "</p>";
			    template.find("#storage_disk_stat").html(statHtml);
    			
			    // State
			    if (json.state == 'Up') {
				    template.find("#storage_state_bar").removeClass("yellow_statusbar grey_statusbar red_statusbar").addClass("green_statusbar ");
				    template.find("#storage_state").text(json.state).removeClass("grid_celltitles grid_stoppedtitles").addClass("grid_runningtitles");
				    template.find(".grid_links").find("#storage_action_cancel_maint_container, #storage_action_remove_container").hide();
			    } else if (json.state == 'Down' || json.state == "Alert") {
				    template.find("#storage_state_bar").removeClass("yellow_statusbar grey_statusbar green_statusbar").addClass("red_statusbar");
				    template.find("#storage_state").text(json.state).removeClass("grid_celltitles grid_runningtitles").addClass("grid_stoppedtitles");
    				
				    if (json.state == "Alert") {
					    template.find(".grid_links").find("#storage_action_reconnect_container, #storage_action_enable_maint_container, #storage_action_cancel_maint_container, #storage_action_remove_container").hide();
				    } else {
					    template.find(".grid_links").find("#storage_action_reconnect_container, #storage_action_cancel_maint_container, #storage_action_remove_container").hide();
				    }
			    } else {
				    template.find("#storage_state_bar").removeClass("yellow_statusbar green_statusbar red_statusbar").addClass("grey_statusbar");
				    template.find("#storage_state").text(json.state).removeClass("grid_runningtitles grid_stoppedtitles").addClass("grid_celltitles ");
    				
				    if (json.state == "ErrorInMaintenance") {
					    template.find(".grid_links").find("#storage_action_reconnect_container, #storage_action_remove_container").hide();
				    } else if (json.state == "PrepareForMaintenance") {
					    template.find(".grid_links").find("#storage_action_reconnect_container, #storage_action_enable_maint_container, #storage_action_remove_container").hide();
				    } else if (json.state == "Maintenance") {
					    template.find(".grid_links").find("#storage_action_reconnect_container, #storage_action_enable_maint_container, #storage_action_cancel_maint_container").hide();
				    } else if (json.state == "Disconnected") {
					    template.find(".grid_links").find("#storage_action_reconnect_container, #storage_action_enable_maint_container, #storage_action_cancel_maint_container, #storage_action_remove_container").hide();
				    } else {
					    alert("Unsupported Host State: " + json.state);
				    }
			    } */
		    }
    		
		    // Dialog Setup
		    activateDialog($("#dialog_add_pool").dialog({ 
			    autoOpen: false,
			    modal: true,
			    zIndex: 2000
		    }));
			
			$("#dialog_add_pool #pool_zone").bind("change", function(event) {
				var zoneId = $(this).val();
				$.ajax({
					data: "command=listPods&zoneId="+zoneId+"&response=json",
					dataType: "json",
					async: false,
					success: function(json) {
						var pods = json.listpodsresponse.pod;
						var podSelect = $("#dialog_add_pool #pool_pod").empty();	
						if (pods != null && pods.length > 0) {
						    for (var i = 0; i < pods.length; i++) {
							    podSelect.append("<option value='" + pods[i].id + "'>" + pods[i].name + "</option>"); 
						    }
						}
						$("#dialog_add_pool #pool_pod").change();
					}
				});
			});
    		
    		function nfsURL(nfs_server, path) {
    		    var url;
    		    if(nfs_server.indexOf("://")==-1)
				    url = "nfs://" + nfs_server + path;
				else
				    url = nfs_server + path;
				return url;
    		}
    		
		    // Add New Primary Storage
		    $("#storage_action_new_pool").bind("click", function(event) {
			    $("#dialog_add_pool")
			    .dialog('option', 'buttons', { 
				    "Cancel": function() { 
					    $(this).dialog("close"); 
				    },
				    "Add": function() { 		
					    // validate values
					    var isValid = true;					
					    isValid &= validateString("Name", $("#add_pool_name"), $("#add_pool_name_errormsg"));
					    isValid &= validateString("NFS Server", $("#add_pool_nfs_server"), $("#add_pool_nfs_server_errormsg"));	
					    isValid &= validateString("Path", $("#add_pool_path"), $("#add_pool_path_errormsg"));			    		
					    if (!isValid) return;
    					
    					var submenuContent = $("#submenu_content_pool");
    					
					    var name = trim($("#add_pool_name").val());
					    var nfs_server = trim($("#add_pool_nfs_server").val());		
					    var path = trim($("#add_pool_path").val());				    
					    var zoneId = $("#dialog_add_pool #pool_zone").val();	
						var podId = $("#dialog_add_pool #pool_pod").val();
												
						var url = nfsURL(nfs_server, path);
						
					    var dialogBox = $(this);
					    dialogBox.dialog("close");
					    $.ajax({
						    data: "command=createStoragePool&zoneId="+zoneId+"&podId="+podId+"&name="+encodeURIComponent(name)+"&url="+encodeURIComponent(url)+"&response=json",
						    dataType: "json",
						    success: function(json) {
							    var json = json.createstoragepoolresponse;
							    var template = $("#pool_template").clone(true).attr("id", "pool"+json.storagepool[0].id);
							    poolJSONToTemplate(json.storagepool[0], template);
							    submenuContent.find("#grid_content").append(template.fadeIn("slow"));
							    changeGridRowsTotal(submenuContent.find("#grid_rows_total"), 1);
						    }
					    });
				    } 
			    }).dialog("open");
			    return false;
		    });
    		    			
    		function listStoragePools() {   
    		    var submenuContent = $("#submenu_content_pool");
    		     
               	var commandString;            
			    var advanced = submenuContent.find("#search_button").data("advanced");                    
			    if (advanced != null && advanced) {		
			        var name = submenuContent.find("#advanced_search #adv_search_name").val();				     
			        var zone = submenuContent.find("#advanced_search #adv_search_zone").val();	
			        var ip = submenuContent.find("#advanced_search #adv_search_ip").val();		
			        var path = submenuContent.find("#advanced_search #adv_search_path").val();				      
			        var moreCriteria = [];								
				    if (name!=null && trim(name).length > 0) 
					    moreCriteria.push("&name="+encodeURIComponent(trim(name)));					   
			        if (zone!=null && zone.length > 0) 
					    moreCriteria.push("&zoneId="+zone);	
					if (ip!=null && trim(ip).length > 0) 
					    moreCriteria.push("&ipaddress="+encodeURIComponent(trim(ip)));		
					if (path!=null && trim(path).length > 0) 
					    moreCriteria.push("&path="+encodeURIComponent(trim(path)));						       	
				    commandString = "command=listStoragePools&page="+currentPage+moreCriteria.join("")+"&response=json";
			    } else {          			
                    var searchInput = submenuContent.find("#search_input").val();            
                    if (searchInput != null && searchInput.length > 0) 
                        commandString = "command=listStoragePools&page="+currentPage+"&keyword="+searchInput+"&response=json"
                    else
                        commandString = "command=listStoragePools&page="+currentPage+"&response=json";
                }
               	
               	//listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
                listItems(submenuContent, commandString, "liststoragepoolsresponse", "storagepool", $("#pool_template"), poolJSONToTemplate);               
    		}
    			
    		submenuContentEventBinder($("#submenu_content_pool"), listStoragePools);	
    			
		    $("#submenu_pool").bind("click", function(event) {  		       
			    event.preventDefault();
			    
			    currentSubMenu.addClass("submenu_links_off").removeClass("submenu_links_on");		
			    $(this).addClass("submenu_links_on").removeClass("submenu_links_off");
			    currentSubMenu = $(this); 
			    
			    $("#submenu_content_pool").show();
			    $("#submenu_content_storage").hide();
			    $("#submenu_content_volume").hide();
			    $("#submenu_content_snapshot").hide();
                
                currentPage = 1;
			    listStoragePools();
		    });	    		
		    // *** Primary Storage (end) ***
    		
    		
    		
    		
    		
    			
		    // *** Secondary Storage (begin) ***				
		    // Add Secondary Storage Dialog (begin)
		    activateDialog($("#dialog_add_host").dialog({ 
			    autoOpen: false,
			    modal: true,
			    zIndex: 2000
		    }));		
		    $("#storage_action_new_host").bind("click", function(event) {
			    $("#dialog_add_host")
			    .dialog('option', 'buttons', { 
				    "Cancel": function() { 
					    $(this).dialog("close"); 
				    },
				    "Add": function() { 
					    // validate values					
					    var isValid = true;							    
					    isValid &= validateString("NFS Server", $("#add_storage_nfs_server"), $("#add_storage_nfs_server_errormsg"));	
					    isValid &= validatePath("Path", $("#add_storage_path"), $("#add_storage_path_errormsg"));					
					    if (!isValid) return;
    						
    					var submenuContent = $("#submenu_content_storage");	    								            				
					    var zoneId = $("#dialog_add_host #storage_zone").val();		
					    var nfs_server = trim($("#add_storage_nfs_server").val());		
					    var path = trim($("#add_storage_path").val());	    					    				    					   					
    					var url = nfsURL(nfs_server, path);    					
    					   					
					    var dialogBox = $(this);
					    dialogBox.dialog("close");					  
					    $.ajax({
						    data: "command=addSecondaryStorage&zoneId="+zoneId+"&url="+encodeURIComponent(url)+"&response=json",
						    dataType: "json",
						    success: function(json) {								    						    
							    var secondaryStorage = json.addsecondarystorageresponse.secondarystorage[0];
							    var template = $("#storage_template").clone(true).attr("id", "secondaryStorage_"+secondaryStorage.id);
							    storageJSONToTemplate(secondaryStorage, template);
							    submenuContent.find("#grid_content").append(template.fadeIn("slow"));
							    changeGridRowsTotal(submenuContent.find("#grid_rows_total"), 1) 
						    }			    
					    });
				    } 
			    }).dialog("open");
			    return false;
		    });
		    // Add Secondary Storage Dialog (end)
    				
		    // FUNCTION: Storage JSON to Template
		    function storageJSONToTemplate(json, template) {
		        template.attr("id", "host"+json.id);
			    if (index++ % 2 == 0) {
				    template.find("#row_container").addClass("smallrow_even");
			    } else {
				    template.find("#row_container").addClass("smallrow_odd");
			    }
			    template.data("id", "secondaryStorage_"+json.id).data("hostName", json.name);
				template.find("#storage_type").text(json.type);
			    template.find("#storage_name").text(json.name);
			    template.find("#storage_ip").text(json.ipaddress);
			    template.find("#storage_version").text(json.version);
			    template.find("#storage_mgmt").text(json.managementserverid);
			    if (json.disconnected != null && json.disconnected.length > 0) {
				    var disconnected = new Date();
				    disconnected.setISO8601(json.disconnected);
				    var showDate = disconnected.format("m/d/Y H:i:s");
				    template.find("#storage_disconnected").text(showDate);
			    }		
		    }    		    	
    		
    		function listSecondaryStorage() {    	
    		    var submenuContent = $("#submenu_content_storage");    		
    			
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
				    commandString = "command=listHosts&type=SecondaryStorage&page="+currentPage+moreCriteria.join("")+"&response=json"; 
			    } else {    
                    var searchInput = $("#submenu_content_storage #search_input").val();              
                    if (searchInput != null && searchInput.length > 0) 
                        commandString = "command=listHosts&type=SecondaryStorage&page="+currentPage+"&keyword="+searchInput+"&response=json"
                    else
                        commandString = "command=listHosts&type=SecondaryStorage&page="+currentPage+"&response=json";    
                }
                	
                //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
                listItems(submenuContent, commandString, "listhostsresponse", "host", $("#storage_template"), storageJSONToTemplate);    	    
    		}
    			
    		submenuContentEventBinder($("#submenu_content_storage"), listSecondaryStorage);	
    						
		    $("#submenu_storage").bind("click", function(event) {   		
			    event.preventDefault();
			    
			    $(this).addClass("submenu_links_on").removeClass("submenu_links_off");
			    currentSubMenu.addClass("submenu_links_off").removeClass("submenu_links_on");
			    currentSubMenu = $(this);
			    
			    $("#submenu_content_storage").show();
			    $("#submenu_content_pool").hide();
			    $("#submenu_content_volume").hide();
			    $("#submenu_content_snapshot").hide();
    			
    			currentPage = 1;
			    listSecondaryStorage();
		    });
    		
    			
		    // *** Secondary Storage (end) ***	
    		    		
    		
		    // *** Volume (begin) ***				
		    initializeVolumeTab(true);  
		    $("#volume_hostname_header, #volume_hostname_container, #volume_account_header, #volume_account_container").show();	   	    		
		    // *** Volume (end) ***	      		
    		    	
    		if(targetTab==null)  {  	
    		    currentSubMenu = $("#submenu_pool");	
		        $("#submenu_pool").click();	  //default tab is Primary Storage page
		    }
		    else {
		        currentSubMenu = $("#"+targetTab);	
		        $("#"+targetTab).click(); 	   
		    }  
	       
        } else {  //*** isAdmin()==false              
            $("#submenu_content_pool, #pool_template, #dialog_add_pool, #submenu_content_storage, #storage_template, #dialog_add_host, #submenu_pool, #submenu_storage").hide(); //hide Primary Storage tab, Secondary Storage tab 
                                                           	  	
            populateZoneField(false);    		
	        populateDiskOfferingField();
	            		 		
	        // *** Volume (begin) ***				
	        initializeVolumeTab(false);	 
	        $("#volume_hostname_header, #volume_hostname_container, #volume_account_header, #volume_account_container").hide();				
	        // *** Volume (end) ***		        
	        
	        currentSubMenu = $("#submenu_volume"); //default tab is volume
		    $("#submenu_volume").click(); 	    	    
        }
    });
}
