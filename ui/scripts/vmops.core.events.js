function showEventsTab(showEvents) {
    var currentSubMenu = $("#submenu_events");
    
    var initializeEventTab = function(isAdmin) {   
        var eIndex = 0;		        
        function eventJSONToTemplate(json, template) {           
            if (eIndex++ % 2 == 0) {
			    template.addClass("smallrow_odd");
		    } else {
			    template.addClass("smallrow_even");
		    }		    
		    template.find("#event_account").text(json.account);
		    template.find("#event_username").text(json.username);
		    template.find("#event_type").text(json.type);
		    template.find("#event_level").text(json.level);
		    template.find("#event_desc").text(json.description);  
		    
		    var created = new Date();
		    created.setISO8601(json.created);
		    var showDate = created.format("m/d/Y H:i:s");
		    template.find("#event_date").text(showDate);						    
        }
      
        function listEvents() {      
            var submenuContent = $("#submenu_content_events");  
            
            var commandString;            
			var advanced = submenuContent.find("#search_button").data("advanced");                    
			if (advanced != null && advanced) {		
			    var type = submenuContent.find("#advanced_search #adv_search_type").val();	
			    var level = submenuContent.find("#advanced_search #adv_search_level").val();	
			    var account = submenuContent.find("#advanced_search #adv_search_account").val();
			    var moreCriteria = [];								
				if (type!=null && trim(type).length > 0) 
					moreCriteria.push("&type="+encodeURIComponent(trim(type)));		
			    if (level!=null && trim(level).length > 0) 
					moreCriteria.push("&level="+encodeURIComponent(trim(level)));					
				if (account!=null && account.length > 0) 
					moreCriteria.push("&account="+account);		
				commandString = "command=listEvents&page="+currentPage+moreCriteria.join("")+"&response=json";   
			} else {          	 
                var searchInput = submenuContent.find("#search_input").val();            
                if (searchInput != null && searchInput.length > 0) 
                    commandString = "command=listEvents&page="+currentPage+"&keyword="+searchInput+"&response=json"
                else
                    commandString = "command=listEvents&page="+currentPage+"&response=json";	
            } 
            
            //listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
            listItems(submenuContent, commandString, "listeventsresponse", "event", $("#event_template"), eventJSONToTemplate);    
		}	
	    
	    submenuContentEventBinder($("#submenu_content_events"), listEvents);
	    	    
	    if(isAdmin) {	        
	        $("#submenu_events").bind("click", function(event) {
	            event.preventDefault();
	            
				$(this).removeClass().addClass("submenu_links_on");
				currentSubMenu.removeClass().toggleClass("submenu_links_off");
			    currentSubMenu = $(this);
			    
				var submenuContent = $("#submenu_content_events").show();
				$("#submenu_content_alerts").hide();				
				
				submenuContent.find("#adv_search_account_li").show();  
					
				currentPage = 1;			
				listEvents();    
	        });	   
	         
	        $(".submenu_links, #submenu_content_alerts, #alert_template").show(); 
	        $("#event_username_header, #event_username_container, #event_account_header, #event_account_container").show();	 	
	                    
	        if (showEvents == null || showEvents) {
				currentSubMenu = $("#submenu_alerts");
				$("#submenu_events").click();  //Default tab is Events when login as admin	  
			} else {
				currentSubMenu = $("#submenu_events");
				$("#submenu_alerts").click();
			}
	    }
	    else {
	        $(".submenu_links, #submenu_content_alerts, #alert_template").hide();
	        $("#event_username_header, #event_username_container, #event_account_header, #event_account_container").hide();	 
			$("#submenu_content_events").show();
	        listEvents();    
	    }   
    }
    

	// Manage Events 
	mainContainer.load("content/tab_events.html", function() {		
	    if (isAdmin()) {				
			// *** Alerts (begin) ***
			var alertIndex = 0;
			function alertJSONToTemplate(json, template) {           
                if (alertIndex++ % 2 == 0) {
			        template.addClass("smallrow_odd");
		        } else {
			        template.addClass("smallrow_even");
		        }		    
    		   		    
		        template.find("#alert_type").text((toAlertType(json.type)));
			    template.find("#alert_desc").text(json.description);
    			
			    var sent = new Date();
			    sent.setISO8601(json.sent);
			    var showDate = sent.format("m/d/Y H:i:s");
			    template.find("#alert_sent").text(showDate);		    					    
            }
			
			function listAlerts() {		
			    var submenuContent = $("#submenu_content_alerts");
            	   
            	var commandString;            
			    var advanced = submenuContent.find("#search_button").data("advanced");                    
			    if (advanced != null && advanced) {		
			        var type = submenuContent.find("#advanced_search #adv_search_type").val();				       
			        var moreCriteria = [];								
				    if (type!=null && trim(type).length > 0) 
					    moreCriteria.push("&type="+encodeURIComponent(trim(type)));			   
				    commandString = "command=listAlerts&page="+currentPage+moreCriteria.join("")+"&response=json";     
			    } else {            
            	    var searchInput = submenuContent.find("#search_input").val();            
                    if (searchInput != null && searchInput.length > 0) 
                        commandString = "command=listAlerts&page="+currentPage+"&keyword="+searchInput+"&response=json"
                    else
                        commandString = "command=listAlerts&page="+currentPage+"&response=json";    
                }
            	
            	//listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate);         
                listItems(submenuContent, commandString, "listalertsresponse", "alert", $("#alert_template"), alertJSONToTemplate);              	
			}			
			
			submenuContentEventBinder($("#submenu_content_alerts"), listAlerts);
				
			$("#submenu_alerts").bind("click", function(event) {
			    event.preventDefault();         			   			
				
				$(this).removeClass().addClass("submenu_links_on");
				currentSubMenu.removeClass().toggleClass("submenu_links_off");
				currentSubMenu = $(this);
				
				var submenuContent = $("#submenu_content_alerts").show();
				$("#submenu_content_events").hide();
				
				currentPage = 1;
				listAlerts();
			});					
			// *** Alerts (end) ***
			
			// *** Events (begin) ***
			initializeEventTab(true);	
			// *** Events (end) ***
			
		
	    } else {
		   
	        // *** Events (begin) ***	    
	        initializeEventTab(false);	
	        // *** Events (end) ***	    
		    
	    }
    });
}