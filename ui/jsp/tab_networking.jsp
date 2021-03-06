<%@ page import="java.util.Date" %>
<%
long milliseconds = new Date().getTime();
%>
<script type="text/javascript" src="scripts/cloud.core.network.js?t=<%=milliseconds%>"></script>
	
<!-- Content Panel -->
<!-- Submenus -->
<div class="submenu_links">
    <div class="submenu_links_off" id="submenu_network">
        IP Addresses</div>
    <div class="submenu_links_off" id="submenu_network_groups">
        Network Groups</div>
</div>
<!-- ***** Network (begin) ********************************************************************************************************************* -->
<div class="maincontent" id="submenu_content_network" style="display: none;">
    <div id="maincontent_title">
        <div class="maintitle_icon">
            <img src="images/loadtitle_icons.gif" title="Network" />
        </div>
        <h1>
            IP Addresses</h1>
        <a class="add_publicipbutton" id="" href="#"></a>
    </div>
    <div class="net_gridwrapper">
        <!--page loader starts here-->
        <div id="overlay_white" style="display: none;">
        </div>
        <div id="loading_gridtable" class="loading_gridtable" style="top: 300px; left: 20%;
            position: absolute; z-index: 1100; border: 1px solid #999; display: none;">
            <div class="loading_gridanimation">
            </div>
            <p id="message">
                Waiting....</p>
        </div>
        <!--page loader ends here-->
        <div class="publicip_panel" id="network_container">
            <div class="select_directipbg_user" style="display:none;">
                <form action="#" method="post">
                <ol>
                    <li>
                        <label>
                            Select IP:</label>
                        <select class="select" id="ip_select">
                        </select></li>
                </ol>
                </form>
            </div>
            
          
            
            <div class="select_directipbg_admin" style="display:block;">
                <form action="#" method="post">
                <ol>
                    <li>
                    	
                    	<p style="float:left; font-size:11px; font-weight:bold; color:#FFF; margin:7px 0 0 0;">Search </p>
                        
						<input class="text ipwatermark_text" style="width:186px;" type="text" name="ip" id="admin_ip_search" value="By Public IP Address"/>
                    </li>                    
                </ol>
                <div class="ip_searchbutton" id="ip_searchbutton1"></div>
                </form>
                
                <form action="#" method="post">
                <ol>
                     <li style="margin-left:15px;">
                    	<div class="ip_oricon"></div>
                       	<input class="text ipwatermark_text" type="text" id="search_by_account" value="By Account"/>
                        <select class="select" id="search_by_domain">
                        </select>
                    </li>                    
                </ol>
                <div class="ip_searchbutton" id="ip_searchbutton2"></div>
                </form>
            </div>
            
            <div class="select_directip_actions">
                <a href="#" id="release_ip_link" style="display: none">Release IP</a>
            </div>
            <div class="ip_descriptionbox" id="network_detail_template">
                <div class="ip_descriptionbox_top">
                	<a href="#" id="show_last_search" style="display:none">Back to Search Results</a>
                </div>
                <div class="ip_descriptionbox_mid" id="ip_descriptionbox_mid">
					<div id="ip_list_container">
						<div class="ip_description_topdetailspanel">
							<div class="ip_description_detailbox">
								<p>
									IP: <span id="ipaddress"></span><a href="#" id="ip_manage" style="display:none">manage</a><a href="#" id="ip_release" style="display:none">release</a>
								</p>
							</div>
							<div class="ip_description_detailbox">
								<p>
									Zone: <span id="zonename"></span>
								</p>
							</div>
							<div class="ip_description_detailbox">
								<p>
									VLAN: <span id="vlanname"></span>
								</p>
							</div>
							<div class="ip_description_detailbox">
								<p>
									Source NAT: <span id="source_nat"></span>
								</p>
							</div>
							<div class="ip_description_detailbox">
								<p>
									Network Type: <span id="network_type"></span>
								</p>
							</div>
							<div class="ip_description_detailbox">
								<p>
									Domain: <span id="domain"></span>
								</p>
							</div>
							<div class="ip_description_detailbox">
								<p>
									Account: <span id="account"></span>
								</p>
							</div>
							<div class="ip_description_detailbox">
								<p>
									Allocated: <span id="allocated"></span>
								</p>
							</div>
						</div>
					</div>

                    <div class="ip_description_contentbox" id="pf_lb_area_blank" style="display: none;">
                        <div class="ip_description_contentbox_top" style="background: url(images/ipdescr_conttop_blank.gif) no-repeat top left;">
                        </div>
                        <div class="ip_description_contentbox_mid" style="background: url(images/ipdescr_contmid_blank.gif) repeat-y top left;">
                            <p>
                                This IP address is managed by the CloudStack for use with System VMs.</p>
                        </div>
                        <div class="ip_description_contentbox_bot" style="background: url(images/ipdescr_contbot_blank.gif) no-repeat top left;">
                        </div>
                    </div>
                    <div class="ip_description_contentbox" id="pf_lb_area" style="display: none;">
                        <div class="ip_description_contentbox_top">
                            <div class="ip_description_titlearea">
                                <div class="ip_description_titleicon">
                                    <img src="images/portnetwork_titleicon.gif" alt="Port Forwarding" /></div>
                                <h3>
                                    Port Forwarding</h3>
                            </div>
                            <div class="ip_description_titlearea">
                                <div class="ip_description_titleicon">
                                    <img src="images/loadnetwork_titleicon.gif" alt="Load Balancer" /></div>
                                <h3>
                                    Load Balancer</h3>
                            </div>
                        </div>
                        <div class="ip_description_contentbox_mid">
                            <!-- Port Forwarding Content starts here-->
                            <div class="ip_description_contentarea" id="port_forwarding_panel">
                                <div class="ip_description_gridarea">
                                    <div class="grid_container">
                                        <div class="grid_header">
                                            <div class="grid_genheader_cell" style="width: 17%;">
                                                <div class="grid_headertitles" style="font-size: 11px;">
                                                    Public Port</div>
                                            </div>
                                            <div class="grid_genheader_cell" style="width: 19%;">
                                                <div class="grid_headertitles" style="font-size: 11px;">
                                                    Private Port</div>
                                            </div>
                                            <div class="grid_genheader_cell" style="width: 14%;">
                                                <div class="grid_headertitles" style="font-size: 11px;">
                                                    Protocol</div>
                                            </div>
                                            <div class="grid_genheader_cell" style="width: 28%;">
                                                <div class="grid_headertitles" style="font-size: 11px;">
                                                    Instance</div>
                                            </div>
                                            <div class="grid_genheader_cell" style="width: 20%; border: 0;">
                                                <div class="grid_headertitles" style="font-size: 11px;">
                                                </div>
                                            </div>
                                        </div>
                                        <div id="create_port_forwarding_row">
                                            <div class="smallrow_odd">
                                                <div class="grid_smallgenrow_cell" style="width: 17%;">
                                                    <div class="netgrid_celltitles">
                                                        <input class="text" style="width: 57px;" type="text" id="public_port"></input>
                                                        <div id="public_port_errormsg" class="errormsg" style="display: none;">
                                                            Error msg will appear here</div>
                                                    </div>
                                                </div>
                                                <div class="grid_smallgenrow_cell" style="width: 19%;">
                                                    <div class="netgrid_celltitles" id="Div10">
                                                        <input class="text" style="width: 62px;" type="text" id="private_port"></input>
                                                        <div id="private_port_errormsg" class="errormsg" style="display: none;">
                                                            Error msg will appear here</div>
                                                    </div>
                                                </div>
                                                <div class="grid_smallgenrow_cell" style="width: 14%;">
                                                    <div class="netgrid_celltitles" id="Div11">
                                                        <select class="select" id="protocol">
                                                            <option value="TCP">TCP</option>
                                                            <option value="UDP">UDP</option>
                                                        </select>
                                                    </div>
                                                </div>
                                                <div class="grid_smallgenrow_cell" style="width: 28%;">
                                                    <div class="netgrid_celltitles" id="Div12">
                                                        <select class="select" style="width: 104px;" id="vm">
                                                        </select>
                                                    </div>
                                                </div>
                                                <div class="grid_smallgenrow_cell" style="width: 20%; border: 0;">
                                                    <div class="netgrid_celltitles">
                                                        <a id="add_link" href="#">Add</a></div>
                                                </div>
                                            </div>
                                        </div>
                                        <div id="grid_content">
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <!-- Port Forwarding Content ends here-->
                            <!-- Load Balancer Content starts here-->
                            <div class="ip_description_contentarea" id="load_balancer_panel">
                                <div class="ip_description_gridarea">
                                    <div class="grid_container">
                                        <div class="grid_header">
                                            <div class="grid_genheader_cell" style="width: 20%;">
                                                <div class="grid_headertitles" style="font-size: 11px;">
                                                    Name</div>
                                            </div>
                                            <div class="grid_genheader_cell" style="width: 16%;">
                                                <div class="grid_headertitles" style="font-size: 11px;">
                                                    Public Port</div>
                                            </div>
                                            <div class="grid_genheader_cell" style="width: 16%;">
                                                <div class="grid_headertitles" style="font-size: 11px;">
                                                    Private Port</div>
                                            </div>
                                            <div class="grid_genheader_cell" style="width: 16%;">
                                                <div class="grid_headertitles" style="font-size: 11px;">
                                                    Algorithm</div>
                                            </div>
                                            <div class="grid_genheader_cell" style="width: 29%; border: 0;">
                                                <div class="grid_headertitles" style="font-size: 11px;">
                                                </div>
                                            </div>
                                        </div>
                                        <div class="smallrow_odd" id="create_load_balancer_row">
                                            <div class="grid_smallgenrow_cell" style="width: 20%;">
                                                <div class="netgrid_celltitles">
                                                    <input id="name" class="text" style="width: 70px;" type="text"></input>
                                                    <div id="name_errormsg" class="errormsg" style="display: none;">
                                                        Error msg will appear here</div>
                                                </div>
                                            </div>
                                            <div class="grid_smallgenrow_cell" style="width: 16%;">
                                                <div class="netgrid_celltitles" id="Div22">
                                                    <input id="public_port" class="text" style="width: 52px;" type="text"></input>
                                                    <div id="public_port_errormsg" class="errormsg" style="display: none;">
                                                        Error msg will appear here</div>
                                                </div>
                                            </div>
                                            <div class="grid_smallgenrow_cell" style="width: 16%;">
                                                <div class="netgrid_celltitles" id="Div23">
                                                    <input id="private_port" class="text" style="width: 52px;" type="text"></input>
                                                    <div id="private_port_errormsg" class="errormsg" style="display: none;">
                                                        Error msg will appear here</div>
                                                </div>
                                            </div>
                                            <div class="grid_smallgenrow_cell" style="width: 16%;">
                                                <div class="netgrid_celltitles" id="Div24">
                                                    <select id="algorithm_select" class="select" style="width: 58px;">
                                                        <option value="roundrobin">roundrobin</option>
                                                        <option value="leastconn">leastconn</option>
                                                        <option value="source">source</option>
                                                    </select>
                                                </div>
                                            </div>
                                            <div class="grid_smallgenrow_cell" style="width: 29%; border: 0;">
                                                <div class="netgrid_celltitles">
                                                    <a id="add_link" href="#">Add</a></div>
                                            </div>
                                        </div>
                                        <div id="grid_content">
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <!-- Load Balancer Content ends here-->
                        </div>
                        <div class="ip_description_contentbox_bot">
                        </div>
                    </div>
                </div>
                <div class="ip_descriptionbox_bot">
                </div>
            </div>
        </div>
    </div>
