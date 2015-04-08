package org.obiba.git.gitblit;

import com.atlassian.crowd.exception.ApplicationPermissionException;
import com.atlassian.crowd.exception.InvalidAuthenticationException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.service.client.CrowdClient;
import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

import java.util.List;

public class CrowdUtils {

    private CrowdUtils() {

    }

    public static UserModel mapCrowdUserToModel(final CrowdClient client, final RepositoryPermissionsManager repo,
                                                final User u) {
        UserModel um = new UserModel(u.getName());
        um.accountType = AccountType.EXTERNAL;
        um.displayName = u.getDisplayName();
        um.password = Constants.EXTERNAL_ACCOUNT;
        um.emailAddress = u.getEmailAddress();

        try {
            List<String> groups = client.getNamesOfGroupsForUser(u.getName(), 0, 1024);
            um.canAdmin = groups.contains("administrators");

            for (String g : groups) {
                TeamModel teamModel = new TeamModel(g);
                teamModel.accountType = AccountType.EXTERNAL;
                um.teams.add(teamModel);

                for (TeamModel repoTeam : repo.getTeamModels()) {
                    if (teamModel.name.equals(repoTeam.name)) {
                        teamModel.permissions.putAll(repoTeam.permissions);
                        teamModel.canAdmin = repoTeam.canAdmin;
                        teamModel.canCreate = repoTeam.canCreate;
                        teamModel.canFork = repoTeam.canFork;
                    }
                }
            }
        } catch (UserNotFoundException e) {
            e.printStackTrace();
        } catch (OperationFailedException e) {
            e.printStackTrace();
        } catch (InvalidAuthenticationException e) {
            e.printStackTrace();
        } catch (ApplicationPermissionException e) {
            e.printStackTrace();
        }

        return um;
    }

    public static UserModel mapCrowdUserToModel(final CrowdClient client, final RepositoryPermissionsManager repo,
                                                final String username) {
        try {
            User u = client.getUser(username);
            return mapCrowdUserToModel(client, repo, u);
        } catch (UserNotFoundException e) {
            e.printStackTrace();
        } catch (OperationFailedException e) {
            e.printStackTrace();
        } catch (ApplicationPermissionException e) {
            e.printStackTrace();
        } catch (InvalidAuthenticationException e) {
            e.printStackTrace();
        }
        return new UserModel(username);
    }
}
