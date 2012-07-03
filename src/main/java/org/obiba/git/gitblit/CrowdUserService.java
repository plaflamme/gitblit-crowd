/*******************************************************************************
 * Copyright 2012(c) OBiBa. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0.
 * 
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>.
 ******************************************************************************/
package org.obiba.git.gitblit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.protocol.http.WebRequestCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.crowd.exception.ApplicationAccessDeniedException;
import com.atlassian.crowd.exception.ApplicationPermissionException;
import com.atlassian.crowd.exception.CrowdException;
import com.atlassian.crowd.exception.InvalidAuthenticationException;
import com.atlassian.crowd.exception.InvalidTokenException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.integration.http.CrowdHttpAuthenticator;
import com.atlassian.crowd.integration.http.CrowdHttpAuthenticatorImpl;
import com.atlassian.crowd.integration.http.util.CrowdHttpTokenHelperImpl;
import com.atlassian.crowd.integration.http.util.CrowdHttpValidationFactorExtractorImpl;
import com.atlassian.crowd.integration.rest.service.factory.RestCrowdClientFactory;
import com.atlassian.crowd.model.group.Group;
import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.search.query.entity.restriction.NullRestrictionImpl;
import com.atlassian.crowd.service.client.ClientProperties;
import com.atlassian.crowd.service.client.ClientPropertiesImpl;
import com.atlassian.crowd.service.client.CrowdClient;
import com.gitblit.GitBlit;
import com.gitblit.IStoredSettings;
import com.gitblit.IUserService;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * Implements {@code IUserService} on Crowd REST API. This implementation allows authenticating users and will use Crowd
 * groups as GitBlit teams.
 * <p>
 * Since {@code IUserService} is also responsible for authorization, this implementation will also store permissions in
 * a configurable file location. It uses a custom format instead of GitBlit's {@code conf} or {@code properties} format
 * since these are user and team oriented, instead of repository oriented.
 * <p>
 * Configuration is done through the gitblit's configuration file with the following keys:
 * 
 * <pre>
 * crowd.serverUrl=http://crowd.domain.com/
 * crowd.applicationName=gitblit
 * crowd.applicationPassword=my-super-secret-password
 * crowd.permFile=perms.xml
 * # Optionally specify a list of Crowd groups that will have gitblit admin permission
 * crowd.adminGroups=domain-administrators gitblit-administrators
 * </pre>
 * 
 * @author Philippe Laflamme
 */
public class CrowdUserService implements IUserService {

  private static final Logger log = LoggerFactory.getLogger(CrowdUserService.class);

  private final int BATCH_SIZE = 100;

  private final Set<String> adminGroups = new HashSet<String>();

  private CrowdClient client;

  private RepositoryPermissionsManager repoPermissions;

  // Used for SSO
  private CrowdHttpAuthenticator authenticator;

  @Override
  public UserModel authenticate(String username, char[] passwd) {
    try {
      User crowdUser = doCrowdauthenticate(username, new String(passwd));
      UserModel model = makeUserModel(crowdUser.getName());
      log.info("user {} successfully authenticated", username);
      return model;
    } catch(CrowdException e) {
      log.info("unable to authenticate user {}: {}", username, e.getMessage());
    } catch(ApplicationPermissionException e) {
      log.warn("unable to authenticate to crowd: {}", e.getMessage());
    }
    return null;
  }

  @Override
  public UserModel getUserModel(String username) {
    return makeUserModel(username);
  }

  @Override
  public TeamModel getTeamModel(final String teamName) {
    return makeTeamModel(teamName);
  }

  @Override
  public List<String> getAllTeamNames() {
    return loop(new GroupNameLoop());
  }

  @Override
  public List<String> getAllUsernames() {
    return loop(new UserNameLoop());
  }

  @Override
  public List<TeamModel> getAllTeams() {
    // Not implemented. This is used for managing teams from gitblit
    return Collections.emptyList();
  }

  @Override
  public List<UserModel> getAllUsers() {
    // Not implemented. This is used for managing users from gitblit
    return Collections.emptyList();
  }

  @Override
  public List<String> getTeamnamesForRepositoryRole(String role) {
    return repoPermissions.repoTeams(role);
  }

  @Override
  public List<String> getUsernamesForRepositoryRole(String role) {
    return repoPermissions.repoUsers(role);
  }

  @Override
  public boolean renameRepositoryRole(String original, String newName) {
    repoPermissions.renameRepo(original, newName);
    return true;
  }

  @Override
  public boolean setTeamnamesForRepositoryRole(String role, List<String> teams) {
    repoPermissions.setRepoTeams(role, teams);
    return true;
  }