</div>
<!-- IP Address Template (begin) -->
<div id="ip_template" class="ip_description_topdetailspanel" style="display:none">
	<div class="ip_description_detailbox">
		<p>
			IP: <span id="ipaddress"></span><a href="#" id="ip_manage" style="display:none">manage</a><a href="#" id="ip_release">release</a>
		</p>
	</div>
	<div class="ip_description_detailbox">
		<p>
			Zone: <span id="zonename"></span>
		</p>
	</div>
	<div class="ip_description_detailbox">
		<p>
			VLAN: <span id="vlanname"></span>
		</p>
	</div>
	<div class="ip_description_detailbox">
		<p>
			Source NAT: <span id="source_nat"></span>
		</p>
	</div>
	<div class="ip_description_detailbox">
		<p>
			Network Type: <span id="network_type"></span>
		</p>
	</div>
	<div class="ip_description_detailbox">
		<p>
			Domain: <span id="domain"></span>
		</p>
	</div>
	<div class="ip_description_detailbox">
		<p>
			Account: <span id="account"></span>
		</p>
	</div>
	<div class="ip_description_detailbox">
		<p>
			Allocated: <span id="allocated"></span>
		</p>
	</div>
</div>
<!-- IP Address Template (end) -->
<!-- Load Balancer Template (begin) -->
<div id="load_balancer_template" style="display: none">
    <div class="adding_loading" style="height: 25px; display: none;" id="loading_container">
        <div class="adding_animation">
        </div>
        <div class="adding_text">
            Waiting &hellip;
        </div>
    </div>
    <div class="smallrow_odd" id="row_container_edit" style="display:none">
		<div class="grid_smallgenrow_cell" style="width: 20%;">
			<div class="netgrid_celltitles">
				<input id="name" class="text" style="width: 70px;" type="text"></input>
				<div id="name_errormsg" class="errormsg" style="display: none;">
					Error msg will appear here</div>
			</div>
		</div>
		<div class="grid_smallgenrow_cell" style="width: 16%;">
			<div class="netgrid_celltitles" id="public_port">
			</div>
		</div>
		<div class="grid_smallgenrow_cell" style="width: 16%;">
			<div class="netgrid_celltitles" id="Div8">
				<input id="private_port" class="text" style="width: 52px;" type="text"></input>
				<div id="private_port_errormsg" class="errormsg" style="display: none;">
					Error msg will appear here</div>
			</div>
		</div>
		<div class="grid_smallgenrow_cell" style="width: 16%;">
			<div class="netgrid_celltitles" id="Div13">
				<select id="algorithm_select" class="select" style="width: 58px;">
					<option value="roundrobin">roundrobin</option>
					<option value="leastconn">leastconn</option>
					<option value="source">source</option>
				</select>
			</div>
		</div>
		<div class="grid_smallgenrow_cell" style="width: 29%; border: 0;">
			<div class="netgrid_celltitles">
				<a id="save_link" href="#">Save</a></div>
			<div class="netgrid_celltitles">
				<a id="cancel_link" href="#">Cancel</a></div>
		</div>
    </div>
    <div id="row_container">
        <div class="grid_smallgenrow_cell" style="width: 20%;">
            <div class="netgrid_celltitles" id="name">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 16%;">
            <div class="netgrid_celltitles" id="public_port">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 16%;">
            <div class="netgrid_celltitles" id="private_port">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 16%;">
            <div class="netgrid_celltitles" id="algorithm">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 29%;">
            <div class="netgrid_celltitles">
                <a id="manage_link" href="#">Manage</a> 
                <a id="delete_link" href="#">Delete</a>
                <a id="edit_link" href="#">Edit</a>
            </div>
        </div>
    </div>
    <div class="ip_description_managelist" id="add_vm_to_lb_row" style="display: none;">
        <!--manage loader-->
        <div class="ip_description_manageloading" style="display: none;" id="adding_loading">
            <div class="ip_description_manageloader">
            </div>
            <p>
                Adding &hellip;</p>
        </div>
        <!--manage loader ends here-->
        <div id="adding_row_container" style="display: block; background: none;">
            <div class="ip_description_managelist_icon">
            </div>
            <div class="ip_description_managelist_cell" style="width: 265px;">
                <select id="vm_select" class="select" style="width: 255px; margin: -3px 0 0 0;">
                </select>
            </div>
            <div class="ip_description_managelist_cell">
                <a href="#" id="add_link">Add</a></div>
        </div>
    </div>
    <div id="vm_subgrid" class="ip_description_managearea" style="display: none;">
    </div>
