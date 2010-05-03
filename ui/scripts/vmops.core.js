var g_mySession = null;
var g_role = null; // roles - root, domain-admin, ro-admin, user
var g_username = null;
var g_enableLogging = false;

// capabilities
var g_networkType = "vnet"; // vnet, vlan, direct
function getNetworkType() { return g_networkType; }
var g_hypervisorType = "kvm";
function getHypervisorType() { return g_hypervisorType; }


var g_logger = new Logger();
$(function() {
	if(g_enableLogging)
		g_logger.open();
});

// Test Tool.  Please comment this out or remove this when going production.
// This is intended to provide a simple test tool to create user accounts and
// domains.
function initializeTestTool() {
	$("#launch_test").click(function(event) {
		window.open('/client/test');
		return false;
	});
}

// Role Functions
function isAdmin() {
	return (g_role == 1);
}

function isUser() {
	return (g_role == 0);
}

function isDomainAdmin() {
	return (g_role == 2);
}

//deal with loading image before adding a new item
function beforeAddItem(submenuContent, template, dialogBox) {    		
	var loadingImg = template.find(".adding_loading");		
	var rowContainer = template.find("#row_container");    									        
    
    loadingImg.find(".adding_text").text("Adding....");	
    loadingImg.show();  
    rowContainer.hide();		            
     
    submenuContent.find("#grid_content").prepend(template);	 
    template.fadeIn("slow");		       			
	
	dialogBox.dialog("close");					    					
}

//deal with loading image, showing new row after adding a new item succesfully
function afterAddItemSuccessfully(submenuContent, template) {    
    var loadingImg = template.find(".adding_loading");	
    var rowContainer = template.find("#row_container");     
       
    loadingImg.hide(); 								                            
                                                    
    var createdSuccessfullyImg = template.find("#created_successfully").show();	
    createdSuccessfullyImg.find("#close_button").bind("click", function() {
        createdSuccessfullyImg.hide();
        rowContainer.show(); 
    });
    
    changeGridRowsTotal(submenuContent.find("#grid_rows_total"), 1);   
}

//listItems() function takes care of loading image, pagination
function listItems(submenuContent, commandString, jsonResponse1, jsonResponse2, template, fnJSONToTemplate ) {            
    if(currentPage==1)
        submenuContent.find("#prevPage_div").hide();
    else 
	    submenuContent.find("#prevPage_div").show();     
        
    submenuContent.find("#loading_gridtable").show();     
    submenuContent.find("#pagination_panel").hide();
    	           
    index = 0;	    
    $.ajax({
	    data: commandString,
	    dataType: "json",
	    success: function(json) {			        
	        //IF jsonResponse1=="listaccountsresponse", jsonResponse2=="account", THEN json[jsonResponse1][jsonResponse2] == json.listaccountsresponse.account
		    var items = json[jsonResponse1][jsonResponse2]; 
		    
		    var grid = submenuContent.find("#grid_content").empty();		    	    		
		    if (items != null && items.length > 0) {				        			        
			    for (var i = 0; i < items.length; i++) {
				    var newTemplate = template.clone(true);
				    fnJSONToTemplate(items[i], newTemplate); 
				    grid.append(newTemplate.show());						   
			    }
			    setGridRowsTotal(submenuContent.find("#grid_rows_total"), items.length);
			    if(items.length < pageSize)
			        submenuContent.find("#nextPage_div").hide();
			    else
			        submenuContent.find("#nextPage_div").show();
		    } else {				        
	            setGridRowsTotal(submenuContent.find("#grid_rows_total"), null);
	            submenuContent.find("#nextPage_div").hide();
	        }	
	        submenuContent.find("#loading_gridtable").hide();     
            submenuContent.find("#pagination_panel").show();	      		    						
	    },
		error: function(XMLHttpRequest) {	
		    submenuContent.find("#loading_gridtable").hide();     						
			handleError(XMLHttpRequest);					
		}
    });
}


