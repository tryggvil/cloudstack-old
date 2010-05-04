function showAccountsTab(domainId) {
	// Manage Events 
	mainContainer.load("content/tab_accounts.html", function() {	
        var systemAccountId = 1;
        var adminAccountId = 2;

		activateDialog($("#dialog_resource_limits").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_disable_account").dialog({ 
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		function updateResourceLimit(domainId, account, type, max) {
			$.ajax({
				data: "command=updateResourceLimit&domainid="+domainId+"&account="+account+"&resourceType="+type+"&max="+max+"&response=json",
				dataType: "json",
				success: function(json) {								    												
				}
			});
		}
	        
	    $("#account_template #account_change_state").bind("click", function(event) {	        
	        var elementId = $(this).attr("id");	      	       
	        var thisLink = $("#"+elementId); 
	        var accountId = thisLink.data("accountId");
	        var accountName = thisLink.data("accountName");
	        var template = $("#account"+accountId);	        
	        var action = $(this).text();	        
	        if(action == "enable") {	    
                $("#dialog_confirmation")
                .html("<p>Please confirm you want to enable account: </b>" + accountName + "</b></p>")
                .dialog('option', 'buttons', {
                    "Cancel": function() {
                        $(this).dialog("close");		     
                    },
                    "Yes": function() { 		                    
                        $(this).dialog("close");	    		                    
                        $.ajax({
			                data: "command=enableAccount&id="+accountId+"&response=json",
			                dataType: "json",
			                success: function(json) {				                    					                
				                template.find("#account_state").text("enabled");
				                thisLink.text("disable");
			                }
		                });	 		                    	     
                    }
                }).dialog("open"); 
            }  
            else if(action == "disable") {            
                $("#dialog_disable_account")
                .dialog('option', 'buttons', {
                    "Cancel": function() {
                        $(this).dialog("close");
                    },
                    "Save": function() {
                        $(this).dialog("close");			                                             
                        if($("#change_state_type").val()=="disable") {                            
                            var loadingImg = template.find(".adding_loading");		
                            var rowContainer = template.find("#account_body");    	                               
                            loadingImg.find(".adding_text").text("Disabling....");	
                            loadingImg.show();  
                            rowContainer.hide();                                           
                            
                            $.ajax({
			                    data: "command=disableAccount&id="+accountId+"&response=json", 
			                    dataType: "json",
			                    success: function(json) {						        
			                        var jobId = json.disableaccountresponse.jobid;					                       
			                        var timerKey = "disableAccountJob"+jobId;
        								    
			                        $("body").everyTime(2000, timerKey, function() {
					                    $.ajax({
						                    data: "command=queryAsyncJobResult&jobId="+json.disableaccountresponse.jobid+"&response=json",
						                    dataType: "json",
						                    success: function(json) {										       						   
							                    var result = json.queryasyncjobresultresponse;
							                    if (result.jobstatus == 0) {
								                    return; //Job has not completed
							                    } else {											    
								                    $("body").stopTime(timerKey);
								                    if (result.jobstatus == 1) {
									                    // Succeeded				                    
        											    template.find("#account_state").text("disabled");
						                                thisLink.text("enable");
									                    loadingImg.hide();  
                                                        rowContainer.show();	                                                             
								                    } else if (result.jobstatus == 2) {										        
									                    $("#dialog_alert").html("<p>" + result.jobresult + "</p>").dialog("open");		
									                    loadingImg.hide();  
                                                        rowContainer.show();										   					    
								                    }
							                    }
						                    },
						                    error: function(XMLHttpRequest) {
							                    $("body").stopTime(timerKey);
							                    handleError(XMLHttpRequest);	
							                    loadingImg.hide();  
                                                rowContainer.show();									    
						                    }
					                    });
				                    }, 0);						    					
			                    },
			                    error: function(XMLHttpRequest) {							    
					                handleError(XMLHttpRequest);
					                loadingImg.hide();  
                                    rowContainer.show();								
			                    }
		                    });	                            
                        }
                        else { //"lock"                   
                            $.ajax({
					            data: "command=lockAccount&id="+accountId+"&response=json",
					            dataType: "json",
					            success: function(json) {							                
						            template.find("#account_state").text("locked");
						            thisLink.text("enable");
					            }
				            });		                            
                        }
                    }		           
                }).dialog("open");                 
            }                
            return false; //event.preventDefault() + event.stopPropagation()  		            
        });	    
	    
	    function accountJSONToTemplate(json, template) {   
	        (index++ % 2 == 0)? template.addClass("smallrow_even"): template.addClass("smallrow_odd");		    		    
		    var accountId = json.id;
		    var accountName = json.name;
		    template.attr("id", "account"+accountId).data("accountId", accountId).data("accountName", accountName);		    		    				    
		    template.find("#account_role").text(toRole(json.accounttype));
		    template.find("#account_accountid").text(json.id);
		    template.find("#account_accountname").text(accountName);
		    template.find("#account_domain").text(json.domain);
		    template.find("#account_vms").text(json.vmtotal);
		    template.find("#account_ips").text(json.iptotal);
		    template.find("#account_received").text(convertBytes(json.receivedbytes));
		    template.find("#account_sent").text(convertBytes(json.sentbytes));
		    template.find("#account_state").text(json.state);
		    		    
		    if(accountId == systemAccountId || accountId == adminAccountId) {
		        template.find("#action_links").hide();
		    } else {			        
		        var accountChangeState = template.find("#account_change_state").attr("id", "account_change_state_"+accountId).data("accountId", accountId).data("accountName",accountName);    
		        if(json.state=="enabled") {	
		            accountChangeState.text("disable");
		        	
		            /*           
		            template.find("#account_change_state").text("disable").bind("click", function() {		                
		                $("#dialog_disable_account")
		                .dialog('option', 'buttons', {
		                    "Cancel": function() {
		                        $(this).dialog("close");
		                    },
		                    "Save": function() {
		                        $(this).dialog("close");			                                             
		                        if($("#change_state_type").val()=="disable") {                            
		                            var loadingImg = template.find(".adding_loading");		
	                                var rowContainer = template.find("#account_body");    	                               
                                    loadingImg.find(".adding_text").text("Disabling....");	
                                    loadingImg.show();  
                                    rowContainer.hide();                                           
		                            
		                            $.ajax({
					                    data: "command=disableAccount&id="+accountId+"&response=json", 
					                    dataType: "json",
					                    success: function(json) {						        
					                        var jobId = json.disableaccountresponse.jobid;					                       
					                        var timerKey = "disableAccountJob"+jobId;
                								    
					                        $("body").everyTime(2000, timerKey, function() {
							                    $.ajax({
								                    data: "command=queryAsyncJobResult&jobId="+json.disableaccountresponse.jobid+"&response=json",
								                    dataType: "json",
								                    success: function(json) {										       						   
									                    var result = json.queryasyncjobresultresponse;
									                    if (result.jobstatus == 0) {
										                    return; //Job has not completed
									                    } else {											    
										                    $("body").stopTime(timerKey);
										                    if (result.jobstatus == 1) {
											                    // Succeeded				                    
                											    template.find("#account_state").text("disabled");
								                                template.find("#account_change_state").text("enable");
											                    loadingImg.hide();  
                                                                rowContainer.show();	                                                             
										                    } else if (result.jobstatus == 2) {										        
											                    $("#dialog_alert").html("<p>" + result.jobresult + "</p>").dialog("open");		
											                    loadingImg.hide();  
                                                                rowContainer.show();										   					    
										                    }
									                    }
								                    },
								                    error: function(XMLHttpRequest) {
									                    $("body").stopTime(timerKey);
									                    handleError(XMLHttpRequest);	
									                    loadingImg.hide();  
                                                        rowContainer.show();									    
								                    }
							                    });
						                    }, 0);						    					
					                    },
					                    error: function(XMLHttpRequest) {							    
							                handleError(XMLHttpRequest);
							                loadingImg.hide();  
                                            rowContainer.show();								
					                    }
				                    });	                            
		                        }
		                        else { //"lock"                   
		                            $.ajax({
							            data: "command=lockAccount&id="+accountId+"&response=json",
							            dataType: "json",
							            success: function(json) {							                
								            template.find("#account_state").text("locked");
								            template.find("#account_change_state").text("enable");
							            }
						            });		                            
		                        }
		                    }		           
		                }).dialog("open");
		            });
		            */
		            
		            
		        }
		        else if(json.state=="disabled" || json.state=="locked") {
		            accountChangeState.text("enable");
		            
		            /*
		            template.find("#account_change_state").text("enable").data("accountId", json.id).bind("click", function() {
    		            $("#dialog_confirmation")
    		            .html("<p>Please confirm you want to enable account: </b>" + accountName + "</b></p>")
    		            .dialog('option', 'buttons', {
    		                "Cancel": function() {
    		                    $(this).dialog("close");		     
    		                },
    		                "Yes": function() { 		                    
    		                    $(this).dialog("close");	    		                    
    		                    $.ajax({
						            data: "command=enableAccount&id="+accountId+"&response=json",
						            dataType: "json",
						            success: function(json) {							                
							            template.find("#account_state").text("enabled");
							            template.find("#account_change_state").text("disable");
						            }
					            });	 		                    	     
    		                }
    		            }).dialog("open");    		            
		            });
		            */
		            
		        }		        
		    }
		    
			if (json.accounttype == 0) {
				template.find("#account_resource_limits").show().data("account",json.name).data("domainId",json.domainid).bind("click", function() {
					var domainId = $(this).data("domainId");
					var account = $(this).data("account");
					$.ajax({
						cache: false,				
						data: "command=listResourceLimits&domainid="+domainId+"&account="+account+"&response=json",
						dataType: "json",
						success: function(json) {
							var limits = json.listresourcelimitsresponse.resourcelimit;		
							var preInstanceLimit, preIpLimit, preDiskLimit, preSnapshotLimit, preTemplateLimit = -1;
							if (limits != null) {	
								for (var i = 0; i < limits.length; i++) {
									var limit = limits[i];
									switch (limit.resourcetype) {
										case "0":
											preInstanceLimit = limit.max;
											$("#dialog_resource_limits #limits_vm").val(limit.max);
											break;
										case "1":
											preIpLimit = limit.max;
											$("#dialog_resource_limits #limits_ip").val(limit.max);
											break;
										case "2":
											preDiskLimit = limit.max;
											$("#dialog_resource_limits #limits_volume").val(limit.max);
											break;
										case "3":
											preSnapshotLimit = limit.max;
											$("#dialog_resource_limits #limits_snapshot").val(limit.max);
											break;
										case "4":
											preTemplateLimit = limit.max;
											$("#dialog_resource_limits #limits_template").val(limit.max);
											break;
									}
								}
							}	
							$("#dialog_resource_limits")
							.dialog('option', 'buttons', { 
								"Cancel": function() { 
									$(this).dialog("close"); 
								},
								"Save": function() { 	
									// validate values
									var isValid = true;					
									isValid &= validateNumber("Instance Limit", $("#dialog_resource_limits #limits_vm"), $("#dialog_resource_limits #limits_vm_errormsg"), -1, 32000, false);
									isValid &= validateNumber("Public IP Limit", $("#dialog_resource_limits #limits_ip"), $("#dialog_resource_limits #limits_ip_errormsg"), -1, 32000, false);
									isValid &= validateNumber("Disk Volume Limit", $("#dialog_resource_limits #limits_volume"), $("#dialog_resource_limits #limits_volume_errormsg"), -1, 32000, false);
									isValid &= validateNumber("Snapshot Limit", $("#dialog_resource_limits #limits_snapshot"), $("#dialog_resource_limits #limits_snapshot_errormsg"), -1, 32000, false);
									isValid &= validateNumber("Template Limit", $("#dialog_resource_limits #limits_template"), $("#dialog_resource_limits #limits_template_errormsg"), -1, 32000, false);
									if (!isValid) return;
																
									var instanceLimit = trim($("#dialog_resource_limits #limits_vm").val());
									var ipLimit = trim($("#dialog_resource_limits #limits_ip").val());
									var diskLimit = trim($("#dialog_resource_limits #limits_volume").val());
									var snapshotLimit = trim($("#dialog_resource_limits #limits_snapshot").val());
									var templateLimit = trim($("#dialog_resource_limits #limits_template").val());
															
									$(this).dialog("close"); 
									if (instanceLimit != preInstanceLimit) {
										updateResourceLimit(domainId, account, 0, instanceLimit);
									}
									if (ipLimit != preIpLimit) {
										updateResourceLimit(domainId, account, 1, ipLimit);
									}
									if (diskLimit != preDiskLimit) {
										updateResourceLimit(domainId, account, 2, diskLimit);
									}
									if (snapshotLimit != preSnapshotLimit) {
										updateResourceLimit(domainId, account, 3, snapshotLimit);
									}
									if (templateLimit != preTemplateLimit) {
										updateResourceLimit(domainId, account, 4, templateLimit);
									}
								} 
							}).dialog("open");
						}
					});
					return false;
				});
			} else {
				template.find("#account_resource_limits").hide();
			}
	    }
		    
        function listAccounts() {      
            var submenuContent = $("#submenu_content_account");
            
            var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();	
			    var role = submenuContent.find("#advanced_search #adv_search_role").val();
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));				
				if (trim(role).length > 0) 
					moreCriteria.push("&accounttype="+role);	
				commandString = "command=listAccounts&page="+currentPage+moreCriteria.join("")+"&response=json";  
			} else {     
			    var moreCriteria = [];		
			    if(domainId!=null)
			        moreCriteria.push("&domainid="+domainId);			   
			    var searchInput = submenuContent.find("#search_input").val();         
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listAccounts&page="+currentPage+moreCriteria.join("")+"&keyword="+searchInput+"&response=json"
                else
                    commandString = "command=listAccounts&page="+currentPage+moreCriteria.join("")+"&response=json";          
            }   
            
            //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listaccountsresponse", "account", $("#account_template"), accountJSONToTemplate);    
	    }
	    
	    submenuContentEventBinder($("#submenu_content_account"), listAccounts);	   
	    	    
	    var index;
	    currentPage = 1;	
	    listAccounts();
		
		
	});
}