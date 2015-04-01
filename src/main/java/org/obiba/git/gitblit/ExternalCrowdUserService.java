package org.obiba.git.gitblit;

import com.atlassian.crowd.exception.ApplicationPermissionException;
import com.atlassian.crowd.exception.InvalidAuthenticationException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.integration.http.CrowdHttpAuthenticator;
import com.atlassian.crowd.integration.http.CrowdHttpAuthenticatorImpl;
import com.atlassian.crowd.integration.http.util.CrowdHttpTokenHelperImpl;
import com.atlassian.crowd.integration.http.util.CrowdHttpValidationFactorExtractorImpl;
import com.atlassian.crowd.integration.rest.service.factory.RestCrowdClientFactory;
import com.atlassian.crowd.model.group.Group;
import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.search.query.entity.restriction.NullRestrictionImpl;
import com.atlassian.crowd.search.query.entity.restriction.PropertyImpl;
import com.atlassian.crowd.search.query.entity.restriction.TermRestriction;
import com.atlassian.crowd.service.client.ClientProperties;
import com.atlassian.crowd.service.client.ClientPropertiesImpl;
import com.atlassian.crowd.service.client.CrowdClient;
import com.gitblit.Constants.AccountType;
import com.gitblit.IUserService;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class ExternalCrowdUserService implements IUserService {

    private static CrowdClient client;
    private static CrowdHttpAuthenticator authenticator;
    private static RepositoryPermissionsManager repo;

    private final List<String> adminGroups = new ArrayList<String>();
    private static final Logger log = LoggerFactory.getLogger(ExternalCrowdUserService.class);

    private Properties loadCrowdProperties(final File crowdProperties) {
        Properties clientProperties = new Properties();
        FileInputStream is = null;
        try {
            clientProperties.load(is = new FileInputStream(crowdProperties));
            return clientProperties;
        } catch (FileNotFoundException e) {
            log.error("crowd.properties file {} does not exist.", crowdProperties.getAbsolutePath());
            return null;
        } catch (IOException e) {
            log.error("error reading crowd.properties file {}.", crowdProperties.getAbsolutePath());
            throw new RuntimeException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
    }

    @Override
    public void setup(final IRuntimeManager manager) {

        File crowdFile = manager.getFileOrFolder(manager.getSettings().getString("crowd.properties", "crowd.properties"));
        Properties crowdProperties = loadCrowdProperties(crowdFile);
        if (crowdProperties != null) {
            ClientProperties crowdClientProperties = ClientPropertiesImpl.newInstanceFromProperties(crowdProperties);

            ExternalCrowdUserService.client = new RestCrowdClientFactory().newInstance(crowdClientProperties);
            // SSO Necessary stuff
            ExternalCrowdUserService.authenticator = new CrowdHttpAuthenticatorImpl(client, crowdClientProperties,
                    CrowdHttpTokenHelperImpl.getInstance(CrowdHttpValidationFactorExtractorImpl.getInstance()));

            File permsFile = manager.getFileOrFolder(manager.getSettings().getString("crowd.permFile", "perms.xml"));
            log.info("crowd permissions file {}", permsFile.getAbsolutePath());
            ExternalCrowdUserService.repo = new RepositoryPermissionsManager(permsFile);

            // Populate the list of groups with administrative privileges
            // Populate the list of groups with administrative privileges
            List<String> admins = Arrays.asList(crowdProperties.getProperty("crowd.adminGroups").split(","));
            if (admins.isEmpty()) {
                admins.add("administrators");
            }
            log.info("crowd groups with admin privileges {}", this.adminGroups);
        }
    }

    @Override
    public String getCookie(final UserModel model) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public UserModel getUserModel(final char[] cookie) {
        return null;
    }

    @Override
    public UserModel getUserModel(final String username) {
        return CrowdUtils.mapCrowdUserToModel(client, repo, username);
    }

    @Override
    public boolean updateUserModel(final UserModel model) {
        return false;
    }

    @Override
    public boolean updateUserModels(final Collection<UserModel> models) {
        return false;
    }

    @Override
    public boolean updateUserModel(final String username, final UserModel model) {
        return false;
    }

    @Override
    public boolean deleteUserModel(final UserModel model) {
        return false;
    }

    @Override
    public boolean deleteUser(final String username) {
        return false;
    }

    @Override
    public List<String> getAllUsernames() {
        try {
            return client.searchUserNames(new TermRestriction<Boolean>(new PropertyImpl<Boolean>("active", Boolean.class),
                    true), 0, 4096);
        } catch (OperationFailedException e) {
            e.printStackTrace();
        } catch (InvalidAuthenticationException e) {
            e.printStackTrace();
        } catch (ApplicationPermissionException e) {
            e.printStackTrace();
        }
        return new ArrayList<String>();
    }

    @Override
    public List<UserModel> getAllUsers() {
        List<User> searchUsers = new ArrayList<User>();
        List<UserModel> result = new ArrayList<UserModel>();
        try {
            searchUsers = client.searchUsers(new TermRestriction<Boolean>(new PropertyImpl<Boolean>("active",
                    Boolean.class), true), 0, 4096);
        } catch (OperationFailedException e) {
            e.printStackTrace();
        } catch (InvalidAuthenticationException e) {
            e.printStackTrace();
        } catch (ApplicationPermissionException e) {
            e.printStackTrace();
        }

        for (User u : searchUsers) {
            result.add(CrowdUtils.mapCrowdUserToModel(client, repo, u));
        }

        return result;
    }

    @Override
    public List<String> getAllTeamNames() {
        try {
            List<String> searchGroupNames = client.searchGroupNames(NullRestrictionImpl.INSTANCE, 0, 4096);
            return searchGroupNames;
        } catch (OperationFailedException e) {
            e.printStackTrace();
        } catch (InvalidAuthenticationException e) {
            e.printStackTrace();
        } catch (ApplicationPermissionException e) {
            e.printStackTrace();
        }
        return new ArrayList<String>();
    }

    @Override
    public List<TeamModel> getAllTeams() {

        try {
            List<Group> searchGroups = client.searchGroups(NullRestrictionImpl.INSTANCE, 0, 4096);
            List<TeamModel> result = new ArrayList<TeamModel>();
            for (Group g : searchGroups) {
                TeamModel teamModel = new TeamModel(g.getName());
                teamModel.accountType = AccountType.EXTERNAL;
                result.add(teamModel);

                for (TeamModel repoTeam : repo.getTeamModels()) {
                    if (teamModel.name.equals(repoTeam.name)) {
                        teamModel.permissions.putAll(repoTeam.permissions);
                        teamModel.canAdmin = repoTeam.canAdmin;
                        teamModel.canCreate = repoTeam.canCreate;
                        teamModel.canFork = repoTeam.canFork;
                    }
                }

            }
            return result;
        } catch (OperationFailedException e) {
            e.printStackTrace();
        } catch (InvalidAuthenticationException e) {
            e.printStackTrace();
        } catch (ApplicationPermissionException e) {
            e.printStackTrace();
        }
        return new ArrayList<TeamModel>();
    }

    @Override
    public List<String> getTeamNamesForRepositoryRole(final String role) {
        // return repo.repoTeams(role);
        try {
            return client.searchGroupNames(NullRestrictionImpl.INSTANCE, 0, 4096);
        } catch (OperationFailedException e) {
            e.printStackTrace();
        } catch (InvalidAuthenticationException e) {
            e.printStackTrace();
        } catch (ApplicationPermissionException e) {
            e.printStackTrace();
        }
        return new ArrayList<String>();
    }

    @Override
    public TeamModel getTeamModel(final String teamname) {
        TeamModel team = new TeamModel(teamname);
        return team;
    }

    @Override
    public boolean updateTeamModel(final TeamModel model) {
        return false;
    }

    @Override
    public boolean updateTeamModels(final Collection<TeamModel> models) {
        repo.updateTeamModels(models);
        return true;
    }

    @Override
    public boolean updateTeamModel(final String teamname, final TeamModel model) {
        return false;
    }

    @Override
    public boolean deleteTeamModel(final TeamModel model) {
        return false;
    }

    @Override
    public boolean deleteTeam(final String teamname) {
        return false;
    }

    @Override
    public List<String> getUsernamesForRepositoryRole(final String role) {
        return null;
    }

    @Override
    public boolean renameRepositoryRole(final String oldRole, final String newRole) {
        return false;
    }

    @Override
    public boolean deleteRepositoryRole(final String role) {
        return false;
    }

    public static RepositoryPermissionsManager getRepositoryManager() {
        return ExternalCrowdUserService.repo;
    }

    public static CrowdClient getCrowdClient() {
        return ExternalCrowdUserService.client;
    }

    public static CrowdHttpAuthenticator getCrowdAuthenticator() {
        return ExternalCrowdUserService.authenticator;
    }
}