  @Override
  public boolean setUsernamesForRepositoryRole(String role, List<String> users) {
    repoPermissions.setRepoUsers(role, users);
    return true;
  }

  @Override
  public boolean deleteRepositoryRole(String role) {
    repoPermissions.deleteRepo(role);
    return true;
  }

  @Override
  public void setup(IStoredSettings settings) {
    File crowdFile = GitBlit.getFileOrFolder(settings.getString("crowd.properties", "crowd.properties"));
    Properties crowdProperties = loadCrowdProperties(crowdFile);
    if(crowdProperties != null) {
      ClientProperties crowdClientProperties = ClientPropertiesImpl.newInstanceFromProperties(crowdProperties);

      this.client = new RestCrowdClientFactory().newInstance(crowdClientProperties);

      // SSO Necessary stuff
      this.authenticator =
          new CrowdHttpAuthenticatorImpl(client, crowdClientProperties,
              CrowdHttpTokenHelperImpl.getInstance(CrowdHttpValidationFactorExtractorImpl.getInstance()));

      File permsFile = GitBlit.getFileOrFolder(settings.getString("crowd.permFile", "perms.xml"));
      log.info("crowd permissions file {}", permsFile.getAbsolutePath());
      this.repoPermissions = new RepositoryPermissionsManager(permsFile);

      // Populate the list of groups with administrative privileges
      this.adminGroups.addAll(settings.getStrings("crowd.adminGroups"));
      log.info("crowd groups with admin privileges {}", this.adminGroups);
    }
  }

  @Override
  public UserModel authenticate(char[] cookieValue) {
    WebRequestCycle requestCycle = (WebRequestCycle) WebRequestCycle.get();
    if(requestCycle != null) {
      HttpServletRequest request = requestCycle.getWebRequest().getHttpServletRequest();
      HttpServletResponse response = requestCycle.getWebResponse().getHttpServletResponse();
      try {
        // Determines if the request has a valid SSO token
        if(authenticator.isAuthenticated(request, response)) {
          return makeUserModel(authenticator.getUser(request).getName());
        }
      } catch(CrowdException e) {
        // ignore
      } catch(ApplicationPermissionException e) {
        // ignore
      } catch(InvalidTokenException e) {
        // ignore
      }
    }
    return null;
  }

  @Override
  public boolean supportsCookies() {
    return true;
  }

  @Override
  public boolean supportsCredentialChanges() {
    return false;
  }

  @Override
  public boolean supportsDisplayNameChanges() {
    return false;
  }

  @Override
  public boolean supportsEmailAddressChanges() {
    return false;
  }

  @Override
  public boolean supportsTeamMembershipChanges() {
    return false;
  }

  @Override
  public char[] getCookie(UserModel user) {
    // Return some value.
    return StringUtils.getSHA1(user.getName()).toCharArray();
  }
  
  @Override
  public void logout(UserModel user) {
    doCrowdLogout();
  }

  // Everything else is not implemented

  @Override
  public boolean updateTeamModel(TeamModel model) {
    return false;
  }

  @Override
  public boolean updateTeamModel(String teamname, TeamModel model) {
    return false;
  }

  @Override
  public boolean updateUserModel(UserModel arg0) {
    return false;
  }

  @Override
  public boolean updateUserModel(String username, UserModel model) {
    return false;
  }

  @Override
  public boolean deleteTeam(String arg0) {
    return false;
  }

  @Override
  public boolean deleteTeamModel(TeamModel arg0) {
    return false;
  }

  @Override
  public boolean deleteUser(String arg0) {
    return false;
  }

  @Override
  public boolean deleteUserModel(UserModel arg0) {
    return false;
  }

  private User doCrowdauthenticate(String username, String passwd) throws CrowdException,
      ApplicationPermissionException {
    WebRequestCycle requestCycle = (WebRequestCycle) WebRequestCycle.get();
    if(requestCycle != null) {
      // Try an SSO authentication
      HttpServletRequest request = requestCycle.getWebRequest().getHttpServletRequest();
      HttpServletResponse response = requestCycle.getWebResponse().getHttpServletResponse();
      try {
        return this.authenticator.authenticate(request, response, username, passwd);
      } catch(InvalidTokenException e) {
        // ignore
      } catch(ApplicationAccessDeniedException e) {
        // ignore
      }
    }
    return client.authenticateUser(username, passwd);
  }
  
