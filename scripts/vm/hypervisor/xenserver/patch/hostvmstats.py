#!/usr/bin/python
# $Id: hostvmstats.py 9132 2010-06-04 20:17:43Z manuel $ $HeadURL: svn://svn.lab.vmops.com/repos/branches/2.0.0/java/scripts/vm/hypervisor/xenserver/patch/hostvmstats.py $

import XenAPI
import urllib
                      
def get_xapi_session():
    xapi = XenAPI.xapi_local();
    xapi.login_with_password("","")
    return xapi._session

def get_stats(collect_host_stats, consolidation_function, interval, start_time):
    session = get_xapi_session()
    
    url = "http://localhost/rrd_updates?"
    url += "session_id=" + session
    url += "&host=" + collect_host_stats
    url += "&cf=" + consolidation_function
    url += "&interval=" + str(interval)
    url += "&start=" + str(start_time)
    
    sock = urllib.URLopener().open(url)
    xml = sock.read()
    sock.close()
    return xml