//event binder
var currentPage = 1;
var pageSize = 50;  //consistent with server-side
function submenuContentEventBinder(submenuContent, listFunction) {       
    submenuContent.find("#nextPage").bind("click", function(event){	
        event.preventDefault();          
        currentPage++;        
        listFunction(); 
    });		
    
    submenuContent.find("#prevPage").bind("click", function(event){	
        event.preventDefault();           
        currentPage--;	              	    
        listFunction(); 
    });				
		
    submenuContent.find("#refresh").bind("click", function(event){
        event.preventDefault();         
        currentPage=1;       
        listFunction(); 
    });    
        
    submenuContent.find("#search_button").bind("click", function(event) {	       
        event.preventDefault();   
        currentPage = 1;           	        	
        listFunction();         
        $(this).data("advanced", false);
	    submenuContent.find("#advanced_search").hide();	
    });
	
	submenuContent.find("#search_input").bind("keypress", function(event) {		        
        if(event.keyCode == 13) {           
            event.preventDefault();   		        
	        submenuContent.find("#search_button").click();			     
	    }		    
    });   	    

    submenuContent.find("#advanced_search").bind("keypress", function(event) {		        
        if(event.keyCode == 13) {           
            event.preventDefault();   		        
	        submenuContent.find("#search_button").click();			     
	    }		    
    });		   
     
    submenuContent.find("#advanced_search_close").bind("click", function(event) {	    
        event.preventDefault();               
	    submenuContent.find("#search_button").data("advanced", false);	
        submenuContent.find("#advanced_search").hide();
    });	 
        
    submenuContent.find("#advanced_search_link").bind("click", function(event) {	
        event.preventDefault();   
		submenuContent.find("#search_button").data("advanced", true);
		
		var zoneSelect = submenuContent.find("#advanced_search #adv_search_zone");	
		if(zoneSelect.length>0) {
		    var zoneSelect = zoneSelect.empty();			
		    $.ajax({
			    data: "command=listZones&available=true&response=json",
			    dataType: "json",
			    success: function(json) {
				    var zones = json.listzonesresponse.zone;					
				    zoneSelect.append("<option value=''></option>"); 
				    if (zones != null && zones.length > 0) {
				        for (var i = 0; i < zones.length; i++) {
					        zoneSelect.append("<option value='" + zones[i].id + "'>" + zones[i].name + "</option>"); 
				        }
				    }
			    }
		    });
    		
		    var podSelect = submenuContent.find("#advanced_search #adv_search_pod").empty();		
		    if(podSelect.length>0 && isAdmin()) {		    
		        podSelect.empty();	
		        zoneSelect.bind("change", function(event) {
		            podSelect.empty();
			        var zoneId = $(this).val();
			        if (zoneId.length == 0) return false;
			        $.ajax({
				        data: "command=listPods&zoneId="+zoneId+"&response=json",
				        dataType: "json",
				        async: false,
				        success: function(json) {
					        var pods = json.listpodsresponse.pod;						
					        podSelect.append("<option value=''></option>"); 
					        if (pods != null && pods.length > 0) {
					            for (var i = 0; i < pods.length; i++) {
						            podSelect.append("<option value='" + pods[i].id + "'>" + pods[i].name + "</option>"); 
					            }
					        }
				        }
			        });
		        });		
		    }
		}
    	
    	var accountSelect = submenuContent.find("#advanced_search #adv_search_account");	    	
		if(accountSelect.length>0) {
		    accountSelect.empty();			
		    $.ajax({
			    data: "command=listAccounts&response=json",
			    dataType: "json",
			    success: function(json) {			        
				    var accounts = json.listaccountsresponse.account;				
				    accountSelect.append("<option value=''></option>"); 
				    if (accounts != null && accounts.length > 0) {
				        for (var i = 0; i < accounts.length; i++) {
					        accountSelect.append("<option value='" + accounts[i].name + "'>" + accounts[i].name + "</option>"); 
				        }
				    }
			    }
		    });
	    }
    		
        submenuContent.find("#advanced_search").show();
    });	  
}

// Validation functions
function validateNumber(label, field, errMsgField, min, max, isOptional) {
    var isValid = true;
    var errMsg = "";
    var value = field.val();       
	if (value != null && value.length != 0) {
		if(isNaN(value)) {
			errMsg = label + " must be a number";
			isValid = false;
		} else {
			if (min != null && value < min) {
				errMsg = label + " must be a value greater than or equal to " + min;
				isValid = false;
			}
			if (max != null && value > max) {
				errMsg = label + " must be a value less than or equal to " + max;
				isValid = false;
			}
		}
	} else if(isOptional!=true){  //required field
		errMsg = label + " is a required value";
		isValid = false;
	}
	showError(isValid, field, errMsgField, errMsg);	
	return isValid;
}

function validateString(label, field, errMsgField, isOptional) {  
    var isValid = true;
    var errMsg = "";
    var value = field.val();     
	if (isOptional!=true && (value == null || value.length == 0)) {	 //required field   
	    errMsg = label + " is a required value";	   
		isValid = false;		
	} 	
	else if (value!=null && value.length >= 255) {	    
	    errMsg = label + " must be less than 255 characters";	   
		isValid = false;		
	} 	
	showError(isValid, field, errMsgField, errMsg);	
	return isValid;
}

function validateIp(label, field, errMsgField, isOptional) {  
    var isValid = true;
    var errMsg = "";
    var value = field.val();       
	if (isOptional!=true && (value == null || value.length == 0)) {	 //required field   
	    errMsg = label + " is a required value";	   
		isValid = false;		
	} 		
	else {	    
	    if(value!=null && value.length>0) {
	        myregexp = /^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/;	   
            var isMatch = myregexp.test(value);
            if(!isMatch) {
	            errMsg = label + " should be like 75.52.126.11";	   
		        isValid = false;		
		    }
		}
	} 	
	showError(isValid, field, errMsgField, errMsg);	
	return isValid;
}

function validateCIDR(label, field, errMsgField, isOptional) {  
    var isValid = true;
    var errMsg = "";
    var value = field.val();       
	if (isOptional!=true && (value == null || value.length == 0)) {	 //required field   
	    errMsg = label + " is a required value";	   
		isValid = false;		
	} 		
	else {	    
	    if(value!=null && value.length>0) {
	        myregexp = /^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\/\d{1,2}$/;	   
            var isMatch = myregexp.test(value);
            if(!isMatch) {
	            errMsg = label + " should be like 192.168.1.0/24";	   
		        isValid = false;		
		    }
		}
	} 	
	showError(isValid, field, errMsgField, errMsg);	
	return isValid;
}

function validatePath(label, field, errMsgField, isOptional) {  
    var isValid = true;
    var errMsg = "";
    var value = field.val();       
	if (isOptional!=true && (value == null || value.length == 0)) {	 //required field   
	    errMsg = label + " is a required value";	   
		isValid = false;		
	} 		
	else {	    
	    if(value!=null && value.length>0) {
	        myregexp = /^\//;	   
            var isMatch = myregexp.test(value);
            if(!isMatch) {
	            errMsg = label + " should be like /aaa/bbb/ccc";	   
		        isValid = false;		
		    }
		}
	} 	
	showError(isValid, field, errMsgField, errMsg);	
	return isValid;
}

function showError(isValid, field, errMsgField, errMsg) {    
	if(isValid) {
	    errMsgField.text("").hide();
	    field.addClass("text").removeClass("error_text");
	}
	else {
	    errMsgField.text(errMsg).show();
	    field.removeClass("text").addClass("error_text");	
	}
}