  private void doCrowdLogout() {
    WebRequestCycle requestCycle = (WebRequestCycle) WebRequestCycle.get();
    if(requestCycle != null) {
      // Try an SSO authentication
      HttpServletRequest request = requestCycle.getWebRequest().getHttpServletRequest();
      HttpServletResponse response = requestCycle.getWebResponse().getHttpServletResponse();
      try {
        this.authenticator.logout(request, response);
      } catch(ApplicationPermissionException e) {
        // ignore
      } catch(InvalidAuthenticationException e) {
        // ignore
      } catch(OperationFailedException e) {
        // ignore
      }
    }
  }

  private Properties loadCrowdProperties(File crowdProperties) {
    Properties clientProperties = new Properties();
    FileInputStream is = null;
    try {
      clientProperties.load(is = new FileInputStream(crowdProperties));
      return clientProperties;
    } catch(FileNotFoundException e) {
      log.error("crowd.properties file {} does not exist.", crowdProperties.getAbsolutePath());
      return null;
    } catch(IOException e) {
      log.error("error reading crowd.properties file {}.", crowdProperties.getAbsolutePath());
      throw new RuntimeException(e);
    } finally {
      if(is != null) {
        try {
          is.close();
        } catch(IOException e) {
        }
      }
    }
  }

  private boolean isAdminGroup(Group g) {
    return adminGroups.contains(g.getName());
  }

  private TeamModel makeTeamModel(String teamName) {
    TeamModel team = new TeamModel(teamName);
    for(User user : loop(new UserLoop(teamName))) {
      team.users.add(user.getName());
    }
    this.repoPermissions.populateTeamModel(team);
    return team;
  }

  private UserModel makeUserModel(String username) {
    UserModel gitblitUser = new UserModel(username);
    for(Group g : loop(new GroupLoop(username))) {
      TeamModel team = new TeamModel(g.getName());
      gitblitUser.teams.add(team);
      if(isAdminGroup(g)) {
        gitblitUser.canAdmin = true;
      }
    }
    this.repoPermissions.populateUserModel(gitblitUser);
    return gitblitUser;
  }

  private <T> List<T> loop(CrowdLoop<T> loop) {
    List<T> gather = new LinkedList<T>();
    this.loop(gather, loop);
    return gather;
  }

  /**
   * Boilerplate code for populating a collection from multiple calls to the Crowd client.
   * 
   * @param gather collection to populate
   * @param loop the method to invoke that returns a portion of the collection
   */
  private <T> void loop(Collection<T> gather, CrowdLoop<T> loop) {
    int start = 0;
    Collection<T> partial = null;
    try {
      do {
        partial = loop.loop(start);
        gather.addAll(partial);
        start += BATCH_SIZE;
      } while(partial != null && partial.size() >= BATCH_SIZE);
    } catch(ApplicationPermissionException e) {
      log.warn("unable to authenticate to crowd", e);
    } catch(CrowdException e) {
      log.warn("unexpected crowd exception", e);
    }
  }

  /**
   * Abstracts the actual method invocation of a crowd client method that fetches a portion of a list (method has
   * {@code start} and {@code max} parameters)
   */
  private interface CrowdLoop<T> {

    /**
     * Invokes the actual method with {@code start} and {@code BATCH_SIZE}
     */
    Collection<T> loop(int start) throws CrowdException, ApplicationPermissionException;
  }

  /**
   * Users of a group
   */
  private final class UserLoop implements CrowdLoop<User> {

    private final String groupName;

    UserLoop(String groupName) {
      this.groupName = groupName;
    }

    public Collection<User> loop(int start) throws CrowdException, ApplicationPermissionException {
      return client.getUsersOfGroup(groupName, start, BATCH_SIZE);
    }
  }

  /**
   * Groups of a user
   */
  private final class GroupLoop implements CrowdLoop<Group> {

    private final String username;

    private GroupLoop(String username) {
      this.username = username;
    }

    public Collection<Group> loop(int start) throws CrowdException, ApplicationPermissionException {
      return client.getGroupsForUser(username, start, BATCH_SIZE);
    }

  }

  /**
   * All group names
   */
  private final class GroupNameLoop implements CrowdLoop<String> {

    public Collection<String> loop(int start) throws CrowdException, ApplicationPermissionException {
      return client.searchGroupNames(NullRestrictionImpl.INSTANCE, start, BATCH_SIZE);
    }

  }

  /**
   * All user names
   */
  private final class UserNameLoop implements CrowdLoop<String> {
    public Collection<String> loop(int start) throws CrowdException, ApplicationPermissionException {
      return client.searchUserNames(NullRestrictionImpl.INSTANCE, start, BATCH_SIZE);
    }
  }

}