</div>
<!-- Load Balancer Template (end) -->
<!-- Load Balancer's VM subgrid template (begin) -->
<div id="load_balancer_vm_template" class="ip_description_managelist" style="display: none">
    <!--manage loader-->
    <div class="ip_description_manageloading" style="display: none;" id="deleting_loading">
        <div class="ip_description_manageloader">
        </div>
        <p>
            Deleting &hellip;</p>
    </div>
    <!--manage loader ends here-->
    <div id="deleting_row_container" style="display: block;">
        <div class="ip_description_managelist_icon">
        </div>
        <div class="ip_description_managelist_cell" style="width: 135px;" id="vm_name">
        </div>
        <div class="ip_description_managelist_cell" style="width: 125px;" id="vm_private_ip">
        </div>
        <div class="ip_description_managelist_cell">
            <a href="#" id="delete_link">Delete</a></div>
    </div>
</div>
<!-- Load Balancer's VM subgrid template (end) -->
<!-- Port Forwarding template (begin) -->
<div id="port_forwarding_template" style="display: none">
    <div class="adding_loading" style="height: 25px; display: none;">
        <div class="adding_animation">
        </div>
        <div class="adding_text">
            Waiting &hellip;
        </div>
    </div>
    <div id="row_container">
        <div class="grid_smallgenrow_cell" style="width: 17%;">
            <div class="netgrid_celltitles" id="public_port">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 19%;">
            <div class="netgrid_celltitles" id="private_port">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 14%;">
            <div class="netgrid_celltitles" id="protocol">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 28%;">
            <div class="netgrid_celltitles" id="vm_name">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 20%;">
            <div class="netgrid_celltitles">
                <a id="edit_link" href="#">Edit</a> <a id="delete_link" href="#">Delete</a></div>
        </div>
    </div>
    <div id="row_container_edit" style="display: none">
        <div class="smallrow_odd">
            <div class="grid_smallgenrow_cell" style="width: 17%;">
                <div class="netgrid_celltitles" id="public_port">
                </div>
            </div>
            <div class="grid_smallgenrow_cell" style="width: 19%;">
                <div class="netgrid_celltitles" id="Div2">
                    <input class="text" style="width: 62px;" type="text" id="private_port"></input>
                    <div id="private_port_errormsag" class="errormsg" style="display: none;">
                        Error msg will appear here</div>
                </div>
            </div>           
            <div class="grid_smallgenrow_cell" style="width: 14%;">
                <div class="netgrid_celltitles" id="protocol">
                </div>
            </div>            
            <div class="grid_smallgenrow_cell" style="width: 28%;">
                <div class="netgrid_celltitles" id="Div5">
                    <select class="select" style="width: 104px;" id="vm">
                    </select>
                </div>
            </div>
            <div class="grid_smallgenrow_cell" style="width: 20%;">
                <div class="netgrid_celltitles">
                    <a id="save_link" href="#">Save</a></div>
                <div class="netgrid_celltitles">
                    <a id="cancel_link" href="#">Cancel</a></div>
            </div>
        </div>
    </div>
