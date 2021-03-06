<%@ page import="java.util.Date" %>
<%
long milliseconds = new Date().getTime();
%>
<script type="text/javascript" src="scripts/cloud.core.events.js?t=<%=milliseconds%>"></script>

<!-- Content Panel -->
<!-- Submenus -->
<div class="submenu_links">
	<div class="submenu_links_off" id="submenu_events">Events</div>
	<div class="submenu_links_off" id="submenu_alerts">Alerts</div>
</div>

<!-- *** Events (begin) *** -->
<div class="maincontent" id="submenu_content_events" style="display:none;">
	<div id="maincontent_title">
    	<div class="maintitle_icon"> <img src="images/eventstitle_icons.gif" title="events" /> </div>
		<h1>Events</h1>
		<div class="search_formarea">
			<form action="#" method="post">
				<ol>
					<li><input class="text" type="text" name="search_input" id="search_input" /></li>
				</ol>
			</form>
			<a class="search_button" id="search_button" href="#"></a>
			
			<div id="advanced_search_link" class="advsearch_link">Advanced</div>
            <div id="advanced_search" class="adv_searchpopup" style="display: none;">
                <div class="adv_searchformbox">
                    <h3>Advance Search</h3>
                    <a id="advanced_search_close" href="#">Close </a>
                    <form action="#" method="post">
                    <ol>
                        <li>
                            <label for="filter">Type:</label>
                            
                            <select class="select" id="adv_search_type">
								<option value=""></option>
								
								<option value="VM.CREATE">VM.CREATE</option>
								<option value="VM.DESTROY">VM.DESTROY</option>
								<option value="VM.START">VM.START</option>
								<option value="VM.STOP">VM.STOP</option>
								<option value="VM.REBOOT">VM.REBOOT</option>
								<option value="VM.DISABLEHA">VM.DISABLEHA</option>
								<option value="VM.ENABLEHA">VM.ENABLEHA</option>
								<option value="VM.UPGRADE">VM.UPGRADE</option>
								<option value="VM.RESETPASSWORD">VM.RESETPASSWORD</option>
								
								<option value="ROUTER.CREATE">ROUTER.CREATE</option>	
								<option value="ROUTER.DESTROY">ROUTER.DESTROY</option>
								<option value="ROUTER.START">ROUTER.START</option>
								<option value="ROUTER.STOP">ROUTER.STOP</option>
								<option value="ROUTER.REBOOT">ROUTER.REBOOT</option>
								<option value="ROUTER.HA">ROUTER.HA</option>
								
								<option value="PROXY.CREATE">PROXY.CREATE</option>	
								<option value="PROXY.DESTROY">PROXY.DESTROY</option>
								<option value="PROXY.START">PROXY.START</option>
								<option value="PROXY.STOP">PROXY.STOP</option>
								<option value="PROXY.REBOOT">PROXY.REBOOT</option>
								<option value="PROXY.HA">PROXY.HA</option>
								
								<option value="VNC.CONNECT">VNC.CONNECT</option>
								<option value="VNC.DISCONNECT">VNC.DISCONNECT</option>
								
								<option value="NET.IPASSIGN">NET.IPASSIGN</option>	
								<option value="NET.IPRELEASE">NET.IPRELEASE</option>
								<option value="NET.RULEADD">NET.RULEADD</option>
								<option value="NET.RULEDELETE">NET.RULEDELETE</option>
								<option value="NET.RULEMODIFY">NET.RULEMODIFY</option>
								
								<!-- <option value="SECGROUP.APPLY">SECGROUP.APPLY</option>
								<option value="SECGROUP.REMOVE">SECGROUP.REMOVE</option> -->
								<option value="PF.SERVICE.APPLY">PF.SERVICE.APPLY</option>
								<option value="PF.SERVICE.REMOVE">PF.SERVICE.REMOVE</option>
								<option value="SECGROUP.APPLY">SECGROUP.APPLY</option>
								<option value="SECGROUP.REMOVE">SECGROUP.REMOVE</option>
								<option value="LB.CREATE">LB.CREATE</option>
								<option value="LB.DELETE">LB.DELETE</option>
								
								<option value="USER.LOGIN">USER.LOGIN</option>	
								<option value="USER.LOGOUT">USER.LOGOUT</option>
								<option value="USER.CREATE">USER.CREATE</option>
								<option value="USER.DELETE">USER.DELETE</option>
								<option value="USER.UPDATE">USER.UPDATE</option>
								
								<option value="TEMPLATE.CREATE">TEMPLATE.CREATE</option>	
								<option value="TEMPLATE.DELETE">TEMPLATE.DELETE</option>
								<option value="TEMPLATE.UPDATE">TEMPLATE.UPDATE</option>
								<option value="TEMPLATE.COPY">TEMPLATE.COPY</option>
								<option value="TEMPLATE.DOWNLOAD.START">TEMPLATE.DOWNLOAD.START</option>
								<option value="TEMPLATE.DOWNLOAD.SUCCESS">TEMPLATE.DOWNLOAD.SUCCESS</option>
								<option value="TEMPLATE.DOWNLOAD.FAILED">TEMPLATE.DOWNLOAD.FAILED</option>
									
								<option value="VOLUME.CREATE">VOLUME.CREATE</option>	
								<option value="VOLUME.DELETE">VOLUME.DELETE</option>
								<option value="VOLUME.ATTACH">VOLUME.ATTACH</option>
								<option value="VOLUME.DETACH">VOLUME.DETACH</option>
								
								<option value="SERVICEOFFERING.CREATE">SERVICEOFFERING.CREATE</option>
								<option value="SERVICEOFFERING.UPDATE">SERVICEOFFERING.UPDATE</option>
								<option value="SERVICEOFFERING.DELETE">SERVICEOFFERING.DELETE</option>
								
								<option value="DOMAIN.CREATE">DOMAIN.CREATE</option>
								<option value="DOMAIN.DELETE">DOMAIN.DELETE</option>
								<option value="DOMAIN.UPDATE">DOMAIN.UPDATE</option>
								
								<option value="SNAPSHOT.CREATE">SNAPSHOT.CREATE</option>	
								<option value="SNAPSHOT.DELETE">SNAPSHOT.DELETE</option>
								<option value="SNAPSHOTPOLICY.CREATE">SNAPSHOTPOLICY.CREATE</option>
								<option value="SNAPSHOTPOLICY.UPDATE">SNAPSHOTPOLICY.UPDATE</option>
								<option value="SNAPSHOTPOLICY.DELETE">SNAPSHOTPOLICY.DELETE</option>
								
								<option value="ISO.CREATE">ISO.CREATE</option>	
								<option value="ISO.DELETE">ISO.DELETE</option>
								<option value="ISO.COPY">ISO.COPY</option>
								<option value="ISO.ATTACH">ISO.ATTACH</option>	
								<option value="ISO.DETACH">ISO.DETACH</option>
								
								<option value="SSVM.CREATE">SSVM.CREATE</option>	
								<option value="SSVM.DESTROY">SSVM.DESTROY</option>
								<option value="SSVM.START">SSVM.START</option>
								<option value="SSVM.STOP">SSVM.STOP</option>	
								<option value="SSVM.REBOOT">SSVM.REBOOT</option>
								<option value="SSVM.HA">SSVM.HA</option>					
                            </select>                            
                        </li>
                        <li>
                            <label for="filter">Level:</label>                            
                            <select class="select" id="adv_search_level">
								<option value=""></option>
								<option value="INFO">INFO</option>
								<option value="WARN">WARN</option>
								<option value="ERROR">ERROR</option>							
                            </select>                            
                        </li>
                        <li id="adv_search_domain_li" style="display: none;">
                            <label for="filter">Domain:</label>
                            <select class="select" id="adv_search_domain">
                            </select>
                        </li>
                        <li id="adv_search_account_li" style="display: none;">
                            <label for="filter">Account:</label>
                            <input class="text" type="text" id="adv_search_account" />                               
                        </li>
                        <li>
                            <label>Start Date:</label>
                            <input class="text" type="text" id="adv_search_startdate" />     
                        </li>     
                        <li>
                            <label>End Date:</label>
                            <input class="text" type="text" id="adv_search_enddate" />     
                        </li>                       
                    </ol>
                    </form>
                    <div class="adv_search_actionbox">
                    	<div class="adv_searchpopup_button" id="adv_search_button"></div>
					</div>
                </div>
            </div>
			
		</div>
	</div>
    <div class="filter_actionbox">
    	<div class="selection_formarea" style="display:none;">
        	<form action="#" method="post">
            	<label for="filter">Filters:</label>
				<select class="select" id="template_type">
                  <option value="true">Public</option>
                  <option value="false">Private</option>
         		</select>
			</form>
         </div>
    </div>
	<div class="grid_container">
    	<div id="loading_gridtable" class="loading_gridtable">
                  <div class="loading_gridanimation"></div>
                   <p>Loading...</p>
             </div>
		<div class="grid_header"> 
        	<div class="grid_genheader_cell" style="width:13%;">
				<div class="grid_headertitles">Initiated By</div>
			</div>
            <div class="grid_genheader_cell" id="event_account_header" style="width:13%;">
				<div class="grid_headertitles">Owner Account</div>
			</div>
			<div class="grid_genheader_cell" style="width:10%;">
				<div class="grid_headertitles" >Type</div>
			</div>
			<div class="grid_genheader_cell" style="width:10%;">
				<div class="grid_headertitles">Level</div>
			</div>
			<div class="grid_genheader_cell" style="width:30%;">
				<div class="grid_headertitles">Description</div>
			</div>
			<div class="grid_genheader_cell" style="width:10%;">
				<div class="grid_headertitles">State</div>
			</div>			
			<div class="grid_genheader_cell" style="width:13%;">
				<div class="grid_headertitles">Date</div>
			</div>
		</div>
		<div id="grid_content">
             	
        </div>
	</div>
    <div id="pagination_panel" class="pagination_panel" style="display:none;">
    	<p id="grid_rows_total" />
    	<div class="pagination_actionbox">
        	<div class="pagination_actions">
            	<div class="pagination_actionicon"><img src="images/pagination_refresh.gif" title="refresh" /></div>
                <a id="refresh" href="#"> Refresh</a>
            </div>
            <div class="pagination_actions" id="prevPage_div">
            	<div class="pagination_actionicon"><img src="images/pagination_previcon.gif" title="prev" /></div>
                <a id="prevPage" href="#"> Prev</a>
            </div>
            <div class="pagination_actions" id="nextPage_div">
            	<div class="pagination_actionicon"><img src="images/pagination_nexticon.gif" title="next" /></div>
                <a id="nextPage" href="#"> Next</a>
            </div>
        </div>
    </div>