// setter 
function setGridRowsTotal(field, gridRowsTotal) {   
    if(gridRowsTotal==null) {
        field.text("");
        return;
    }
 
    if(gridRowsTotal==1)
	    field.text(gridRowsTotal + " item");
	else
	    field.text(gridRowsTotal + " items");
} 

function changeGridRowsTotal(field, difference) {   
    var t = field.text();
    var oldTotal = 0;
    if(t.length>0 && t.indexOf(" item")!=-1) {      
        var s = t.substring(0, t.indexOf(" item"));
        if(!isNaN(s))
            oldTotal = parseInt(s);
    }
    var newTotal = oldTotal + difference;
    setGridRowsTotal(field, newTotal);
}


// others
function trim(val) {
    return val.replace(/^\s*/, "").replace(/\s*$/, "");
}

// FUNCTION: Handles AJAX error callbacks.  You can pass in an optional function to 
// handle errors that are not already handled by this method.  
function handleError(xmlHttp, handleErrorCallback) {
	// User Not authenticated
	if (xmlHttp.status == 401) {
		$("#dialog_session_expired").dialog("open");
	} else if (handleErrorCallback != undefined) {
		handleErrorCallback();
	} else {
		var start = xmlHttp.responseText.indexOf("h1") + 3;
		var end = xmlHttp.responseText.indexOf("</h1");
		var errorMsg = xmlHttp.responseText.substring(start, end);
		$("#dialog_error").html("<p><b>Encountered an error:</b></p><br/><p>"+errorMsg.substring(errorMsg.indexOf("-")+2)+"</p>").dialog("open");
	}
}

// FUNCTION: Adds a Dialog to the list of active Dialogs so that
// when you shift from one tab to another, we clean out the dialogs
var activeDialogs = new Array();
function activateDialog(dialog) {
	activeDialogs[activeDialogs.length] = dialog;
}
function removeDialogs() {
	for (var i = 0; i < activeDialogs.length; i++) {
		activeDialogs[i].remove();
	}
	activeDialogs = new Array();
}

function convertBytes(bytes) {
	if (bytes < 1024 * 1024) {
		return (bytes / 1024).toFixed(2) + " KB";
	} else if (bytes < 1024 * 1024 * 1024) {
		return (bytes / 1024 / 1024).toFixed(2) + " MB";
	} else if (bytes < 1024 * 1024 * 1024 * 1024) {
		return (bytes / 1024 / 1024 / 1024).toFixed(2) + " GB";
	} else {
		return (bytes / 1024 / 1024 / 1024 / 1024).toFixed(2) + " TB";
	}
}

function convertHz(hz) {
	if (hz < 1000) {
		return hz + " MHZ";
	} else {
		return (hz / 1000).toFixed(2) + " GHZ";
	} 
}

function toDayOfMonthDesp(dayOfMonth) {
    return "at day "+dayOfMonth+" every month";
}

function toDayOfWeekDesp(dayOfWeek) {
    if (dayOfWeek == "1")
        return "Mon.";
    else if (dayOfWeek == "2")
        return "Tue.";
    else if (dayOfWeek == "3")
        return "Wed.";
    else if (dayOfWeek == "4")
        return "Thu."
    else if (dayOfWeek == "5")
        return "Fri.";
    else if (dayOfWeek == "6")
        return "Sat.";
    else if (dayOfWeek == "7")
        return "Sun.";
}

function toRole(type) {
	if (type == "0") {
		return "User";
	} else if (type == "1") {
		return "Admin";
	}
}

function toAlertType(alertCode) {
	switch (alertCode) {
		case "0" : return "Capacity Threshold - Memory";
		case "1" : return "Capacity Threshold - CPU";
		case "2" : return "Capacity Threshold - Storage Used";
		case "3" : return "Capacity Threshold - Storage Allocated";
		case "4" : return "Capacity Threshold - Public IP";
		case "5" : return "Capacity Threshold - Private IP";
		case "6" : return "Monitoring - Host";
		case "7" : return "Monitoring - VM";
		case "8" : return "Monitoring - Domain Router";
		case "9" : return "Monitoring - Console Proxy";
		case "10" : return "Monitoring - Routing Host";
		case "11" : return "Monitoring - Storage";
		case "12" : return "Monitoring - Usage Server";
		case "13" : return "Monitoring - Management Server";
		case "14" : return "Migration - Domain Router";
		case "15" : return "Migration - Console Proxy";
		case "16" : return "VLAN";
	}
}

