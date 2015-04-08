package org.obiba.git.gitblit;

import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.service.client.CrowdClient;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class CrowdUtilsTest {

    @Test
    public void testMapCrowdUserToModel() throws Exception {
        CrowdClient client = Mockito.mock(CrowdClient.class);
        RepositoryPermissionsManager repo = Mockito.mock(RepositoryPermissionsManager.class);
        User u = Mockito.mock(User.class);

        when(client.getUser("john.doe")).thenReturn(u);
        when(u.getName()).thenReturn("jdoe");
        when(u.getDisplayName()).thenReturn("John Doe");
        when(u.getEmailAddress()).thenReturn("john.doe@company.com");

        List<String> groups = new ArrayList<String>(2);
        groups.add("group1");
        groups.add("group2");
        when(client.getNamesOfGroupsForUser("jdoe", 0, 1024)).thenReturn(groups);

        Collection<TeamModel> teamModels = new ArrayList<TeamModel>();
        TeamModel teamModel1 = new TeamModel("group1");
        teamModel1.canAdmin = false;
        teamModel1.canCreate = true;
        teamModel1.canFork = false;
        teamModels.add(teamModel1);
        when(repo.getTeamModels()).thenReturn(teamModels);

        UserModel userModel = CrowdUtils.mapCrowdUserToModel(client, repo, "john.doe");

        assertEquals("John Doe", userModel.displayName);
        assertEquals("john.doe@company.com", userModel.emailAddress);
    }
}