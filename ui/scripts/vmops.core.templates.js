function showTemplatesTab() {
	// Manage Templates 
	mainContainer.load("content/tab_templates.html", function() {
	
	    // *** Template (begin) ***	
		activateDialog($("#dialog_edit_template").dialog({ 
			width:450,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_add_template").dialog({ 
			width:450,
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
					var templateSelect = $("#dialog_add_template #add_template_os_type").empty();
					var isoSelect = $("#dialog_add_iso #add_iso_os_type").empty();
					for (var i = 0; i < types.length; i++) {
						var html = "<option value='" + types[i].id + "'>" + types[i].description + "</option>";
						templateSelect.append(html);
						isoSelect.append(html);
					}
				}	
			}
		});
		
		
		$("#template_action_new").show();
				
		$("#template_action_new").bind("click", function(event) {
			$("#dialog_add_template")
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Create": function() { 					
					// validate values
					var isValid = true;					
					isValid &= validateString("Name", $("#add_template_name"), $("#add_template_name_errormsg"));
					isValid &= validateString("Display Text", $("#add_template_display_text"), $("#add_template_display_text_errormsg"));
					isValid &= validateString("URL", $("#add_template_url"), $("#add_template_url_errormsg"));			
					if (!isValid) return;
											
					var name = trim($("#add_template_name").val());
					var desc = trim($("#add_template_display_text").val());
					var url = trim($("#add_template_url").val());					
					var isPublic = $("#template_type").val();	//is public or not										
					var format = $("#add_template_format").val();					
					var password = $("#dialog_add_template #add_template_password").val();		
					var osType = $("#dialog_add_template #add_template_os_type").val();
					
					var submenuContent = $("#submenu_content_template");						
				    var template = $("#vm_template_template").clone(true);					   	
				    $(this).dialog("close");						   
								
					$.ajax({
						data: "command=registerTemplate&name="+encodeURIComponent(name)+"&displayText="+encodeURIComponent(desc)+"&url="+encodeURIComponent(url)+"&isPublic="+isPublic+"&format="+format+"&passwordEnabled="+password+"&osTypeId="+osType+"&response=json",
						dataType: "json",
						success: function(json) {
							var result = json.registertemplateresponse;	
							templateJSONToTemplate(result.template[0], template);
							submenuContent.find("#grid_content").prepend(template.fadeIn("slow"));	 
							changeGridRowsTotal(submenuContent.find("#grid_rows_total"), 1);                               			                  				
						}
					});
				} 
			}).dialog("open");
			return false;
		});
				
		function templateJSONToTemplate(json, template) {
			template.attr("id", "template"+json.id);
			if (index++ % 2 == 0) {
				template.addClass("dbsmallrow_odd");
			} else {
				template.addClass("dbsmallrow_even");
			}
			template.data("public", json.ispublic);
			template.find("#template_id").text(json.id);
			template.find("#template_name").text(json.name);
			template.find("#template_display_text").text(json.displaytext);
			var status = "Ready";
			if (json.isready == "false") {
				status = json.templatestatus;
			}
			template.find("#template_status").text(status);
			
			var created = new Date();
			created.setISO8601(json.created);
			var showDate = created.format("m/d/Y H:i:s");
			template.find("#template_created").text(showDate);
			/*
			if (json.bits == "32") {
				template.find("#template_bit").attr("src", "images/32bit_icon.gif");
			}
			if (json.requireshvm == "false") {
				template.find("#template_hvm").attr("src", "images/hvm_nonselectedicon.gif");
			}
			*/
			if (json.passwordenabled == "false") {
				template.find("#template_password").attr("src", "images/password_nonselectedicon.gif");
			}
			
			template.find("#template_ostype").text(json.ostypename);			
			
			// disable edit/delete of public templates for Users
			if (isUser() && json.ispublic == "true") {
				template.find("#template_crud").hide();
			}
			template.find("#template_edit").data("templateId", json.id).bind("click", function(event) {
				event.preventDefault();
				var id = $(this).data("templateId");
				var template = $("#template"+id);
				var name = template.find("#template_name").text();
				var displayText = template.find("#template_display_text").text();
				$("#dialog_edit_template #edit_template_name").val(name);
				$("#dialog_edit_template #edit_template_display_text").val(displayText);
								
				$("#dialog_edit_template")
				.dialog('option', 'buttons', { 
					"Cancel": function() { 
						$(this).dialog("close"); 
					},
					"Save": function() { 						
						// validate values
					    var isValid = true;					
					    isValid &= validateString("Name", $("#edit_template_name"), $("#edit_template_name_errormsg"));
					    isValid &= validateString("Display Text", $("#edit_template_display_text"), $("#edit_template_display_text_errormsg"));			
					    if (!isValid) return;
													
						var name = trim($("#edit_template_name").val());
						var desc = trim($("#edit_template_display_text").val());									
												
						var dialogBox = $(this);
						dialogBox.dialog("close");
						$.ajax({
							data: "command=updateTemplate&id="+id+"&name="+encodeURIComponent(name)+"&displayText="+encodeURIComponent(desc)+"&response=json",
							dataType: "json",
							success: function(json) {								    					
							    template.find("#template_name").text(name);
								template.find("#template_display_text").text(desc);								
							}
						});
					} 
				}).dialog("open");
			});
			template.find("#template_delete").data("templateId", json.id).data("templateName", json.name).bind("click", function(event) {
				var id = $(this).data("templateId");
				var name = $(this).data("templateName");
				$("#dialog_confirmation")
				.html("<p>Please confirm you want to delete your template <b>"+name+"</b>.</p>")
				.dialog('option', 'buttons', { 
					"Cancel": function() { 
						$(this).dialog("close"); 
					},
					"Confirm": function() { 
						var dialogBox = $(this);
						$(this).dialog("close");
						$.ajax({
							data: "command=deleteTemplate&id="+id+"&response=json",
							dataType: "json",
							success: function(json) {
								$("#template"+id).slideUp("slow", function() { $(this).remove() });
							}
						});
					} 
				}).dialog("open");
			});
		}
				
		function listTemplates() { 
		    var submenuContent = $("#submenu_content_template");	
								
			var type =  $("#template_type").val();	//is public or not
            var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();				 
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));				
				commandString = "command=listTemplates&page="+currentPage+moreCriteria.join("")+"&ispublic="+type+"&response=json";    
			} else {          
                var searchInput = $("#submenu_content_template #search_input").val();  //search button          
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listTemplates&page="+currentPage+"&ispublic="+type+"&keyword="+searchInput+"&response=json";
                else
                    commandString = "command=listTemplates&page="+currentPage+"&ispublic="+type+"&response=json"; 
            }

            //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listtemplatesresponse", "template", $("#vm_template_template"), templateJSONToTemplate);  			
		}
		
		submenuContentEventBinder($("#submenu_content_template"), listTemplates);
				
		$("#template_type").bind("change", function(event){		  
		    currentPage=1; 
		    event.preventDefault();   
		    listTemplates();
		});
		
		$("#submenu_template").bind("click",function(event){	
		    event.preventDefault();    
		    
		    $(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);  	
				
			var submenuContent = $("#submenu_content_template").show();
			$("#submenu_content_iso").hide();   		    
		    
		    currentPage=1;	 		    
		    listTemplates();
		});
				
		// *** Template (end) ***
		
			
		
	    // *** ISO (begin) ***	
		activateDialog($("#dialog_edit_iso").dialog({ 
			width:450,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		activateDialog($("#dialog_add_iso").dialog({ 
			width:450,
			autoOpen: false,
			modal: true,
			zIndex: 2000
		}));
		
		$("#iso_action_new").show();		
		
		$("#iso_action_new").bind("click", function(event) {
			$("#dialog_add_iso")
			.dialog('option', 'buttons', { 
				"Cancel": function() { 
					$(this).dialog("close"); 
				},
				"Create": function() { 					
					// validate values
					var isValid = true;					
					isValid &= validateString("Name", $("#add_iso_name"), $("#add_iso_name_errormsg"));
					isValid &= validateString("Display Text", $("#add_iso_display_text"), $("#add_iso_display_text_errormsg"));
					isValid &= validateString("URL", $("#add_iso_url"), $("#add_iso_url_errormsg"));			
					if (!isValid) return;
											
					var name = trim($("#add_iso_name").val());
					var desc = trim($("#add_iso_display_text").val());
					var url = trim($("#add_iso_url").val());	
					var isPublic = $("#iso_type").val();	//is public or not
					var osType = $("#add_iso_os_type").val();
					var bootable = $("#add_iso_bootable").val();
					
				    var submenuContent = $("#submenu_content_iso");						
				    var template = $("#vm_iso_template").clone(true);					    
				    $(this).dialog("close");									    
				    				
					$.ajax({
						data: "command=registerIso&name="+encodeURIComponent(name)+"&displayText="+encodeURIComponent(desc)+"&url="+encodeURIComponent(url)+"&isPublic="+isPublic+"&osTypeId="+osType+"&bootable="+bootable+"&response=json",
						dataType: "json",
						success: function(json) {						    
							var result = json.registerisoresponse;
							isoJSONToTemplate(result.iso[0], template);  
							submenuContent.find("#grid_content").prepend(template.fadeIn("slow"));	
							changeGridRowsTotal(submenuContent.find("#grid_rows_total"), 1);                            												
						}
					});
				} 
			}).dialog("open");
			return false;
		});
			
		//*** isoJSONToTemplate (begin) *******************************
		function isoJSONToTemplate(json, template) {
		    template.attr("id", "iso"+json.id);
			if (index++ % 2 == 0) {
				template.addClass("dbsmallrow_odd");
			} else {
				template.addClass("dbsmallrow_even");
			}
			template.data("public", json.ispublic);
			template.find("#iso_id").text(json.id);
			template.find("#iso_name").text(json.name);
			template.find("#iso_display_text").text(json.displaytext);
						
            var status = "Ready";
			if (json.isready == "false") {
				status = json.templatestatus;
			}
			template.find("#iso_status").text(status);
			template.find("#iso_bootable").text((json.bootable == "true") ? "Yes" : "No");
			
			var created = new Date();
			created.setISO8601(json.created);
			var showDate = created.format("m/d/Y H:i:s");
			template.find("#iso_created").text(showDate);
			
			// disable edit/delete of public isos for Users
			if (isUser() && json.ispublic == "true") {
				template.find("#iso_crud").hide();
			}
			template.find("#iso_edit").data("isoId", json.id).bind("click", function(event) {
				event.preventDefault();
				var id = $(this).data("isoId");
				var iso = $("#iso"+id);
				var name = template.find("#iso_name").text();
				var displayText = template.find("#iso_display_text").text();
				$("#dialog_edit_iso #edit_iso_name").val(name);
				$("#dialog_edit_iso #edit_iso_display_text").val(displayText);
								
				$("#dialog_edit_iso")
				.dialog('option', 'buttons', { 
					"Cancel": function() { 
						$(this).dialog("close"); 
					},
					"Save": function() { 						
						// validate values
					    var isValid = true;					
					    isValid &= validateString("Name", $("#edit_iso_name"), $("#edit_iso_name_errormsg"));
					    isValid &= validateString("Display Text", $("#edit_iso_display_text"), $("#edit_iso_display_text_errormsg"));			
					    if (!isValid) return;
												
						var name = trim($("#edit_iso_name").val());
						var desc = trim($("#edit_iso_display_text").val());
												
						var dialogBox = $(this);
						dialogBox.dialog("close");
						$.ajax({
							data: "command=updateIso&id="+id+"&name="+encodeURIComponent(name)+"&displayText="+encodeURIComponent(desc)+"&response=json",
							dataType: "json",
							success: function(json) {							    
								template.find("#iso_name").text(name);
								template.find("#iso_display_text").text(desc);								
							}
						});
					} 
				}).dialog("open");
			});
			template.find("#iso_delete").data("isoId", json.id).data("isoName", json.name).bind("click", function(event) {			    
				var id = $(this).data("isoId");
				var name = $(this).data("isoName");
				$("#dialog_confirmation")
				.html("<p>Please confirm you want to delete your iso <b>"+name+"</b>.</p>")
				.dialog('option', 'buttons', { 
					"Cancel": function() { 
						$(this).dialog("close"); 
					},
					"Confirm": function() { 					    
						var dialogBox = $(this);
						$(this).dialog("close");
						$.ajax({
							data: "command=deleteIso&id="+id+"&response=json",
							dataType: "json",
							success: function(json) {							    
								$("#iso"+id).slideUp("slow", function() { $(this).remove() });
							}
						});
					} 
				}).dialog("open");
			});
		}
		//*** isoJSONToTemplate (end) *******************************
			
		function listIsos() {		
		    var submenuContent = $("#submenu_content_iso");		   
        	    		
			var type = $("#iso_type").val(); //is public or not			
			var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var name = submenuContent.find("#advanced_search #adv_search_name").val();				  
			    var moreCriteria = [];								
				if (name!=null && trim(name).length > 0) 
					moreCriteria.push("&name="+encodeURIComponent(trim(name)));			
				commandString = "command=listIsos&page="+currentPage+moreCriteria.join("")+"&ispublic="+type+"&response=json";    
			} else {          
			    var searchInput = $("#submenu_content_iso #search_input").val(); //keyword  				    
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listIsos&page="+currentPage+"&ispublic="+type+"&keyword="+searchInput+"&response=json"
                else
                    commandString = "command=listIsos&page="+currentPage+"&ispublic="+type+"&response=json";  
            }			
		
		    //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listisosresponse", "iso", $("#vm_iso_template"), isoJSONToTemplate);  
		}
		
	    submenuContentEventBinder($("#submenu_content_iso"), listIsos);
		
		$("#iso_type").bind("change", function(event) {		
		    currentPage=1;  
		    event.preventDefault(); 
		    listIsos();
		});		
		
		$("#submenu_iso").bind("click", function(event) {	
		    event.preventDefault();  
		    
		    $(this).toggleClass("submenu_links_on").toggleClass("submenu_links_off");
			currentSubMenu.toggleClass("submenu_links_off").toggleClass("submenu_links_on");
			currentSubMenu = $(this);  		
			
			var submenuContent = $("#submenu_content_iso").show();
			$("#submenu_content_template").hide();
			
			currentPage=1;	   		    
		    listIsos();
		});		
		
		// *** ISO (end) ***	
		
		
		
		var currentSubMenu = $("#submenu_template");
		currentSubMenu.click();	
	});
}