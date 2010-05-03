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

package com.vmops.user.dao;

import java.util.List;

import javax.ejb.Local;

import com.vmops.user.UserVO;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.exception.VmopsRuntimeException;

/**
 * Implementation of the UserDao
 * 
 * @author Will Chan
 *
 */
@Local(value={UserDao.class})
public class UserDaoImpl extends GenericDaoBase<UserVO, Long> implements UserDao {
    protected SearchBuilder<UserVO> UsernamePasswordSearch;
    protected SearchBuilder<UserVO> UsernameSearch;
    protected SearchBuilder<UserVO> UsernameLikeSearch;
    protected SearchBuilder<UserVO> UserIdSearch;
    protected SearchBuilder<UserVO> AccountIdSearch;
    protected SearchBuilder<UserVO> SecretKeySearch;
    
    protected UserDaoImpl () {
    	UsernameSearch = createSearchBuilder();
    	UsernameSearch.addAnd("username", UsernameSearch.entity().getUsername(), SearchCriteria.Op.EQ);
    	UsernameSearch.done();
    	
        UsernameLikeSearch = createSearchBuilder();
        UsernameLikeSearch.addAnd("username", UsernameLikeSearch.entity().getUsername(), SearchCriteria.Op.LIKE);
        UsernameLikeSearch.done();
        
        AccountIdSearch = createSearchBuilder();
        AccountIdSearch.addAnd("account", AccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        AccountIdSearch.done();
        
        UsernamePasswordSearch = createSearchBuilder();
        UsernamePasswordSearch.addAnd("username", UsernamePasswordSearch.entity().getUsername(), SearchCriteria.Op.EQ);
        UsernamePasswordSearch.addAnd("password", UsernamePasswordSearch.entity().getPassword(), SearchCriteria.Op.EQ);
        UsernamePasswordSearch.done();

        UserIdSearch = createSearchBuilder();
        UserIdSearch.addAnd("id", UserIdSearch.entity().getId(), SearchCriteria.Op.EQ);
        UserIdSearch.done();

        SecretKeySearch = createSearchBuilder();
        SecretKeySearch.addAnd("secretKey", SecretKeySearch.entity().getSecretKey(), SearchCriteria.Op.EQ);
        SecretKeySearch.done();
    }

	@Override
	public UserVO getUser(String username, String password) {
	    SearchCriteria sc = UsernamePasswordSearch.create();
	    sc.setParameters("username", username);
	    sc.setParameters("password", password);
	    return findOneActiveBy(sc);
	}
	
	public List<UserVO> listByAccount(long accountId) {
	    SearchCriteria sc = AccountIdSearch.create();
	    sc.setParameters("account", accountId);
	    return listActiveBy(sc, null);
	}

	@Override
	public UserVO getUser(String username) {
	    SearchCriteria sc = UsernameSearch.create();
	    sc.setParameters("username", username);
	    return findOneActiveBy(sc);
	}

    @Override
    public UserVO getUser(long userId) {
        SearchCriteria sc = UserIdSearch.create();
        sc.setParameters("id", userId);
        return findOneActiveBy(sc);
    }

	@Override
	public List<UserVO> findUsersLike(String username) {
        SearchCriteria sc = UsernameLikeSearch.create();
        sc.setParameters("username", "%" + username + "%");
        return listActiveBy(sc);
    }
	
    @Override
    public UserVO findUserBySecretKey(String secretKey) {
        SearchCriteria sc = SecretKeySearch.create();
        sc.setParameters("secretKey", secretKey);
        return findOneActiveBy(sc);
    }
    
	@Override
	public Long persist(UserVO user) {
	    throw new VmopsRuntimeException("Can not call persist directly on the UserDao.  Use create instead");
	}
	
	@Override
	public synchronized UserVO create(UserVO user) {
	    long id = super.persist(user);
	    UserVO dbUser = findById(id);

        return dbUser;
	}

    @Override
    public void update(long id, String username, String password, String firstname, String lastname, String email, Long accountId) {
        UserVO dbUser = getUser(username);
        if ((dbUser == null) || (dbUser.getId().longValue() == id)) {
            UserVO ub = createForUpdate();
            ub.setUsername(username);
            ub.setPassword(password);
            ub.setFirstname(firstname);
            ub.setLastname(lastname);
            ub.setEmail(email);
            ub.setAccountId(accountId);
            update(id, ub);
        } else {
            throw new VmopsRuntimeException("unable to update user -- a user with that name exists");
        }
    }
}
