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

package com.vmops.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.server.ManagementServerImpl;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.user.AccountVO;
import com.vmops.configuration.ResourceCount;
import com.vmops.configuration.ResourceLimitVO;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.configuration.dao.ResourceCountDao;
import com.vmops.configuration.dao.ResourceLimitDao;
import com.vmops.domain.DomainVO;
import com.vmops.domain.dao.DomainDao;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.user.dao.AccountDao;
import com.vmops.user.dao.UserDao;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.db.Filter;
import com.vmops.utils.db.SearchCriteria;

@Local(value={AccountManager.class})
public class AccountManagerImpl implements AccountManager {
	public static final Logger s_logger = Logger.getLogger(AccountManagerImpl.class.getName());
	
	private String _name;
	private AccountDao _accountDao;
	private DomainDao _domainDao;
	private UserDao _userDao;
	private VMTemplateDao _templateDao;
	ResourceLimitDao _resourceLimitDao;
	ResourceCountDao _resourceCountDao;
	
	@Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
    	_name = name;
    	final ComponentLocator locator = ComponentLocator.getCurrentLocator();
    	
    	_accountDao = locator.getDao(AccountDao.class);
        if (_accountDao == null) {
            throw new ConfigurationException("Unable to get the account dao.");
        }
        
        _domainDao = locator.getDao(DomainDao.class);
        if (_domainDao == null) {
        	throw new ConfigurationException("Unable to get the domain dao.");
        }
        
        _userDao = locator.getDao(UserDao.class);
        if (_userDao == null) {
            throw new ConfigurationException("Unable to get the user dao.");
        }
        
        _templateDao = locator.getDao(VMTemplateDao.class);
        if (_templateDao == null) {
        	throw new ConfigurationException("Unable to get the template dao.");
        }
        
        _resourceLimitDao = locator.getDao(ResourceLimitDao.class);
        if (_resourceLimitDao == null) {
            throw new ConfigurationException("Unable to get " + ResourceLimitDao.class.getName());
        }
        
        _resourceCountDao = locator.getDao(ResourceCountDao.class);
        if (_resourceCountDao == null) {
            throw new ConfigurationException("Unable to get " + ResourceCountDao.class.getName());
        }
    	
