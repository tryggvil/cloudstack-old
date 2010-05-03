function showAccountsTab(domainId) {
	// Manage Events 
	mainContainer.load("content/tab_accounts.html", function() {	  
	    
	    function accountJSONToTemplate(json, template) {   
	        (index++ % 2 == 0)? template.addClass("smallrow_even"): template.addClass("smallrow_odd");		    		    
		    template.attr("id", "account"+json.id);		    		    				    
		    template.find("#account_role").text(toRole(json.accounttype));
		    template.find("#account_accountid").text(json.id);
		    template.find("#account_accountname").text(json.name);
		    template.find("#account_domain").text(json.domain);
		    template.find("#account_vms").text(json.vmtotal);
		    template.find("#account_ips").text(json.ipalloc);
		    template.find("#account_received").text(convertBytes(json.receivedbytes));
		    template.find("#account_sent").text(convertBytes(json.sentbytes));
		    template.find("#account_disabled").text(((json.isdisabled == "true") ? "Yes" : "No"));
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