</div>
<!-- Port Forwarding template (end) -->
<!-- Acquire Public IP Dialog -->
<div id="dialog_acquire_public_ip" title="Acquire New IP" style="display: none">
    <p>
        Please select an availability zone to associate your new IP with. Acquiring additional
        IP may cost you an additional X dollars per month.</p>
    <div class="dialog_formcontent">
        <form action="#" method="post" id="form1">
        <ol>
            <li>
                <label for="user_name">
                    Availability Zone:</label>
                <select class="select" name="acquire_zone" id="acquire_zone">
                    <option value="default">Please wait...</option>
                </select>
            </li>
        </ol>
        </form>
    </div>
</div>
<!-- ***** Network (end) *********************************************************************************************************************** -->
<!-- ***** Network Groups (begin) ************************************************************************************************************** -->
<div class="maincontent" id="submenu_content_network_groups" style="display: none;">
    <div id="maincontent_title">
        <div class="maintitle_icon">
            <img src="images/sgtitle_icons.gif" title="Network Groups" />
        </div>
        <h1>
            Network Groups</h1>
        <a class="add_networkgroupbutton" id="network_groups_action_new" href="#"></a>
        <div class="search_formarea">
            <form action="#" method="post">
            <ol>
                <li>
                    <input class="text" type="text" name="search_input" id="search_input" /></li>
            </ol>
            </form>
            <a class="search_button" href="#" id="search_button"></a>
            <div id="advanced_search_link" class="advsearch_link">
                Advanced</div>
            <div id="advanced_search" class="adv_searchpopup" style="display: none;">
                <div class="adv_searchformbox">
                    <h3>
                        Advance Search</h3>
                    <a id="advanced_search_close" href="#">Close </a>
                    <form action="#" method="post">
                    <ol>
                        <li>
                            <label for="filter">
                                Name:</label>
                            <input class="text" type="text" name="adv_search_name" id="adv_search_name" />
                        </li>
                        <li>
                            <label for="adv_search_vm">
                                Virtual Machine:</label>
                            <select class="select" id="adv_search_vm">
                            </select>
                        </li>
                        <li id="adv_search_domain_li" style="display: none;">
                            <label for="filter">
                                Domain:</label>
                            <select class="select" id="adv_search_domain">
                            </select>
                        </li>
                        <li id="adv_search_account_li" style="display: none;">
                            <label for="filter">
                                Account:</label>
                            <input class="text" type="text" id="adv_search_account" />
                        </li>
                    </ol>
                    </form>
                    <div class="adv_search_actionbox">
                        <div class="adv_searchpopup_button" id="adv_search_button">
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <div class="grid_container">
        <div id="loading_gridtable" class="loading_gridtable">
            <div class="loading_gridanimation">
            </div>
            <p>
                Loading...</p>
        </div>
        <div class="grid_header">
            <div class="grid_genheader_cell" style="width: 10%;">
                <div class="grid_headertitles">
                    ID</div>
            </div>
            <div class="grid_genheader_cell" style="width: 10%;">
                <div class="grid_headertitles">
                    Name</div>
            </div>
            <div class="grid_genheader_cell" style="width: 20%;">
                <div class="grid_headertitles">
                    Description</div>
            </div>
            <div class="grid_genheader_cell" style="width: 15%;">
                <div class="grid_headertitles">
                    Domain</div>
            </div>
            <div class="grid_genheader_cell" style="width: 15%;">
                <div class="grid_headertitles">
                    Account</div>
            </div>
            <div class="grid_genheader_cell" style="width: 20%;">
                <div class="grid_headertitles">
                    Actions</div>
            </div>
        </div>
        <div id="grid_content">
        </div>
    </div>
    <div id="pagination_panel" class="pagination_panel" style="display: none;">
        <p id="grid_rows_total" />
        <div class="pagination_actionbox">
            <div class="pagination_actions">
                <div class="pagination_actionicon">
                    <img src="images/pagination_refresh.gif" title="refresh" /></div>
                <a id="refresh" href="#">Refresh</a>
            </div>
            <div class="pagination_actions" id="prevPage_div">
                <div class="pagination_actionicon">
                    <img src="images/pagination_previcon.gif" title="prev" /></div>
                <a id="prevPage" href="#">Prev</a>
            </div>
            <div class="pagination_actions" id="nextPage_div">
                <div class="pagination_actionicon">
                    <img src="images/pagination_nexticon.gif" title="next" /></div>
                <a id="nextPage" href="#">Next</a>
            </div>
        </div>
    </div>