</div>

<!-- Events Template -->
<div id="event_template" style="display:none">

	<div class="grid_smallgenrow_cell" style="width:13%;">
		<div class="netgrid_celltitles" id="event_username"></div>
	</div>
    <div class="grid_smallgenrow_cell" id="event_account_container" style="width:13%;">
		<div class="netgrid_celltitles" id="event_account"></div>
	</div>
	<div class="grid_smallgenrow_cell" style="width:10%;">
		<div class="netgrid_celltitles" id="event_type"></div>
	</div>
	<div class="grid_smallgenrow_cell" style="width:10%;">
		<div class="netgrid_celltitles" id="event_level"></div>
	</div>
	<div class="grid_smallgenrow_cell" style="width:30%;">
		<div class="netgrid_celltitles" id="event_desc"></div>
	</div>
	<div class="grid_smallgenrow_cell" style="width:10%;">
		<div class="netgrid_celltitles" id="event_state"></div>
	</div>	
	<div class="grid_smallgenrow_cell" style="width:13%;">
		<div class="netgrid_celltitles" id="event_date"></div>
	</div>
</div>
<!-- *** Events (end) *** -->

<!-- *** Alerts (begin) *** -->
<div class="maincontent" id="submenu_content_alerts" style="display:none">
	<div id="maincontent_title">
    	<div class="maintitle_icon"> <img src="images/alerttitle_icons.gif" title="events" /> </div>
		<h1>Alerts</h1>
		<div class="search_formarea">
			<form action="#" method="post">
				<ol>
					<li><input class="text" type="text" name="search_input" id="search_input" /></li>
				</ol>
			</form>
			<a class="search_button" id="search_button" href="#"></a>
			
			<div id="advanced_search_link" class="advsearch_link">Advanced</div>
            <div id="advanced_search" class="adv_searchpopup" style="display: none;">
                <div class="adv_searchformbox">
                    <h3>Advance Search</h3>
                    <a id="advanced_search_close" href="#">Close </a>
                    <form action="#" method="post">
                    <ol>
                        <li>
                            <label for="filter">Type:</label>
                            
                            
                            <select class="select" id="adv_search_type">
								<option value=""></option>
								
								<option value="0">Capacity Threshold - Memory</option>
								<option value="1">Capacity Threshold - CPU</option>
								<option value="2">Capacity Threshold - Storage Used</option>
								<option value="3">Capacity Threshold - Storage Allocated</option>
								<option value="4">Capacity Threshold - Public IP</option>
								<option value="5">Capacity Threshold - Private IP</option>
								<option value="6">Monitoring - Host</option>
								<option value="7">Monitoring - VM</option>
								<option value="8">Monitoring - Domain Router</option>
								<option value="9">Monitoring - Console Proxy</option>
								<option value="10">Monitoring - Routing Host</option>
								
								<option value="11">Monitoring - Storage</option>
								<option value="12">Monitoring - Usage Server</option>
								<option value="13">Monitoring - Management Server</option>
								<option value="14">Migration - Domain Router</option>
								<option value="15">Migration - Console Proxy</option>
								<option value="16">Migration - User VM</option>
								<option value="17">VLAN</option>
								<option value="18">Monitoring - Secondary Storage VM</option>							
                            </select>
                            
                            
                        </li>               
                    </ol>
                    </form>
                    <div class="adv_search_actionbox">
                    	<div class="adv_searchpopup_button" id="adv_search_button"></div>
					</div>
                </div>
            </div>
			
		</div>
	</div>
    <div class="filter_actionbox">
    	<div class="selection_formarea" style="display:none;">
        	<form action="#" method="post">
            	<label for="filter">Filters:</label>
				<select class="select" id="template_type">
                  <option value="true">Public</option>
                  <option value="false">Private</option>
         		</select>
			</form>
         </div>
    </div>
	<div class="grid_container">
    	<div id="loading_gridtable" class="loading_gridtable">
                  <div class="loading_gridanimation"></div>
                   <p>Loading...</p>
             </div>
		<div class="grid_header">
			<div class="grid_genheader_cell" style="width:25%;">
				<div class="grid_headertitles">Type</div>
			</div>
			<div class="grid_genheader_cell" style="width:49%;">
				<div class="grid_headertitles">Description</div>
			</div>
			<div class="grid_genheader_cell" style="width:25%;">
				<div class="grid_headertitles">Sent</div>
			</div>
		</div>
		<div id="grid_content">
        	 
        </div>
	</div>
    <div id="pagination_panel" class="pagination_panel" style="display:none;">
    	<p id="grid_rows_total" />
    	<div class="pagination_actionbox">
        	<div class="pagination_actions">
            	<div class="pagination_actionicon"><img src="images/pagination_refresh.gif" title="refresh" /></div>
                <a id="refresh" href="#"> Refresh</a>
            </div>
            <div class="pagination_actions" id="prevPage_div">
            	<div class="pagination_actionicon"><img src="images/pagination_previcon.gif" title="prev" /></div>
                <a id="prevPage" href="#"> Prev</a>
            </div>
            <div class="pagination_actions" id="nextPage_div">
            	<div class="pagination_actionicon"><img src="images/pagination_nexticon.gif" title="next" /></div>
                <a id="nextPage" href="#"> Next</a>
            </div>
        </div>
    </div>
</div>

<!-- Alert Template -->
<div id="alert_template" style= "display:none">
	<div class="grid_smallgenrow_cell" style="width:25%;">
		<div class="netgrid_celltitles" id="alert_type"></div>
	</div>
	 <div class="grid_smallgenrow_cell" style="width:49%;">
		<div class="netgrid_celltitles" id="alert_desc"></div>
	</div>
	<div class="grid_smallgenrow_cell" style="width:25%;">
		<div class="netgrid_celltitles" id="alert_sent"></div>
	</div>
</div>
<!-- *** Alerts (end) *** -->