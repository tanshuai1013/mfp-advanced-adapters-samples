/*
 * IBM Confidential OCO Source Materials
 *
 * 5725-I43 Copyright IBM Corp. 2006, 2016
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has
 * been deposited with the U.S. Copyright Office.
 */
package com.github.mfpdev.sample.LDAP;

import com.ibm.mfp.security.checks.base.UserAuthenticationSecurityCheck;
import com.ibm.mfp.server.registration.external.model.AuthenticatedUser;
import com.ibm.mfp.server.security.external.checks.SecurityCheckConfiguration;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.util.*;
import java.util.logging.Logger;

/**
 * Sample implementation of ldap security check.
 *
 */
public class LDAPLoginSecurityCheck extends UserAuthenticationSecurityCheck {
    static Logger logger = Logger.getLogger(LDAPSecurityCheckConfig.class.getName());

    private String userId, displayName;
    private String errorMsg;

    @Override
    protected AuthenticatedUser createUser() {
        return new AuthenticatedUser(userId, displayName, this.getName());
    }

    @Override
    public SecurityCheckConfiguration createConfiguration(Properties properties) {
        return new LDAPSecurityCheckConfig(properties);
    }

    /**
     * This method is called by the base class UserAuthenticationSecurityCheck when an authorization
     * request is made that requests authorization for this security check or a scope which contains this security check
     * @param credentials
     * @return true if the credentials are valid, false otherwise
     */
    @Override
    protected boolean validateCredentials(Map<String, Object> credentials) {
        LDAPSecurityCheckConfig config = (LDAPSecurityCheckConfig) getConfiguration();

        if(credentials!=null && credentials.containsKey("username") && credentials.containsKey("password")){
            String username = credentials.get("username").toString();
            String password = credentials.get("password").toString();


            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.PROVIDER_URL, config.getLdapURL());

            String adminDN = config.getLdapAdminDN();
            String passwordAdmin = config.getLdapAdminPassword();

            //LDAP admin is not mandatory
            if (adminDN != null && !adminDN.equals("*") && passwordAdmin != null && !passwordAdmin.equals("*")) {
                env.put(Context.SECURITY_PRINCIPAL, config.getLdapAdminDN());
                env.put(Context.SECURITY_CREDENTIALS, config.getLdapAdminPassword());
            }

            SearchControls sc = new SearchControls();
            String[] attributeFilter = { config.getLdapUserAttribute(),config.getLdapDisplayNameAttribute() };

            sc.setReturningAttributes(attributeFilter);
            sc.setSearchScope(SearchControls.SUBTREE_SCOPE);

            try {
                LdapContext ldapContext = new InitialLdapContext(env,null);
                String searchString = config.getLdapSearchString();
                searchString = searchString.replaceAll("\\{searchValue\\}", username);

                NamingEnumeration<SearchResult> searchResults = ldapContext.search("", searchString, sc);
                ArrayList<SearchResult> searchResultsList = Collections.list(searchResults);
                if (searchResultsList.size() != 1) {
                    logger.info("user" + username + " has more than one instance in the LDAP repository");
                    errorMsg = "Wrong Credentials";
                    return false;
                } else {
                    SearchResult searchResult = searchResultsList.get(0);
                    env.put(Context.SECURITY_PRINCIPAL, searchResult.getName());
                    env.put(Context.SECURITY_CREDENTIALS, password);
                    try {

                        ldapContext = new InitialLdapContext(env, null);
                        userId = (String) searchResult.getAttributes().get(config.getLdapUserAttribute()).get();
                        displayName = (String) searchResult.getAttributes().get(config.getLdapDisplayNameAttribute()).get();
                        return true;
                    } catch (Exception e) {
                        errorMsg = "Wrong Credentials";
                    }
                }
            } catch (Exception e) {
                errorMsg = "Connection to user repository failed";
            }
        }
        else{
            errorMsg = "Credentials not set properly";
        }
        return false;
    }

    /**
     *
     * This method is describes the challenge JSON that gets sent to the client during the authorization process
     * This is called by the base class UserAuthenticationSecurityCheck when validateCredentials returns false and
     * the number of remaining attempts is > 0
     * @return the challenge object
     */
    @Override
    protected Map<String, Object> createChallenge() {
        Map<String, Object> challenge = new HashMap<String, Object>();
        challenge.put("errorMsg",errorMsg);
        challenge.put("remainingAttempts",getRemainingAttempts());
        return challenge;
    }
}