</div>
<!-- Network Group template -->
<div style="display: none;" id="network_group_template">
    <div class="adding_loading" style="height: 25px; display: none;" id="loading_container">
        <div class="adding_animation">
        </div>
        <div class="adding_text">
            Waiting &hellip;
        </div>
    </div>
    <div id="row_container">
        <div class="grid_smallgenrow_cell" style="width: 10%;">
            <div class="netgrid_celltitles" id="id">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 10%;">
            <div class="netgrid_celltitles" id="name">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 20%;">
            <div class="netgrid_celltitles" id="description">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 15%;">
            <div class="netgrid_celltitles" id="domain">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 15%;">
            <div class="netgrid_celltitles" id="account">
            </div>
        </div>
        <div class="grid_smallgenrow_cell" style="width: 20%;">
            <div class="netgrid_celltitles">
                <span id="ingress_rule_link" class="vm_botactionslinks_down">Ingress Rules </span>
                <span><a id="delete_link" href="#">delete</a> </span>
            </div>
        </div>
    </div>
    <div class="hostadmin_showdetails_panel" id="ingress_rule_panel" style="display: none;">
        <div class="hostadmin_showdetails_grid">
            <div class="hostadmin_showdetailsheader">
                <div class="hostadmin_showdetailsheader_cell" style="width: 5%">
                    <div class="grid_headertitles">
                        ID</div>
                </div>
                <div class="hostadmin_showdetailsheader_cell" style="width: 10%">
                    <div class="grid_headertitles">
                        Protocol</div>
                </div>
                <div class="hostadmin_showdetailsheader_cell" style="width: 20%">
                    <div class="grid_headertitles">
                        Endpoint or Operation</div>
                </div>
                <div class="hostadmin_showdetailsheader_cell" style="width: 40%">
                    <div class="grid_headertitles">
                        CIDR or Account/Network Group</div>
                </div>
                <div class="hostadmin_showdetailsheader_cell" style="width: 10%">
                    <div class="grid_headertitles">
                        Actions
                    </div>
                </div>
            </div>
            <div id="ingress_rule_grid">
                <div id="no_ingress_rule" class="hostadmin_showdetails_row_odd">
                    <div class="hostadmin_showdetailsrow_cell" style="width: 100%">
                        <div class="netgrid_celltitles">
                            No Ingress Rule
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<!-- Network Group's Ingress Rule Template (begin) -->
<div id="network_group_ingress_rule_template" style="display: none">
    <div class="adding_loading" style="height: 25px; display: none;">
        <div class="adding_animation">
        </div>
        <div class="adding_text">
            Waiting &hellip;
        </div>
    </div>
    <div id="row_container">
        <div class="hostadmin_showdetailsrow_cell" style="width: 5%">
            <div class="netgrid_celltitles" id="id">
            </div>
        </div>
        <div class="hostadmin_showdetailsrow_cell" style="width: 10%">
            <div class="netgrid_celltitles" id="protocol">
            </div>
        </div>
        <div class="hostadmin_showdetailsrow_cell" style="width: 20%">
            <div class="netgrid_celltitles" id="endpoint">
            </div>
        </div>
        <div class="hostadmin_showdetailsrow_cell" style="width: 40%;">
            <div class="netgrid_celltitles" id="cidr">
            </div>
        </div>
        <div class="hostadmin_showdetailsrow_cell" style="width: 10%">
            <div class="netgrid_celltitles">
                <span><a id="ingress_rule_delete_link" href="#">Delete</a> </span>
            </div>
        </div>
    </div>