    	return true;
    }
	
    @Override
    public String getName() {
        return _name;
    }
	
    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }
    
    /*
	public List<VMTemplateVO> findAllIsosForUser(long userId) {
		s_logger.warn("******* findAllIsosForUser called with userId: " + userId);
		
		List<VMTemplateVO> allIsos = new ArrayList<VMTemplateVO>();
		List<VMTemplateVO> currentIsos;
		
		// Get the User
		UserVO user = _userDao.findById(userId);
		
		// Get the Account
		AccountVO account;
		if (user != null) {
			account = _accountDao.findById(user.getAccountId());
		} else {
			s_logger.warn("********* Could not find user with ID " + userId);
			s_logger.debug("Could not find user with ID " + userId);
			return allIsos;
		}
		
		// Add ISOs for this account to the running list
		if (account != null) {
			s_logger.warn("********* Finding ISOs for account ID: " + account.getId());
			currentIsos = _templateDao.findIsosByIdAndPath(null, account.getId(), null);
			if (currentIsos != null) {
				for (VMTemplateVO iso : currentIsos)
					s_logger.warn("******** Adding iso: " + iso.getName() + " to running list.");
				
				allIsos.addAll(currentIsos);
			}
		} else {
			s_logger.warn("********** Could not find account for user with ID " + userId);
			s_logger.debug("Could not find account for user with ID " + userId);
			return allIsos;
		}
		
		// Recursively check the account's parent Domains, and add any ISOs we find to the running list
		DomainVO parent;
		Long parentId = account.getDomainId();
		
		if (parentId != null)
			parent = _domainDao.findById(account.getDomainId());
		else
			parent= null;
		
		while (parent != null) {
			s_logger.warn("********* Finding ISOs for domain ID: " + parent.getId());
			currentIsos = _templateDao.findIsosByIdAndPath(parent.getId(), null, null);
			if (currentIsos != null) {
				for (VMTemplateVO iso : currentIsos)
					s_logger.warn("******** Adding iso: " + iso.getName() + " to running list.");
				
				allIsos.addAll(currentIsos);
			}
			
			parentId = parent.getParent();
			if (parentId != null)
				parent = _domainDao.findById(parentId);
			else
				parent = null;
		}
		
		return allIsos;
	}
	*/
    
    public void incrementResourceCount(long accountId, ResourceType type, Long...delta) {
    	long numToIncrement = (delta.length == 0) ? 1 : delta[0].longValue();
    	_resourceCountDao.updateCount(accountId, type, true, numToIncrement);
    }
    
    public void decrementResourceCount(long accountId, ResourceType type, Long...delta) {
    	long numToDecrement = (delta.length == 0) ? 1 : delta[0].longValue();
    	_resourceCountDao.updateCount(accountId, type, false, numToDecrement);
    }
    
    public long findCorrectResourceLimit(AccountVO account, ResourceType type) {
    	long max = -1;
    	
    	// Check account 
		ResourceLimitVO limit = _resourceLimitDao.findByAccountIdAndType(account.getId(), type);
		
		if (limit != null) {
			max = limit.getMax().longValue();
		}

		// If the account has an infinite limit, check the account's parent domain, and then the ROOT domain
		Long[] domainIds = {account.getDomainId(), DomainVO.ROOT_DOMAIN};
		for (Long domainId : domainIds) {
			if (domainId != null) {
				limit = _resourceLimitDao.findByDomainIdAndType(domainId, type);
				
				if (limit != null) {
					max = limit.getMax().longValue();
				}
				
				if (max >= 0) {
					break;
				}
			}
		}
		
		return max;
    }
    
    public boolean resourceLimitExceeded(AccountVO account, ResourceType type) {
    	// Don't place any limits on system or admin accounts
    	long accountType = account.getType();
		if (accountType == Account.ACCOUNT_TYPE_ADMIN || accountType == Account.ACCOUNT_ID_SYSTEM) {
			return false;
		}
		
		long max = findCorrectResourceLimit(account, type);
		
		if (max >= 0) {
			long potentialCount = _resourceCountDao.getCount(account.getId(), type) + 1;
			return (potentialCount > max);
		} else {
			return false;
		}

    }
    
    public long getResourceCount(AccountVO account, ResourceType type) {
    	return _resourceCountDao.getCount(account.getId(), type);
    }
    
    public ResourceLimitVO createResourceLimit(Long domainId, Long accountId, ResourceType type, Long max) throws InvalidParameterValueException  {
    	// Either a domainId or an accountId must be passed in, but not both.
        if ((domainId == null && accountId == null) || (domainId != null && accountId != null))
            throw new InvalidParameterValueException("Either a domain ID or an account ID must be passed in, but not both.");
        
        // Check if the domain or account exists
        DomainVO domain = null;
        if (domainId != null) {
            if ((domain = _domainDao.findById(domainId)) == null) {
                throw new InvalidParameterValueException("Please specify a valid domain ID.");
            }
        } else if (accountId != null) {
            if (_accountDao.findById(accountId) == null) {
                throw new InvalidParameterValueException("Please specify a valid account ID.");
            }
        }
        
        // If a domain was passed in, make sure it is the ROOT domain
        if (domainId != null && !domainId.equals(DomainVO.ROOT_DOMAIN)) {
        	throw new InvalidParameterValueException("Please specify either an account, or the ROOT domain.");
        }

        // A valid limit type must be passed in
        if (type == null) {
            throw new InvalidParameterValueException("A valid limit type must be passed in.");
        }

        // Check if a limit with the specified domainId/accountId/type combo already exists
        Filter searchFilter = new Filter(ResourceLimitVO.class, null, false, null, null);
        SearchCriteria sc = _resourceLimitDao.createSearchCriteria();

        if (domainId != null) {
            sc.addAnd("domainId", SearchCriteria.Op.EQ, domainId);
        }

        if (accountId != null) {
            sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
        }

        if (type != null) {
            sc.addAnd("type", SearchCriteria.Op.EQ, type);
        }

        List<ResourceLimitVO> limits = _resourceLimitDao.search(sc, searchFilter);
        if (limits.size() > 0) {
            throw new InvalidParameterValueException("A limit with the specified domain/account ID and type already exists.");
        }

        // Persist the new Limit
        Long id = _resourceLimitDao.persist(new ResourceLimitVO(domainId, accountId, type, max));
        return _resourceLimitDao.findById(id);
    }
    
    public boolean updateResourceLimit(long limitId, Long max) {
    	return _resourceLimitDao.update(limitId, max);
    }

}