$(document).ready(function() {
	// We don't support IE6 at the moment, so let's just inform customers it won't work
	var IE6 = false /*@cc_on || @_jscript_version < 5.7 @*/;
	var gteIE7 = false /*@cc_on || @_jscript_version >= 5.7 @*/;

	// Disable IE6 browsers as UI does not support it
	if (IE6 == true) {
		alert("Only IE7, IE8, FireFox 3.x, Chrome, and Safari browsers are supported at this time.");
		return;
	}
	
	initializeTestTool();
	
	// We will be dropping all the main tab content into this container
	mainContainer = $("#maincontentarea");

	// Tab Links, dashboard is the initial active tab
	mainContainer.load("content/tab_dashboard.html");
	
	// Default AJAX Setup
	$.ajaxSetup({
		url: "/client/api",
		dataType: "json",
		cache: false,
		error: function(XMLHttpRequest) {
			handleError(XMLHttpRequest);
		},
		beforeSend: function(XMLHttpRequest) {
			if (g_mySession == $.cookie("JSESSIONID")) {
				return true;
			} else {
				$("#dialog_session_expired").dialog("open");
				return false;
			}
		}		
	});
	
	// LOGIN/LOGOUT
	// 'Enter' Key in any login form element = Submit click
	$("#logoutpage #loginForm").keypress(function(event) {
		if(event.keyCode == 13) {
			login();
		}
	});

	$("#logoutpage .loginbutton").bind("click", function(event) {
		login();
		return false;
	});
	
	$("#logoutaccount_link").bind("click", function(event) {
		$.ajax({
			data: "command=logout&response=json",
			dataType: "json",
			success: function(json) {
				logout();
			},
			error: function() {
				logout();
			},
			beforeSend : function(XMLHTTP) {
				return true;
			}
		});
	});
	
	// FUNCTION: logs the user out
	var activeTab = null;
	function logout() {
		g_mySession = null;
		g_username = null;
		$.cookie('JSESSIONID', null);
		$.cookie('username', null);
		$.cookie('role', null);
		$.cookie('networktype', null); 
		$("body").stopTime();

		// default is to redisplay the login page
		if (onLogoutCallback()) {
			$("#account_password").val("");
			$(".loginbutton_box p").hide();
			$("#logoutpage").show();
			$("body").css("background", "#4e4e4e url(images/logout_bg.gif) repeat-x top left");
			mainContainer.empty();
			$("#mainmaster").hide();
			
			var menuOnClass = "menutab_on";
			var menuOffClass = "menutab_off";
			var tab = null;
			if (isAdmin() || isDomainAdmin()) {
				tab = $("#menutab_dashboard_root");
				menuOnClass = "admin_menutab_on";
				menuOffClass = "admin_menutab_off";
			} else if (isUser()) {
				tab = $("#menutab_dashboard_user");
				menuOnClass = "menutab_on";
				menuOffClass = "menutab_off";
			}
			if (activeTab != null) {
				activeTab.removeClass(menuOnClass).addClass(menuOffClass);
				activeTab = null;
			}
			if (tab != null) {
				tab.removeClass(menuOffClass).addClass(menuOnClass);
			}
			g_role = null;
			$("#account_username").focus();
		}
	}
	
	// FUNCTION: logs the user in
	function login() {
		var username = encodeURIComponent($("#account_username").val());
		var password = encodeURIComponent($("#account_password").val());
		var domain = encodeURIComponent($("#account_domain").val());
		$.ajax({
			data: "command=login&username="+username+"&password="+password+"&domain="+domain+"&response=json",
			dataType: "json",
			async: false,
			success: function(json) {
				g_mySession = $.cookie('JSESSIONID');
				g_role = json.loginresponse.type;
				g_username = json.loginresponse.username;
				if (json.loginresponse.networktype != undefined) {
					g_networkType = json.loginresponse.networktype;
				}
				if (json.loginresponse.hypervisortype != undefined) {
					g_hypervisorType = json.loginresponse.hypervisortype;
				}
				$.cookie('networktype', g_networkType, { expires: 1});
				$.cookie('hypervisortype', g_hypervisorType, { expires: 1});
				$.cookie('username', g_username, { expires: 1});
				$.cookie('role', g_role, { expires: 1});
				// Set Role
				if (isUser()) {
					$(".loginbutton_box p").text("").hide();			
					$("#menutab_role_user #menutab_dashboard_user").click();
				} else if (isAdmin()) {
					$(".loginbutton_box p").text("").hide();			
					$("#menutab_role_root #menutab_dashboard_root").click();
				} else if (isDomainAdmin()) {
					$(".loginbutton_box p").text("").hide();			
					$("#menutab_role_domain #menutab_dashboard_root").click();
				} else {
				    $(".loginbutton_box p").text("Account type of '" + username + "' is neither user nor admin.").show();
				    return;
				}				
				
				$("#logoutpage").hide();
				$("body").css("background", "#FFF repeat top left");
				$("#mainmaster").show();				
			},
			error: function() {
				$("#account_password").val("");
				$("#logoutpage").show();				
				$(".loginbutton_box p").text("Your username/password does not match our records.").show();
				$("#account_username").focus();
			},
			beforeSend: function(XMLHttpRequest) {
				return true;
			}
		});
	}
	
	// Dialogs
	$("#dialog_confirmation").dialog({ 
		autoOpen: false,
		modal: true,
		zIndex: 2000
	});
	
	$("#dialog_info").dialog({ 
		autoOpen: false,
		modal: true,
		zIndex: 2000,
		buttons: { "OK": function() { $(this).dialog("close"); } }
	});
	
	$("#dialog_alert").dialog({ 
		autoOpen: false,
		modal: true,
		zIndex: 2000,
		buttons: { "OK": function() { $(this).dialog("close"); } }
	});
	$("#dialog_alert").siblings(".ui-widget-header").css("background", "url('/client/css/images/ui-bg_errorglass_30_ffffff_1x400.png') repeat-x scroll 50% 50% #393939");
	$("#dialog_alert").siblings(".ui-dialog-buttonpane").find(".ui-state-default").css("background", "url('/client/css/images/ui-bg_errorglass_30_ffffff_1x400.png') repeat-x scroll 50% 50% #393939");
	
	$("#dialog_error").dialog({ 
		autoOpen: false,
		modal: true,
		zIndex: 2000,
		buttons: { "Close": function() { $(this).dialog("close"); } }
	});
	$("#dialog_error").siblings(".ui-widget-header").css("background", "url('/client/css/images/ui-bg_errorglass_30_ffffff_1x400.png') repeat-x scroll 50% 50% #393939");
	$("#dialog_error").siblings(".ui-dialog-buttonpane").find(".ui-state-default").css("background", "url('/client/css/images/ui-bg_errorglass_30_ffffff_1x400.png') repeat-x scroll 50% 50% #393939");
	
	$("#dialog_session_expired").dialog({ 
		autoOpen: false,
		modal: true,
		zIndex: 2000,
		buttons: { "OK": function() { logout(); $(this).dialog("close"); } }
	});
	$("#dialog_session_expired").siblings(".ui-widget-header").css("background", "url('/client/css/images/ui-bg_errorglass_30_ffffff_1x400.png') repeat-x scroll 50% 50% #393939");
	$("#dialog_session_expired").siblings(".ui-dialog-buttonpane").find(".ui-state-default").css("background", "url('/client/css/images/ui-bg_errorglass_30_ffffff_1x400.png') repeat-x scroll 50% 50% #393939");
	
	$("#dialog_server_error").dialog({ 
		autoOpen: false,
		modal: true,
		zIndex: 2000,
		buttons: { "OK": function() { $(this).dialog("close"); } }
	});
	$("#dialog_server_error").siblings(".ui-widget-header").css("background", "url('/client/css/images/ui-bg_errorglass_30_ffffff_1x400.png') repeat-x scroll 50% 50% #393939");
	$("#dialog_server_error").siblings(".ui-dialog-buttonpane").find(".ui-state-default").css("background", "url('/client/css/images/ui-bg_errorglass_30_ffffff_1x400.png') repeat-x scroll 50% 50% #393939");
	
	// Menu Tabs
	$("#global_nav").bind("click", function(event) {
		var tab = $(event.target);
		var tabId = tab.attr("id");
		var menuOnClass = "menutab_on";
		var menuOffClass = "menutab_off";
		if (tabId == "menutab_dashboard_user" || tabId == "menutab_dashboard_root") {
			showDashboardTab();
		} else if (tabId == "menutab_vm") {
			showInstancesTab(tab.data("domainId"));
		} else if (tabId == "menutab_networking") {
			showNetworkingTab();
		} else if (tabId == "menutab_templates") {
			showTemplatesTab();
		} else if (tabId == "menutab_events") {
			showEventsTab(tab.data("showEvents"));
		} else if (tabId == "menutab_hosts") {
			showHostsTab();
	    } else if (tabId == "menutab_storage") {
			showStorageTab(tab.data("domainId"), tab.data("targetTab"));
		} else if (tabId == "menutab_accounts") {
			showAccountsTab(tab.data("domainId"));
		} else if (tabId == "menutab_domain") {
			showDomainsTab();
		} else if (tabId == "menutab_configuration") {
			showConfigurationTab();
		}
		
		if (isAdmin() || isDomainAdmin()) {
			menuOnClass = "admin_menutab_on";
			menuOffClass = "admin_menutab_off";
		} else if (isUser()) {
			menuOnClass = "menutab_on";
			menuOffClass = "menutab_off";
		}
		if (activeTab != null) {
			activeTab.removeClass(menuOnClass).addClass(menuOffClass); 
		}
		tab.removeClass(menuOffClass).addClass(menuOnClass);
		activeTab = tab;
		removeDialogs();
		return false;
	});
	
	// Dashboard Tab
	function showDashboardTab() {
		mainContainer.load("content/tab_dashboard.html", function() {
			$(".header_topright #header_username").text($.cookie("username"));
			
			if (isAdmin()) {
				var sessionExpired = false;
				var zones = null;
				var noZones = false;
				var noPods = true;
				$("#menutab_dashboard_root, #menutab_vm, #menutab_networking, #menutab_templates, #menutab_events, #menutab_hosts, #menutab_storage, #menutab_accounts, #menutab_domain").hide();							
				$.ajax({
					data: "command=listZones&available=true&response=json",
					dataType: "json",
					async: false,
					success: function(json) {
						zones = json.listzonesresponse.zone;
						var zoneSelect = $("#capacity_zone_select").empty();	
						if (zones != null && zones.length > 0) {
							for (var i = 0; i < zones.length; i++) {
								zoneSelect.append("<option value='" + zones[i].id + "'>" + zones[i].name + "</option>"); 								
								if(noPods) {
								    $.ajax({
						                data: "command=listPods&zoneId="+zones[i].id+"&response=json",
						                dataType: "json",
						                async: false,
						                success: function(json) {
							                var pods = json.listpodsresponse.pod;						
							                if (pods != null && pods.length > 0) {
            							        noPods = false;
            							        $("#menutab_dashboard_root, #menutab_vm, #menutab_networking, #menutab_templates, #menutab_events, #menutab_hosts, #menutab_storage, #menutab_accounts, #menutab_domain").show();							
							                }							
						                }
					                });
								}
							}
						} else {							
							noZones = true;
						}
					},
					error: function(xmlHttp) {
						if (xmlHttp.status == 401 || xmlHttp.status == 531) {
							sessionExpired = true;
							logout();
						} else if (xmlHttp.status == 530) {
							$("#dialog_error").html("<p>We have encountered an internal server error.  Please contact support.</p>").dialog("open");
						} else {
							if (handleErrorCallback != undefined) {
								handleErrorCallback();
							} else {
								var start = xmlHttp.responseText.indexOf("h1") + 3;
								var end = xmlHttp.responseText.indexOf("</h1");
								var errorMsg = xmlHttp.responseText.substring(start, end);
								$("#dialog_error").html("<p><b>Encountered an error:</b></p><br/><p>"+errorMsg.substring(errorMsg.indexOf("-")+2)+"</p>").dialog("open");
							}
						}
					},
					beforeSend: function(XMLHttpRequest) {
						return true;
					}	
				});
				if (sessionExpired) return false;
				if (noZones || noPods) {
					$("#tab_dashboard_user").hide();
					$("#menutab_role_user").hide();
					$("#menutab_role_root").show();
					$("#menutab_configuration").click();
					return false;
				}
				
				var capacities = null;
				$.ajax({
					cache: false,
					async: false,
					data: "command=listCapacity&response=json",
					dataType: "json",
					success: function(json) {
						capacities = json.listcapacityresponse.capacity;
					}
				});
				
				$("#capacity_pod_select").bind("change", function(event) {
					// Reset to Defaults
					$("#public_ip_total, #storage_total, #storage_alloc_total, #sec_storage_total, #memory_total, #cpu_total, #private_ip_total").text("N/A");
					$("#public_ip_used, #storage_used, #storage_alloc, #sec_storage_used, #memory_used, #cpu_used, #private_ip_used,").attr("style", "width:50%").text("N/A");
					$(".db_bargraph_barbox_safezone").attr("style", "width:0%");
					$(".db_bargraph_barbox_unsafezone").attr("style", "width:0%");
					
					if (capacities != null && capacities.length > 0) {
						var selectedZone = $("#capacity_zone_select option:selected").text();
						var selectedPod = $("#capacity_pod_select").val();
						for (var i = 0; i < capacities.length; i++) {
							var capacity = capacities[i];
							if (capacity.zonename == selectedZone) {
								// Public IPs
								if (capacity.type == "4") {
									$("#public_ip_used").attr("style", "width: " + ((parseFloat(capacity.percentused) < 50) ? "50%" : capacity.percentused + "%")).text("Used: " + capacity.capacityused + " / " + capacity.percentused + "%");
									$("#public_ip_total").text("Total: " + capacity.capacitytotal);
									var usedPercentage = parseInt(capacity.percentused);
									if (usedPercentage > 70) {
										$("#capacity_public_ip .db_bargraph_barbox_safezone").attr("style", "width:70%");
										if(usedPercentage <= 100) 										
										    $("#capacity_public_ip .db_bargraph_barbox_unsafezone").attr("style", "width:"+(usedPercentage - 70)+"%");
										else 
										    $("#capacity_public_ip .db_bargraph_barbox_unsafezone").attr("style", "width:30%");
									} else {
										$("#capacity_public_ip .db_bargraph_barbox_safezone").attr("style", "width:"+usedPercentage+"%");
									    $("#capacity_public_ip .db_bargraph_barbox_unsafezone").attr("style", "width:0%");
									}
								// Storage Used
								} else if (capacity.type == "2") {
									$("#storage_used").attr("style", "width: " + ((parseFloat(capacity.percentused) < 50) ? "50%" : capacity.percentused + "%")).text("Used: " + convertBytes(parseInt(capacity.capacityused)) + " / " + capacity.percentused + "%");
									$("#storage_total").text("Total: " + convertBytes(parseInt(capacity.capacitytotal)));
									var usedPercentage = parseInt(capacity.percentused);									
									if (usedPercentage > 70) {
										$("#capacity_storage .db_bargraph_barbox_safezone").attr("style", "width:70%");
										if(usedPercentage <= 100) 
										    $("#capacity_storage .db_bargraph_barbox_unsafezone").attr("style", "width:"+(usedPercentage - 70)+"%");
										else
										    $("#capacity_storage .db_bargraph_barbox_unsafezone").attr("style", "width:30%");
									} else {
										$("#capacity_storage .db_bargraph_barbox_safezone").attr("style", "width:"+usedPercentage+"%");
									    $("#capacity_storage .db_bargraph_barbox_unsafezone").attr("style", "width:0%");
									}
								// Storage Allocated
								} else if (capacity.type == "3") {
									$("#storage_alloc").attr("style", "width: " + ((parseFloat(capacity.percentused) < 50) ? "50%" : capacity.percentused + "%")).text("Used: " + convertBytes(parseInt(capacity.capacityused)) + " / " + capacity.percentused + "%");
									$("#storage_alloc_total").text("Total: " + convertBytes(parseInt(capacity.capacitytotal)));
									var usedPercentage = parseInt(capacity.percentused);
									if (usedPercentage > 70) {
										$("#capacity_storage_alloc .db_bargraph_barbox_safezone").attr("style", "width:70%");
										if(usedPercentage <= 100) 
										    $("#capacity_storage_alloc .db_bargraph_barbox_unsafezone").attr("style", "width:"+(usedPercentage - 70)+"%");
										else
										    $("#capacity_storage_alloc .db_bargraph_barbox_unsafezone").attr("style", "width:30%");
									} else {
										$("#capacity_storage_alloc .db_bargraph_barbox_safezone").attr("style", "width:"+usedPercentage+"%");
									    $("#capacity_storage_alloc .db_bargraph_barbox_unsafezone").attr("style", "width:0%");
									}
								// Secondary Storage
								} else if (capacity.type == "6") {
									$("#sec_storage_used").attr("style", "width: " + ((parseFloat(capacity.percentused) < 50) ? "50%" : capacity.percentused + "%")).text("Used: " + convertBytes(parseInt(capacity.capacityused)) + " / " + capacity.percentused + "%");
									$("#sec_storage_total").text("Total: " + convertBytes(parseInt(capacity.capacitytotal)));
									var usedPercentage = parseInt(capacity.percentused);
									if (usedPercentage > 70) {
										$("#capacity_sec_storage .db_bargraph_barbox_safezone").attr("style", "width:70%");
										if(usedPercentage <= 100) 
										    $("#capacity_sec_storage .db_bargraph_barbox_unsafezone").attr("style", "width:"+(usedPercentage - 70)+"%");
										else
										    $("#capacity_sec_storage .db_bargraph_barbox_unsafezone").attr("style", "width:30%");
									} else {
										$("#capacity_sec_storage .db_bargraph_barbox_safezone").attr("style", "width:"+usedPercentage+"%");
									    $("#capacity_sec_storage .db_bargraph_barbox_unsafezone").attr("style", "width:0%");
									}
								} else {
									if (capacity.podname == selectedPod) {
										// Memory
										if (capacity.type == "0") {
											$("#memory_used").attr("style", "width: " + ((parseFloat(capacity.percentused) < 50) ? "50%" : capacity.percentused + "%")).text("Used: " + convertBytes(parseInt(capacity.capacityused)) + " / " + capacity.percentused + "%");
											$("#memory_total").text("Total: " + convertBytes(parseInt(capacity.capacitytotal)));
											var usedPercentage = parseInt(capacity.percentused);
											if (usedPercentage > 70) {
												$("#capacity_memory .db_bargraph_barbox_safezone").attr("style", "width:70%");
												if(usedPercentage <= 100) 
												    $("#capacity_memory .db_bargraph_barbox_unsafezone").attr("style", "width:"+(usedPercentage - 70)+"%");
												else
												    $("#capacity_memory .db_bargraph_barbox_unsafezone").attr("style", "width:30%");
											} else {
												$("#capacity_memory .db_bargraph_barbox_safezone").attr("style", "width:"+usedPercentage+"%");
											    $("#capacity_memory .db_bargraph_barbox_unsafezone").attr("style", "width:0%");
											}
										// CPU
										} else if (capacity.type == "1") {
											$("#cpu_used").attr("style", "width: " + ((parseFloat(capacity.percentused) < 50) ? "50%" : capacity.percentused + "%")).text("Used: " + convertHz(parseInt(capacity.capacityused)) + " / " + capacity.percentused + "%");
											$("#cpu_total").text("Total: " + convertHz(parseInt(capacity.capacitytotal)));
											var usedPercentage = parseInt(capacity.percentused);
											if (usedPercentage > 70) {
												$("#capacity_cpu .db_bargraph_barbox_safezone").attr("style", "width:70%");
												if(usedPercentage <= 100) 
												    $("#capacity_cpu .db_bargraph_barbox_unsafezone").attr("style", "width:"+(usedPercentage - 70)+"%");
												else
												    $("#capacity_cpu .db_bargraph_barbox_unsafezone").attr("style", "width:30%");
											} else {
												$("#capacity_cpu .db_bargraph_barbox_safezone").attr("style", "width:"+usedPercentage+"%");
											    $("#capacity_cpu .db_bargraph_barbox_unsafezone").attr("style", "width:0%");
											}
										// Private IPs
										} else if (capacity.type == "5") {
											$("#private_ip_used").attr("style", "width: " + ((parseFloat(capacity.percentused) < 50) ? "50%" : capacity.percentused + "%")).text("Used: " + capacity.capacityused + " / " + capacity.percentused + "%");
											$("#private_ip_total").text("Total: " + capacity.capacitytotal);
											var usedPercentage = parseInt(capacity.percentused);
											if (usedPercentage > 70) {
												$("#capacity_private_ip .db_bargraph_barbox_safezone").attr("style", "width:70%");
												if(usedPercentage <= 100) 
												    $("#capacity_private_ip .db_bargraph_barbox_unsafezone").attr("style", "width:"+(usedPercentage - 70)+"%");
												else
												    $("#capacity_private_ip .db_bargraph_barbox_unsafezone").attr("style", "width:30%");
											} else {
												$("#capacity_private_ip .db_bargraph_barbox_safezone").attr("style", "width:"+usedPercentage+"%");
											    $("#capacity_private_ip .db_bargraph_barbox_unsafezone").attr("style", "width:0%");
											}
										}
									}
								}
							}
						}
					}
				});
				
				$("#capacity_zone_select").bind("change", function(event) {
					var zoneId = $(this).val();
					$.ajax({
						data: "command=listPods&zoneId="+zoneId+"&response=json",
						dataType: "json",
						async: false,
						success: function(json) {
							var pods = json.listpodsresponse.pod;
							var podSelect = $("#capacity_pod_select").empty();	
							if (pods != null && pods.length > 0) {
							    for (var i = 0; i < pods.length; i++) {
								    podSelect.append("<option value='" + pods[i].name + "'>" + pods[i].name + "</option>"); 
							    }
							}
							$("#capacity_pod_select").change();
						}
					});
				});
				$("#capacity_zone_select").change();
				
				// Show Recent Alerts
				$.ajax({
					data: "command=listAlerts&response=json",
					dataType: "json",
					success: function(json) {
						var alerts = json.listalertsresponse.alert;
						if (alerts != null && alerts.length > 0) {
							var alertGrid = $("#alert_grid_content").empty();
							var length = (alerts.length>=5) ? 5 : alerts.length;
							for (var i = 0; i < length; i++) {
								var errorTemplate = $("#recent_error_template").clone(true);
								errorTemplate.find("#db_error_type").text(toAlertType(alerts[i].type));
								errorTemplate.find("#db_error_msg").append(alerts[i].description);
								var created = new Date();
								created.setISO8601(alerts[i].sent);
								var showDate = created.format("m/d/Y H:i:s");
								errorTemplate.find("#db_error_date").text(showDate);
								alertGrid.append(errorTemplate.show());
							}
						}
					}
				});
				
				// Show Host Alerts
				$.ajax({
					data: "command=listHosts&state=Alert&response=json",
					dataType: "json",
					success: function(json) {
						var alerts = json.listhostsresponse.host;
						if (alerts != null && alerts.length > 0) {
							var alertGrid = $("#host_alert_grid_content").empty();
							var length = (alerts.length>=4) ? 4 : alerts.length;
							for (var i = 0; i < length; i++) {
								var errorTemplate = $("#recent_error_template").clone(true);
								errorTemplate.find("#db_error_type").text("Host - Alert State");
								errorTemplate.find("#db_error_msg").append("Host - <b>" + alerts[i].name + "</b> has been detected in Alert state.");
								var created = new Date();
								created.setISO8601(alerts[i].disconnected);
								var showDate = created.format("m/d/Y");
								errorTemplate.find("#db_error_date").text(showDate);
								alertGrid.append(errorTemplate.show());
							}
						}
					}
				});
				
				$("#alert_more").bind("click", function(event) {
					event.preventDefault();
					
					$("#menutab_role_root #menutab_events").data("showEvents", false).click();
				});
				$("#host_alert_more").bind("click", function(event) {
					event.preventDefault();
					$("#menutab_hosts").click();
				});
				
				$("#tab_dashboard_user").hide();
				$("#tab_dashboard_root").show();
				$("#menutab_role_user").hide();
				$("#menutab_role_root").show();
				$("#menutab_role_domain").hide();
				$("#launch_test").show();
			} else if (isDomainAdmin()) {
				$("#tab_dashboard_user").hide();
				$("#tab_dashboard_root").show();
				$("#menutab_role_user").hide();
				$("#menutab_role_root").hide();
				$("#menutab_role_domain").show();
				$("#launch_test").hide();
			} else {
			    if(g_role == null) {
			        logout();
			        return;
			    }
			    $("#launch_test").hide();
				$.ajax({
					cache: false,
					data: "command=listAccounts&response=json",
					dataType: "json",
					success: function(json) {
					    var accounts = json.listaccountsresponse.account;						
						if (accounts != null && accounts.length > 0) {
						    var statJSON = accounts[0];
						    var sent = parseInt(statJSON.sentbytes);
						    var rec = parseInt(statJSON.receivedbytes);
    						
						    $("#menutab_role_user").show();
						    $("#menutab_role_root").hide();
							$("#menutab_role_domain").hide();
						    $("#tab_dashboard_user").show();
						    $("#tab_dashboard_root").hide();
							
						    // This is in bytes, so let's change to KB
						    sent = Math.round(sent / 1024);
						    rec = Math.round(rec / 1024);
						    $("#db_sent").text(sent + "KB");
						    $("#db_received").text(rec + "KB");
						    $("#db_available_public_ips").text(statJSON.ipavailable);
						    $("#db_owned_public_ips").text(statJSON.ipalloc);
						    $("#db_running_vms").text(statJSON.vmrunning + " VM(s)");
						    $("#db_stopped_vms").text(statJSON.vmstopped + " VM(s)");
						    $("#db_total_vms").text(statJSON.vmtotal + " VM(s)");
						    $("#db_avail_vms").text(statJSON.vmavailable + " VM(s)");						   
						    $("#db_account_id").text(statJSON.id);
						    $("#db_account").text(statJSON.name);						    
						    $("#db_type").text(toRole(statJSON.accounttype));
						    $("#db_domain").text(statJSON.domain);						    			   
						}
						
						// Events
						$.ajax({
							data: "command=listEvents&level=ERROR&response=json",
							dataType: "json",
							success: function(json) {
								var events = json.listeventsresponse.event;
								if (events != null && events.length > 0) {
									var errorGrid = $("#error_grid_content").empty();
									var length = (events.length>=3) ? 3 : events.length;
									for (var i = 0; i < length; i++) {
										var errorTemplate = $("#recent_error_template").clone(true);
										errorTemplate.find("#db_error_type").text(events[i].type);
										errorTemplate.find("#db_error_msg").append(events[i].description);
										var created = new Date();
										created.setISO8601(events[i].created);
										var showDate = created.format("m/d/Y");
										errorTemplate.find("#db_error_date").text(showDate);
										errorGrid.append(errorTemplate.show());
									}
								}
							}
						});
					},
					error: function(xmlHttp) {
						if (xmlHttp.status == 401 || xmlHttp.status == 531) {
							logout();
						} else if (xmlHttp.status == 530) {
							$("#dialog_error").html("<p>We have encountered an internal server error.  Please contact support.</p>").dialog("open");
						} else {
							if (handleErrorCallback != undefined) {
								handleErrorCallback();
							} else {
								var start = xmlHttp.responseText.indexOf("h1") + 3;
								var end = xmlHttp.responseText.indexOf("</h1");
								var errorMsg = xmlHttp.responseText.substring(start, end);
								$("#dialog_error").html("<p><b>Encountered an error:</b></p><br/><p>"+errorMsg.substring(errorMsg.indexOf("-")+2)+"</p>").dialog("open");
							}
						}
					},
					beforeSend: function(XMLHttpRequest) {
						return true;
					}	
				});
			}
		});
	}

	// Check whether the session is valid.
	g_mySession = $.cookie("JSESSIONID");
	g_role = $.cookie("role");
	g_username = $.cookie("username");
	g_networkType = $.cookie("networktype");
	g_hypervisorType = $.cookie("hypervisortype");
	if (!g_networkType || g_networkType.length == 0) {
		//default to vnet
		g_networkType = "vnet";
	}
	if (!g_hypervisorType || g_hypervisorType.length == 0) {
		//default to vnet
		g_hypervisorType = "kvm";
	}
	$.ajax({
		data: "command=listZones&available=true&response=json",
		dataType: "json",
		async: false,
		success: function(json) {
			// session is valid, continue
			if (isUser()) {
				$("#menutab_role_user #menutab_dashboard_user").click();
			} else if (isAdmin()) {
				$("#menutab_role_root #menutab_dashboard_root").click();
			} else if (isDomainAdmin()) {
				$(".#menutab_role_domain #menutab_dashboard_root").click();
			} else {
				alert("Role is not supported");
				return;
			}
		},
		error: function(xmlHTTP) {
			logout();
		},
		beforeSend: function(xmlHTTP) {
			return true;
		}
	});
});