</div>
<!-- Network Group's Ingress Rule Template (end) -->
<!-- Network Group's Ingress Rule - add row (begin) -->
<div id="network_group_ingress_rule_add_row" class="hostadmin_showdetails_row_even"
    style="display: none">
    <div class="hostadmin_showdetailsrow_cell" style="width: 100%">
        <div class="netgrid_celltitles">
            <a id="network_group_ingress_rule_add_link" href="#">Click here to add a new ingress
                rule</a>
        </div>
    </div>
</div>
<!-- Network Group's Ingress Rule - add row (end) -->
<!-- Add Ingress Rule Dialog (begin) -->
<div id="dialog_add_ingress_rule" title="Add Ingress Rule" style="display: none">
    <div class="dialog_formcontent">
        <form action="#" method="post">
        <ol>
            <li>
                <label for="protocol">
                    Protocol</label>
                <select class="select" id="protocol">
                    <option value="TCP">TCP</option>
                    <option value="UDP">UDP</option>
                    <option value="ICMP">ICMP</option>
                </select>
            </li>
            <li id="start_port_container">
                <label for="start_port">
                    Start Port:</label>
                <input class="text" type="text" id="start_port" />
                <div id="start_port_errormsg" class="dialog_formcontent_errormsg" style="display: none;">
                </div>
            </li>
            <li id="end_port_container">
                <label for="end_port">
                    End Port:</label>
                <input class="text" type="text" id="end_port" />
                <div id="end_port_errormsg" class="dialog_formcontent_errormsg" style="display: none;">
                </div>
            </li>
            <li id="icmp_type_container">
                <label for="start_port">
                    Type:</label>
                <input class="text" type="text" id="icmp_type" />
                <div id="icmp_type_errormsg" class="dialog_formcontent_errormsg" style="display: none;">
                </div>
            </li>
            <li id="icmp_code_container">
                <label for="end_port">
                    Code:</label>
                <input class="text" type="text" id="icmp_code" />
                <div id="icmp_code_errormsg" class="dialog_formcontent_errormsg" style="display: none;">
                </div>
            </li>
            <li>
                <label>
                    <input type="radio" name="ingress_rule_type" value="cidr" checked>
                    Add by CIDR:</label>
                <div id="cidr_container">
                </div>
                <a style="margin-left: 110px; display: inline;" id="add_more_cidr" href="#">Add more</a>
            </li>
            <li style="margin-top: 7px;">
                <label>
                    <input type="radio" name="ingress_rule_type" value="account_networkgroup">
                    Add by Group:</label>
                <p style="color: #999;">
                    Account Name</p>
                <p style="margin-left: 25px; display: inline; color: #999;">
                    Network Group Name</p>
                <div id="account_networkgroup_container">
                </div>
                <a style="margin-left: 110px; display: inline;" id="add_more_account_networkgroup"
                    href="#">Add more</a></li>
        </ol>
        </form>
    </div>
