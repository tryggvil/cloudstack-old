/**
 *  Copyright (C) 2010 VMOps, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.  
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.vmops.storage.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.storage.Storage;
import com.vmops.storage.VMTemplateVO;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;

@Local(value={VMTemplateDao.class})
public class VMTemplateDaoImpl extends GenericDaoBase<VMTemplateVO, Long> implements VMTemplateDao {
    private static final Logger s_logger = Logger.getLogger(VMTemplateDaoImpl.class);

    private final String SELECT_ALL = "SELECT t.id, t.unique_name, t.name, t.public, t.type, t.hvm, t.bits, t.url, t.format, t.created, t.account_id, " +
                                       "t.checksum, t.ready, t.create_status, t.display_text, t.enable_password, t.guest_os_id, t.bootable FROM vm_template t";

    protected SearchBuilder<VMTemplateVO> TemplateNameSearch;
    protected SearchBuilder<VMTemplateVO> UniqueNameSearch;
    protected SearchBuilder<VMTemplateVO> AccountIdSearch;

    protected SearchBuilder<VMTemplateVO> PublicSearch;
    private String routerTmpltName;
    private String consoleProxyTmpltName;
    
    protected VMTemplateDaoImpl() {
    }
    
    public List<VMTemplateVO> listByPublic() {
    	SearchCriteria sc = PublicSearch.create();
    	sc.setParameters("public", 1);
	    return listActiveBy(sc);
	}
    
	@Override
	public VMTemplateVO findByName(String templateName) {
		SearchCriteria sc = UniqueNameSearch.create();
		sc.setParameters("uniqueName", templateName);
		return findOneBy(sc);
	}

	@Override
	public VMTemplateVO findRoutingTemplate() {
		SearchCriteria sc = UniqueNameSearch.create();
		sc.setParameters("uniqueName", routerTmpltName);
		return findOneBy(sc);
	}
	
	@Override
	public VMTemplateVO findConsoleProxyTemplate() {
		SearchCriteria sc = UniqueNameSearch.create();
		sc.setParameters("uniqueName", consoleProxyTmpltName);
		return findOneBy(sc);
	}
	
	@Override
	public List<VMTemplateVO> listReadyTemplates() {
		SearchCriteria sc = createSearchCriteria();
		sc.addAnd("ready", SearchCriteria.Op.EQ, true);
		sc.addAnd("format", SearchCriteria.Op.NEQ, Storage.ImageFormat.ISO);
		return listBy(sc);
	}
	
	@Override
	public List<VMTemplateVO> findIsosByIdAndPath(Long domainId, Long accountId, String path) {
		SearchCriteria sc = createSearchCriteria();
		sc.addAnd("iso", SearchCriteria.Op.EQ, true);
		if (domainId != null)
			sc.addAnd("domainId", SearchCriteria.Op.EQ, domainId);
		if (accountId != null)
			sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
		if (path != null)
			sc.addAnd("path", SearchCriteria.Op.EQ, path);
		return listBy(sc);
	}

	@Override
	public List<VMTemplateVO> listByAccountId(long accountId) {
        SearchCriteria sc = AccountIdSearch.create();
        sc.setParameters("accountId", accountId);
        sc.setParameters("publicTemplate", false);
        return listActiveBy(sc);
	}

	@Override
	public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
		boolean result = super.configure(name, params);
		
	    PublicSearch = createSearchBuilder();
	    PublicSearch.addAnd("public", PublicSearch.entity().isPublicTemplate(), SearchCriteria.Op.EQ);

		routerTmpltName = (String)params.get("routing.uniquename");
		
		s_logger.debug("Found parameter routing unique name " + routerTmpltName);
		if (routerTmpltName==null) {
			routerTmpltName="routing";
		}
		
		consoleProxyTmpltName = (String)params.get("consoleproxy.uniquename");
		if(consoleProxyTmpltName == null)
			consoleProxyTmpltName = "routing";
		if(s_logger.isDebugEnabled())
			s_logger.debug("Use console proxy template : " + consoleProxyTmpltName);
		
		TemplateNameSearch = createSearchBuilder();
		TemplateNameSearch.addAnd("name", TemplateNameSearch.entity().getName(), SearchCriteria.Op.EQ);
		UniqueNameSearch = createSearchBuilder();
		UniqueNameSearch.addAnd("uniqueName", UniqueNameSearch.entity().getUniqueName(), SearchCriteria.Op.EQ);

		AccountIdSearch = createSearchBuilder();
		AccountIdSearch.addAnd("accountId", AccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        AccountIdSearch.addAnd("publicTemplate", AccountIdSearch.entity().isPublicTemplate(), SearchCriteria.Op.EQ);
		AccountIdSearch.done();

		return result;
	}

	@Override
	public String getRoutingTemplateUniqueName() {
		return routerTmpltName;
	}

	@Override
	public List<VMTemplateVO> searchTemplates(String name, String keyword, Boolean isReady, Boolean isPublic, boolean isIso, Boolean bootable, Long accountId, Integer pageSize, Long startIndex) {
        Transaction txn = Transaction.currentTxn();
        List<VMTemplateVO> templates = new ArrayList<VMTemplateVO>();
        PreparedStatement pstmt = null;
        try {
            String sql = SELECT_ALL;

            // prepare the where, order by, and limit clauses
            String whereClause = "";
            if ((isPublic != null) && (isPublic.booleanValue() == true) &&
                (isReady != null) && (isReady.booleanValue() == true)) {
                whereClause = getPublicReadyTemplateWhere();
            } else if ((isPublic != null) && (isPublic.booleanValue() == true) &&
                       (isReady != null) && (isReady.booleanValue() == false)) {
                whereClause = getPublicNonReadyTemplateWhere(accountId);
            } else if ((isPublic != null) && (isPublic.booleanValue() == true) && (isReady == null)) {
                whereClause = getPublicTemplateWhere(accountId);
            } else if ((isPublic != null) && (isPublic.booleanValue() == false) &&
                       (isReady != null) && (isReady.booleanValue() == true)) {
                whereClause = getReadyPrivateTemplateWhere(accountId);
            } else if ((isPublic != null) && (isPublic.booleanValue() == false) &&
                       (isReady != null) && (isReady.booleanValue() == false)) {
                whereClause = getNonReadyPrivateTemplateWhere(accountId);
            } else if ((isPublic != null) && (isPublic.booleanValue() == false) && (isReady == null)) {
                whereClause = getPrivateTemplateWhere(accountId);
            } else if ((isPublic == null) && (isReady != null) && (isReady.booleanValue() == true)) {
                whereClause = getReadyTemplateWhere(accountId);
            } else if ((isPublic == null) && (isReady != null) && (isReady.booleanValue() == false)) {
                whereClause = getNonReadyTemplateWhere(accountId);
            } else if ((isPublic == null) && (isReady == null)) {
                whereClause = getTemplateWhere(accountId);
            }
            if ((whereClause == null) || "".equals(whereClause)) {
                whereClause = " WHERE";
            } else {
                whereClause += " AND";
            }
            sql += whereClause + getExtrasWhere(name, keyword, isIso, bootable) + getOrderByLimit(pageSize, startIndex);

            pstmt = txn.prepareAutoCloseStatement(sql);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                templates.add(toEntityBean(rs, false));
            }
        } catch (Exception e) {
            s_logger.warn("Error listing templates", e);
        }
        return templates;
	}

	private String getExtrasWhere(String name, String keyword, boolean isIso, Boolean bootable) {
	    String sql = "";
        if (keyword != null) {
            sql += " t.name LIKE '%" + keyword + "%' AND";
        } else if (name != null) {
            sql += " t.name LIKE '%" + name + "%' AND";
        }

        if (isIso) {
            sql += " t.format = 'ISO'";
        } else {
            sql += " t.format <> 'ISO'";
        }
        
        if (bootable != null) {
        	sql += " AND t.bootable = " + bootable;
        }
        
        sql += " AND t.create_status != 'Corrupted'";

        sql += " AND t.removed IS NULL";

        return sql;
	}

	private String getOrderByLimit(Integer pageSize, Long startIndex) {
        String sql = " ORDER BY t.created DESC";
        if ((pageSize != null) && (startIndex != null)) {
            sql += " LIMIT " + startIndex.toString() + "," + pageSize.toString();
        }
        return sql;
	}

	private String getPublicReadyTemplateWhere() {
        String sql = " WHERE t.public = 1 AND t.ready = 1";

        return sql;
	}

	private String getPublicNonReadyTemplateWhere(Long accountId) {
        String sql = " WHERE t.public = 1 AND t.ready = 0";
        if (accountId != null) {
            sql += " AND t.account_id = " + accountId.toString();
        }

        return sql;
    }

	private String getPublicTemplateWhere(Long accountId) {
        String sql = " WHERE t.public = 1";
        if (accountId != null) {
            sql += " AND ((t.ready = 1) OR (t.ready = 0 AND t.account_id = " + accountId.toString() + "))";
        }

        return sql;
    }

	private String getReadyPrivateTemplateWhere(Long accountId) {
        String sql = "";
        if (accountId == null) {
            sql += " WHERE t.public = 0 AND t.ready = 1";
        } else {
            sql += " LEFT JOIN launch_permission lp ON t.id = lp.template_id WHERE ((t.public = 0 AND t.ready = 1 AND t.account_id = " + accountId.toString() + ") OR" +
                   " (t.public = 0 AND t.ready = 1 AND lp.account_id = " + accountId + "))";
        }

        return sql;
    }

    private String getNonReadyPrivateTemplateWhere(Long accountId) {
        String sql = "";
        if (accountId == null) {
            sql += " WHERE t.public = 0 AND t.ready = 0";
        } else {
            sql += " LEFT JOIN launch_permission lp ON t.id = lp.template_id WHERE ((t.public = 0 AND t.ready = 0 AND t.account_id = " + accountId.toString() + ") OR" +
                   " (t.public = 0 AND t.ready = 0 AND lp.account_id = " + accountId + "))";
        }

        return sql;
    }

    private String getPrivateTemplateWhere(Long accountId) {
        String sql = "";
        if (accountId == null) {
            sql += " WHERE t.public = 0";
        } else {
            sql += " LEFT JOIN launch_permission lp ON t.id = lp.template_id WHERE ((t.public = 0 AND t.account_id = " + accountId.toString() + ") OR" +
                   " (t.public = 0 AND lp.account_id = " + accountId + "))";
        }

        return sql;
    }

    private String getReadyTemplateWhere(Long accountId) {
        String sql = "";
        if (accountId == null) {
            sql += " WHERE t.ready = 1";
        } else {
            sql += " LEFT JOIN launch_permission lp ON t.id = lp.template_id WHERE ((t.public = 1 AND t.ready = 1) OR" +
                   " (t.public = 0 AND t.ready = 1 AND t.account_id = " + accountId.toString() + ") OR" +
                   " (t.public = 0 AND t.ready = 1 AND lp.account_id = " + accountId + "))";
        }

        return sql;
    }

    private String getNonReadyTemplateWhere(Long accountId) {
        String sql = "";
        if (accountId == null) {
            sql += " WHERE t.ready = 0";
        } else {
            sql += " LEFT JOIN launch_permission lp ON t.id = lp.template_id WHERE ((t.ready = 0 AND t.account_id = " + accountId.toString() + ") OR" +
                   " (t.public = 0 AND t.ready = 0 AND lp.account_id = " + accountId + "))";
        }

        return sql;
    }

    private String getTemplateWhere(Long accountId) {
        String sql = "";
        if (accountId != null) {
            sql += " LEFT JOIN launch_permission lp ON t.id = lp.template_id WHERE ((t.public = 1 AND t.ready = 1) OR" +
                   " (t.account_id = " + accountId.toString() + ") OR" +
                   " (t.public = 0 AND lp.account_id = " + accountId + "))";
        }

        return sql;
    }
}
