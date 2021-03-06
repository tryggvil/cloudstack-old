<%@ page import="java.util.Date" %>
<%
long milliseconds = new Date().getTime();
%>
<script type="text/javascript" src="scripts/cloud.core.domains.js?t=<%=milliseconds%>"></script>
	
<!-- Content Panel -->
<div class="maincontent" style="display:block;" id="submenu_content_domains">
	<div id="maincontent_title">
    	<div class="maintitle_icon"> <img src="images/domaintitle_icons.gif" title="Domain" /> </div>
		<h1>Domains</h1>
        <div class="search_formarea">
			<form action="#" method="post">
				<ol>
					<li><input class="text" type="text" name="search_input" id="search_input" /></li>
				</ol>
			</form>
			<a class="search_button" id="search_button" href="#"></a>
		</div>	
    </div>
    <div id="breadcrumb_box" class="breadcrumb_box">    	
    </div>
	<div class="net_gridwrapper">
    	<div class="tree_box">
        	<div class="tree_boxleft"></div>
            <div class="tree_boxmid">
            	<div id="tree_contentbox" class="tree_contentbox">                                     
                </div>
            </div>
            <div class="tree_boxright"></div>
        </div>
        <div class="domain_detailsbox">
        	<div class="domain_detailsbox_left"></div>
            <div class="domain_detailsbox_mid">
            	<div class="domain_detailsbox_contentpanel">           	
                                        
                    <div id="right_panel_detail_content" class="domain_detailsbox_content" style="display:none">      
                        <div id="domain_detail">
	                        <p><div class="domain_detailsbox_label">Name:</div><span id="domain_name"></span></p>
	                        <p><div class="domain_detailsbox_label">ID:</div><span id="domain_id"></span></p>	    
	                        <p><div class="domain_detailsbox_label">Accounts:</div><span id="redirect_to_account_page" class="domain_search_contentlinks"></span></p>                  
	                        <p><div class="domain_detailsbox_label">Instances:</div><span id="redirect_to_instance_page" class="domain_search_contentlinks"></span></p>
	                        <p><div class="domain_detailsbox_label">Volume:</div><span id="redirect_to_volume_page" class="domain_search_contentlinks"></span></p>
							<div id="limits_container" style="display:none"><p><div class="domain_detailsbox_label">Limits:</div><span class="domain_search_contentlinks"><a href="#" id="account_resource_limits">change resource limits</a></span></p></div>
	                        <!--
	                        <p><span>Snapshots:</span><span id="domain_snapshot"></span></p> 	
	                        -->                                               
	                        <p><div class="domain_detailsbox_label">Admins:</div></p>
	                        <div id="right_panel_grid" class="domain_detailsgridcontainer" style="display:block;"> 
		                        <div id="grid_header" class="grid_header">
        	                        <div class="grid_genheader_cell" style="width:100px;">
				                        <div id="grid_header_cell1" class="grid_headertitles">Domain</div>
			                        </div>
			                        <div class="grid_genheader_cell" style="width:200px;">
				                        <div id="grid_header_cell2" class="grid_headertitles">Account</div>
			                        </div>        	                       		               
		                        </div>
		                        <div id="grid_content">                            	 
                                </div>
	                        </div>	                  
	                    </div>                                                            
                    </div>                       
 
    	            <div id="right_panel_search_result" class="domain_search_contentpanel" style="display:none">
                    	<div class="domain_searchicon"></div>
        	            <div class="domain_searchtitle">Search Results</div>
                        <div id="search_results_container" class="domain_search_contentbox">                       
                        </div>
                    </div>                      
                                                    
                </div>                
            </div>
            <div class="domain_detailsbox_right"></div>
        </div>   
    
    </div>
</div>

<!-- treenode template (begin) -->
<div id="treenode_template" class="tree_levelspanel" style="display:none">
	<div class="tree_levelsbox" style="margin-left:20px;">
        <div id="domain_title_container" class="tree_levels">
            <div id="domain_expand_icon" class="zonetree_closedarrows"></div>
            <div id="domain_name" class="tree_links">Domain Name</div>
        </div>
		<div id="domain_children_container" style="display:none">
		</div>   
    </div>
</div>
<!-- treenode template (end) -->

<!-- admin grid row template (begin) -->
<div id="grid_row_template" style="height:24px;display:none">
    <div class="grid_smallgenrow_cell" style="width:100px;">
	    <div id="grid_row_cell1" class="netgrid_celltitles">Domain</div>
    </div>
    <div class="grid_smallgenrow_cell" style="width:200px;">
	    <div id="grid_row_cell2" class="netgrid_celltitles">Account</div>
    </div>   
</div>
<!-- admin grid row template (end) -->

<!-- search result template (begin) -->
<div id="search_result_template" class="domain_search_contentlinksbox" style="display:none">
    <div class="arrow_bullet"></div>
    <div class="domain_search_contentlinks" id="domain_name">Domain Name</div>
</div>
<!-- search result template (end) -->

<!-- breadcrumb piece template (begin)-->
<div id="breadcrumb_piece_template" class="breadcrumb_contentlinks" style="display:none">breadcrumb</div>
<!-- breadcrumb piece template (end)-->

<div id="dialog_resource_limits" title="Resource Limits" style="display:none">
	<p>Please specify limits to the various resources.  A "-1" means the resource has no limits.</p>
	<div class="dialog_formcontent">
		<form action="#" method="post" id="form_acquire">
			<ol>
				<li>
					<label for="user_name">Instance Limit:</label>
					<input class="text" type="text" name="limits_vm" id="limits_vm" value="-1" />
					<div id="limits_vm_errormsg" class="dialog_formcontent_errormsg" style="display:none;"></div> 
				</li>
				<li>
					<label for="user_name">Public IP Limit:</label>
					<input class="text" type="text" name="limits_ip" id="limits_ip" value="-1" />
					<div id="limits_ip_errormsg" class="dialog_formcontent_errormsg" style="display:none;"></div> 
				</li>
				<li>
					<label for="user_name">Disk Volume Limit:</label>
					<input class="text" type="text" name="limits_volume" id="limits_volume" value="-1" />
					<div id="limits_volume_errormsg" class="dialog_formcontent_errormsg" style="display:none;"></div> 
				</li>
				<li>
					<label for="user_name">Snapshot Limit:</label>
					<input class="text" type="text" name="limits_snapshot" id="limits_snapshot" value="-1" />
					<div id="limits_snapshot_errormsg" class="dialog_formcontent_errormsg" style="display:none;"></div> 
				</li>
				<li>
					<label for="user_name">Template Limit:</label>
					<input class="text" type="text" name="limits_template" id="limits_template" value="-1" />
					<div id="limits_template_errormsg" class="dialog_formcontent_errormsg" style="display:none;"></div> 
				</li>
			</ol>
		</form>
	</div>
</div>