</div>
<!-- Add Ingress Rule Dialog (end) -->
<!-- Add Ingress Rule Dialog - CIDR template (begin) -->
<div id="cidr_template" class="cidr_template" style="display: none">
    <input class="text" type="text" id="cidr" />
    <div id="cidr_errormsg" class="dialog_formcontent_errormsg" style="display: none;
        margin: 0;">
    </div>
</div>
<!-- Add Ingress Rule Dialog - CIDR template (end) -->
<!-- Add Ingress Rule Dialog - Account/Network Group template (begin) -->
<div id="account_networkgroup_template" class="account_networkgroup_template" style="width: 200px;
    height: auto; float: left; display: none">
    <input class="text" style="width: 80px" type="text" id="account" />
    <span>/</span>
    <input class="text" style="width: 80px" type="text" id="networkgroup" />
    <div id="account_networkgroup_template_errormsg" class="dialog_formcontent_errormsg"
        style="display: none; margin: 0;">
    </div>
</div>
<!-- Add Ingress Rule Dialog - Account/Network Group template (end) -->
<!-- Add Network Groups Dialog (begin) -->
<div id="dialog_add_network_groups" title="Add Network Group" style="display: none">
    <div class="dialog_formcontent">
        <form action="#" method="post" id="form_acquire">
        <ol>
            <li>
                <label>
                    Name:</label>
                <input class="text" type="text" id="name" />
                <div id="name_errormsg" class="dialog_formcontent_errormsg" style="display: none;">
                </div>
            </li>
            <li>
                <label>
                    Description:</label>
                <input class="text" type="text" id="description" />
                <div id="description_errormsg" class="dialog_formcontent_errormsg" style="display: none;">
                </div>
            </li>
        </ol>
        </form>
    </div>
</div>
<!-- Add Network Groups Dialog (end) -->
<!-- ***** Network Groups (end) **************************************************************************************************************** -->
